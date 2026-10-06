package io.github.danilars111.server.domain.component;

import io.github.danilars111.server.domain.component.interfaces.Component;

public record PositionComponent(int x, int y) implements Component {

    public PositionComponent translate(int deltaX, int deltaY) {
        return new PositionComponent(this.x + deltaX, this.y + deltaY);
    }
    public int getX() {
        return this.x;
    }
    public int getY() {
        return this.y;
    }
}
