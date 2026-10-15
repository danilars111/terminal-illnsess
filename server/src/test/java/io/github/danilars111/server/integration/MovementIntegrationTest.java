package io.github.danilars111.server.integration;

import io.github.danilars111.contracts.command.MoveCommand;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.engine.CommandDispatcher;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.system.CooldownSystem;
import io.github.danilars111.server.domain.system.MovementSystem;
import io.github.danilars111.server.domain.world.LevelGrid;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MovementIntegrationTest {

    private static final String LEVEL_ID = "overworld";
    private static final int WORLD_SIZE = 20;
    private static final int BASE_COOLDOWN = 4;

    private GameEngine engine;
    private MovementSystem movementSystem;
    private CooldownSystem cooldownSystem;
    private TileIndex tileIndex;
    private TilePalette tilePalette;
    private World world;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING),
            Set.of()
    );

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
        engine = new GameEngine();

        DefinitionRepository<TileDefinition> tileRepo = new DefinitionRepository<>();
        tileRepo.replaceAll(Map.of(grass.id(), grass));

        tileIndex = new TileIndex();
        short grassId = tileIndex.register(grass.id());

        tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, tileRepo);

        world = new World(tilePalette, tileIndex);

        short[] tiles = new short[WORLD_SIZE * WORLD_SIZE];
        Arrays.fill(tiles, grassId);
        LevelGrid grid = new LevelGrid(LEVEL_ID, WORLD_SIZE, WORLD_SIZE, tiles);
        world.registerLevel(grid);

        MovementRuleService movementRuleService = new MovementRuleService(world);

        cooldownSystem = new CooldownSystem();
        movementSystem = new MovementSystem(movementRuleService, BASE_COOLDOWN);

        engine.registerSystem(cooldownSystem);
        engine.registerSystem(movementSystem);

        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.registerHandler(MoveCommand.class, moveCmd -> {
            EntityId targetId = new EntityId(moveCmd.entityId());
            engine.getGameState().addComponent(
                    targetId,
                    new MoveIntentComponent(moveCmd.deltaX(), moveCmd.deltaY(), engine.getCurrentTick())
            );
        });

        engine.setCommandHandler(dispatcher::dispatch);
    }

    private EntityId spawnPlayer(int x, int y) {
        EntityId entity = engine.getGameState().createEntity();
        PositionComponent pos = new PositionComponent(LEVEL_ID, x, y);
        engine.getGameState().addComponent(entity, pos);
        engine.getGameState().addComponent(entity, TraversalComponent.of(TraversalMode.WALKING));
        engine.getGameState().getSpatialIndex().updateEntity(entity, pos, null);
        return entity;
    }

    @Test
    @DisplayName("End-to-end: Enqueued MoveCommand updates entity position on engine tick")
    void enqueuedMoveCommandUpdatesPositionOnTick() {
        EntityId entity = spawnPlayer(10, 10);

        engine.enqueueCommand(new MoveCommand(entity.value(), 1, -1));
        engine.tick();

        PositionComponent position = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();

        assertEquals(11, position.getX());
        assertEquals(9, position.getY());
    }

    @Test
    @DisplayName("End-to-end: Enforces cooldown between moves and allows moving again once cooled down")
    void enforcesAndRecoversCooldownOverMultipleTicks() {
        EntityId entity = spawnPlayer(10, 10);

        // Tick 1: Initial step (dx=1 dy=0) -> Orthogonal movement incurs BASE_COOLDOWN cooldown
        engine.enqueueCommand(new MoveCommand(entity.value(), 1, 0));
        engine.tick();

        PositionComponent posAfterFirstMove = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();
        assertEquals(11, posAfterFirstMove.getX());
        assertEquals(10, posAfterFirstMove.getY());

        // Tick 2: Attempt to move immediately on the next tick -> Cooldown enforced
        engine.enqueueCommand(new MoveCommand(entity.value(), 1, 0));
        engine.tick();

        PositionComponent posBlocked = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();

        assertEquals(11, posBlocked.getX());
        assertEquals(10, posBlocked.getY());

        // Tick 3 and 4: Cooldown continues ticking down (remaining 2->1)
        engine.tick();
        engine.tick();

        // Tick 5: Cooldown is now 0, player can move again
        engine.enqueueCommand(new MoveCommand(entity.value(), 1, 0));
        engine.tick();

        PositionComponent posFinal = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();
        assertEquals(12, posFinal.getX());
        assertEquals(10, posFinal.getY());
    }

    @Test
    @DisplayName("End-to-end: Multi-tile entity moves and synchronizes its full footprint in SpatialIndex")
    void multiTileEntityMovesAndUpdatesSpatialIndex() {
        EntityId boss = spawnPlayer(5, 5);
        FootprintComponent footprintComp = FootprintComponent.rectangle(2, 2);
        engine.getGameState().addComponent(boss, footprintComp);
        engine.getGameState().getSpatialIndex().updateEntity(boss, new PositionComponent(LEVEL_ID, 5, 5), footprintComp);

        engine.enqueueCommand(new MoveCommand(boss.value(), 1, 0));
        engine.tick();

        PositionComponent pos = engine.getGameState()
                .getComponent(boss, PositionComponent.class)
                .orElseThrow();
        assertEquals(6, pos.getX());
        assertEquals(5, pos.getY());

        SpatialIndex spatialIndex = engine.getGameState().getSpatialIndex();
        Set<TileCoord> occupied = spatialIndex.getFootprintOf(boss);
        assertEquals(4, occupied.size());
        assertTrue(occupied.contains(new TileCoord(6, 5)));
        assertTrue(occupied.contains(new TileCoord(7, 5)));
        assertTrue(occupied.contains(new TileCoord(6, 6)));
        assertTrue(occupied.contains(new TileCoord(7, 6)));

        assertTrue(spatialIndex.getEntitiesAt(LEVEL_ID, 5, 5).isEmpty());
        assertTrue(spatialIndex.getEntitiesAt(LEVEL_ID, 5, 6).isEmpty());
    }

    @Test
    @DisplayName("End-to-end: MoveCommand is rejected when path is blocked by dynamic entity with ColliderComponent")
    void moveCommandBlockedByDynamicColliderEntity() {
        EntityId mover = spawnPlayer(10, 10);

        EntityId obstacle = engine.getGameState().createEntity();
        PositionComponent obsPos = new PositionComponent(LEVEL_ID, 11, 10);
        engine.getGameState().addComponent(obstacle, obsPos);
        engine.getGameState().addComponent(obstacle, new ColliderComponent());
        engine.getGameState().getSpatialIndex().updateEntity(obstacle, obsPos, null);

        engine.enqueueCommand(new MoveCommand(mover.value(), 1, 0));
        engine.tick();

        PositionComponent pos = engine.getGameState()
                .getComponent(mover, PositionComponent.class)
                .orElseThrow();

        assertEquals(10, pos.getX(), "Mover must be blocked by the obstacle");
        assertEquals(10, pos.getY());
    }

    @Test
    @DisplayName("End-to-end: Destroying an obstacle unblocks the path for subsequent MoveCommands")
    void destroyingObstacleUnblocksMovementPath() {
        EntityId mover = spawnPlayer(10, 10);

        EntityId obstacle = engine.getGameState().createEntity();
        PositionComponent obsPos = new PositionComponent(LEVEL_ID, 11, 10);
        engine.getGameState().addComponent(obstacle, obsPos);
        engine.getGameState().addComponent(obstacle, new ColliderComponent());
        engine.getGameState().getSpatialIndex().updateEntity(obstacle, obsPos, null);

        // Tick 1: Move blocked
        engine.enqueueCommand(new MoveCommand(mover.value(), 1, 0));
        engine.tick();
        assertEquals(10, engine.getGameState().getComponent(mover, PositionComponent.class).orElseThrow().getX());

        // Destroy the obstacle (GameState clears it from SpatialIndex)
        engine.getGameState().destroyEntity(obstacle);

        // Tick 2: Move now succeeds
        engine.enqueueCommand(new MoveCommand(mover.value(), 1, 0));
        engine.tick();

        PositionComponent posAfterDestroy = engine.getGameState()
                .getComponent(mover, PositionComponent.class)
                .orElseThrow();
        assertEquals(11, posAfterDestroy.getX(), "Path must open once obstacle is destroyed");
        assertEquals(10, posAfterDestroy.getY());
    }

    @Test
    @DisplayName("End-to-end: Player with NOCLIP bypasses dynamic colliders via MoveCommand")
    void noClipPlayerPhasesThroughDynamicColliders() {
        EntityId cheater = engine.getGameState().createEntity();
        PositionComponent cheaterPos = new PositionComponent(LEVEL_ID, 10, 10);
        engine.getGameState().addComponent(cheater, cheaterPos);
        engine.getGameState().addComponent(cheater, TraversalComponent.of(TraversalMode.NOCLIP));
        engine.getGameState().getSpatialIndex().updateEntity(cheater, cheaterPos, null);

        EntityId solidWall = engine.getGameState().createEntity();
        PositionComponent wallPos = new PositionComponent(LEVEL_ID, 11, 10);
        engine.getGameState().addComponent(solidWall, wallPos);
        engine.getGameState().addComponent(solidWall, new ColliderComponent());
        engine.getGameState().getSpatialIndex().updateEntity(solidWall, wallPos, null);

        engine.enqueueCommand(new MoveCommand(cheater.value(), 1, 0));
        engine.tick();

        PositionComponent pos = engine.getGameState()
                .getComponent(cheater, PositionComponent.class)
                .orElseThrow();

        assertEquals(11, pos.getX(), "NOCLIP should walk through dynamic colliders");
        assertEquals(10, pos.getY());
    }

    @Test
    @DisplayName("End-to-end: Two entities attempting to step into the same tile resolve deterministically")
    void competingEntitiesResolveMovementDeterministically() {
        EntityId p1 = spawnPlayer(9, 10);
        engine.getGameState().addComponent(p1, new ColliderComponent());
        engine.getGameState().getSpatialIndex().updateEntity(p1, new PositionComponent(LEVEL_ID, 9, 10), null);

        EntityId p2 = spawnPlayer(11, 10);
        engine.getGameState().addComponent(p2, new ColliderComponent());
        engine.getGameState().getSpatialIndex().updateEntity(p2, new PositionComponent(LEVEL_ID, 11, 10), null);

        engine.enqueueCommand(new MoveCommand(p1.value(), 1, 0));
        engine.enqueueCommand(new MoveCommand(p2.value(), -1, 0));

        engine.tick();

        PositionComponent p1Pos = engine.getGameState().getComponent(p1, PositionComponent.class).orElseThrow();
        PositionComponent p2Pos = engine.getGameState().getComponent(p2, PositionComponent.class).orElseThrow();

        assertEquals(10, p1Pos.getX(), "Lower EntityId wins contested tile");
        assertEquals(10, p1Pos.getY());

        assertEquals(11, p2Pos.getX(), "Competing entity is blocked by winner's collider");
        assertEquals(10, p2Pos.getY());
    }
}