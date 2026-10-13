package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.world.records.TileCoord;

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

    public TileCoord getTileCoord() {
        return new TileCoord(this.x, this.y);
    }
}
