package io.github.danilars111.server.domain.service.pathfinding.interfaces;

import io.github.danilars111.server.domain.service.pathfinding.records.Path;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Optional;

public sealed interface PathResult {

    enum FailureReason {
        UNREACHABLE,       // Destination exists in a disconnected graph partition
        BUDGET_EXHAUSTED,  // Search step limit hit (partial path / too far)
        START_IMPASSABLE   // Mover's start tile is invalid/blocked
    }

    /**
     * Successfully reached the exact destination.
     */
    record Success(Path path) implements PathResult {}

    /**
     * Could not reach the exact destination.
     * Provides the failure reason and the closest reachable waypoint found during the search.
     */
    record Failure(
            FailureReason reason,
            Optional<Path> partialPathToClosest,
            TileCoord closestReachableCoord
    ) implements PathResult {}
}
