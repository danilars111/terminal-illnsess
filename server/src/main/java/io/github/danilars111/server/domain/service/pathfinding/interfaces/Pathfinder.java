package io.github.danilars111.server.domain.service.pathfinding.interfaces;

import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;

public interface Pathfinder {
    PathResult findPath(GameState gameState, PathfindingRequest request);
}