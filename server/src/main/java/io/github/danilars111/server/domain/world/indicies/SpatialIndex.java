package io.github.danilars111.server.domain.world.indicies;

import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.apache.catalina.security.SecurityUtil.remove;

public class SpatialIndex {

    // One tile can hold multiple entities
    private Map<TileCoord, Set<EntityId>> tileToEntities = new HashMap<>();
    private Map<EntityId, TileCoord> entityToTile = new HashMap<>();

    public void move(EntityId entityId, int toX, int toY) {
        remove(entityId);
        TileCoord target = new TileCoord(toX, toY);
        tileToEntities.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(entityId);
        entityToTile.put(entityId, target);
    }
    public void remove(EntityId entityId) {
        TileCoord current = entityToTile.remove(entityId);
        if(current != null) {
            Set<EntityId> entities = tileToEntities.get(current);
            if(entities != null) {
                entities.remove(entityId);
                if(entities.isEmpty()) {
                    tileToEntities.remove(current);
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

    public Optional<TileCoord> getPositionOf(EntityId entityId) {
        return Optional.ofNullable(entityToTile.get(entityId));
    }

    /**
     * Radial range query for entities within a given radius
     * Uses Euclidean distance
     */
    public List<EntityId> getEntitiesInRadius(int centerX, int centerY, int radius) {
        List<EntityId> found = new ArrayList<>();
        int radiusSquared = radius * radius;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                // Euclidean circle constraint: dx^2 + dy^2 <= r^2
                if (dx * dx + dy * dy <= radiusSquared) {
                    Set<EntityId> onTile = tileToEntities.get(new TileCoord(centerX + dx, centerY + dy));
                    if (onTile != null) {
                        found.addAll(onTile);
                    }
                }
            }
        }
        return found;
    }
}
