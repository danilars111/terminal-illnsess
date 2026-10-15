package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.EntityManager;
import io.github.danilars111.server.domain.ecs.listener.ComponentChangeListener;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SpatialIndex {

    public record LevelTileCoord(String levelId, TileCoord coord) {
        public LevelTileCoord {
            Objects.requireNonNull(levelId, "levelId cannot be null");
            Objects.requireNonNull(coord, "coord cannot be null");
        }

        public LevelTileCoord(String levelId, int x, int y) {
            this(levelId, new TileCoord(x, y));
        }
    }

    private final Map<LevelTileCoord, Set<EntityId>> tileToEntities = new HashMap<>();
    private final Map<EntityId, Set<LevelTileCoord>> entityToTiles = new HashMap<>();

    public void updateEntity(EntityId entityId, PositionComponent position, FootprintComponent footprint) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        remove(entityId);

        String levelId = position.levelId();
        TileCoord anchor = position.getTileCoord();
        List<TileCoord> occupiedOffsets = (footprint != null)
                ? footprint.getOccupiedCoords(anchor)
                : List.of(anchor);

        Set<LevelTileCoord> occupiedLevelCoords = new HashSet<>();

        for (TileCoord coord : occupiedOffsets) {
            LevelTileCoord levelCoord = new LevelTileCoord(levelId, coord);
            occupiedLevelCoords.add(levelCoord);
            tileToEntities.computeIfAbsent(levelCoord, k -> new HashSet<>()).add(entityId);
        }

        entityToTiles.put(entityId, occupiedLevelCoords);
    }

    public void remove(EntityId entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Set<LevelTileCoord> oldCoords = entityToTiles.remove(entityId);
        if (oldCoords == null) {
            return;
        }

        for (LevelTileCoord levelCoord : oldCoords) {
            Set<EntityId> occupants = tileToEntities.get(levelCoord);
            if (occupants != null) {
                occupants.remove(entityId);
                if (occupants.isEmpty()) {
                    tileToEntities.remove(levelCoord);
                }
            }
        }
    }

    public Set<EntityId> getEntitiesAt(String levelId, int x, int y) {
        return getEntitiesAt(levelId, new TileCoord(x, y));
    }

    public Set<EntityId> getEntitiesAt(String levelId, TileCoord coord) {
        Set<EntityId> occupants = tileToEntities.get(new LevelTileCoord(levelId, coord));
        if (occupants == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(occupants);
    }

    public Set<TileCoord> getFootprintOf(EntityId entityId) {
        Set<LevelTileCoord> levelCoords = entityToTiles.get(entityId);
        if (levelCoords == null) {
            return Collections.emptySet();
        }

        Set<TileCoord> coords = new HashSet<>(levelCoords.size());
        for (LevelTileCoord ltc : levelCoords) {
            coords.add(ltc.coord());
        }
        return Collections.unmodifiableSet(coords);
    }

    public List<EntityId> getEntitiesInRadius(String levelId, int centerX, int centerY, int radius) {
        Objects.requireNonNull(levelId, "levelId cannot be null");
        if (radius < 0) {
            return Collections.emptyList();
        }

        Set<EntityId> uniqueEntities = new HashSet<>();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radiusSq) {
                    LevelTileCoord query = new LevelTileCoord(levelId, centerX + dx, centerY + dy);
                    Set<EntityId> occupants = tileToEntities.get(query);
                    if (occupants != null) {
                        uniqueEntities.addAll(occupants);
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

    public void bindTo(EntityManager entityManager) {
        Objects.requireNonNull(entityManager, "entityManager cannot be null");

        // Position updates
        entityManager.addComponentListener(PositionComponent.class, new ComponentChangeListener<PositionComponent>() {
            @Override
            public void onComponentAddedOrReplaced(EntityId entityId, PositionComponent position) {
                FootprintComponent footprint = entityManager.getComponent(entityId, FootprintComponent.class)
                        .orElse(FootprintComponent.singleTile());
                updateEntity(entityId, position, footprint);
            }
            @Override
            public void onComponentRemoved(EntityId entityId, PositionComponent position) {
                remove(entityId);
            }
        });

        entityManager.addComponentListener(FootprintComponent.class, new ComponentChangeListener<FootprintComponent>() {
            @Override
            public void onComponentAddedOrReplaced(EntityId entityId, FootprintComponent footprint) {
                entityManager.getComponent(entityId, PositionComponent.class)
                        .ifPresent(positionComponent -> updateEntity(entityId, positionComponent, footprint));
            }
            @Override
            public void onComponentRemoved(EntityId entityId, FootprintComponent component) {
                entityManager.getComponent(entityId, PositionComponent.class)
                        .ifPresent(positionComponent -> updateEntity(entityId, positionComponent, component));
            }
        });

        entityManager.addEntityDestroyedListener(this::remove);

    }
}