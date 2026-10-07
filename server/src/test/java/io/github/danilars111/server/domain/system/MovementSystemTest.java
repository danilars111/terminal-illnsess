package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;

import io.github.danilars111.server.domain.world.GameWorld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MovementSystemTest {

    private static final int BASE_COOLDOWN = 4;
    private static final int DIAGONAL_COOLDOWN = (int) Math.round(BASE_COOLDOWN * Math.sqrt(2));

    private final TileDefinition grass = new TileDefinition("grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING));
    private final TileDefinition water = new TileDefinition("water",
            "Water",
            Set.of(TraversalMode.SWIMMING, TraversalMode.FLYING));
    private final TileDefinition mountain = new TileDefinition("mountain",
            "Mountain",
            Set.of(TraversalMode.FLYING));
    private final TileDefinition wall = new TileDefinition("wall",
            "Wall",
            Set.of());

    private GameState gameState;
    private MovementSystem movementSystem;
    private GameWorld world;


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
        gameState = new GameState();
        // 3x3 layout:
        // y = 2: (0,2) grass    | (1,2) grass | (2,2) mountain
        // y = 1: (0,1) grass    | (1,1) water | (2,1) wall
        // y = 0: (0,0) grass    | (1,0) grass | (2,0) wall
        TileDefinition[] grid = new TileDefinition[]{
                grass, grass, wall,     // y = 0
                grass, water, wall,     // y = 1
                grass, grass, mountain  // y = 2
        };
        world = new GameWorld(3, 3, grid);
        movementSystem = new MovementSystem(world, BASE_COOLDOWN, true);
    }

    private EntityId createWalkingEntity(int x, int y) {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(x, y));
        gameState.addComponent(entity, TraversalComponent.of(TraversalMode.WALKING));
        return entity;
    }

    private EntityId createFlyingEntity(int x, int y) {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(x, y));
        gameState.addComponent(entity, TraversalComponent.of(TraversalMode.FLYING));
        return entity;
    }
    private EntityId createSwimmingEntity(int x, int y) {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(x, y));
        gameState.addComponent(entity, TraversalComponent.of(TraversalMode.SWIMMING));
        return entity;
    }

    @Nested
    @DisplayName("Basic Orthogonal Movement and Cooldowns")
    class OrthogonalMovementTests {

        @Test
        @DisplayName("Successfully moves 1 tile orthogonally, consumes intent and applies base cooldown")
        void movesOrthogonallyAndAppliesCooldown() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(0, pos.getY());

            assertTrue(gameState.getComponent(id, MoveIntentComponent.class).isEmpty());

            CooldownComponent cd = gameState.getComponent(id, CooldownComponent.class).orElseThrow();
            assertEquals(BASE_COOLDOWN, cd.getCooldown(MovementSystem.COOLDOWN_KEY));
        }

        @Test
        @DisplayName("Rejects move when movement cooldown is active")
        void rejectsMoveWhenOnCooldown() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.addComponent(id, CooldownComponent.empty().withCooldown(MovementSystem.COOLDOWN_KEY, 3));
            gameState.addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
            assertTrue(gameState.getComponent(id, MoveIntentComponent.class).isEmpty());
        }

        @Test
        @DisplayName("Rejects moves exceeding 1 tile step distance")
        void rejectsStepLargerThanOneTile() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.addComponent(id, new MoveIntentComponent(2, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
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
            gameState.addComponent(id, new MoveIntentComponent(1, 1, 10L)); // (1, 2) is grass

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(2, pos.getY());

            CooldownComponent cd = gameState.getComponent(id, CooldownComponent.class).orElseThrow();
            assertEquals(DIAGONAL_COOLDOWN, cd.getCooldown(MovementSystem.COOLDOWN_KEY));
        }

        @Test
        @DisplayName("Disallows diagonal movement if allowDiagonalMovement flag is false")
        void blocksDiagonalWhenDisabled() {
            MovementSystem noDiagonalSystem = new MovementSystem(world, BASE_COOLDOWN, false);
            EntityId id = createWalkingEntity(0, 1);
            gameState.addComponent(id, new MoveIntentComponent(1, 1, 10L));

            noDiagonalSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(1, pos.getY());
        }
        @Test
        @DisplayName("Permissive cornering: Allows diagonal when one cardinal side is blocked and one is open")
        void allowsDiagonalWhenOneSideBlocked() {
            // Walker at (0, 1) -> target (1, 2) [grass]:
            // Side A: (1, 1) [water -> impassable for walking]
            // Side B: (0, 2) [grass -> passable for walking]
            // Permissive allows cutting the corner
            EntityId walker = createWalkingEntity(0, 1);
            gameState.addComponent(walker, new MoveIntentComponent(1, 1, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(walker, PositionComponent.class).orElseThrow();
            assertEquals(1, pos.getX());
            assertEquals(2, pos.getY());
        }
        @Test
        @DisplayName("Permissive cornering: Blocks diagonal when BOTH cardinal sides are blocked")
        void blocksDiagonalWhenBothSidesBlocked() {
            TileDefinition[] slitTiles = new TileDefinition[]{
                    grass, wall,
                    wall,  grass
            };
            GameWorld slitWorld = new GameWorld(2, 2, slitTiles);
            MovementSystem slitSystem = new MovementSystem(slitWorld, BASE_COOLDOWN, true);

            EntityId id = createWalkingEntity(0, 0);
            gameState.addComponent(id, new MoveIntentComponent(1, 1, 10L));

            slitSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }
    }

    @Nested
    @DisplayName("Traversal & Immobilization Rules")
    class TraversalRulesTests {

        @Test
        @DisplayName("Walker is blocked by water, swimmer can cross water")
        void respectsWalkingAndSwimmingDistinction() {
            EntityId walker = createWalkingEntity(0, 1);
            gameState.addComponent(walker, new MoveIntentComponent(1, 0, 10L)); // (1, 1) is water

            movementSystem.update(gameState, 10L);
            assertEquals(0, gameState.getComponent(walker, PositionComponent.class).orElseThrow().getX());

            EntityId swimmer = gameState.createEntity();
            gameState.addComponent(swimmer, new PositionComponent(0, 1));
            gameState.addComponent(swimmer, TraversalComponent.of(TraversalMode.SWIMMING));
            gameState.addComponent(swimmer, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(1, gameState.getComponent(swimmer, PositionComponent.class).orElseThrow().getX());
        }

        @Test
        @DisplayName("Only flying entities can traverse mountain tiles")
        void respectsFlyingOnlyMountainTiles() {
            // Target is (2, 2) mountain from (1, 2) grass
            EntityId walker = createWalkingEntity(1, 2);
            gameState.addComponent(walker, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(1, gameState.getComponent(walker, PositionComponent.class).orElseThrow().getX());

            EntityId flyer = createFlyingEntity(1, 2);
            gameState.addComponent(flyer, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);
            assertEquals(2, gameState.getComponent(flyer, PositionComponent.class).orElseThrow().getX());
        }

        @Test
        @DisplayName("Entity with empty TraversalComponent (immobilized/rooted) cannot move")
        void immobilizedEntityCannotMove() {
            EntityId id = gameState.createEntity();
            gameState.addComponent(id, new PositionComponent(0, 0));
            gameState.addComponent(id, TraversalComponent.empty());
            gameState.addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }

        @Test
        @DisplayName("Entity without TraversalComponent (static object) cannot move")
        void staticEntityCannotMove() {
            EntityId id = gameState.createEntity();
            gameState.addComponent(id, new PositionComponent(0, 0));
            gameState.addComponent(id, new MoveIntentComponent(1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
            assertEquals(0, pos.getX());
            assertEquals(0, pos.getY());
        }

        @Test
        @DisplayName("Entity cannot step outside world boundaries")
        void blocksOutOfBoundsMovement() {
            EntityId id = createWalkingEntity(0, 0);
            gameState.addComponent(id, new MoveIntentComponent(-1, 0, 10L));

            movementSystem.update(gameState, 10L);

            PositionComponent pos = gameState.getComponent(id, PositionComponent.class).orElseThrow();
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
            MovementSystem trackingSystem = new MovementSystem(world, BASE_COOLDOWN, true) {
                @Override
                protected void processEntityMove(GameState gs, EntityId id, MoveIntentComponent intent, long tick) {
                    executionOrder.add(id);
                    super.processEntityMove(gs, id, intent, tick);
                }
            };

            EntityId entityLate = createWalkingEntity(0, 0);   // tick 12
            EntityId entityEarly = createWalkingEntity(0, 2);  // tick 10

            gameState.addComponent(entityLate, new MoveIntentComponent(1, 0, 12L));
            gameState.addComponent(entityEarly, new MoveIntentComponent(1, 0, 10L));

            trackingSystem.update(gameState, 15L);

            assertEquals(List.of(entityEarly, entityLate), executionOrder);
        }
    }
}