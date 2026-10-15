package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidationResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Objects;
import java.util.Optional;

public class PathfollowingSystem implements GameSystem {

    private final PathValidator pathValidator;
    private final Pathfinder pathfinder;
    private final int defaultLookahead;

    public PathfollowingSystem(PathValidator pathValidator, Pathfinder pathfinder, int defaultLookahead) {
        this.pathValidator = Objects.requireNonNull(pathValidator, "pathValidator must not be null");
        this.pathfinder = Objects.requireNonNull(pathfinder, "pathfinder must not be null");
        this.defaultLookahead = Math.max(defaultLookahead, 1);
    }

    public PathfollowingSystem(PathValidator pathValidator, Pathfinder pathfinder) {
        this(pathValidator, pathfinder, 1);
    }

    @Override
    public void update(GameState gameState, long tick) {
        for (EntityId entityId : gameState.entityManager().getEntitiesWith(PathFollowComponent.class, PositionComponent.class)) {
            processEntity(gameState, entityId, tick);
        }
    }

    private void processEntity(GameState gameState, EntityId entityId, long tick) {
        Optional<PathFollowComponent> followOpt = gameState.entityManager().getComponent(entityId, PathFollowComponent.class);
        Optional<PositionComponent> posOpt = gameState.entityManager().getComponent(entityId, PositionComponent.class);
        if (followOpt.isEmpty() || posOpt.isEmpty()) {
            return;
        }

        PathFollowComponent follow = followOpt.get();
        PositionComponent currentPos = posOpt.get();

        // Check if the entity has already reached the current target waypoint
        Optional<TileCoord> targetOpt = follow.getCurrentTarget();
        if (targetOpt.isPresent() && currentPos.getTileCoord().equals(targetOpt.get())) {
            follow.advance();
        }

        // Completion Check
        if (follow.isComplete()) {
            gameState.entityManager().removeComponent(entityId, PathFollowComponent.class);
            return;
        }

        // Do not overwrite if an intent is already pending this tick
        if (gameState.entityManager().getComponent(entityId, MoveIntentComponent.class).isPresent()) {
            return;
        }

        FootprintComponent footprint = gameState.entityManager().getComponent(entityId, FootprintComponent.class)
                .orElseGet(FootprintComponent::singleTile);

        // Lookahead path validation
        PathValidationResult validation = pathValidator.validate(entityId, follow, defaultLookahead);

        switch (validation) {
            case PathValidationResult.Blocked blocked -> {
                if (!attemptRepath(gameState, entityId, follow, footprint, currentPos.getTileCoord())) {
                    gameState.entityManager().removeComponent(entityId, PathFollowComponent.class);
                }
                return;
            }
            case PathValidationResult.CostInflated inflated -> {
                if (attemptRepath(gameState, entityId, follow, footprint, currentPos.getTileCoord())) {
                    return; // Re-path installed, next tick will steer along new path
                }
                // If no detour exists, continue on the existing route
            }
            case PathValidationResult.Valid ignored -> {
                // Path is valid
            }
        }

        // Emit MoveIntentComponent towards the next waypoint
        TileCoord nextTile = follow.getCurrentTarget().orElseThrow();
        int dx = nextTile.getX() - currentPos.getX();
        int dy = nextTile.getY() - currentPos.getY();

        if (dx != 0 || dy != 0) {
            gameState.entityManager().addComponent(entityId, new MoveIntentComponent(dx, dy, tick));
        }
    }

    private boolean attemptRepath(
            GameState gameState,
            EntityId entityId,
            PathFollowComponent follow,
            FootprintComponent footprint,
            TileCoord currentCoord
    ) {
        Optional<TileCoord> finalGoal = follow.getFinalDestination();
        if (finalGoal.isEmpty()) {
            return false;
        }
        PathfindingRequest request = new PathfindingRequest(
                entityId,
                currentCoord,
                finalGoal.get()
        );
        PathResult result = pathfinder.findPath(gameState, request);

        if (result instanceof PathResult.Success success && !success.path().isEmpty()) {
            PathFollowComponent detour = new PathFollowComponent(
                    success.path().waypoints(),
                    success.path().stepCostUnits(),
                    1
            );
            gameState.entityManager().addComponent(entityId, detour);
            return true;
        }
        return false;
    }
}