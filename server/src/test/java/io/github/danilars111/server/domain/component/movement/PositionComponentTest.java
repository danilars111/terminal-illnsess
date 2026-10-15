package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class PositionComponentTest {

    private static final String LEVEL_ID = "dungeon_1";

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

    @Test
    @DisplayName("PositionComponent holds coordinates and levelId and implements Component marker")
    void storesCoordinatesAndLevel() {
        PositionComponent position = new PositionComponent(LEVEL_ID, 5, 12);
        assertEquals(LEVEL_ID, position.levelId());
        assertEquals(5, position.getX());
        assertEquals(12, position.getY());
        assertEquals(new TileCoord(5, 12), position.getTileCoord());
        assertInstanceOf(Component.class, position);
    }

    @Test
    @DisplayName("Throws NullPointerException if levelId is null")
    void throwsOnNullLevelId() {
        assertThrows(NullPointerException.class, () -> new PositionComponent(null, 5, 12));
    }

    @Test
    @DisplayName("Convenience translation method creates a new shifted PositionComponent preserving levelId")
    void translateReturnsNewShiftedInstance() {
        PositionComponent initial = new PositionComponent(LEVEL_ID, 3, 4);
        PositionComponent translated = initial.translate(2, -1);

        assertEquals(LEVEL_ID, translated.levelId());
        assertEquals(5, translated.getX());
        assertEquals(3, translated.getY());
        assertNotSame(initial, translated);

        assertEquals(LEVEL_ID, initial.levelId());
        assertEquals(3, initial.getX());
        assertEquals(4, initial.getY());
    }

    @Test
    @DisplayName("withLevel creates a new instance with updated levelId preserving coordinates")
    void withLevelReturnsNewInstanceWithUpdatedLevel() {
        PositionComponent initial = new PositionComponent(LEVEL_ID, 7, 9);
        PositionComponent switched = initial.withLevel("dungeon_2");

        assertEquals("dungeon_2", switched.levelId());
        assertEquals(7, switched.getX());
        assertEquals(9, switched.getY());
        assertNotSame(initial, switched);

        assertEquals(LEVEL_ID, initial.levelId());
    }
}