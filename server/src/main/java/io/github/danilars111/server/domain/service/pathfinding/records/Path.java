package io.github.danilars111.server.domain.service.pathfinding.records;

import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.List;

public record Path(
        List<TileCoord> waypoints,
        List<Long> stepCostUnits,
        long totalCostUnits
) {
    public Path {
        waypoints = waypoints != null ? List.copyOf(waypoints) : List.of();
        stepCostUnits =  stepCostUnits != null ? List.copyOf(stepCostUnits) : List.of();
    }

    public Path(List<TileCoord> waypoints, long totalCostUnits) {
        this(waypoints, List.of(), totalCostUnits);
    }

    public static Path empty() {
        return new Path(List.of(), List.of(), 0L);
    }

    public static Path atDestination(TileCoord destination) {
        return new Path(List.of(destination), List.of(0L), 0L);
    }

    public boolean contains(TileCoord coord) { return waypoints.contains(coord); }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public long totalDurationTicksCeil() {
        return EngineTime.unitsToTicksCeil(totalCostUnits);
    }

    public long totalDurationTicksRound() {
        return EngineTime.unitsToTicksRound(totalCostUnits);
    }
}