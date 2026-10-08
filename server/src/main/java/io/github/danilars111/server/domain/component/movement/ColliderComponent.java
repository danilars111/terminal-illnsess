package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;

public record ColliderComponent(boolean isColliding) implements Component {
    public static ColliderComponent solid() {
        return new ColliderComponent(true);
    }
}
