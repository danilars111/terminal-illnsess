package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;

public record MoveIntentComponent(int deltaX, int deltaY, long requestedTick) implements Component {

    public MoveIntentComponent(int deltaX, int deltaY) {
        this(deltaX, deltaY, 0L);
    }
}
