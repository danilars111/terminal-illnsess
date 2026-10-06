package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.GameCommand;
import io.github.danilars111.contracts.event.TickEvent;
import io.github.danilars111.server.domain.port.out.interfaces.GameStatePublisher;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GameEngine {
    private static final Logger LOGGER = System.getLogger(GameEngine.class.getName());

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
            LOGGER.log(Level.DEBUG, "Tick advanced to {0}", nextTick);
            publisher.publish(new TickEvent(nextTick));
        } else {
            LOGGER.log(Level.TRACE, "Tick skipped due to pause");
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
        if (paused.compareAndSet(false, true)) {
            LOGGER.log(Level.INFO, "GameEngine paused at tick {0}", currentTick.get());
        }
    }

    public void resume() {
        if (paused.compareAndSet(true, false)) {
            LOGGER.log(Level.INFO, "GameEngine resumed at tick {0}", currentTick.get());
        }
    }

    public void setSpeed(GameSpeed speed) {
        Objects.requireNonNull(speed, "GameSpeed cannot be null");
        GameSpeed previous = this.speed.getAndSet(speed);
        if (previous != speed) {
            LOGGER.log(Level.INFO, "GameEngine speed changed from {0} to {1}", previous, speed);
        }
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
