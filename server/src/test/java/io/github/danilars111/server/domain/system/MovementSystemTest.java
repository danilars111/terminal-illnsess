package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.PositionComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MovementSystemTest {

    private GameState gameState;
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
        gameState = new GameState();
        movementSystem = new MovementSystem();
    }

    @Test
    @DisplayName("Moves Entity by translating PositionComponnt when movment intent is present")
    void movesEntityWithIntent() {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(5, 5));

        // Submit intent for entity to move delta (+1, -2)
        movementSystem.requestMove(entity, 1, -2);

        // Run system update for tick 1
        movementSystem.update(gameState, 1);

        PositionComponent position = gameState.getComponent(entity, PositionComponent.class).orElseThrow();
        assert position.getX() == 6;
        assert position.getY() == 3;
    }

    @Test
    @DisplayName("Clears movement intent after processing")
    void intentIsConsumedPerTick() {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(0, 0));

        movementSystem.requestMove(entity, 2, 3);

        //Tick 1
        movementSystem.update(gameState, 1L);
        assertEquals(new PositionComponent(2, 3), gameState.getComponent(entity, PositionComponent.class).orElseThrow());

        // Tick 2 (no new intent requested)
        movementSystem.update(gameState, 2L);
        assertEquals(new PositionComponent(2, 3), gameState.getComponent(entity, PositionComponent.class).orElseThrow());
    }

    @Test
    @DisplayName("Ignores move requests for entities without PositionComponent")
    void ignoresEntitiesWithoutPosition() {
        EntityId entity = gameState.createEntity();

        assertDoesNotThrow(() -> movementSystem.requestMove(entity, 1, 1));
        assertTrue(gameState.getComponent(entity, PositionComponent.class).isEmpty());
    }
}
