package io.github.danilars111.server.domain.service.pathfinding.records;

import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathTest {

    @Test
    @DisplayName("Path represents an immutable sequence of waypoints with total duration in fixed-point units")
    void pathRecordIntegrity() {
        List<TileCoord> waypoints = List.of(new TileCoord(0, 0), new TileCoord(1, 0));
        List<Long> stepCosts = List.of(0L, EngineTime.ticksToUnits(4)); // 0, 4000
        long totalCostUnits = EngineTime.ticksToUnits(4);               // 4000

        Path path = new Path(waypoints, stepCosts, totalCostUnits);

        assertEquals(2, path.waypoints().size());
        assertEquals(stepCosts, path.stepCostUnits());
        assertEquals(4_000L, path.totalCostUnits());
        assertEquals(4L, path.totalDurationTicksCeil());
        assertEquals(4L, path.totalDurationTicksRound());
        assertFalse(path.isEmpty());
    }

    @Test
    @DisplayName("Path immutability preserves internal lists against external mutation")
    void pathImmutability() {
        List<TileCoord> waypoints = List.of(new TileCoord(0, 0), new TileCoord(1, 0));
        Path path = new Path(waypoints, 4_000L);

        assertThrows(UnsupportedOperationException.class, () ->
                path.waypoints().add(new TileCoord(2, 0))
        );
        assertThrows(UnsupportedOperationException.class, () ->
                path.stepCostUnits().add(1_000L)
        );
    }

    @Test
    @DisplayName("Empty path maintains consistent empty state")
    void emptyPathState() {
        Path emptyPath = Path.empty();

        assertTrue(emptyPath.isEmpty());
        assertTrue(emptyPath.waypoints().isEmpty());
        assertTrue(emptyPath.stepCostUnits().isEmpty());
        assertEquals(0L, emptyPath.totalCostUnits());
        assertEquals(0L, emptyPath.totalDurationTicksCeil());
    }
}