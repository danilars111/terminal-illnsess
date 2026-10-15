package io.github.danilars111.server.domain.service.pathfinding.algorithm;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.EntityManager;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.records.Path;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.LevelGrid;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FastestPathAStarPathfinderTest {

    private static final String DEFAULT_LEVEL_ID = "level_main";

    private GameState gameState;
    private TileDefinition defaultTile;
    private EntityId mover;
    private TileIndex tileIndex;
    private TilePalette tilePalette;
    private short defaultTileId;

    @BeforeEach
    void setUp() {
        defaultTile = new TileDefinition("grass", "Grass", Set.of(TraversalMode.WALKING), Set.of());

        DefinitionRepository<TileDefinition> tileRepo = new DefinitionRepository<>();
        tileRepo.replaceAll(Map.of(defaultTile.id(), defaultTile));

        tileIndex = new TileIndex();
        defaultTileId = tileIndex.register(defaultTile.id());

        tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, tileRepo);

        World initialWorld = createUniformWorld(5, 5);
        gameState = new GameState(new EntityManager(), new SpatialIndex(), initialWorld);

        mover = gameState.entityManager().createEntity();

        // Pathfinder resolves the entity's level via PositionComponent
        gameState.entityManager().addComponent(mover, new PositionComponent(DEFAULT_LEVEL_ID, 0, 0));
    }

    private World createUniformWorld(int width, int height) {
        World world = new World(tilePalette, tileIndex);
        short[] tiles = new short[width * height];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = defaultTileId;
        }
        LevelGrid grid = new LevelGrid(DEFAULT_LEVEL_ID, width, height, tiles);
        world.registerLevel(grid);
        return world;
    }

    private PathfindingRequest createRequest(TileCoord start, TileCoord goal) {
        return new PathfindingRequest(
                mover,
                start,
                goal
        );
    }

    @Nested
    @DisplayName("Trivial & Boundary Cases")
    class BoundaryCases {

        @Test
        @DisplayName("Returns single-step zero-cost success when start equals goal")
        void returnsSingleStepZeroCostWhenStartEqualsGoal() {
            World world = createUniformWorld(5, 5);
            StepCostCalculator costCalc = (entityId, from, to) -> EngineTime.ticksToUnits(4);
            Pathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);

            TileCoord start = new TileCoord(2, 2);
            TileCoord goal = start;
            PathfindingRequest request = createRequest(start, goal);

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
            World world = createUniformWorld(5, 1);
            long stepCost = EngineTime.ticksToUnits(4);
            StepCostCalculator costCalc = (entityId, from, to) -> stepCost;
            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);

            TileCoord start = new TileCoord(0, 0);
            TileCoord goal = new TileCoord(3, 0);
            PathfindingRequest request = createRequest(start, goal);

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

            assertEquals(12L, path.totalDurationTicksCeil());
        }
    }

    @Nested
    @DisplayName("Cost Optimization & Detours")
    class CostOptimization {

        @Test
        @DisplayName("Prefers longer road route over short direct swamp route when road is faster")
        void prefersFasterDetourAroundSlowTerrain() {
            World world = createUniformWorld(3, 3);

            TileCoord swamp = new TileCoord(1, 1);
            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(swamp)) {
                    return EngineTime.ticksToUnits(30);
                }
                if (to.getY() == 2) {
                    return EngineTime.ticksToUnits(1);
                }
                return EngineTime.ticksToUnits(4);
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 1), new TileCoord(2, 1));

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            assertFalse(path.waypoints().contains(swamp), "Must avoid high-cost swamp");
            assertTrue(path.waypoints().contains(new TileCoord(1, 2)), "Must utilize fast road");
        }

        @Test
        @DisplayName("Routes around impassable infinite-cost obstacle")
        void routesAroundImpassableWall() {
            World world = createUniformWorld(3, 3);
            TileCoord wall = new TileCoord(1, 1);

            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(wall)) {
                    return EngineTime.INFINITE;
                }
                return EngineTime.ticksToUnits(2);
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 1), new TileCoord(2, 1));

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Success.class, result);
            Path path = ((PathResult.Success) result).path();

            assertFalse(path.waypoints().contains(wall));
            assertEquals(new TileCoord(2, 1), path.waypoints().get(path.waypoints().size() - 1));
        }

        @Test
        @DisplayName("Uses diagonal movement when allowed and costs match SQRT_2 scale")
        void supportsDiagonalMovement() {
            World world = createUniformWorld(3, 3);
            long baseCost = EngineTime.ticksToUnits(1);

            StepCostCalculator costCalc = (entityId, from, to) -> {
                int dx = Math.abs(from.getX() - to.getX());
                int dy = Math.abs(from.getY() - to.getY());
                if (dx != 0 && dy != 0) {
                    return (long) EngineTime.SQRT_2;
                }
                return baseCost;
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, true, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 0), new TileCoord(2, 2));

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
            World world = createUniformWorld(5, 1);
            TileCoord wall1 = new TileCoord(2, 0);
            TileCoord wall2 = new TileCoord(3, 0);

            StepCostCalculator costCalc = (entityId, from, to) -> {
                if (to.equals(wall1) || to.equals(wall2)) {
                    return EngineTime.INFINITE;
                }
                return EngineTime.ticksToUnits(2);
            };

            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc);
            PathfindingRequest request = createRequest(new TileCoord(0, 0), new TileCoord(4, 0));

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
            World world = createUniformWorld(20, 20);
            StepCostCalculator costCalc = (entityId, from, to) -> EngineTime.ticksToUnits(1);

            int maxExpansions = 5;
            FastestPathAStarPathfinder pathfinder = new FastestPathAStarPathfinder(world, false, costCalc, maxExpansions);

            TileCoord start = new TileCoord(0, 0);
            TileCoord distantGoal = new TileCoord(19, 19);
            PathfindingRequest request = createRequest(start, distantGoal);

            PathResult result = pathfinder.findPath(gameState, request);

            assertInstanceOf(PathResult.Failure.class, result);
            PathResult.Failure failure = (PathResult.Failure) result;

            assertEquals(PathResult.FailureReason.BUDGET_EXHAUSTED, failure.reason());
            assertTrue(failure.partialPathToClosest().isPresent());
            assertNotNull(failure.closestReachableCoord());
        }
    }
}