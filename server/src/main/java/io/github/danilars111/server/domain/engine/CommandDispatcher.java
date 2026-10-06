package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.interfaces.GameCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class CommandDispatcher {

    private static final System.Logger LOGGER = System.getLogger(CommandDispatcher.class.getName());
    private final Map<Class<? extends GameCommand>, List<Consumer<GameCommand>>> handlers = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends GameCommand> void registerHandler(Class<T> commandType, Consumer<T> handler) {
        Objects.requireNonNull(commandType, "CommandType cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");

        handlers.computeIfAbsent(commandType, k -> new ArrayList<>())
                .add((Consumer<GameCommand>) handler);
    }
    public void dispatch(GameCommand command) {
        Objects.requireNonNull(command, "Command cannot be null");

        List<Consumer<GameCommand>> matchingHandlers = this.handlers.get(command.getClass());
        if(matchingHandlers == null || matchingHandlers.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No handler registered for command: {0}", command.getClass().getSimpleName());
            return;
        }
        matchingHandlers.forEach(handler -> handler.accept(command));
    }

}
