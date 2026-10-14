package io.github.danilars111.server.domain.service.pathfinding.records;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Objects;
import java.util.Set;

public record PathfindingRequest(
        EntityId entityId,
        TileCoord start,
        TileCoord goal
) {
    public PathfindingRequest {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        Objects.requireNonNull(start, "Start coordinate cannot be null");
        Objects.requireNonNull(goal, "Goal coordinate cannot be null");
    }
}
