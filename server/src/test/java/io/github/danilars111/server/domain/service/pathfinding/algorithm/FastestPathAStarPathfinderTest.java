package io.github.danilars111.server.domain.service.pathfinding.algorithm;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.records.Path;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FastestPathAStarPathfinderTest {

    private GameState gameState;
    private TileDefinition defaultTile;
    private EntityId mover;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        mover = gameState.createEntity();
        defaultTile = new TileDefinition("grass", "Grass", Set.of(TraversalMode.WALKING));
    }

    private GameWorld createUniformWorld(int width, int height) {
        return new GameWorld(width, height, defaultTile);
    }

    private PathfindingRequest createRequest(TileCoord start, TileCoord goal, boolean allowNoclip) {
        Set<TraversalMode> modes = allowNoclip
                ? Set.of(TraversalMode.WALKING, TraversalMode.NOCLIP)
                : Set.of(TraversalMode.WALKING);
        return new PathfindingRequest(
                mover,
                start,
                goal,
                modes,
                FootprintComponent.singleTile()
        );
    }

    @Nested
    @DisplayName("Trivial & Boundry Cases")
    class BoundaryCases {

        @Test
        @DisplayName("Returns single-step zero-cost success when start equals goal")
        void returnsSingleStepZeroCostWhenStartEqualsGoal() {
            GameWorld gameWorld = createUniformWorld(5, 5);
            StepCostCalculator costCalc = (entityId, from, to) -> EngineTime.ticksToUnits(4);
            Pathfinder pathfinder = new FastestPathAStarPathfinder(gameWorld, false, costCalc);

            TileCoord start = new TileCoord(2,2);
            TileCoord goal = start;
            PathfindingRequest request = createRequest(start, goal, false);

            PathResult result = pathfinder.findPath(gameState, request);
            assertInstanceOf(PathResult.Success.class, result);
            PathResult.Success success = (PathResult.Success) result;

            assertEquals(List.of(goal), success.path().waypoints());
            assertEquals(0L, success.path().totalCostUnits());
            assertEquals(0L, success.path().totalDurationTicksCeil());
            assertFalse(success.path().isEmpty());
        }

        @Test
        @DisplayName("Finds simple straight-line path in uniform terrain")
        void findsStraightLinePath() {
            GameWorld world = createUniformWorld(5, 1);
            long stepCost = EngineTime.ticksToUnits(4); // 4000 units per step
            StepCostCalculator costCalc = (entityId, from, to) -> stepCost;
            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);

            TileCoord start = new TileCoord(0, 0);
            TileCoord goal = new TileCoord(3, 0);
            PathfindingRequest request = createRequest(start, goal, false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            List<TileCoord> expectedWaypoints = List.of(
                    new TileCoord(0, 0),
                    new TileCoord(1, 0),
                    new TileCoord(2, 0),
                    new TileCoord(3, 0)
            );
            assertEquals(expectedWaypoints, path.waypoints());
            assertEquals(3 * stepCost, path.totalCostUnits());
            assertEquals(12L, path.totalDurationTicksCeil()); // 3 steps * 4 ticks = 12 ticks
        }
    }
    @Nested
    @DisplayName("Cost Optimization & Detours")
    class CostOptimization {

        @Test
        @DisplayName("Prefers longer road route over short direct swamp route when road is faster")
        void prefersFasterDetourAroundSlowTerrain() {
            // 3x3 Grid:
            // y=2: (0,2)[road]   - (1,2)[road]  - (2,2)[road]
            // y=1: (0,1)[grass]  - (1,1)[swamp] - (2,1)[grass]
            // y=0: (0,0)[grass]  - (1,0)[grass] - (2,0)[grass]
            //
            // From (0,1) to (2,1):
            // Direct: (0,1) -> (1,1)[swamp: 30 ticks] -> (2,1)[grass: 4 ticks] = 34 ticks
            // Road:   (0,1) -> (0,2)[1 tick] -> (1,2)[1 tick] -> (2,2)[1 tick] -> (2,1)[1 tick] = 4 ticks
            GameWorld world = createUniformWorld(3, 3);

            TileCoord swamp = new TileCoord(1, 1);
            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(swamp)) {
                    return EngineTime.ticksToUnits(30);
                }
                if (to.getY() == 2) {
                    return EngineTime.ticksToUnits(1); // Fast road along top row
                }
                return EngineTime.ticksToUnits(4); // Default terrain
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 1), new TileCoord(2, 1), false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            assertFalse(path.waypoints().contains(swamp), "Must avoid high-cost swamp");
            assertTrue(path.waypoints().contains(new TileCoord(1, 2)), "Must utilize fast road");
        }

        @Test
        @DisplayName("Routes around impassable infinite-cost obstacle")
        void routesAroundImpassableWall() {
            // 3x3 Grid with wall at (1,1)
            GameWorld world = createUniformWorld(3, 3);
            TileCoord wall = new TileCoord(1, 1);

            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(wall)) {
                    return EngineTime.INFINITE;
                }
                return EngineTime.ticksToUnits(2);
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 1), new TileCoord(2, 1), false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            assertFalse(path.waypoints().contains(wall));
            assertEquals(new TileCoord(2, 1), path.waypoints().get(path.waypoints().size() - 1));
        }

        @Test
        @DisplayName("Uses diagonal movement when allowed and costs match SQRT_2 scale")
        void supportsDiagonalMovement() {
            GameWorld world = createUniformWorld(3, 3);
            long baseCost = EngineTime.ticksToUnits(1); // 1000 units

            StepCostCalculator costCalc = (entityId, from, to) -> {
                int dx = Math.abs(from.getX() - to.getX());
                int dy = Math.abs(from.getY() - to.getY());
                if (dx != 0 && dy != 0) {
                    return (long) EngineTime.SQRT_2; // 1414 units
                }
                return baseCost;
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, true, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 0), new TileCoord(2, 2), false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            List<TileCoord> expectedWaypoints = List.of(
                    new TileCoord(0, 0),
                    new TileCoord(1, 1),
                    new TileCoord(2, 2)
            );
            assertEquals(expectedWaypoints, path.waypoints());
            assertEquals(2L * EngineTime.SQRT_2, path.totalCostUnits());
        }
    }
    @Nested
    @DisplayName("Unreachable & Budget Exhaustion Cases")
    class UnreachableCases {
        @Test
        @DisplayName("Returns Failure(UNREACHABLE) with closest reachable coordinate when target is enclosed")
        void returnsFailureWithClosestCoordWhenTargetEnclosed() {
            // 5x1 Grid:
            // (0,0)[start] -> (1,0) -> (2,0)[wall] -> (3,0)[wall] -> (4,0)[isolated goal]
            GameWorld world = createUniformWorld(5, 1);
            TileCoord wall1 = new TileCoord(2, 0);
            TileCoord wall2 = new TileCoord(3, 0);

            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(wall1) || to.equals(wall2)) {
                    return EngineTime.INFINITE;
                }
                return EngineTime.ticksToUnits(2);
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 0), new TileCoord(4, 0), false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Failure.class, result);
            PathResult.Failure failure = (PathResult.Failure) result;

            assertEquals(PathResult.FailureReason.UNREACHABLE, failure.reason());
            assertEquals(new TileCoord(1, 0), failure.closestReachableCoord(), "Must identify closest accessible tile");
            assertTrue(failure.partialPathToClosest().isPresent());

            Path partialPath = failure.partialPathToClosest().get();
            assertEquals(List.of(new TileCoord(0, 0), new TileCoord(1, 0)), partialPath.waypoints());
        }
        @Test
        @DisplayName("Aborts with BUDGET_EXHAUSTED when search exceeds max expansion limit")
        void abortsWithBudgetExhaustedWhenBudgetExceeded() {
            // 20x20 open world
            GameWorld world = createUniformWorld(20, 20);
            StepCostCalculator costCalc = (entityId, from, to) -> EngineTime.ticksToUnits(1);

            // Intentionally set a tiny budget of 5 expansions
            int maxExpansions = 5;
            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc, maxExpansions);

            TileCoord start = new TileCoord(0, 0);
            TileCoord distantGoal = new TileCoord(19, 19);
            PathfindingRequest request = createRequest(start, distantGoal, false);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Failure.class, result);
            PathResult.Failure failure = (PathResult.Failure) result;

            assertEquals(PathResult.FailureReason.BUDGET_EXHAUSTED, failure.reason());
            assertTrue(failure.partialPathToClosest().isPresent());
            assertNotNull(failure.closestReachableCoord());
        }
    }
}