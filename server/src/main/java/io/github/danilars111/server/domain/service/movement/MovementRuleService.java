package io.github.danilars111.server.domain.service.movement;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MovementRuleService {

    public enum DiagonalMode {
        NONE, // Disallowed diagonal movement
        STRICT, // Allowed if no flanks are blocked
        PERMISSIVE, // Allowed if one, and only one flank are blocked
        FULL // Allow all diagonal movement
    }

    private final GameWorld world;

    // TODO:
    // Will be configurable from .properties file later
    private final DiagonalMode diagonalMode;

    public MovementRuleService(GameWorld world, DiagonalMode diagonalMode) {
        this.world = Objects.requireNonNull(world, "GameWorld cannot be null");
        this.diagonalMode = Objects.requireNonNull(diagonalMode, "DiagonalMode cannot be null");
    }

    public MovementRuleService(GameWorld world) {
        this(world, DiagonalMode.PERMISSIVE);
    }

    /**
     * Checks if a single tile is blocked by static terrain or dynamic colliders
     */
    private boolean isTileBlocked(GameState state, TileCoord tileCoord,EntityId mover) {

        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);

        if (!world.canPass(tileCoord, modes)) {
            return true;
        }
        return state.getSpatialIndex().getEntitiesAt(tileCoord).stream()
                .filter(otherId -> !otherId.equals(mover)) // Ignore self
                .anyMatch(otherId -> state.getComponent(otherId, ColliderComponent.class).isPresent());
    }

    private boolean isTileBlocked(GameState state, int x, int y, EntityId mover) {
        return isTileBlocked(state, new TileCoord(x, y), mover);
    }

    /**
     * Evaluates whether a diagonal step from (fromX, fromY) to (fromX + dx, fromY + dy)
     * is blocked by pinched/cutting corners for the given footprint and diagonalMode.
     */
    private boolean isDiagonalBlocked(
        GameState state,
        TileCoord fromCoord,
        int dx,
        int dy,
        EntityId mover
    ) {
        if(diagonalMode == DiagonalMode.FULL) {
            return false;
        }
        if (diagonalMode == DiagonalMode.NONE) {
            return true;
        }

        FootprintComponent footprint = FootprintComponent.getFootprintFrom(state, mover);

        for (TileCoord coord : footprint.getOccupiedCoords(fromCoord)) {
            boolean flankABlocked = isTileBlocked(state, coord.getX() + dx, coord.getY(), mover);
            boolean flankBBlocked = isTileBlocked(state, coord.getX(), coord.getY() + dy, mover);

            if (diagonalMode == DiagonalMode.PERMISSIVE) {
                if (flankABlocked && flankBBlocked) {
                    return true;
                }
            }
            if(diagonalMode == DiagonalMode.STRICT) {
                if (flankABlocked || flankBBlocked) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Validates if an entity can move from an origin coordinate to a target coordinate.
     */
    public boolean canTraverseStep(
            GameState state,
            EntityId mover,
            TileCoord fromCoord,
            TileCoord toCoord
    ) {
        int dx = toCoord.getX() - fromCoord.getX();
        int dy = toCoord.getY() - fromCoord.getY();

        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);

        //  Bound check (max 1 tile in any direction)
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) {
            return false;
        }

        // NoClip bypasses all collision and terrain rules
        if (modes.contains(TraversalMode.NOCLIP)) {
            return true;
        }

        boolean isDiagonal = dx != 0 && dy != 0;
        if (isDiagonal && diagonalMode == DiagonalMode.NONE) {
            return false;
        }


        // Diagonal corner pinch/cutting rule
        if (isDiagonal && isDiagonalBlocked(state, fromCoord, dx, dy, mover)) {
            return false;
        }

        // Target footprint tiles check (static terrain + dynamic colliders)
        return !isFootprintBlocked(state, mover, toCoord);
    }

    public boolean isFootprintBlocked(GameState state, EntityId mover, TileCoord anchorTile) {
        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);

        if (modes.contains(TraversalMode.NOCLIP)) {
            return false;
        }

        FootprintComponent footprint = FootprintComponent.getFootprintFrom(state, mover);
        List<TileCoord> targetCoords = footprint.getOccupiedCoords(anchorTile);
        for (TileCoord coord : targetCoords) {
            if (isTileBlocked(state, coord, mover)) {
                return true;
            }
        }
        return false;
    }
}
