package io.github.danilars111.server.integration;

import io.github.danilars111.contracts.command.MoveCommand;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.movement.MoveIntentComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.engine.CommandDispatcher;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.system.MovementSystem;
import io.github.danilars111.server.domain.world.GameWorld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MovementIntegrationTest {
    private GameEngine engine;
    private MovementSystem movementSystem;

    private static final int WORLD_SIZE = 20;
    private static final int BASE_COOLDOWN = 4;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING)
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
        // 1. Create a traversable world covering (10, 10) -> (11, 9)
        TileDefinition[] grid = new TileDefinition[WORLD_SIZE * WORLD_SIZE];
        Arrays.fill(grid, grass);
        GameWorld world = new GameWorld(WORLD_SIZE, WORLD_SIZE, grid);

        // 2. Initialize MovementSystem with world & cooldown configuration
        movementSystem = new MovementSystem(world, BASE_COOLDOWN, true);

        engine = new GameEngine();
        engine.registerSystem(movementSystem);

        // 3. Dispatch MoveCommand by attaching MoveIntentComponent to GameState
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.registerHandler(MoveCommand.class, moveCmd -> {
            EntityId targetId = new EntityId(moveCmd.entityId());
            // Attach transient intent with current tick (or 0L if tick not exposed on dispatcher)
            engine.getGameState().addComponent(
                    targetId,
                    new MoveIntentComponent(moveCmd.deltaX(), moveCmd.deltaY(), engine.getCurrentTick())
            );
        });

        engine.setCommandHandler(dispatcher::dispatch);
    }

    @Test
    @DisplayName("End-to-end: Enqueued MoveCommand updates entity position on engine tick")
    void enqueuedMoveCommandUpdatesPositionOnTick() {
        EntityId entity = engine.getGameState().createEntity();
        engine.getGameState().addComponent(entity, new PositionComponent(10, 10));
        // Add walking mode so the entity is allowed to step on grass
        engine.getGameState().addComponent(entity, TraversalComponent.of(TraversalMode.WALKING));

        engine.enqueueCommand(new MoveCommand(entity.value(), 1, -1));

        // Advance engine by one tick
        engine.tick();

        PositionComponent position = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();

        assertEquals(11, position.getX());
        assertEquals(9, position.getY());
    }
}
