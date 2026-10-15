package io.github.danilars111.server.domain.ecs.listener;

import io.github.danilars111.server.domain.ecs.records.EntityId;

@FunctionalInterface
public interface EntityDestroyedListener {
    void onEntityDestroyed(EntityId entityId);
}
