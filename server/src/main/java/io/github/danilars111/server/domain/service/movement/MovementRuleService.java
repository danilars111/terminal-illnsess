package io.github.danilars111.server.domain.service.movement;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MovementRuleService {

    public enum DiagonalMode {
        NONE,       // Disallowed diagonal movement
        STRICT,     // Allowed only if neither flank is blocked
        PERMISSIVE, // Allowed if at most one flank is blocked
        FULL        // Unrestricted diagonal movement
    }

    private final World world;
    private final DiagonalMode diagonalMode;

    public MovementRuleService(World world, DiagonalMode diagonalMode) {
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.diagonalMode = Objects.requireNonNull(diagonalMode, "DiagonalMode cannot be null");
    }

    public MovementRuleService(World world) {
        this(world, DiagonalMode.PERMISSIVE);
    }

    /**
     * Checks if a single tile is blocked on a specific level by terrain or dynamic colliders.
     */
    public boolean isTileBlocked(GameState state, String levelId, TileCoord tileCoord, EntityId mover) {
        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);

        if (modes.contains(TraversalMode.NOCLIP)) {
            return false;
        }

        // Out of bounds is impassable
        if (!world.isWithinBounds(levelId, tileCoord)) {
            return true;
        }

        // Static terrain passability check via TilePalette lookup
        TileDefinition tileDef = world.getTileDefinition(levelId, tileCoord);
        if (tileDef == null || !isTerrainPassable(tileDef, modes)) {
            return true;
        }

        // Dynamic collider check scoped to the active level
        return state.getSpatialIndex().getEntitiesAt(levelId, tileCoord).stream()
                .filter(otherId -> !otherId.equals(mover))
                .anyMatch(otherId -> state.getComponent(otherId, ColliderComponent.class).isPresent());
    }
    private boolean isTileBlocked(GameState state, String levelId, int x, int y, EntityId mover) {
        return isTileBlocked(state, levelId, new TileCoord(x, y), mover);
    }

    private boolean isTerrainPassable(TileDefinition tileDef, Set<TraversalMode> modes) {
        for (TraversalMode mode : modes) {
            if (tileDef.isTraversable(mode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates corner cutting for diagonals based on the configured DiagonalMode.
     */
    private boolean isDiagonalBlocked(
            GameState state,
            String levelId,
            TileCoord fromCoord,
            int dx,
            int dy,
            EntityId mover
    ) {
        if (diagonalMode == DiagonalMode.FULL) {
            return false;
        }
        if (diagonalMode == DiagonalMode.NONE) {
            return true;
        }

        FootprintComponent footprint = FootprintComponent.getFootprintFrom(state, mover);

        for (TileCoord coord : footprint.getOccupiedCoords(fromCoord)) {
            boolean flankABlocked = isTileBlocked(state, levelId, coord.getX() + dx, coord.getY(), mover);
            boolean flankBBlocked = isTileBlocked(state, levelId, coord.getX(), coord.getY() + dy, mover);

            if (diagonalMode == DiagonalMode.PERMISSIVE && flankABlocked && flankBBlocked) {
                return true;
            }
            if (diagonalMode == DiagonalMode.STRICT && (flankABlocked || flankBBlocked)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates if an entity can move from an origin coordinate to a target coordinate on a level.
     */
    public boolean canTraverseStep(
            GameState state,
            EntityId mover,
            String levelId,
            TileCoord fromCoord,
            TileCoord toCoord
    ) {
        int dx = toCoord.getX() - fromCoord.getX();
        int dy = toCoord.getY() - fromCoord.getY();

        // Must move exactly 1 step in an adjacent tile
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0)) {
            return false;
        }

        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);
        if (modes.contains(TraversalMode.NOCLIP)) {
            return true;
        }

        boolean isDiagonal = dx != 0 && dy != 0;
        if (isDiagonal && diagonalMode == DiagonalMode.NONE) {
            return false;
        }

        if (isDiagonal && isDiagonalBlocked(state, levelId, fromCoord, dx, dy, mover)) {
            return false;
        }

        return !isFootprintBlocked(state, mover, levelId, toCoord);
    }

    /**
     * Convenience overload that extracts the current levelId from the mover's PositionComponent.
     */
    public boolean canTraverseStep(
            GameState state,
            EntityId mover,
            TileCoord toCoord
    ) {
        PositionComponent pos = state.getComponent(mover, PositionComponent.class)
                .orElseThrow(() -> new IllegalArgumentException("Mover entity " + mover + " has no PositionComponent"));
        return canTraverseStep(state, mover, pos.levelId(), pos.getTileCoord(), toCoord);
    }

    public boolean isFootprintBlocked(GameState state, EntityId mover, String levelId, TileCoord anchorTile) {
        Set<TraversalMode> modes = TraversalComponent.getActiveModesFrom(state, mover);
        if (modes.contains(TraversalMode.NOCLIP)) {
            return false;
        }

        FootprintComponent footprint = FootprintComponent.getFootprintFrom(state, mover);
        List<TileCoord> targetCoords = footprint.getOccupiedCoords(anchorTile);
        for (TileCoord coord : targetCoords) {
            if (isTileBlocked(state, levelId, coord, mover)) {
                return true;
            }
        }
        return false;
    }

    public World getWorld() {
        return world;
    }

    public DiagonalMode getDiagonalMode() {
        return diagonalMode;
    }
}