package io.github.danilars111.server.domain.world.records;

public record TileCoord(int x, int y) {
    public int getX() { return  this.x; }
    public int getY() { return  this.y; }
}
