package io.github.danilars111.server.domain.ecs.interfaces;

import io.github.danilars111.server.domain.ecs.GameState;

@FunctionalInterface
public interface GameSystem {
    void update(GameState gameState, long tick);
}
