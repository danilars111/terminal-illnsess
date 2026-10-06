package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CommandQueueTest {

    private record DummyCommand(String command) implements GameCommand {}

    private CommandQueue queue = new CommandQueue();

    @BeforeEach
    void setUp() {
        queue = new CommandQueue();
    }

    @Test
    @DisplayName("Enqueue stores commands and drainsAll drains them in FIFO order")
    void enqueueAndDrainsInFIFOOrder() {
        GameCommand cmd1 = new DummyCommand("move_north");
        GameCommand cmd2 = new DummyCommand("move_south");

        queue.enqueue(cmd1);
        queue.enqueue(cmd2);

        List<GameCommand> drained = new ArrayList<>();
        queue.drainAll(drained::add);

        assertEquals(List.of(cmd1, cmd2), drained);

        List<GameCommand> drained2 = new ArrayList<>();
        queue.drainAll(drained2::add);
        assertEquals(List.of(), drained2);
    }

    @Test
    @DisplayName("Enqueue throws NullPointerException when null command is provided")
    void rejectNullCommand() {
        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
    }

    @Test
    @DisplayName("DrainAll throws NullPointerException when null consumer is provided")
    void rejectNullDrainConsumer() {
        assertThrows(NullPointerException.class, () -> queue.drainAll(null));
    }

}
