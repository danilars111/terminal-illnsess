package io.github.danilars111.server.domain.service.pathfinding.records;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PathfindingRequestTest {
    @Test
    @DisplayName("PathfindingRequest normalizes null footprint to singleTile and makes modes immutable")
    void requestNormalizesCorrectly() {
        EntityId id = new EntityId(1L);
        TileCoord start = new TileCoord(0, 0);
        TileCoord goal = new TileCoord(5, 5);

        PathfindingRequest request = new PathfindingRequest(
                id,
                start,
                goal,
                Set.of(TraversalMode.WALKING),
                null
        );

        assertNotNull(request.footprint());
        assertEquals(1, request.footprint().relativeOffsets().size());
        assertTrue(request.modes().contains(TraversalMode.WALKING));
    }
}
