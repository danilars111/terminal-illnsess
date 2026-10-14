package io.github.danilars111.server.domain.world;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public class GameWorld {
    private final int width;
    private final int height;
    private final short[] tiles;
    private final TileIndex tileIndex;

    public GameWorld(int width, int height, short defaultTileId, TileIndex tileIndex) {
        short[] tiles = new short[width * height];
        Arrays.fill(tiles, defaultTileId);
        this(width, height, tiles, tileIndex);
    }

    public GameWorld(int width, int height, String defaultTileId, TileIndex tileIndex) {
        short[] tiles = new short[width * height];
        Arrays.fill(tiles, tileIndex.getId(defaultTileId));
        this(width, height, tiles, tileIndex);
    }

    public GameWorld(int width, int height, String[] stringTiles, TileIndex tileIndex) {
        short[] tiles = new short[width * height];
        for (int i = 0; i < stringTiles.length; i++) {
            tiles[i] = tileIndex.getId(stringTiles[i]);
        }
        this(width, height, tiles, tileIndex);
    }


    /**
     * Initializes a world directly with raw numeric palette IDs.
     */
    public GameWorld(int width, int height, short[] tileIds, TileIndex tileIndex) {
        Objects.requireNonNull(tileIds, "TileIds cannot be null");
        Objects.requireNonNull(tileIndex, "TileIndex cannot be null");
        validateDimensions(width, height);

        if (tileIds.length != width * height) {
            throw new IllegalArgumentException("TileIds length (" + tileIds.length + ") must equal width * height (" + (width * height) + ")");
        }

        this.width = width;
        this.height = height;
        this.tileIndex = tileIndex;
        this.tiles = new short[width * height];
        System.arraycopy(tileIds, 0, this.tiles, 0, tileIds.length);
    }

    public boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean isWithinBounds(TileCoord coord) {
        return isWithinBounds(coord.getX(), coord.getY());
    }

    public boolean canPass(int x, int y, Set<TraversalMode> modes) {
        if (!isWithinBounds(x, y)) {
            return false;
        }
        short tileId = tiles[toIndex(x, y)];
        TileDefinition tile = tileIndex.get(tileId);
        for (TraversalMode mode : modes) {
            if (tile.isTraversable(mode)) {
                return true;
            }
        }
        return false;
    }

    public boolean canPass(TileCoord tileCoord, Set<TraversalMode> modes) {
        return canPass(tileCoord.getX(), tileCoord.getY(), modes);
    }

    /**
     * Fast primitive palette ID retrieval for inner hot paths.
     */
    public short getTileId(int x, int y) {
        if (!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates outside of game world (" + x + ", " + y + ")");
        }
        return tiles[toIndex(x, y)];
    }

    public short getTileId(TileCoord coord) {
        return getTileId(coord.getX(), coord.getY());
    }

    /**
     * Retrieves the TileDefinition via TileIndex lookup.
     */
    public TileDefinition getTile(int x, int y) {
        return tileIndex.get(getTileId(x, y));
    }

    public TileDefinition getTile(TileCoord tileCoord) {
        return getTile(tileCoord.getX(), tileCoord.getY());
    }

    public void setTile(int x, int y, short tileId) {
        if (!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates outside of game world (" + x + ", " + y + ")");
        }
        if (!tileIndex.contains(tileId)) {
            throw new IllegalArgumentException("Tile ID " + tileId + " is not registered in TileIndex");
        }
        tiles[toIndex(x, y)] = tileId;
    }

    public void setTile(TileCoord tileCoord, short tileId) {
        setTile(tileCoord.getX(), tileCoord.getY(), tileId);
    }

    public void setTile(int x, int y, String stringId) {
        short tileId = tileIndex.getId(stringId);
        if (tileId == TileIndex.UNKNOWN_TILE_ID) {
            throw new IllegalArgumentException("Unknown tile string ID: " + stringId);
        }
        setTile(x, y, tileId);
    }

    public void setTile(TileCoord tileCoord, String stringId) {
        setTile(tileCoord.getX(), tileCoord.getY(), stringId);
    }

    public TileIndex getTileIndex() {
        return tileIndex;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be greater than 0");
        }
    }

    private int toIndex(int x, int y) {
        return y * width + x;
    }
}