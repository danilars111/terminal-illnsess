package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;
import io.github.danilars111.contracts.event.TickEvent;
import io.github.danilars111.server.domain.port.out.interfaces.GameStatePublisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GameEngine {
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicReference<GameSpeed> speed = new AtomicReference<>(GameSpeed.SPEED_1);

    private final CommandQueue commandQueue = new CommandQueue();
    private Consumer<GameCommand> commandHandler = _ -> {};
    private GameStatePublisher publisher = _ -> {};

    public GameEngine() {}

    public void tick() {
        if (!paused.get()) {
            commandQueue.drainAll(commandHandler);
            long nextTick = currentTick.incrementAndGet();
            publisher.publish(new TickEvent(nextTick));
        }
    }

    public void enqueueCommand(GameCommand command) {
        commandQueue.enqueue(command);
    }

    public void setCommandHandler(Consumer<GameCommand> handler) {
        commandHandler = Objects.requireNonNull(handler, "CommandHandler cannot be null");
    }

    public void setPublisher(GameStatePublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "Publisher cannot be null");
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
