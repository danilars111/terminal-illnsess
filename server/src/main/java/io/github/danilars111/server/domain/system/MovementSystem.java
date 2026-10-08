package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.indices.SpatialIndex;
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
        Set<EntityId> intendingEntities = gameState.getEntitiesWith(MoveIntentComponent.class);
        if (intendingEntities.isEmpty()) {
            return;
        }

        List<EntityId> sortedEntities = intendingEntities.stream()
                .sorted(Comparator
                        .comparingLong((EntityId id) ->
                                gameState.getComponent(id, MoveIntentComponent.class)
                                        .orElseThrow().requestedTick())
                        .thenComparing(EntityId::value))
                .toList();

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

        // 1. Validate step bounds (max 1 tile in any direction)
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) {
            return;
        }

        boolean isDiagonal = dx != 0 && dy != 0;
        if (!allowDiagonalMovement && isDiagonal) {
            return;
        }

        PositionComponent currentPos = gameState.getComponent(entityId, PositionComponent.class).orElse(null);
        if (currentPos == null) {
            return;
        }

        // 2. Validate Cooldown
        CooldownComponent cooldown = gameState.getComponent(entityId, CooldownComponent.class)
                .orElseGet(CooldownComponent::empty);

        if (cooldown.isOnCooldown(COOLDOWN_KEY)) {
            LOGGER.log(Level.DEBUG, "Entity {0} is on cooldown, move dropped", entityId.value());
            return;
        }

        int targetX = currentPos.getX() + dx;
        int targetY = currentPos.getY() + dy;

        Set<TraversalMode> modes = gameState.getComponent(entityId, TraversalComponent.class)
                .map(TraversalComponent::activeModes)
                .orElse(Set.of());

        boolean isNoClip = modes.contains(TraversalMode.NOCLIP);

        // 3. Collision & Passability checks (completely skipped if NOCLIP)
        if (!isNoClip) {
            FootprintComponent footprint = gameState.getComponent(entityId, FootprintComponent.class)
                    .orElseGet(FootprintComponent::singleTile);

            List<TileCoord> targetCoords = footprint.getOccupiedCoords(targetX, targetY);

            // 3a. Static terrain passability
            for (TileCoord coord : targetCoords) {
                if (!world.canPass(coord.getX(), coord.getY(), modes)) {
                    return;
                }
            }

            // 3b. Permissive diagonal flank check (terrain + dynamic obstacles)
            if (isDiagonal) {
                for (TileCoord coord : footprint.getOccupiedCoords(currentPos.getX(), currentPos.getY())) {
                    boolean sideABlocked = isBlocked(gameState, coord.getX() + dx, coord.getY(), modes, entityId);
                    boolean sideBBlocked = isBlocked(gameState, coord.getX(), coord.getY() + dy, modes, entityId);
                    if (sideABlocked && sideBBlocked) {
                        return; // Pinched corner
                    }
                }
            }

            // 3c. Dynamic entity collision check at destination
            SpatialIndex spatialIndex = gameState.getSpatialIndex();
            for (TileCoord coord : targetCoords) {
                Set<EntityId> entitiesOnTile = spatialIndex.getEntitiesAt(coord.getX(), coord.getY());

                // Movers respect colliders regardless of whether the mover itself has a ColliderComponent
                boolean blocked = entitiesOnTile.stream()
                        .filter(otherId -> !otherId.equals(entityId)) // Ignore self
                        .anyMatch(otherId -> gameState.getComponent(otherId, ColliderComponent.class).isPresent());

                if (blocked) {
                    LOGGER.log(Level.DEBUG, "Entity {0} blocked by collision at ({1}, {2})",
                            entityId.value(), coord.getX(), coord.getY());
                    return;
                }
            }
        }

        // 4. Commit position (GameState triggers SpatialIndex.updateEntity)
        PositionComponent newPosition = currentPos.translate(dx, dy);
        gameState.addComponent(entityId, newPosition);

        // 5. Cooldown application (~1.414x for diagonal steps)
        if (defaultCooldownTicks > 0) {
            int appliedCooldown = isDiagonal ? (int) Math.round(defaultCooldownTicks * SQRT_2) : defaultCooldownTicks;
            gameState.addComponent(entityId, cooldown.withCooldown(COOLDOWN_KEY, appliedCooldown));
        }

        LOGGER.log(Level.DEBUG, "Entity {0} moved from ({1}, {2}) to ({3}, {4}) on tick {5}",
                entityId.value(), currentPos.getX(), currentPos.getY(),
                newPosition.getX(), newPosition.getY(), tick);
    }

    private boolean isBlocked(GameState gameState, int x, int y, Set<TraversalMode> modes, EntityId selfId) {
        if (!world.canPass(x, y, modes)) {
            return true;
        }
        return gameState.getSpatialIndex().getEntitiesAt(x, y).stream()
                .filter(otherId -> !otherId.equals(selfId))
                .anyMatch(otherId -> gameState.getComponent(otherId, ColliderComponent.class).isPresent());
    }

}