package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.EntityManager;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.world.LevelGrid;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class MovementSystemTest {

    private static final String LEVEL_ID = "test_level";
    private static final int BASE_COOLDOWN = 4;
    private static final int DIAGONAL_COOLDOWN = (int) Math.round(BASE_COOLDOWN * Math.sqrt(2));

    private final TileDefinition grass = new TileDefinition("grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING),
            Set.of());
    private final TileDefinition water = new TileDefinition("water",
            "Water",
            Set.of(TraversalMode.SWIMMING, TraversalMode.FLYING),
            Set.of());
    private final TileDefinition mountain = new TileDefinition("mountain",
            "Mountain",
            Set.of(TraversalMode.FLYING),
            Set.of());
    private final TileDefinition wall = new TileDefinition("wall",
            "Wall",
            Set.of(),
            Set.of());

    private GameState gameState;
    private MovementSystem movementSystem;
    private World world;
    private MovementRuleService movementRuleService;
    private TileIndex tileIndex;
    private TilePalette tilePalette;

    private short grassId;
    private short waterId;
    private short mountainId;
    private short wallId;

    @BeforeAll
    static void enableLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);

        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.SEVERE);
            }
        }
    }

    @BeforeEach
    void setUp() {
        DefinitionRepository<TileDefinition> tileRepo = new DefinitionRepository<>();
        tileRepo.replaceAll(Map.of(
                grass.id(), grass,
                water.id(), water,
                mountain.id(), mountain,
                wall.id(), wall
        ));

        tileIndex = new TileIndex();
        grassId = tileIndex.register(grass.id());
        waterId = tileIndex.register(water.id());
        mountainId = tileIndex.register(mountain.id());
        wallId = tileIndex.register(wall.id());

        tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, tileRepo);

        world = new World(tilePalette, tileIndex);

        // 3x3 layout:
        // y = 0: (0,0) grass | (1,0) grass | (2,0) wall
        // y = 1: (0,1) grass | (1,1) water | (2,1) wall
        // y = 2: (0,2) grass | (1,2) grass | (2,2) mountain
        short[] grid = new short[]{
                grassId, grassId, wallId,      // y = 0
                grassId, waterId, wallId,      // y = 1
                grassId, grassId, mountainId   // y = 2
        };
        LevelGrid levelGrid = new LevelGrid(LEVEL_ID, 3, 3, grid);
        world.registerLevel(levelGrid);

        gameState = new GameState(new EntityManager(), new SpatialIndex(), world);

        movementRuleService = new MovementRuleService(world);
        movementSystem = new MovementSystem(movementRuleService, BASE_COOLDOWN);
    }

    private EntityId createWalkingEntity(int x, int y) {
        EntityId entity = gameState.entityManager().createEntity();
        PositionComponent pos = new PositionComponent(LEVEL_ID, x, y);
        gameState.entityManager().addComponent(entity, pos);
        gameState.entityManager().addComponent(entity, TraversalComponent.of(TraversalMode.WALKING));
        return entity;
    }

    private EntityId createFlyingEntity(int x, int y) {
        EntityId entity = gameState.entityManager().createEntity();
        PositionComponent pos = new PositionComponent(LEVEL_ID, x, y);
        gameState.entityManager().addComponent(entity, pos);
        gameState.entityManager().addComponent(entity, TraversalComponent.of(TraversalMode.FLYING));
        return entity;
    }

    private EntityId createSwimmingEntity(int x, int y) {
        EntityId entity = gameState.entityManager().createEntity();
        PositionComponent pos = new PositionComponent(LEVEL_ID, x, y);
        gameState.entityManager().addComponent(entity, pos);
        gameState.entityManager().addComponent(entity, TraversalComponent.of(TraversalMode.SWIMMING));
        return entity;
    }

    private World createCustomWorld(String levelId, int width, int height, short[] tiles) {
        World customWorld = new World(tilePalette, tileIndex);
        LevelGrid grid = new LevelGrid(levelId, width, height, tiles);
        customWorld.registerLevel(grid);
        return customWorld;
    }

    @Nested
    @DisplayName("Basic Orthogonal Movement and Cooldowns")
    class OrthogonalMovementTests {

        @Test
        @DisplayName("Successfully moves 1 tile orthogonally, consumes intent and applies base cooldown")
        void movesOrthogonallyAndAppliesCooldown() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.entityManager().addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(0, pos.getY());

            assertTrue(gameState.entityManager().getComponent(id, MoveIntentComponent.class).isEmpty());

            CooldownComponent cd = gameState.entityManager().getComponent(id, CooldownComponent.class).orElseThrow();
            assertEquals(BASE_COOLDOWN, cd.getCooldown(MovementSystem.COOLDOWN_KEY));
        }

        @Test
        @DisplayName("Rejects move when movement cooldown is active")
        void rejectsMoveWhenOnCooldown() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.entityManager().addComponent(id, CooldownComponent.empty().withCooldown(MovementSystem.COOLDOWN_KEY, 3));
            gameState.entityManager().addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
            assertTrue(gameState.entityManager().getComponent(id, MoveIntentComponent.class).isEmpty());
        }

        @Test
        @DisplayName("Rejects moves exceeding 1 tile step distance")
        void rejectsStepLargerThanOneTile() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.entityManager().addComponent(id, new MoveIntentComponent(2, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }
    }

    @Nested
    @DisplayName("Diagonal Movement & Permissive Cornering")
    class DiagonalMovementTests {

        @Test
        @DisplayName("Diagonal move scales cooldown by ~1.414x")
        void appliesScaledCooldownForDiagonalMove() {
            EntityId id = createWalkingEntity(0, 1);
            gameState.entityManager().addComponent(id, new MoveIntentComponent(1, 1, 10L)); // (1, 2) is grass

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(2, pos.getY());

            CooldownComponent cd = gameState.entityManager().getComponent(id, CooldownComponent.class).orElseThrow();
            assertEquals(DIAGONAL_COOLDOWN, cd.getCooldown(MovementSystem.COOLDOWN_KEY));
        }

        @Test
        @DisplayName("Permissive cornering: Allows diagonal when one cardinal side is blocked and one is open")
        void allowsDiagonalWhenOneSideBlocked() {
            EntityId walker = createWalkingEntity(0, 1);
            gameState.entityManager().addComponent(walker, new MoveIntentComponent(1, 1, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(walker, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(2, pos.getY());
        }

        @Test
        @DisplayName("Permissive cornering: Blocks diagonal when BOTH cardinal sides are blocked")
        void blocksDiagonalWhenBothSidesBlocked() {
            short[] slitTiles = new short[]{
                    grassId, wallId,
                    wallId, grassId
            };
            World slitWorld = createCustomWorld("slit_level", 2, 2, slitTiles);
            GameState slitState = new GameState(new EntityManager(), new SpatialIndex(), slitWorld);
            MovementRuleService localRuleService = new MovementRuleService(slitWorld);
            MovementSystem slitSystem = new MovementSystem(localRuleService, BASE_COOLDOWN);

            EntityId id = slitState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent("slit_level", 0, 0);
            slitState.entityManager().addComponent(id, pos);
            slitState.entityManager().addComponent(id, TraversalComponent.of(TraversalMode.WALKING));

            slitState.entityManager().addComponent(id, new MoveIntentComponent(1, 1, 10L));

            slitSystem.update(slitState, 10L);

            PositionComponent resultPos = slitState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, resultPos.getX());
            assertEquals(0, resultPos.getY());
        }
    }

    @Nested
    @DisplayName("Traversal & Immobilization Rules")
    class TraversalRulesTests {

        @Test
        @DisplayName("Walker is blocked by water, swimmer can cross water")
        void respectsWalkingAndSwimmingDistinction() {
            EntityId walker = createWalkingEntity(0, 1);
            gameState.entityManager().addComponent(walker, new MoveIntentComponent(1, 0, 10L)); // (1, 1) is water

            movementSystem.update(gameState, 10L);
            assertEquals(0, gameState.entityManager().getComponent(walker, PositionComponent.class).orElseThrow().getX());

            EntityId swimmer = createSwimmingEntity(0, 1);
            gameState.entityManager().addComponent(swimmer, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(1, gameState.entityManager().getComponent(swimmer, PositionComponent.class).orElseThrow().getX());
        }

        @Test
        @DisplayName("Only flying entities can traverse mountain tiles")
        void respectsFlyingOnlyMountainTiles() {
            EntityId walker = createWalkingEntity(1, 2);
            gameState.entityManager().addComponent(walker, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(1, gameState.entityManager().getComponent(walker, PositionComponent.class).orElseThrow().getX());

            EntityId flyer = createFlyingEntity(1, 2);
            gameState.entityManager().addComponent(flyer, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(2, gameState.entityManager().getComponent(flyer, PositionComponent.class).orElseThrow().getX());
        }

        @Test
        @DisplayName("Entity with empty TraversalComponent (immobilized/rooted) cannot move")
        void immobilizedEntityCannotMove() {
            EntityId id = gameState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent(LEVEL_ID, 0, 0);
            gameState.entityManager().addComponent(id, pos);
            gameState.entityManager().addComponent(id, TraversalComponent.empty());

            gameState.entityManager().addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent result = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, result.getX());
            assertEquals(0, result.getY());
        }

        @Test
        @DisplayName("Entity without TraversalComponent (static object) cannot move")
        void staticEntityCannotMove() {
            EntityId id = gameState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent(LEVEL_ID, 0, 0);
            gameState.entityManager().addComponent(id, pos);

            gameState.entityManager().addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent result = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, result.getX());
            assertEquals(0, result.getY());
        }

        @Test
        @DisplayName("Entity cannot step outside world boundaries")
        void blocksOutOfBoundsMovement() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.entityManager().addComponent(id, new MoveIntentComponent(-1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }
    }

    @Nested
    @DisplayName("Deterministic Tick Sorting")
    class DeterminismTests {

        @Test
        @DisplayName("Intents requested on earlier ticks are processed before later ticks")
        void processesEarlierTickFirst() {
            List<EntityId> executionOrder = new ArrayList<>();
            MovementSystem trackingSystem = new MovementSystem(movementRuleService, BASE_COOLDOWN) {
                @Override
                protected void processEntityMove(GameState gs, EntityId id, MoveIntentComponent intent, long tick) {
                    executionOrder.add(id);
                    super.processEntityMove(gs, id, intent, tick);
                }
            };

            EntityId entityLate = createWalkingEntity(0, 0);
            EntityId entityEarly = createWalkingEntity(0, 2);

            gameState.entityManager().addComponent(entityLate, new MoveIntentComponent(1, 0, 12L));
            gameState.entityManager().addComponent(entityEarly, new MoveIntentComponent(1, 0, 10L));

            trackingSystem.update(gameState, 15L);

            assertEquals(List.of(entityEarly, entityLate), executionOrder);
        }
    }

    @Nested
    @DisplayName("Footprint Movement Tests")
    class FootprintMovementTests {

        @Test
        @DisplayName("2x2 footprint moves successfully when all destination tiles are passable")
        void movesMultiTileFootprintSuccessfully() {
            EntityId flyer = createFlyingEntity(0, 0);
            FootprintComponent footprintComp = FootprintComponent.rectangle(2, 2);
            gameState.entityManager().addComponent(flyer, footprintComp);

            gameState.entityManager().addComponent(flyer, new MoveIntentComponent(0, 1, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(flyer, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(1, pos.getY());

            Set<TileCoord> footprint = gameState.spatialIndex().getFootprintOf(flyer);
            assertEquals(4, footprint.size());
            assertTrue(footprint.contains(new TileCoord(0, 1)));
            assertTrue(footprint.contains(new TileCoord(1, 1)));
            assertTrue(footprint.contains(new TileCoord(0, 2)));
            assertTrue(footprint.contains(new TileCoord(1, 2)));
        }

        @Test
        @DisplayName("Multi-tile footprint move is rejected if even a single cell hits impassable terrain")
        void rejectsMoveIfAnyFootprintCellIsImpassable() {
            EntityId walker = createWalkingEntity(0, 0);
            FootprintComponent footprintComp = FootprintComponent.rectangle(2, 1);
            gameState.entityManager().addComponent(walker, footprintComp);

            gameState.entityManager().addComponent(walker, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(walker, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }

        @Test
        @DisplayName("Multi-tile entity moving 1 step forward does not collide with its own trailing tiles")
        void selfExclusionAllowsOverlappingMovement() {
            short[] openRow = new short[]{grassId, grassId, grassId, grassId};
            World linearWorld = createCustomWorld("linear_level", 4, 1, openRow);
            GameState linearState = new GameState(new EntityManager(), new SpatialIndex(), linearWorld);
            MovementRuleService localRuleService = new MovementRuleService(linearWorld);
            MovementSystem openMovementSystem = new MovementSystem(localRuleService, BASE_COOLDOWN);

            EntityId walker = linearState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent("linear_level", 0, 0);
            FootprintComponent footprintComp = FootprintComponent.rectangle(2, 1);

            linearState.entityManager().addComponent(walker, pos);
            linearState.entityManager().addComponent(walker, TraversalComponent.of(TraversalMode.WALKING));
            linearState.entityManager().addComponent(walker, footprintComp);
            linearState.entityManager().addComponent(walker, new ColliderComponent(true));

            linearState.entityManager().addComponent(walker, new MoveIntentComponent(1, 0, 10L));

            openMovementSystem.update(linearState, 10L);

            PositionComponent resultPos = linearState.entityManager().getComponent(walker, PositionComponent.class).orElseThrow();
            assertEquals(1, resultPos.getX());
            assertEquals(0, resultPos.getY());
        }
    }

    @Nested
    @DisplayName("Dynamic Entity Collision Tests")
    class EntityCollisionTests {

        @Test
        @DisplayName("Entity is blocked when stepping into a tile occupied by another entity with ColliderComponent")
        void blockedByStaticOrDynamicCollider() {
            EntityId mover = createWalkingEntity(0, 0);

            EntityId obstacle = gameState.entityManager().createEntity();
            PositionComponent obsPos = new PositionComponent(LEVEL_ID, 1, 0);
            gameState.entityManager().addComponent(obstacle, obsPos);
            gameState.entityManager().addComponent(obstacle, new ColliderComponent(true));

            gameState.entityManager().addComponent(mover, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(mover, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }

        @Test
        @DisplayName("Entity can step onto a tile occupied by an entity WITHOUT a ColliderComponent")
        void allowsSteppingOnNonColliderEntity() {
            EntityId mover = createWalkingEntity(0, 0);

            EntityId item = gameState.entityManager().createEntity();
            PositionComponent itemPos = new PositionComponent(LEVEL_ID, 1, 0);
            gameState.entityManager().addComponent(item, itemPos);

            gameState.entityManager().addComponent(mover, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(mover, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(0, pos.getY());
        }

        @Test
        @DisplayName("Moving entity respects colliders even if the mover lacks a ColliderComponent")
        void nonColliderMoverStillRespectsObstacleColliders() {
            EntityId mover = createWalkingEntity(0, 0);

            EntityId rock = gameState.entityManager().createEntity();
            PositionComponent rockPos = new PositionComponent(LEVEL_ID, 1, 0);
            gameState.entityManager().addComponent(rock, rockPos);
            gameState.entityManager().addComponent(rock, new ColliderComponent(true));

            gameState.entityManager().addComponent(mover, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.entityManager().getComponent(mover, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }
    }

    @Nested
    @DisplayName("Dynamic Corner Pinch Tests")
    class DynamicCornerPinchTests {

        @Test
        @DisplayName("Diagonal movement is blocked when flanked by dynamic colliders on both sides")
        void blocksDiagonalWhenBothFlanksHaveDynamicColliders() {
            short[] allGrass = new short[]{
                    grassId, grassId,
                    grassId, grassId
            };
            World flatWorld = createCustomWorld("flat_level", 2, 2, allGrass);
            GameState flatState = new GameState(new EntityManager(), new SpatialIndex(), flatWorld);
            MovementRuleService localRuleService = new MovementRuleService(flatWorld);
            MovementSystem testSystem = new MovementSystem(localRuleService, BASE_COOLDOWN);

            EntityId mover = flatState.entityManager().createEntity();
            PositionComponent moverPos = new PositionComponent("flat_level", 0, 0);
            flatState.entityManager().addComponent(mover, moverPos);
            flatState.entityManager().addComponent(mover, TraversalComponent.of(TraversalMode.WALKING));

            EntityId blockerA = flatState.entityManager().createEntity();
            PositionComponent posA = new PositionComponent("flat_level", 1, 0);
            flatState.entityManager().addComponent(blockerA, posA);
            flatState.entityManager().addComponent(blockerA, new ColliderComponent(true));

            EntityId blockerB = flatState.entityManager().createEntity();
            PositionComponent posB = new PositionComponent("flat_level", 0, 1);
            flatState.entityManager().addComponent(blockerB, posB);
            flatState.entityManager().addComponent(blockerB, new ColliderComponent(true));

            flatState.entityManager().addComponent(mover, new MoveIntentComponent(1, 1, 10L));

            testSystem.update(flatState, 10L);

            PositionComponent pos = flatState.entityManager().getComponent(mover, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }
    }

    @Nested
    @DisplayName("NOCLIP Mode Tests")
    class NoClipTests {

        @Test
        @DisplayName("Entity with NOCLIP bypasses impassable static terrain (walls)")
        void noClipBypassesImpassableTerrain() {
            EntityId cheater = gameState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent(LEVEL_ID, 1, 0);
            gameState.entityManager().addComponent(cheater, pos);
            gameState.entityManager().addComponent(cheater, TraversalComponent.of(TraversalMode.NOCLIP));

            gameState.entityManager().addComponent(cheater, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent result = gameState.entityManager().getComponent(cheater, PositionComponent.class).orElseThrow();
            assertEquals(2, result.getX());
            assertEquals(0, result.getY());
        }

        @Test
        @DisplayName("Entity with NOCLIP bypasses dynamic entity colliders")
        void noClipBypassesEntityColliders() {
            EntityId cheater = gameState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent(LEVEL_ID, 0, 0);
            gameState.entityManager().addComponent(cheater, pos);
            gameState.entityManager().addComponent(cheater, TraversalComponent.of(TraversalMode.NOCLIP));

            EntityId rock = gameState.entityManager().createEntity();
            PositionComponent rockPos = new PositionComponent(LEVEL_ID, 1, 0);
            gameState.entityManager().addComponent(rock, rockPos);
            gameState.entityManager().addComponent(rock, new ColliderComponent(true));

            gameState.entityManager().addComponent(cheater, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent result = gameState.entityManager().getComponent(cheater, PositionComponent.class).orElseThrow();
            assertEquals(1, result.getX());
            assertEquals(0, result.getY());
        }

        @Test
        @DisplayName("Entity with NOCLIP can cut diagonally through fully blocked pinch points")
        void noClipBypassesCornerPinching() {
            short[] slitTiles = new short[]{
                    grassId, wallId,
                    wallId, grassId
            };
            World slitWorld = createCustomWorld("slit_level_noclip", 2, 2, slitTiles);
            GameState slitState = new GameState(new EntityManager(), new SpatialIndex(), slitWorld);
            MovementRuleService localRuleService = new MovementRuleService(slitWorld);
            MovementSystem slitSystem = new MovementSystem(localRuleService, BASE_COOLDOWN);

            EntityId cheater = slitState.entityManager().createEntity();
            PositionComponent pos = new PositionComponent("slit_level_noclip", 0, 0);
            slitState.entityManager().addComponent(cheater, pos);
            slitState.entityManager().addComponent(cheater, TraversalComponent.of(TraversalMode.NOCLIP));

            slitState.entityManager().addComponent(cheater, new MoveIntentComponent(1, 1, 10L));

            slitSystem.update(slitState, 10L);

            PositionComponent result = slitState.entityManager().getComponent(cheater, PositionComponent.class).orElseThrow();
            assertEquals(1, result.getX());
            assertEquals(1, result.getY());
        }
    }
}