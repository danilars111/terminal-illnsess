package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidationResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.records.Path;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathfollowingSystemTest {

    private GameState gameState;
    private EntityId entity;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        entity = gameState.createEntity();
    }

    @Test
    @DisplayName("Emits MoveIntentComponent pointing towards the next waypoint")
    void emitsMoveIntentTowardsNextWaypoint() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord next = new TileCoord(1, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        PathFollowComponent follow = new PathFollowComponent(
                List.of(start, next),
                List.of(0L, 1000L),
                1
        );
        gameState.addComponent(entity, follow);

        PathValidator alwaysValid = (id, pf, fp) -> new PathValidationResult.Valid();
        Pathfinder dummyFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), start);

        PathfollowingSystem system = new PathfollowingSystem(alwaysValid, dummyFinder, 1);
        system.update(gameState, 10L);

        Optional<MoveIntentComponent> intentOpt = gameState.getComponent(entity, MoveIntentComponent.class);
        assertTrue(intentOpt.isPresent(), "MoveIntentComponent should be emitted");

        MoveIntentComponent intent = intentOpt.get();
        assertEquals(1, intent.deltaX());
        assertEquals(0, intent.deltaY());
        assertEquals(10L, intent.requestedTick());
    }

    @Test
    @DisplayName("Advances waypoint and emits intent toward subsequent tile when already arrived at target")
    void advancesWaypointWhenAlreadyAtTarget() {
        TileCoord step1 = new TileCoord(1, 0);
        TileCoord step2 = new TileCoord(1, 1);

        // Entity has physically arrived at step1
        gameState.addComponent(entity, new PositionComponent(step1));

        // PathFollowComponent was targeting step1 (index 1)
        PathFollowComponent follow = new PathFollowComponent(
                List.of(new TileCoord(0, 0), step1, step2),
                List.of(0L, 1000L, 1000L),
                1
        );
        gameState.addComponent(entity, follow);

        PathValidator alwaysValid = (id, pf, fp) -> new PathValidationResult.Valid();
        Pathfinder dummyFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), step1);

        PathfollowingSystem system = new PathfollowingSystem(alwaysValid, dummyFinder, 1);
        system.update(gameState, 1L);

        // Component should have advanced to step2 (index 2)
        assertEquals(2, follow.getCurrentIndex());
        assertEquals(step2, follow.getCurrentTarget().orElseThrow());

        // Intent should aim towards step2 from step1: dx = 0, dy = 1
        MoveIntentComponent intent = gameState.getComponent(entity, MoveIntentComponent.class).orElseThrow();
        assertEquals(0, intent.deltaX());
        assertEquals(1, intent.deltaY());
    }

    @Test
    @DisplayName("Cleans up PathFollowComponent when reaching final destination")
    void cleansUpWhenPathCompletes() {
        TileCoord goal = new TileCoord(2, 0);

        gameState.addComponent(entity, new PositionComponent(goal));
        PathFollowComponent follow = new PathFollowComponent(
                List.of(new TileCoord(1, 0), goal),
                List.of(0L, 1000L),
                1 // target is goal, which entity is already at
        );
        gameState.addComponent(entity, follow);

        PathValidator alwaysValid = (id, pf, fp) -> new PathValidationResult.Valid();
        Pathfinder dummyFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), goal);

        PathfollowingSystem system = new PathfollowingSystem(alwaysValid, dummyFinder, 1);
        system.update(gameState, 1L);

        assertTrue(gameState.getComponent(entity, PathFollowComponent.class).isEmpty(),
                "PathFollowComponent should be removed upon path completion");
        assertTrue(gameState.getComponent(entity, MoveIntentComponent.class).isEmpty(),
                "No MoveIntent should be emitted when complete");
    }

    @Test
    @DisplayName("Does not emit duplicate MoveIntentComponent if one is already pending")
    void doesNotOverwriteExistingPendingIntent() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord next = new TileCoord(1, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        gameState.addComponent(entity, new PathFollowComponent(List.of(start, next), List.of(0L, 1000L), 1));

        // Pre-existing pending intent
        MoveIntentComponent existingIntent = new MoveIntentComponent(0, 1, 5L);
        gameState.addComponent(entity, existingIntent);

        PathValidator alwaysValid = (id, pf, fp) -> new PathValidationResult.Valid();
        Pathfinder dummyFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), start);

        PathfollowingSystem system = new PathfollowingSystem(alwaysValid, dummyFinder, 1);
        system.update(gameState, 6L);

        MoveIntentComponent intent = gameState.getComponent(entity, MoveIntentComponent.class).orElseThrow();
        assertSame(existingIntent, intent, "Existing intent must not be replaced");
    }

    @Test
    @DisplayName("Repaths when lookahead detects a Blocked tile")
    void repathsOnBlockedValidationResult() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord blockedTile = new TileCoord(1, 0);
        TileCoord detourTile = new TileCoord(0, 1);
        TileCoord goal = new TileCoord(2, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        gameState.addComponent(entity, new TraversalComponent(Set.of(TraversalMode.WALKING)));
        gameState.addComponent(entity, new PathFollowComponent(
                List.of(start, blockedTile, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        PathValidator blockedValidator = (id, pf, lookahead) -> new PathValidationResult.Blocked(blockedTile, 1);

        // Detour finder returns alternate route via (0, 1)
        Path detourPath = new Path(List.of(start, detourTile, goal), List.of(0L, 1000L, 1000L), 2000L);
        Pathfinder detourFinder = (gs, req) -> new PathResult.Success(detourPath);

        PathfollowingSystem system = new PathfollowingSystem(blockedValidator, detourFinder, 1);
        system.update(gameState, 1L);

        PathFollowComponent newFollow = gameState.getComponent(entity, PathFollowComponent.class).orElseThrow();
        assertEquals(detourTile, newFollow.getCurrentTarget().orElseThrow(),
                "PathFollowComponent should be replaced with detour route");
    }

    @Test
    @DisplayName("Removes PathFollowComponent when path is blocked and no detour is found")
    void cancelsPathWhenBlockedAndRepathFails() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord blockedTile = new TileCoord(1, 0);
        TileCoord goal = new TileCoord(2, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        gameState.addComponent(entity, new PathFollowComponent(
                List.of(start, blockedTile, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        PathValidator blockedValidator = (id, pf, lookahead) -> new PathValidationResult.Blocked(blockedTile, 1);
        Pathfinder failingFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), start);

        PathfollowingSystem system = new PathfollowingSystem(blockedValidator, failingFinder, 1);
        system.update(gameState, 1L);

        assertTrue(gameState.getComponent(entity, PathFollowComponent.class).isEmpty(),
                "PathFollowComponent should be dropped when trapped");
        assertTrue(gameState.getComponent(entity, MoveIntentComponent.class).isEmpty(),
                "No intent should be emitted when route is impassable");
    }

    @Test
    @DisplayName("Repaths when lookahead detects CostInflated hazard")
    void repathsOnCostInflatedValidationResult() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord inflatedTile = new TileCoord(1, 0);
        TileCoord detourTile = new TileCoord(0, 1);
        TileCoord goal = new TileCoord(2, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        gameState.addComponent(entity, new PathFollowComponent(
                List.of(start, inflatedTile, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        PathValidator inflatedValidator = (id, pf, lookahead) ->
                new PathValidationResult.CostInflated(inflatedTile, 1000L, 3000L);

        Path detourPath = new Path(List.of(start, detourTile, goal), List.of(0L, 1000L, 1000L), 2000L);
        Pathfinder detourFinder = (gs, req) -> new PathResult.Success(detourPath);

        PathfollowingSystem system = new PathfollowingSystem(inflatedValidator, detourFinder, 1);
        system.update(gameState, 1L);

        PathFollowComponent newFollow = gameState.getComponent(entity, PathFollowComponent.class).orElseThrow();
        assertEquals(detourTile, newFollow.getCurrentTarget().orElseThrow());
    }

    @Test
    @DisplayName("Continues on existing path if CostInflated but repath finds no alternative")
    void continuesOnInflatedPathWhenNoDetourAvailable() {
        TileCoord start = new TileCoord(0, 0);
        TileCoord inflatedTile = new TileCoord(1, 0);
        TileCoord goal = new TileCoord(2, 0);

        gameState.addComponent(entity, new PositionComponent(start));
        gameState.addComponent(entity, new PathFollowComponent(
                List.of(start, inflatedTile, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        PathValidator inflatedValidator = (id, pf, lookahead) ->
                new PathValidationResult.CostInflated(inflatedTile, 1000L, 3000L);

        // Pathfinder fails to find an alternative route
        Pathfinder failingFinder = (gs, req) -> new PathResult.Failure(PathResult.FailureReason.UNREACHABLE, Optional.empty(), start);

        PathfollowingSystem system = new PathfollowingSystem(inflatedValidator, failingFinder, 1);
        system.update(gameState, 1L);

        // PathFollowComponent should be retained and intent emitted anyway
        assertTrue(gameState.getComponent(entity, PathFollowComponent.class).isPresent(),
                "Must continue on original path if no better alternative exists");

        MoveIntentComponent intent = gameState.getComponent(entity, MoveIntentComponent.class).orElseThrow();
        assertEquals(1, intent.deltaX());
        assertEquals(0, intent.deltaY());
    }
}