package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.world.World;

import java.util.Objects;

public class GameState {

    private final EntityManager entityManager;
    private final SpatialIndex spatialIndex;
    private final World world;

    public GameState(EntityManager entityManager, SpatialIndex spatialIndex, World world) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex must not be null");
        this.world = Objects.requireNonNull(world, "world must not be null");

        this.spatialIndex.bindTo(this.entityManager);
    }

    public World world() {
        return world;
    }

    public EntityManager entityManager() {
        return entityManager;
    }

    public SpatialIndex spatialIndex() {
        return spatialIndex;
    }
}