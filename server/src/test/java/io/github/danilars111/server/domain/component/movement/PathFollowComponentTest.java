package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathFollowComponentTest {

    @Test
    @DisplayName("Defaults to target index 1 and tracks steps sequentially")
    void advancesWaypointsSequentially() {
        List<TileCoord> points = List.of(
                new TileCoord(0, 0),
                new TileCoord(1, 0),
                new TileCoord(2, 0)
        );
        List<Long> costs = List.of(0L, 4_000L, 4_000L);

        PathFollowComponent component = new PathFollowComponent(points, costs);

        assertEquals(1, component.getCurrentIndex());
        assertEquals(2, component.getRemainingSteps());
        assertEquals(new TileCoord(1, 0), component.getCurrentTarget().orElseThrow());
        assertEquals(4_000L, component.getExpectedCostForCurrentStep().orElseThrow());
        assertFalse(component.isComplete());

        component.advance();

        assertEquals(2, component.getCurrentIndex());
        assertEquals(1, component.getRemainingSteps());
        assertEquals(new TileCoord(2, 0), component.getCurrentTarget().orElseThrow());
        assertFalse(component.isComplete());

        component.advance();

        assertEquals(3, component.getCurrentIndex());
        assertEquals(0, component.getRemainingSteps());
        assertTrue(component.isComplete());
        assertTrue(component.getCurrentTarget().isEmpty());
    }

    @Test
    @DisplayName("Single-tile path is immediately complete upon construction")
    void singleTilePathIsAlreadyComplete() {
        List<TileCoord> single = List.of(new TileCoord(5, 5));
        PathFollowComponent component = new PathFollowComponent(single);

        assertTrue(component.isComplete());
        assertTrue(component.getCurrentTarget().isEmpty());
        assertEquals(0, component.getRemainingSteps());
        assertEquals(new TileCoord(5, 5), component.getFinalDestination().orElseThrow());
    }

    @Test
    @DisplayName("Empty waypoint list is safely handled")
    void emptyWaypoints() {
        PathFollowComponent component = new PathFollowComponent(List.of());

        assertTrue(component.isComplete());
        assertTrue(component.getCurrentTarget().isEmpty());
        assertTrue(component.getFinalDestination().isEmpty());
        assertEquals(0, component.getRemainingSteps());
    }
}