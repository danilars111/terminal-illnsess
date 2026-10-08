package io.github.danilars111.server.domain.world.indices;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SpatialIndex {

    // One tile can hold multiple entities
    private Map<TileCoord, Set<EntityId>> tileToEntities = new HashMap<>();
    private Map<EntityId, Set<TileCoord>> entityToTiles = new HashMap<>();

    public void updateEntity(EntityId entityId, PositionComponent pos) {
        updateEntity(entityId, pos, null);
    }

    /**
     * Updates an entity's occupied footprint in the index.
     * If footprint is null, defaults to a single tile at (pos.x(), pos.y()).
     */
    public void updateEntity(EntityId entityId, PositionComponent pos, FootprintComponent footprint) {
       remove(entityId);

       List<TileCoord> occupiedCoords = footprint != null
               ? footprint.getOccupiedCoords(pos.getX(), pos.getY())
               : List.of(new TileCoord(pos.getX(), pos.getY()));

       Set<TileCoord> tiles = new LinkedHashSet<>(occupiedCoords);
       entityToTiles.put(entityId, tiles);
       for (TileCoord tile : tiles) {
           tileToEntities.computeIfAbsent(tile, k -> new LinkedHashSet<>()).add(entityId);
       }
   }

    /**
     * Completely removes an entity from all tiles it occupies.
     */
    public void remove(EntityId entityId) {
        Set<TileCoord> currentTiles = entityToTiles.remove(entityId);
        if(currentTiles != null) {
            for(TileCoord tile : currentTiles) {
                Set<EntityId> occupants = tileToEntities.get(tile);
                if(occupants != null) {
                    occupants.remove(entityId);
                    if(occupants.isEmpty()) {
                        tileToEntities.remove(tile);
                    }
                }
            }
        }
    }

    public Set<EntityId> getEntitiesAt(int x, int y) {
        return getEntitiesAt(new TileCoord(x, y));
    }
    public Set<EntityId> getEntitiesAt(TileCoord coord) {
        return Collections.unmodifiableSet(tileToEntities.getOrDefault(coord, Set.of()));
    }

    /**
     * Returns all world tiles currently covered by this entity.
     */
    public Set<TileCoord> getFootprintOf(EntityId entityId) {
        return Collections.unmodifiableSet(
                entityToTiles.getOrDefault(entityId, Set.of())
        );
    }

    /**
     * Radial Euclidean query: returns unique entities overlapping any tile inside the radius.
     */
    public List<EntityId> getEntitiesInRadius(int centerX, int centerY, int radius) {
        Set<EntityId> uniqueEntities = new LinkedHashSet<>();
        int radiusSquared = radius * radius;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radiusSquared) {
                    Set<EntityId> onTile = tileToEntities.get(new TileCoord(centerX + dx, centerY + dy));
                    if (onTile != null) {
                        uniqueEntities.addAll(onTile);
                    }
                }
            }
        }
        return new ArrayList<>(uniqueEntities);
    }

    public void clear() {
        tileToEntities.clear();
        entityToTiles.clear();
    }
}
