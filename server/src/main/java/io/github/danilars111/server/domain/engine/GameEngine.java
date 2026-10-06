package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GameEngine {
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<GameSpeed> speed = new AtomicReference<>(GameSpeed.SPEED_1);

    private final Queue<GameCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private Consumer<GameCommand> commandHandler = _ -> {};

    public GameEngine() {}

    public void tick() {
        if (!paused.get()) {
            drainCommands();
            currentTick.incrementAndGet();
        }
    }

    public void enqueueCommand(GameCommand command) {
        Objects.requireNonNull(command, "GameCommand cannot be null");
        commandQueue.add(command);
    }

    public void setCommandHandler(Consumer<GameCommand> handler) {
        commandHandler = Objects.requireNonNull(handler, "CommandHandler cannot be null");
    }

    private void drainCommands() {
        GameCommand command;
        while ((command = commandQueue.poll()) != null) {
            commandHandler.accept(command);
        }
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public void setSpeed(GameSpeed speed) {
        Objects.requireNonNull(speed, "GameSpeed cannot be null");
        this.speed.set(speed);
    }

    public GameSpeed getSpeed() {
        return speed.get();
    }

    public long getCurrentTick() {
        return currentTick.get();
    }

    public boolean isPaused() {
        return paused.get();
    }
}
