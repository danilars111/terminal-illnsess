package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Objects;

public record PositionComponent(String levelId, int x, int y) implements Component {

    public PositionComponent {
        Objects.requireNonNull(levelId, "levelId cannot be null");
    }

    public PositionComponent(String levelId, TileCoord tileCoord) {
        this(levelId, tileCoord.getX(), tileCoord.getY());
    }

    public PositionComponent translate(int deltaX, int deltaY) {
        return new PositionComponent(this.levelId, this.x + deltaX, this.y + deltaY);
    }

    public PositionComponent withLevel(String newLevelId) {
        return new PositionComponent(newLevelId, this.x, this.y);
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