package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameEngineTest {
    private  record DummyCommand(String command) implements GameCommand {}

    private GameEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GameEngine();
    }

    @Test
    @DisplayName("Queued commands are drained and executed on tick")
    void drainsCommandsOnTick() {
        List<GameCommand> executedCommands = new ArrayList<>();
        engine.setCommandHandler(executedCommands::add);

        GameCommand cmd1 = new DummyCommand("move_north");
        GameCommand cmd2 = new DummyCommand("attack");

        engine.enqueueCommand(cmd1);
        engine.enqueueCommand(cmd2);

        assertTrue(executedCommands.isEmpty());
        engine.tick();
        assertEquals(List.of(cmd1, cmd2), executedCommands);
    }

    @Test
    @DisplayName("Queued commands are NOT executed when paused")
    void commandsRetainedWhenPaused() {
        List<GameCommand> executedCommands = new ArrayList<>();
        engine.setCommandHandler(executedCommands::add);
        GameCommand cmd1 = new DummyCommand("move_north");
        engine.enqueueCommand(cmd1);
        engine.pause();
        engine.tick();

        assertTrue(executedCommands.isEmpty());

        engine.resume();
        engine.tick();
        assertEquals(List.of(cmd1), executedCommands);
    }

    @Test
    @DisplayName("Enqueueing Null command throws NullPointerException")
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> engine.enqueueCommand(null));
    }

    @Test
    @DisplayName("Engine should start at tick 0 and unpaused")
    void startsAtZeroAndUnpaused() {
        assertEquals(GameSpeed.SPEED_1, engine.getSpeed());
        assertEquals(0, engine.getCurrentTick());
        assertFalse(engine.isPaused());
    }

    @Test
    @DisplayName("Advancing engine increments the tick by 1 regardless of speed setting")
    void tickAlwaysAdvancesByOne() {
        int expectedTick = 0;
        assertEquals(expectedTick, engine.getCurrentTick());
        for(GameSpeed speed : GameSpeed.values()) {
            engine.setSpeed(speed);
            engine.tick();
            expectedTick++;
            assertEquals(expectedTick, engine.getCurrentTick());
        }
    }

    @Test
    @DisplayName("Engine does not increment tick count when paused")
    void doesNotAdvanceTickWhenPaused() {
        engine.pause();
        engine.tick();
        assertEquals(0, engine.getCurrentTick());
        assertTrue(engine.isPaused());
    }

    @Test
    @DisplayName("Resuming after pause allows ticks to advance again")
    void resumesAfterPause(){
        engine.pause();
        engine.tick();
        assertEquals(0, engine.getCurrentTick());
        engine.resume();
        engine.tick();
        assertEquals(1, engine.getCurrentTick());
    }

    @Test
    @DisplayName("Can set different valid speed settings")
    void canChangeSpeedSettings() {
        for(GameSpeed speed : GameSpeed.values()) {
            engine.setSpeed(speed);
            assertEquals(speed, engine.getSpeed());
        }
    }

    @Test
    @DisplayName("Setting Null speed throws NullPointerException")
    void rejectsNullSpeed() {
        assertThrows(NullPointerException.class, () -> engine.setSpeed(null));
    }
}
