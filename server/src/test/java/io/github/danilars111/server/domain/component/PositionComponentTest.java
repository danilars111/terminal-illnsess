package io.github.danilars111.server.domain.component;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class PositionComponentTest {

    @BeforeAll
    static void enableLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE); // Enables System.Logger Level.DEBUG

        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.SEVERE);
            }
        }
    }

    @Test
    @DisplayName("PositionComponent holds coordinates and implements Component marker")
    void storesCoordinates() {
        PositionComponent position = new PositionComponent(5, 12);
        assertEquals(5, position.getX());
        assertEquals(12, position.getY());
        assertInstanceOf(Component.class, position);
    }

    @Test
    @DisplayName("Convenience translation method creates a new shifted PositionComponent")
    void translateReturnsNewShiftedInstance() {
        PositionComponent initial = new PositionComponent(3, 4);
        PositionComponent translated = initial.translate(2, -1);
        assertEquals(5, translated.getX());
        assertEquals(3, translated.getY());
        assertNotSame(initial, translated);

        // Verify original remains unmutable
        assertEquals(3, initial.getX());
        assertEquals(4, initial.getY());
    }

}
