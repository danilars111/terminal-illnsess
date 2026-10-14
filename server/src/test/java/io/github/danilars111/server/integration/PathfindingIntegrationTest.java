package io.github.danilars111.server.integration;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.service.pathfinding.algorithm.FastestPathAStarPathfinder;
import io.github.danilars111.server.domain.service.pathfinding.cost.StandardStepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;
import io.github.danilars111.server.domain.system.PathfollowingSystem;
import io.github.danilars111.server.domain.service.pathfinding.validators.DefaultPathValidator;
import io.github.danilars111.server.domain.system.CooldownSystem;
import io.github.danilars111.server.domain.system.MovementSystem;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathfindingIntegrationTest {

    private static final int WORLD_SIZE = 20;
    private static final int BASE_COOLDOWN = 2; // 1 tick cooldown between steps

    private GameEngine engine;
    private GameWorld world;
    private Pathfinder pathfinder;
    private PathValidator pathValidator;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING)
    );

    @BeforeEach
    void setUp() {
        TileDefinition[] grid = new TileDefinition[WORLD_SIZE * WORLD_SIZE];
        Arrays.fill(grid, grass);
        world = new GameWorld(WORLD_SIZE, WORLD_SIZE, grid);

        engine = new GameEngine();

        // 1. Setup cost calculator based on terrain & colliders
        StepCostCalculator costCalculator = new StandardStepCostCalculator(engine.getGameState(), new MovementRuleService(world));

        pathValidator = new DefaultPathValidator(costCalculator);
        pathfinder = new FastestPathAStarPathfinder(world, costCalculator);

        // 2. Systems must be registered in execution order:
        // CooldownSystem -> PathfollowingSystem (issues MoveIntent) -> MovementSystem (executes MoveIntent)
        engine.registerSystem(new CooldownSystem());
        engine.registerSystem(new PathfollowingSystem(pathValidator, pathfinder, 1));
        engine.registerSystem(new MovementSystem(world, BASE_COOLDOWN, true));
    }

    private EntityId spawnNpc(TileCoord start) {
        EntityId npc = engine.getGameState().createEntity();
        engine.getGameState().addComponent(npc, new PositionComponent(start));
        engine.getGameState().addComponent(npc, TraversalComponent.of(TraversalMode.WALKING));
        return npc;
    }

    @Test
    @DisplayName("End-to-end: NPC autonomously traverses a path across ticks and unregisters when arrived")
    void npcAutonomouslyTraversesPath() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord goal = new TileCoord(7, 5); // 2 orthogonal steps
        EntityId npc = spawnNpc(start);

        // Assign path: (5,5) -> (6,5) -> (7,5)
        PathFollowComponent follow = new PathFollowComponent(
                List.of(start, new TileCoord(6, 5), goal),
                List.of(0L, 1000L, 1000L),
                1
        );
        engine.getGameState().addComponent(npc, follow);

        // Tick 1: Pathfollower issues intent to (6, 5), MovementSystem executes it, Cooldown set to 1
        engine.tick();
        assertEquals(new TileCoord(6, 5), engine.getGameState().getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 2: On cooldown; CooldownSystem decrements cooldown, entity remains at (6, 5)
        engine.tick();
        assertEquals(new TileCoord(6, 5), engine.getGameState().getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 3: Cooldown ready; Pathfollower issues intent to (7, 5), MovementSystem executes it
        engine.tick();
        assertEquals(goal, engine.getGameState().getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 4: Pathfollower sees goal reached and removes PathFollowComponent
        engine.tick();
        assertTrue(engine.getGameState().getComponent(npc, PathFollowComponent.class).isEmpty(),
                "PathFollowComponent should be cleaned up on completion");
    }

    @Test
    @DisplayName("End-to-end: Dynamic obstacle erected ahead causes lookahead validator to repath NPC around it")
    void dynamicObstacleCausesAutonomousRepath() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord directStep = new TileCoord(6, 5);
        TileCoord goal = new TileCoord(7, 5);
        EntityId npc = spawnNpc(start);

        // Initially planned straight line
        engine.getGameState().addComponent(npc, new PathFollowComponent(
                List.of(start, directStep, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        // Place a solid dynamic obstacle on the direct step (6, 5)
        EntityId rock = engine.getGameState().createEntity();
        engine.getGameState().addComponent(rock, new PositionComponent(directStep));
        engine.getGameState().addComponent(rock, new ColliderComponent());

        // Tick 1: Pathfollower inspects (6, 5), detects block, repaths around the obstacle, and issues detour intent
        engine.tick();

        PositionComponent currentPos = engine.getGameState().getComponent(npc, PositionComponent.class).orElseThrow();
        assertTrue(currentPos.getX() != 6 || currentPos.getY() != 5, "NPC must not step into the solid rock");
        assertTrue(engine.getGameState().getComponent(npc, PathFollowComponent.class).isPresent(), "Path should still be active");
    }
}