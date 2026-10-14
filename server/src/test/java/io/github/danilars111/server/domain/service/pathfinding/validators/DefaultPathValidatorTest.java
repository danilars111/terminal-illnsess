package io.github.danilars111.server.domain.service.pathfinding.validators;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidationResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DefaultPathValidatorTest {
    private final EntityId entity = new EntityId(1);
    private final FootprintComponent footprint = FootprintComponent.singleTile();

    @Test
    @DisplayName("Returns Valid when all waypoints within lookahead remain passable at planned cost")
    void validPathStaysPassable() {
        StepCostCalculator costCalc = (id, from, to) -> EngineTime.ticksToUnits(2);
        DefaultPathValidator validator = new DefaultPathValidator(costCalc);

        List<TileCoord> waypoints = List.of(
                new TileCoord(0, 0),
                new TileCoord(1, 0),
                new TileCoord(2, 0)
        );
        List<Long> costs = List.of(0L, EngineTime.ticksToUnits(2), EngineTime.ticksToUnits(2));
        PathFollowComponent follow = new PathFollowComponent(waypoints, costs, 1);

        PathValidationResult result = validator.validate(entity, follow, 3);

        assertInstanceOf(PathValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("Detects Blocked when a tile within the lookahead window becomes IMPASSABLE")
    void detectsBlockedTileInLookahead() {
        TileCoord obstacleTile = new TileCoord(2, 0);

        StepCostCalculator costCalc = (id, from, to) -> {
            if (to.equals(obstacleTile)) {
                return EngineTime.INFINITE; // Dynamic barricade erected
            }
            return EngineTime.ticksToUnits(2);
        };
        DefaultPathValidator validator = new DefaultPathValidator(costCalc);

        List<TileCoord> waypoints = List.of(
                new TileCoord(0, 0),
                new TileCoord(1, 0),
                new TileCoord(2, 0),
                new TileCoord(3, 0)
        );
        PathFollowComponent follow = new PathFollowComponent(waypoints, List.of(0L, 2000L, 2000L, 2000L), 1);

        PathValidationResult result = validator.validate(entity, follow, 3);

        assertInstanceOf(PathValidationResult.Blocked.class, result);
        PathValidationResult.Blocked blocked = (PathValidationResult.Blocked) result;
        assertEquals(obstacleTile, blocked.blockedAt());
        assertEquals(2, blocked.waypointIndex());
    }

    @Test
    @DisplayName("Ignores blocked tiles located beyond the lookahead horizon")
    void ignoresBlockBeyondLookaheadHorizon() {
        TileCoord farObstacle = new TileCoord(4, 0);

        StepCostCalculator costCalc = (id, from, to) -> {
            if (to.equals(farObstacle)) {
                return EngineTime.INFINITE;
            }
            return EngineTime.ticksToUnits(2);
        };
        DefaultPathValidator validator = new DefaultPathValidator(costCalc);

        List<TileCoord> waypoints = List.of(
                new TileCoord(0, 0),
                new TileCoord(1, 0),
                new TileCoord(2, 0),
                new TileCoord(3, 0),
                new TileCoord(4, 0)
        );
        PathFollowComponent follow = new PathFollowComponent(waypoints, List.of(0L, 2000L, 2000L, 2000L, 2000L), 1);

        // Lookahead is only 2 steps: checks index 1 and 2, but farObstacle is at index 4
        PathValidationResult result = validator.validate(entity, follow, 2);

        assertInstanceOf(PathValidationResult.Valid.class, result);
    }

    @Test
    @DisplayName("Flags CostInflated whenever actual cost exceeds the planned cost")
    void flagsCostInflationWhenActualCostExceedsPlanned() {
        TileCoord hazardTile = new TileCoord(1, 0);

        // Hazard adds even 1 millitick or whole tick
        StepCostCalculator costCalc = (id, from, to) -> {
            if (to.equals(hazardTile)) {
                return EngineTime.ticksToUnits(3); // Planned was 2, now 3
            }
            return EngineTime.ticksToUnits(2);
        };
        DefaultPathValidator validator = new DefaultPathValidator(costCalc);

        List<TileCoord> waypoints = List.of(new TileCoord(0, 0), hazardTile);
        List<Long> plannedCosts = List.of(0L, EngineTime.ticksToUnits(2));
        PathFollowComponent follow = new PathFollowComponent(waypoints, plannedCosts, 1);

        PathValidationResult result = validator.validate(entity, follow, 2);

        assertInstanceOf(PathValidationResult.CostInflated.class, result);
        PathValidationResult.CostInflated inflated = (PathValidationResult.CostInflated) result;
        assertEquals(hazardTile, inflated.atCoord());
        assertEquals(EngineTime.ticksToUnits(2), inflated.expectedCost());
        assertEquals(EngineTime.ticksToUnits(3), inflated.actualCost());
    }
}
