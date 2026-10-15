package io.github.danilars111.server.domain.world;

import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Objects;

public class LevelGrid {

    private final String id;
    private final int width;
    private final int height;
    private final short[] tiles;

    public LevelGrid(String id, int width, int height, short[] tiles) {
        this.id = Objects.requireNonNull(id, "Level id cannot be null");
        Objects.requireNonNull(tiles, "Tiles array cannot be null");

        validateDimensions(width, height, tiles);

        this.width = width;
        this.height = height;
        this.tiles = new short[tiles.length];
        System.arraycopy(tiles, 0, this.tiles, 0, tiles.length);
    }

    public String getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    public int getTileCount() {
        return tiles.length;
    }
    public boolean isWithinBounds(TileCoord tileCoord) {
        return isWithinBounds(tileCoord.getX(), tileCoord.getY());
    }

    public boolean isWithinBounds(int x, int y) {
        return  x >= 0 && x < width && y >= 0 && y < height;
    }

    public int toIndex(int x, int y) {
        return x + y * width;
    }

    public int toIndex(TileCoord coord) {
        return toIndex(coord.getX(), coord.getY());
    }

    public TileCoord toCoord(int index) {
        return new TileCoord(index % width, index / width);
    }

    public short getTileId(int x, int y) {
        if (!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates out of bounds (" + x + ", " + y + ") on grid: " + id);
        }
        return tiles[toIndex(x, y)];
    }

    public short getTileId(TileCoord coord) {
        return getTileId(coord.getX(), coord.getY());
    }

    public short getTileIdByIndex(int index) {
        if (index < 0 || index >= tiles.length) {
            throw new IndexOutOfBoundsException("Grid index out of bounds: " + index);
        }
        return tiles[index];
    }

    public void setTileId(int x, int y, short tileId) {
        if (!isWithinBounds(x, y)) {
            throw new IndexOutOfBoundsException("Coordinates out of bounds (" + x + ", " + y + ") on grid: " + id);
        }
        setTileByIndex(toIndex(x, y), tileId);
    }

    public void setTileId(TileCoord coord, short tileId) {
        setTileId(coord.getX(), coord.getY(), tileId);
    }

    public void setTileByIndex(int index, short tileId) {
        if (index < 0 || index >= tiles.length) {
            throw new IndexOutOfBoundsException("Grid index out of bounds: " + index);
        }
        tiles[index] = tileId;
    }


    private void validateDimensions(int width, int height,  short[] tiles) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be > 0 (width: " + width + ", height: " + height + ")");
        }

        if (tiles.length != width * height) {
            throw new IllegalArgumentException("Tiles array length (" + tiles.length + ") does not match dimensions (" + (width * height) + ")");
        }
    }
}
