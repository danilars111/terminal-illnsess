package io.github.danilars111.server.integration;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;
import io.github.danilars111.contracts.definition.ai.pathfinding.enums.StepCostModifierStrategy;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.ai.PathfindingModifierComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.service.indices.StepCostModifierIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.service.pathfinding.algorithm.FastestPathAStarPathfinder;
import io.github.danilars111.server.domain.service.pathfinding.cost.StandardStepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.Pathfinder;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.service.pathfinding.records.Path;
import io.github.danilars111.server.domain.service.pathfinding.records.PathfindingRequest;
import io.github.danilars111.server.domain.service.pathfinding.validators.DefaultPathValidator;
import io.github.danilars111.server.domain.system.CooldownSystem;
import io.github.danilars111.server.domain.system.MovementSystem;
import io.github.danilars111.server.domain.system.PathfollowingSystem;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.LevelGrid;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PathfindingIntegrationTest {

    private static final String LEVEL_ID = "dungeon_level";
    private static final int WORLD_SIZE = 20;
    private static final int BASE_COOLDOWN = 2;

    private GameEngine engine;
    private World world;
    private Pathfinder pathfinder;
    private PathValidator pathValidator;
    private GameState gameState;
    private StepCostCalculator costCalculator;
    private MovementRuleService movementRuleService;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING),
            Set.of()
    );

    @BeforeEach
    void setUp() {
        engine = new GameEngine();
        gameState = engine.getGameState();

        DefinitionRepository<TileDefinition> tileRepo = new DefinitionRepository<>();
        tileRepo.replaceAll(Map.of(grass.id(), grass));

        TileIndex tileIndex = new TileIndex();
        short grassId = tileIndex.register(grass.id());

        TilePalette tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, tileRepo);

        world = new World(tilePalette, tileIndex);
        short[] tiles = new short[WORLD_SIZE * WORLD_SIZE];
        Arrays.fill(tiles, grassId);
        LevelGrid grid = new LevelGrid(LEVEL_ID, WORLD_SIZE, WORLD_SIZE, tiles);
        world.registerLevel(grid);

        movementRuleService = new MovementRuleService(world);
        costCalculator = new StandardStepCostCalculator(gameState, movementRuleService);

        pathValidator = new DefaultPathValidator(costCalculator);
        pathfinder = new FastestPathAStarPathfinder(world, costCalculator);

        engine.registerSystem(new CooldownSystem());
        engine.registerSystem(new PathfollowingSystem(pathValidator, pathfinder, 1));
        engine.registerSystem(new MovementSystem(movementRuleService, BASE_COOLDOWN));
    }

    private EntityId spawnNpc(TileCoord start) {
        EntityId npc = gameState.createEntity();
        PositionComponent pos = new PositionComponent(LEVEL_ID, start);
        gameState.addComponent(npc, pos);
        gameState.addComponent(npc, TraversalComponent.of(TraversalMode.WALKING));
        gameState.getSpatialIndex().updateEntity(npc, pos, null);
        return npc;
    }

    @Test
    @DisplayName("End-to-end: NPC autonomously traverses a path across ticks and unregisters when arrived")
    void npcAutonomouslyTraversesPath() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord goal = new TileCoord(7, 5);
        EntityId npc = spawnNpc(start);

        PathFollowComponent follow = new PathFollowComponent(
                List.of(start, new TileCoord(6, 5), goal),
                List.of(0L, 1000L, 1000L),
                1
        );
        gameState.addComponent(npc, follow);

        // Tick 1: Pathfollower issues intent to (6, 5), MovementSystem executes it
        engine.tick();
        assertEquals(new TileCoord(6, 5), gameState.getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 2: On cooldown; CooldownSystem decrements cooldown, entity stays put
        engine.tick();
        assertEquals(new TileCoord(6, 5), gameState.getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 3: Cooldown expires; Pathfollower moves to (7, 5)
        engine.tick();
        assertEquals(goal, gameState.getComponent(npc, PositionComponent.class).orElseThrow().getTileCoord());

        // Tick 4: Pathfollower sees goal arrived and tears down component
        engine.tick();
        assertTrue(gameState.getComponent(npc, PathFollowComponent.class).isEmpty(),
                "PathFollowComponent should be cleaned up on completion");
    }

    @Test
    @DisplayName("End-to-end: Dynamic obstacle erected ahead causes lookahead validator to repath NPC around it")
    void dynamicObstacleCausesAutonomousRepath() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord directStep = new TileCoord(6, 5);
        TileCoord goal = new TileCoord(7, 5);
        EntityId npc = spawnNpc(start);

        gameState.addComponent(npc, new PathFollowComponent(
                List.of(start, directStep, goal),
                List.of(0L, 1000L, 1000L),
                1
        ));

        // Spawn a solid dynamic obstacle at (6, 5)
        EntityId rock = gameState.createEntity();
        PositionComponent rockPos = new PositionComponent(LEVEL_ID, directStep);
        gameState.addComponent(rock, rockPos);
        gameState.addComponent(rock, new ColliderComponent());
        gameState.getSpatialIndex().updateEntity(rock, rockPos, null);

        // Tick 1: Lookahead detects blocked node, triggers repath detour, and takes step
        engine.tick();

        PositionComponent currentPos = gameState.getComponent(npc, PositionComponent.class).orElseThrow();
        assertTrue(currentPos.getX() != 6 || currentPos.getY() != 5, "NPC must not step into the solid rock");
        assertTrue(gameState.getComponent(npc, PathFollowComponent.class).isPresent(), "Path should still be active");
    }

    @Test
    @DisplayName("End-to-end: High-cost modifier causes pathfinder to route around passable penalty tiles")
    void highCostModifierForcesDetourAroundPassableTiles() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord goal = new TileCoord(8, 5);
        EntityId npc = spawnNpc(start);

        PathResult directResult = pathfinder.findPath(gameState, new PathfindingRequest(npc, start, goal));
        Path directPath = extractSuccessPath(directResult);
        assertEquals(4, directPath.waypoints().size(), "Baseline path should be direct straight line");

        StepCostModifierIndex modifierIndex = gameState.getStepCostModifierIndex();
        modifierIndex.register(new StepCostModifierDefinition(
                "swamp_penalty",
                "Swamp Penalty",
                StepCostModifierStrategy.PLACEHOLDER,
                Map.of("penalty", 50000L)
        ));

        StepCostCalculator modifiedCalculator = new StandardStepCostCalculator(
                gameState,
                movementRuleService
        ) {
            @Override
            public long calculateStepCost(EntityId entityId, TileCoord from, TileCoord to) {
                long base = super.calculateStepCost(entityId, from, to);
                if (base == EngineTime.INFINITE) return EngineTime.INFINITE;

                var modComp = gameState.getComponent(entityId, PathfindingModifierComponent.class);
                if (modComp.isPresent() && modComp.get().getActiveModifiers().contains("swamp_penalty")) {
                    if ((to.getX() == 6 || to.getX() == 7) && Math.abs(to.getY() - 5) <= 1) {
                        return base + 50000L;
                    }
                }
                return base;
            }
        };

        FastestPathAStarPathfinder penalizedPathfinder = new FastestPathAStarPathfinder(world, modifiedCalculator);

        gameState.addComponent(npc, PathfindingModifierComponent.of("swamp_penalty"));

        PathResult detourResult = penalizedPathfinder.findPath(gameState, new PathfindingRequest(npc, start, goal));
        Path detourPath = extractSuccessPath(detourResult);

        assertTrue(detourPath.waypoints().stream().noneMatch(t -> t.equals(new TileCoord(6, 5))),
                "Path should not traverse penalized tile (6,5)");
        assertTrue(detourPath.waypoints().stream().noneMatch(t -> t.equals(new TileCoord(7, 5))),
                "Path should not traverse penalized tile (7,5)");
        assertTrue(detourPath.waypoints().size() > directPath.waypoints().size(),
                "Detour path should have more steps than the direct path");
    }

    @Test
    @DisplayName("End-to-end: Entity isolation guarantees differing modifier configurations yield distinct paths")
    void entitiesWithDifferentModifierConfigurationsTakeDifferentPaths() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord goal = new TileCoord(8, 5);

        EntityId braveNpc = spawnNpc(start);
        EntityId cautiousNpc = spawnNpc(start);

        StepCostModifierIndex modifierIndex = gameState.getStepCostModifierIndex();
        modifierIndex.register(new StepCostModifierDefinition(
                "hazard_avoidance",
                "Hazard Avoidance",
                StepCostModifierStrategy.PLACEHOLDER,
                Map.of("penalty", 50000L)
        ));

        StepCostCalculator dynamicCalc = new StandardStepCostCalculator(
                gameState,
                movementRuleService
        ) {
            @Override
            public long calculateStepCost(EntityId entityId, TileCoord from, TileCoord to) {
                long base = super.calculateStepCost(entityId, from, to);
                if (base == EngineTime.INFINITE) return EngineTime.INFINITE;

                var modComp = gameState.getComponent(entityId, PathfindingModifierComponent.class);
                if (modComp.isPresent() && modComp.get().getActiveModifiers().contains("hazard_avoidance")) {
                    Map<String, Object> effective = modifierIndex.resolveEffectiveParams(
                            "hazard_avoidance",
                            modComp.get().getOverrides("hazard_avoidance")
                    );
                    long penalty = ((Number) effective.getOrDefault("penalty", 0L)).longValue();
                    if (to.equals(new TileCoord(6, 5)) || to.equals(new TileCoord(7, 5))) {
                        return base + penalty;
                    }
                }
                return base;
            }
        };

        FastestPathAStarPathfinder pf = new FastestPathAStarPathfinder(world, dynamicCalc);

        gameState.addComponent(braveNpc, new PathfindingModifierComponent(Map.of(
                "hazard_avoidance", Map.of("penalty", 0L)
        )));

        gameState.addComponent(cautiousNpc, new PathfindingModifierComponent(Map.of(
                "hazard_avoidance", Map.of("penalty", 80000L)
        )));

        PathResult braveResult = pf.findPath(gameState, new PathfindingRequest(braveNpc, start, goal));
        PathResult cautiousResult = pf.findPath(gameState, new PathfindingRequest(cautiousNpc, start, goal));

        Path bravePath = extractSuccessPath(braveResult);
        Path cautiousPath = extractSuccessPath(cautiousResult);

        assertEquals(4, bravePath.waypoints().size(), "Brave NPC takes direct path");
        assertTrue(bravePath.contains(new TileCoord(6, 5)));

        assertTrue(cautiousPath.totalCostUnits() > bravePath.totalCostUnits(), "Cautious path costs more due to detour");
        assertTrue(cautiousPath.waypoints().stream().noneMatch(t -> t.equals(new TileCoord(6, 5))),
                "Cautious NPC avoids hazard tile (6,5)");
    }

    @Test
    @DisplayName("End-to-end: Entities with large FootprintComponent are blocked from narrow 1-tile passages")
    void footprintComponentBlocksLargeEntitiesFromNarrowGaps() {
        TileCoord start = new TileCoord(5, 5);
        TileCoord goal = new TileCoord(7, 5);

        EntityId smallNpc = spawnNpc(start);
        EntityId largeBoss = spawnNpc(start);

        FootprintComponent bossFootprint = FootprintComponent.rectangle(2, 2);
        gameState.addComponent(largeBoss, bossFootprint);
        gameState.getSpatialIndex().updateEntity(largeBoss, new PositionComponent(LEVEL_ID, start), bossFootprint);

        EntityId topWall = gameState.createEntity();
        PositionComponent topWallPos = new PositionComponent(LEVEL_ID, 6, 4);
        gameState.addComponent(topWall, topWallPos);
        gameState.addComponent(topWall, new ColliderComponent());
        gameState.getSpatialIndex().updateEntity(topWall, topWallPos, null);

        EntityId bottomWall = gameState.createEntity();
        PositionComponent bottomWallPos = new PositionComponent(LEVEL_ID, 6, 6);
        gameState.addComponent(bottomWall, bottomWallPos);
        gameState.addComponent(bottomWall, new ColliderComponent());
        gameState.getSpatialIndex().updateEntity(bottomWall, bottomWallPos, null);

        PathResult smallResult = pathfinder.findPath(gameState, new PathfindingRequest(smallNpc, start, goal));
        assertInstanceOf(PathResult.Success.class, smallResult);
        Path smallPath = ((PathResult.Success) smallResult).path();
        assertTrue(smallPath.contains(new TileCoord(6, 5)));

        long costForBoss = costCalculator.calculateStepCost(largeBoss, start, new TileCoord(6, 5));
        assertEquals(EngineTime.INFINITE, costForBoss,
                "2x2 boss should be physically blocked from entering narrow 1-tile choke point");
    }

    private Path extractSuccessPath(PathResult result) {
        if (result instanceof PathResult.Success success) {
            return success.path();
        }
        throw new AssertionError("Expected PathResult.Success but got: " + result);
    }
}