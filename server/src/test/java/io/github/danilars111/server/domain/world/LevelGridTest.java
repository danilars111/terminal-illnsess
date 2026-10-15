package io.github.danilars111.server.domain.world;

import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LevelGrid Tests")
class LevelGridTest {

    private static final String LEVEL_ID = "dungeon_1";
    private static final int WIDTH = 4;
    private static final int HEIGHT = 3;

    private short[] createSampleTiles() {
        // 4x3 grid with indices 0..11 matching their tile IDs
        short[] tiles = new short[WIDTH * HEIGHT];
        for (short i = 0; i < tiles.length; i++) {
            tiles[i] = i;
        }
        return tiles;
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should create level grid with valid parameters")
        void shouldCreateWithValidParams() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            assertEquals(LEVEL_ID, grid.getId());
            assertEquals(WIDTH, grid.getWidth());
            assertEquals(HEIGHT, grid.getHeight());
            assertEquals(WIDTH * HEIGHT, grid.getTileCount());
        }

        @Test
        @DisplayName("Should throw when ID or tiles array is null")
        void shouldThrowOnNullArguments() {
            assertThrows(NullPointerException.class, () ->
                    new LevelGrid(null, WIDTH, HEIGHT, createSampleTiles()));
            assertThrows(NullPointerException.class, () ->
                    new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, null));
        }

        @ParameterizedTest(name = "width={0}, height={1}")
        @CsvSource({
                "0, 5",
                "-1, 5",
                "5, 0",
                "5, -1"
        })
        @DisplayName("Should throw when dimensions are zero or negative")
        void shouldThrowOnInvalidDimensions(int width, int height) {
            short[] tiles = new short[Math.max(1, width * height)];
            assertThrows(IllegalArgumentException.class, () ->
                    new LevelGrid(LEVEL_ID, width, height, tiles));
        }

        @Test
        @DisplayName("Should throw when tiles array length does not match width * height")
        void shouldThrowWhenTilesArrayLengthMismatches() {
            short[] wrongSizeTiles = new short[WIDTH * HEIGHT - 1];
            assertThrows(IllegalArgumentException.class, () ->
                    new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, wrongSizeTiles));
        }

        @Test
        @DisplayName("Should defensively copy input tiles array")
        void shouldDefensivelyCopyInputArray() {
            short[] source = createSampleTiles();
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, source);

            source[0] = 999;
            assertEquals(0, grid.getTileId(0, 0), "Grid should maintain its own independent copy");
        }
    }

    @Nested
    @DisplayName("Boundary and Coordinate Checks")
    class BoundaryTests {

        @ParameterizedTest(name = "({0}, {1}) -> inBounds={2}")
        @CsvSource({
                "0, 0, true",
                "3, 2, true",
                "2, 1, true",
                "-1, 0, false",
                "0, -1, false",
                "4, 2, false",
                "3, 3, false",
                "10, 10, false"
        })
        @DisplayName("Should correctly evaluate coordinate boundaries")
        void shouldCheckCoordinateBounds(int x, int y, boolean expected) {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            assertEquals(expected, grid.isWithinBounds(x, y));
            assertEquals(expected, grid.isWithinBounds(new TileCoord(x, y)));
        }

        @Test
        @DisplayName("Should convert 2D coordinates to 1D index correctly")
        void shouldConvertCoordToIndex() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            // index = x + y * width
            assertEquals(0, grid.toIndex(0, 0));
            assertEquals(3, grid.toIndex(3, 0));
            assertEquals(4, grid.toIndex(0, 1));
            assertEquals(7, grid.toIndex(3, 1));
            assertEquals(11, grid.toIndex(3, 2));

            assertEquals(7, grid.toIndex(new TileCoord(3, 1)));
        }

        @Test
        @DisplayName("Should convert 1D index to 2D coordinates correctly")
        void shouldConvertIndexToCoord() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            assertEquals(new TileCoord(0, 0), grid.toCoord(0));
            assertEquals(new TileCoord(3, 0), grid.toCoord(3));
            assertEquals(new TileCoord(0, 1), grid.toCoord(4));
            assertEquals(new TileCoord(3, 1), grid.toCoord(7));
            assertEquals(new TileCoord(3, 2), grid.toCoord(11));
        }
    }

    @Nested
    @DisplayName("Tile Retrieval and Mutation")
    class TileAccessTests {

        @Test
        @DisplayName("Should retrieve correct tile ID by coordinates and index")
        void shouldGetTileId() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            // Coordinates (1, 2) in a 4-wide grid is index 1 + 2 * 4 = 9
            assertEquals(9, grid.getTileId(1, 2));
            assertEquals(9, grid.getTileId(new TileCoord(1, 2)));
            assertEquals(9, grid.getTileIdByIndex(9));
        }

        @Test
        @DisplayName("Should throw IndexOutOfBoundsException when reading outside bounds")
        void shouldThrowWhenReadingOutOfBounds() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            assertThrows(IndexOutOfBoundsException.class, () -> grid.getTileId(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.getTileId(4, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.getTileIdByIndex(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.getTileIdByIndex(12));
        }

        @Test
        @DisplayName("Should mutate tile ID by coordinates, coord record, and index")
        void shouldMutateTileId() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            grid.setTileId(1, 1, (short) 42);
            assertEquals(42, grid.getTileId(1, 1));

            grid.setTileId(new TileCoord(2, 0), (short) 77);
            assertEquals(77, grid.getTileId(2, 0));

            grid.setTileByIndex(0, (short) 100);
            assertEquals(100, grid.getTileId(0, 0));
        }

        @Test
        @DisplayName("Should throw IndexOutOfBoundsException when setting outside bounds")
        void shouldThrowWhenSettingOutOfBounds() {
            LevelGrid grid = new LevelGrid(LEVEL_ID, WIDTH, HEIGHT, createSampleTiles());

            assertThrows(IndexOutOfBoundsException.class, () -> grid.setTileId(-1, 0, (short) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.setTileId(4, 2, (short) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.setTileByIndex(-1, (short) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.setTileByIndex(12, (short) 1));
        }
    }
}