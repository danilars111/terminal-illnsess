package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class CommandQueue {

    private final Queue<GameCommand> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(GameCommand command) {
        Objects.requireNonNull(command, "GameCommand cannot be null");
        queue.offer(command);
    }

    public void drainAll(Consumer<GameCommand> consumer) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        GameCommand command;
        while ((command = queue.poll()) != null) {
            consumer.accept(command);
        }
    }
}
