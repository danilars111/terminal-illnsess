package io.github.danilars111.server.domain.world;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public class GameWorld {
    private final int width;
    private final int height;
    private final TileDefinition[] tiles;

    public GameWorld(int width, int height, TileDefinition defaultTile) {
        if(width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be greater than 0");
        }
        Objects.requireNonNull(defaultTile, "DefaultTile cannot be null");
        this.width = width;
        this.height = height;
        this.tiles = new TileDefinition[width * height];
        Arrays.fill(this.tiles, defaultTile);
    }
    
    public boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
    
    public boolean canPass(int x, int y, Set<TraversalMode> modes) {
        if(!isWithinBounds(x, y)) {
            return false;
        }
        TileDefinition tile = getTile(x, y);
        for(TraversalMode mode : modes) {
            if(tile.isTraversable(mode)) {
                return true;
            }
        }
        return false;
    }
    
    public TileDefinition getTile(int x, int y) {
        if(!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates outside of game world (" + x + ", " + y + ")");
        }
        return tiles[toIndex(x, y)];
    }

    public void setTile(int x, int y, TileDefinition tile) {
        if(!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates outside of game world (" + x + ", " + y + ")");
        }
        tiles[toIndex(x, y)] = tile;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private int toIndex(int x, int y) {
        return y * width + x;
    }
}
