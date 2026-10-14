package io.github.danilars111.server.domain.service.pathfinding.interfaces;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.nio.file.InvalidPathException;

@FunctionalInterface
public interface PathValidator {
    PathValidationResult validate(
            EntityId entityId,
            PathFollowComponent pathFollow,
            int lookaheadSteps
    );
}