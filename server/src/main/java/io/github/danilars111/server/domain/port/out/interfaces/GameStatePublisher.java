package io.github.danilars111.server.domain.port.out.interfaces;

import io.github.danilars111.contracts.event.TickEvent;

public interface GameStatePublisher {
    void publish(TickEvent event);
}