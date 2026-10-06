package io.github.danilars111.server.integration;

import io.github.danilars111.contracts.command.MoveCommand;
import io.github.danilars111.server.domain.component.PositionComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.system.MovementSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MovementIntegrationTest {
    private GameEngine engine;
    private MovementSystem movementSystem;

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
        movementSystem = new MovementSystem();

        engine.registerSystem(movementSystem);

        // Route incoming commands to appropriate systems
        engine.setCommandHandler(command -> {
            if(command instanceof MoveCommand(long entityId, int deltaX, int deltaY)) {
                movementSystem.requestMove(
                        new EntityId(entityId),
                        deltaX,
                        deltaY
                );
            }
        });
    }

    @Test
    @DisplayName("End-to-end: Enqueued MoveCommand updates entity position on engine tick")
    void enqueuedMoveCommandUpdatesPositionOnTick() {
        EntityId entity = engine.getGameState().createEntity();
        engine.getGameState().addComponent(entity, new PositionComponent(10, 10));

        engine.enqueueCommand(new MoveCommand(entity.value(), 1, -1));

        // Advance engine by one tick
        engine.tick();

        PositionComponent position = engine.getGameState()
                .getComponent(entity, PositionComponent.class)
                .orElseThrow();

        assertEquals(11, position.x());
        assertEquals(9, position.y());
    }
}
