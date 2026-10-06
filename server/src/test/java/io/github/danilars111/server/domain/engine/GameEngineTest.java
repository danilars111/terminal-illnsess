package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.interfaces.GameCommand;
import io.github.danilars111.contracts.event.TickEvent;
import io.github.danilars111.server.domain.engine.enums.GameSpeed;
import io.github.danilars111.server.domain.port.out.interfaces.GameStatePublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;


public class GameEngineTest {
    private  record DummyCommand(String command) implements GameCommand {}

    private GameEngine engine;

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

    @BeforeEach
    void setUp() {
        engine = new GameEngine();
    }

    @Test
    @DisplayName("Executes registered systems sequentially on tick")
    void executesSystemsOnTick() {
        List<String> executionOrder = new ArrayList<>();

        engine.registerSystem((state, tick) -> executionOrder.add("system1_tick_" + tick));
        engine.registerSystem((state, tick) -> executionOrder.add("system2_tick_" + tick));
        engine.registerSystem((state, tick) -> executionOrder.add("system3_tick_" + tick));

        engine.tick();
        assertEquals(List.of("system1_tick_1", "system2_tick_1", "system3_tick_1"), executionOrder);
    }

    @Test
    @DisplayName("Does not execute systems when paused")
    void doesNotExecuteSystemsWhenPaused() {
        List<String> executionOrder = new ArrayList<>();
        engine.registerSystem((state, tick) -> executionOrder.add("system"));

        engine.pause();
        engine.tick();
        assertTrue(executionOrder.isEmpty());
    }

    @Test
    @DisplayName("Provides access to its underlying GameState")
    void providesAccessToGameState() {
        assertNotNull(engine.getGameState());
    }

    @Test
    @DisplayName("Registering null system throws NullPointerException")
    void rejectsNullSystem() {
        assertThrows(NullPointerException.class, () -> engine.registerSystem(null));
    }

    @Test
    @DisplayName("Publishes TickEvent with current tick number after advancing")
    void publishesTickEventOnTick() {
        List<TickEvent> publishedEvents = new ArrayList<>();
        GameStatePublisher publisher = publishedEvents::add;
        engine.setPublisher(publisher);

        engine.tick();
        assertEquals(List.of(new TickEvent(1)), publishedEvents);
        engine.tick();
        assertEquals(List.of(new TickEvent(1), new TickEvent(2)), publishedEvents);
    }

    @Test
    @DisplayName("Does not publish TickEvent when paused")
    void doesNotPublishTickEventWhenPaused() {
        List<TickEvent> publishedEvents = new ArrayList<>();
        GameStatePublisher publisher = publishedEvents::add;
        engine.setPublisher(publisher);
        engine.pause();
        engine.tick();
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    @DisplayName("Setting Null publisher throws NullPointerException")
    void rejectsNullPublisher() {
        assertThrows(NullPointerException.class, () -> engine.setPublisher(null));
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
