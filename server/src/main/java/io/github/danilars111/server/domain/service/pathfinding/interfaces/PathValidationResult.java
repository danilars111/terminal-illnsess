package io.github.danilars111.server.domain.service.pathfinding.interfaces;

import io.github.danilars111.server.domain.world.records.TileCoord;

public sealed interface PathValidationResult {

    record Valid() implements PathValidationResult {}

    /**
     * The path cannot be traversed anymore (hard obstacle encountered).
     */
    record Blocked(TileCoord blockedAt, int waypointIndex) implements PathValidationResult {}

    /**
     * Path is still traversable, but traversal cost increased beyond the accepted tolerance.
     */
    record CostInflated(TileCoord atCoord, long expectedCost, long actualCost) implements PathValidationResult {}
}
