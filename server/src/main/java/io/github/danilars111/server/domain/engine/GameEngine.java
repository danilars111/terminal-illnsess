package io.github.danilars111.server.domain.engine;

import io.github.danilars111.contracts.command.interfaces.GameCommand;
import io.github.danilars111.contracts.event.TickEvent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.engine.enums.GameSpeed;
import io.github.danilars111.server.domain.port.out.interfaces.GameStatePublisher;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
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

    private final GameState gameState;
    private final List<GameSystem> systems = new ArrayList<>();

    private final CommandQueue commandQueue = new CommandQueue();
    private Consumer<GameCommand> commandHandler = _ -> {};
    private GameStatePublisher publisher = _ -> {};

    public GameEngine(GameState gameState) {
        this.gameState = Objects.requireNonNull(gameState, "GameState cannot be null");
    }

    public void tick() {
        if (!paused.get()) {
            commandQueue.drainAll(commandHandler);
            long nextTick = currentTick.incrementAndGet();

            for (GameSystem system : systems) {
                system.update(gameState, nextTick);
            }

            LOGGER.log(Level.DEBUG, "Tick advanced to {0}", nextTick);
            publisher.publish(new TickEvent(nextTick));
        } else {
            LOGGER.log(Level.TRACE, "Tick skipped due to pause");
        }
    }

    public void registerSystem(GameSystem system) {
        Objects.requireNonNull(system, "GameSystem cannot be null");
        systems.add(system);
        LOGGER.log(Level.DEBUG, "Registered GameSystem: {0}", system.getClass().getSimpleName());
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

    public GameState getGameState() {
        return gameState;
    }

    public boolean isPaused() {
        return paused.get();
    }
}