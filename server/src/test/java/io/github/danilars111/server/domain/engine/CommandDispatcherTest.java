package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.interfaces.GameCommand;
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

public class CommandDispatcherTest {

    private CommandDispatcher dispatcher;
    
    private record DummyCommand(Long entityId, String command) implements GameCommand {}
    private record OtherDummyCommand(long entityId) implements GameCommand {}


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
        dispatcher = new CommandDispatcher();
    }
    
    @Test
    @DisplayName("Dispatches command to its specifically registered typed handler")
    void dispatchesToSpecificHandler() {
        List<DummyCommand> handledCommands = new ArrayList<>();
        
        dispatcher.registerHandler(DummyCommand.class, handledCommands::add);
        DummyCommand cmd = new DummyCommand(0L, "Move");
        dispatcher.dispatch(cmd);
        assertEquals(List.of(cmd), handledCommands);
    }

    @Test
    @DisplayName("Ignores commands that have no registered handler without throwing")
    void ignoresCommandsWithoutHandler() {
        assertDoesNotThrow(() -> dispatcher.dispatch(new DummyCommand(0L,"Attack")));
    }

    @Test
    @DisplayName("Can Register multiple handlers across different commandtypes")
    void handlesMultipleCommandTypes() {
        List<String> auditLogs = new ArrayList<>();

        dispatcher.registerHandler(DummyCommand.class, cmd -> auditLogs.add("DummyCommand: " + cmd.entityId()));
        dispatcher.registerHandler(OtherDummyCommand.class, cmd -> auditLogs.add("OtherDummyCommand: " + cmd.entityId()));

        dispatcher.dispatch(new DummyCommand(1L, "Move"));
        dispatcher.dispatch(new OtherDummyCommand(123L));

        assertEquals(List.of("DummyCommand: 1", "OtherDummyCommand: 123"), auditLogs);
    }

    @Test
    @DisplayName("Throws NullPointerException when null command is provided")
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> dispatcher.registerHandler(null, _ -> {}));
        assertThrows(NullPointerException.class, () -> dispatcher.registerHandler(DummyCommand.class, null));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(null));
    }
}
