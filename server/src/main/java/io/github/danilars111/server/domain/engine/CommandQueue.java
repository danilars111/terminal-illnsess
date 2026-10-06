package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.interfaces.GameCommand;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class CommandQueue {

    Logger LOGGER = System.getLogger(CommandQueue.class.getName());

    private final Queue<GameCommand> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(GameCommand command) {
        Objects.requireNonNull(command, "GameCommand cannot be null");
        queue.offer(command);
        LOGGER.log(Level.DEBUG, "Enqueued Command: {0}", command);
    }

    public void drainAll(Consumer<GameCommand> consumer) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        int count = 0;
        GameCommand command;
        while((command = queue.poll()) != null) {
            consumer.accept(command);
            count++;
        }
        if(count > 0) {
            LOGGER.log(Level.DEBUG, "Drained {0} Commands from queue", count);
        }
    }
}
