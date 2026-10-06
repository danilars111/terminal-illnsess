package io.github.danilars111.contracts.command;

import io.github.danilars111.contracts.command.interfaces.GameCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MoveCommandTest {

    @Test
    @DisplayName("MoveCommand stores target entity ID and movement delta")
    void storesDetlaAndTarget() {
        MoveCommand command = new MoveCommand(1L, 1, 0);

        assertEquals(1L, command.entityId());
        assertEquals(1, command.deltaX());
        assertEquals(0, command.deltaY());
        assertInstanceOf(GameCommand.class, command);
    }

    @Test
    @DisplayName("MoveCommand rejects non-positive EntityId")
    void rejectInvalidEntityId() {
        assertThrows(IllegalArgumentException.class, () -> new MoveCommand(-5L, 1, 0));
    }

    @Test
    @DisplayName("MoveCommand reject zero-delta movement")
    void rejectZeroDeltaMovement() {
        assertThrows(IllegalArgumentException.class, () -> new MoveCommand(1L, 0, 0));
    }
}
