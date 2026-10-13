package io.github.danilars111.server.domain.service.pathfinding.algorithm;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.interfaces.Component;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

public class FastestPathAStarPathfinder implements Pathfinder {
    private static final int[][] CARDINAL_DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final int[][] DIAGONAL_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final int MAX_EXPANSIONS_DEFAULT = 5_000;

    private final GameWorld world;
    private final boolean allowDiagonal;
    private final StepCostCalculator costCalculator;
    private final int maxExpansions;

    public FastestPathAStarPathfinder(
            GameWorld gameWorld,
            boolean allowDiagonal,
            StepCostCalculator stepCostCalculator,
            int maxExpansions
    ) {
        this.world = gameWorld;
        this.allowDiagonal = allowDiagonal;
        this.costCalculator = stepCostCalculator;
        this.maxExpansions = maxExpansions > 0 ? maxExpansions : MAX_EXPANSIONS_DEFAULT;
    }

    public FastestPathAStarPathfinder(
            GameWorld world,
            boolean allowDiagonal,
            StepCostCalculator stepCostCalculator
    ) {
        this(world, allowDiagonal, stepCostCalculator, MAX_EXPANSIONS_DEFAULT);
    }

    private record Node(TileCoord coord, long gCost, long fCost) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            int fCmp = Long.compare(this.fCost, o.fCost);
            if (fCmp != 0) { return fCmp; }
            int gCmp = Long.compare(this.gCost, o.gCost);
            if (gCmp != 0) { return gCmp; }
            int yCmp = Integer.compare(this.coord.getY(), o.coord.getY());
            if (yCmp != 0) { return yCmp; }
            int xCmp = Integer.compare(this.coord.getX(), o.coord.getX());
            return xCmp;

        }
    }

    @Override
    public PathResult findPath(GameState state, PathfindingRequest req) {
        TileCoord start = req.start();
        TileCoord goal = req.goal();
        FootprintComponent footprint = req.footprint();
        EntityId entityId = req.entityId();

        if (start.equals(goal)) {
            return new PathResult.Success(Path.atDestination(start));
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<TileCoord, TileCoord> cameFrom = new HashMap<>();
        Map<TileCoord, Long> gScores = new HashMap<>();
        Map<TileCoord, Long> stepsCostTaken = new HashMap<>();

        long initialH = calculateHeuristic(start, goal);

        gScores.put(start, 0L);
        openSet.add(new Node(start, 0L, initialH));

        TileCoord closestNode = start;
        long minRemainingHeuristic = initialH;
        int expansions = 0;

        while(!openSet.isEmpty()) {
            if(++expansions > maxExpansions) {
                Path partial = reconstructPath(cameFrom, stepsCostTaken, closestNode, gScores.getOrDefault(closestNode, 0L));
                return new PathResult.Failure(PathResult.FailureReason.BUDGET_EXHAUSTED, Optional.of(partial), closestNode);
            }
            Node current = openSet.poll();

            if(current.coord.equals(goal)) {
                Path fullPath = reconstructPath(cameFrom, stepsCostTaken, current.coord, current.gCost);
                return new PathResult.Success(fullPath);
            }

            if(current.gCost > gScores.getOrDefault(current.coord, Long.MAX_VALUE)) {
                continue;
            }

            // Track closest node for partial-path recovery on failure
            long hDist = calculateHeuristic(current.coord, goal);
            if (hDist < minRemainingHeuristic) {
                minRemainingHeuristic = hDist;
                closestNode = current.coord;
            }

            expandNeighbors(entityId, current, goal, footprint, openSet, gScores, cameFrom, stepsCostTaken);
        }

        // Search space exhausted without touching the destination
        Path partial = reconstructPath(cameFrom, stepsCostTaken, closestNode, gScores.getOrDefault(closestNode, 0L));
        return new PathResult.Failure(
                PathResult.FailureReason.UNREACHABLE,
                Optional.of(partial),
                closestNode
        );
    }

    /**
     * Admissible heuristic assuming the engine's theoretical maximum speed floor:
     * 1 tick (TICK_SCALE) per cardinal step, SQRT_2 for diagonal.
     */
    private long calculateHeuristic(TileCoord start, TileCoord goal) {
        int dx = Math.abs(start.getX() - goal.getX());
        int dy = Math.abs(start.getY() - goal.getY());

        if(allowDiagonal) {
            int minD = Math.min(dx, dy);
            int maxD = Math.max(dx, dy);
            return (minD * (long) EngineTime.SQRT_2) + ((long) (maxD - minD) * EngineTime.TICK_SCALE);
        }

        return (long) (dx + dy) * EngineTime.TICK_SCALE;
    }

    private Path reconstructPath(Map<TileCoord, TileCoord> cameFrom,
                                 Map<TileCoord, Long> stepCostsTaken,
                                 TileCoord end,
                                 long totalCostUnits) {
        List<TileCoord> waypoints = new ArrayList<>();
        List<Long> costs = new ArrayList<>();

        TileCoord curr = end;
        while(curr != null) {
            waypoints.add(curr);
            costs.add(stepCostsTaken.getOrDefault(curr, 0L));
            curr = cameFrom.get(curr);
        }
        Collections.reverse(waypoints);
        Collections.reverse(costs);
        return new Path(waypoints, costs, totalCostUnits);
    }

    private void expandNeighbors(
            EntityId entityId,
            Node current,
            TileCoord goal,
            FootprintComponent footprint,
            PriorityQueue<Node> openSet,
            Map<TileCoord, Long> gScores,
            Map<TileCoord, TileCoord> cameFrom,
            Map<TileCoord, Long> stepCostsTaken
    ) {
        int[][] directions = allowDiagonal
                ? concatenate(CARDINAL_DIRS, DIAGONAL_DIRS)
                : CARDINAL_DIRS;

        for(int[] dir : directions) {
            int nx = current.coord.getX() + dir[0];
            int ny = current.coord.getY() + dir[1];

            // World Bounds Check: Discard off-grid coordinates immediately
            if(!world.isWithinBounds(nx, ny)) {
                continue;
            }

            TileCoord next = new TileCoord(nx, ny);

            long stepCost = costCalculator.calculateStepCost(entityId, current.coord, next, footprint);
            if (stepCost == EngineTime.INFINITE || stepCost <= 0L) {
                continue; // Blocked or impassable
            }

            long tentativeG = current.gCost + stepCost;
            if(tentativeG < gScores.getOrDefault(next, Long.MAX_VALUE)) {
                gScores.put(next, tentativeG);
                cameFrom.put(next, current.coord);
                stepCostsTaken.put(next, stepCost);

                long fCost = tentativeG + calculateHeuristic(next, goal);
                openSet.add(new Node(next, tentativeG, fCost));
            }
        }
    }

    private static int[][] concatenate(int[][] a, int[][] b) {
        int[][] res = new int[a.length + b.length][];
        System.arraycopy(a, 0, res, 0, a.length);
        System.arraycopy(b, 0, res, a.length, b.length);
        return res;
    }
}