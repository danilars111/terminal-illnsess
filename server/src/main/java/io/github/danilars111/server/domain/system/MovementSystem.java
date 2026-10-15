package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MovementSystem implements GameSystem {

    private static final Logger LOGGER = System.getLogger(MovementSystem.class.getName());
    public static final String COOLDOWN_KEY = "system:movement";

    // Fixed-point scale for sqrt(2) * 1000 = 1414
    private static final long DIAGONAL_SCALE_NUMERATOR = EngineTime.SQRT_2;
    private static final long SCALE_DENOMINATOR = EngineTime.TICK_SCALE;

    private final MovementRuleService movementRuleService;
    private final int defaultCooldownTicks;

    public MovementSystem(MovementRuleService movementRuleService, int defaultCooldownTicks) {
        this.movementRuleService = Objects.requireNonNull(movementRuleService, "movementRuleService cannot be null");
        this.defaultCooldownTicks = Math.max(0, defaultCooldownTicks);
    }

    @Override
    public void update(GameState gameState, long tick) {
        Set<EntityId> intendingEntities = gameState.entityManager().getEntitiesWith(MoveIntentComponent.class);
        if (intendingEntities.isEmpty()) {
            return;
        }

        // Deterministic sorting: by request tick first, tie-break with EntityId
        List<EntityId> sortedEntities = intendingEntities.stream()
                .sorted(Comparator
                        .comparingLong((EntityId id) ->
                                gameState.entityManager().getComponent(id, MoveIntentComponent.class)
                                        .orElseThrow().requestedTick())
                        .thenComparing(EntityId::value))
                .toList();

        for (EntityId entityId : sortedEntities) {
            MoveIntentComponent intent = gameState.entityManager().getComponent(entityId, MoveIntentComponent.class)
                    .orElse(null);
            if (intent == null) {
                continue;
            }

            try {
                processEntityMove(gameState, entityId, intent, tick);
            } finally {
                gameState.entityManager().removeComponent(entityId, MoveIntentComponent.class);
            }
        }
    }

    protected void processEntityMove(GameState gameState, EntityId entityId, MoveIntentComponent intent, long tick) {
        int dx = intent.deltaX();
        int dy = intent.deltaY();

        PositionComponent currentPos = gameState.entityManager().getComponent(entityId, PositionComponent.class).orElse(null);
        if (currentPos == null) {
            return;
        }

        // 1. Validate Cooldown
        CooldownComponent cooldown = gameState.entityManager().getComponent(entityId, CooldownComponent.class)
                .orElseGet(CooldownComponent::empty);

        if (cooldown.isOnCooldown(COOLDOWN_KEY)) {
            LOGGER.log(Level.DEBUG, "Entity {0} is on cooldown, move dropped", entityId.value());
            return;
        }

        TileCoord fromCoord = currentPos.getTileCoord();
        TileCoord toCoord = new TileCoord(currentPos.getX() + dx, currentPos.getY() + dy);

        // 2. Delegate step bounds, diagonal modes, pinch, terrain, and colliders on the active level
        if (!movementRuleService.canTraverseStep(gameState, entityId, currentPos.levelId(), fromCoord, toCoord)) {
            LOGGER.log(Level.DEBUG, "Entity {0} move from {1} to {2} on level {3} rejected by movement rules",
                    entityId.value(), fromCoord, toCoord, currentPos.levelId());
            return;
        }

        // 3. Commit position (SpatialIndex updates automatically via listener)
        PositionComponent newPosition = currentPos.translate(dx, dy);
        gameState.entityManager().addComponent(entityId, newPosition);

        // 4. Cooldown application using fixed-point integer rounding
        boolean isDiagonal = (dx != 0 && dy != 0);
        if (defaultCooldownTicks > 0) {
            int appliedCooldown = isDiagonal
                    ? (int) ((defaultCooldownTicks * DIAGONAL_SCALE_NUMERATOR + (SCALE_DENOMINATOR / 2)) / SCALE_DENOMINATOR)
                    : defaultCooldownTicks;
            gameState.entityManager().addComponent(entityId, cooldown.withCooldown(COOLDOWN_KEY, appliedCooldown));
        }

        LOGGER.log(Level.DEBUG, "Entity {0} moved from {1} to {2} on level {3} at tick {4}",
                entityId.value(), fromCoord, toCoord, currentPos.levelId(), tick);
    }
}