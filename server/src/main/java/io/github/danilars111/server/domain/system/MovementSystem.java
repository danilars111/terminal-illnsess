package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MovementSystem implements GameSystem {

    private static final Logger LOGGER = System.getLogger(MovementSystem.class.getName());
    public static final String COOLDOWN_KEY = "system:movement";
    private static final double SQRT_2 = Math.sqrt(2);

    private final GameWorld world;
    private final int defaultCooldownTicks;
    private final boolean allowDiagonalMovement;

    public MovementSystem(GameWorld world, int defaultCooldownTicks, boolean allowDiagonalMovement) {
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.defaultCooldownTicks = Math.max(0, defaultCooldownTicks);
        this.allowDiagonalMovement = allowDiagonalMovement;
    }

    public MovementSystem(GameWorld world, int defaultCooldownTicks) {
        this(world, defaultCooldownTicks, true);
    }

    @Override
    public void update(GameState gameState, long tick) {
        // 1. Gather all entities with MoveIntentComponent
        Set<EntityId> intendingEntities = gameState.getEntitiesWith(MoveIntentComponent.class);
        if(intendingEntities.isEmpty()) {
            return;
        }

        // 2. Sort determinististically: requestedTick first, then EntityId as tie-breaker)
        List<EntityId> sortedEntities = intendingEntities.stream()
                .sorted(Comparator
                        .comparingLong((EntityId id) ->
                                gameState.getComponent(id, MoveIntentComponent.class)
                                        .orElseThrow().requestedTick())
                        .thenComparing(EntityId::value)).toList();

        // 3. Process entities intent in sorted order
        for (EntityId entityId : sortedEntities) {
            MoveIntentComponent intent = gameState.getComponent(entityId, MoveIntentComponent.class)
                    .orElse(null);
            if (intent == null) {
                continue;
            }

            try {
                processEntityMove(gameState, entityId, intent, tick);
            } finally {
                gameState.removeComponent(entityId, MoveIntentComponent.class);
            }
        }
    }

    protected void processEntityMove(GameState gameState, EntityId entityId, MoveIntentComponent intent, long tick) {
        int dx = intent.deltaX();
        int dy = intent.deltaY();

        // Validate step bounds (max 1 tile in any direction)
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) {
            return;
        }

        boolean isDiagonal = dx != 0 && dy != 0;
        if(!allowDiagonalMovement && isDiagonal) {
            return;
        }

        PositionComponent currentPos = gameState.getComponent(entityId, PositionComponent.class).orElse(null);
        if(currentPos == null) {
            return;
        }

        // Validate Cooldown
        CooldownComponent cooldown = gameState.getComponent(entityId, CooldownComponent.class)
                .orElseGet(CooldownComponent::empty);

        if(cooldown.isOnCooldown(COOLDOWN_KEY)) {
            LOGGER.log(Level.DEBUG, "Entity {0} is on cooldown, move dropped", entityId);
            return;
        }

        int targetX = currentPos.getX() + dx;
        int targetY = currentPos.getY() + dy;

        Set<TraversalMode> modes = gameState.getComponent(entityId, TraversalComponent.class)
                .map(TraversalComponent::activeModes).orElse(Set.of());

        //Validate destination tile passability
        if(!world.canPass(targetX, targetY, modes)) {
            return;
        }

        // Permissive diagonal check: at least ONE cardinal sie must be passable
        if(isDiagonal) {
            boolean sideA = world.canPass(currentPos.getX() + dx, currentPos.getY(), modes);
            boolean sideB = world.canPass(currentPos.getX(), currentPos.getY() + dy, modes);
            if(!sideA && !sideB) {
                return;
            }
        }

        // Commit Position
        PositionComponent newPosition = currentPos.translate(dx, dy);
        gameState.addComponent(entityId, newPosition);

        // Apply Cooldown (~1.414x for diagonal steps)
        if(defaultCooldownTicks > 0 ) {
            int appliedCooldown = isDiagonal ? (int) Math.round(defaultCooldownTicks * SQRT_2) : defaultCooldownTicks;
            gameState.addComponent(entityId, cooldown.withCooldown(COOLDOWN_KEY, appliedCooldown));
        }
        LOGGER.log(Level.DEBUG, "Entity {0} moved from ({1}, {2}) to ({3}, {4}) on tick {5}",
                entityId.value(), currentPos.getX(), currentPos.getY(),
                newPosition.getX(), newPosition.getY(), tick);
    }

}
