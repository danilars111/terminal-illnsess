package io.github.danilars111.server.domain.world;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("World Tests")
class WorldTest {

    private static final String LEVEL_1 = "depth_1";
    private static final String LEVEL_2 = "depth_2";

    private TileIndex tileIndex;
    private TilePalette tilePalette;
    private World world;

    private TileDefinition floorDef;
    private TileDefinition wallDef;
    private short floorId;
    private short wallId;

    @BeforeEach
    void setUp() {
        floorDef = new TileDefinition(
                "floor",
                "Stone Floor",
                Set.of(TraversalMode.WALKING),
                Set.of()
        );
        wallDef = new TileDefinition(
                "wall",
                "Stone Wall",
                Set.of(),
                Set.of()
        );

        DefinitionRepository<TileDefinition> repo = new DefinitionRepository<>();
        repo.replaceAll(Map.of(
                floorDef.id(), floorDef,
                wallDef.id(), wallDef
        ));

        tileIndex = new TileIndex();
        floorId = tileIndex.register("floor");
        wallId = tileIndex.register("wall");

        tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, repo);

        world = new World(tilePalette, tileIndex);
    }

    private LevelGrid createGrid(String id, int width, int height, short initialTile) {
        short[] tiles = new short[width * height];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = initialTile;
        }
        return new LevelGrid(id, width, height, tiles);
    }

    @Nested
    @DisplayName("Constructor and Null Guard Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create World with valid dependencies")
        void shouldCreateWithValidDependencies() {
            assertNotNull(world.getTilePalette());
            assertNotNull(world.getTileIndex());
            assertTrue(world.getAllLevels().isEmpty());
        }

        @Test
        @DisplayName("Should throw NullPointerException when dependencies are null")
        void shouldThrowOnNullDependencies() {
            assertThrows(NullPointerException.class, () -> new World(null, tileIndex));
            assertThrows(NullPointerException.class, () -> new World(tilePalette, null));
        }
    }

    @Nested
    @DisplayName("Level Registration and Lifecycle Tests")
    class LevelRegistrationTests {

        @Test
        @DisplayName("Should register and retrieve LevelGrid successfully")
        void shouldRegisterAndRetrieveLevel() {
            LevelGrid grid = createGrid(LEVEL_1, 5, 5, floorId);
            world.registerLevel(grid);

            assertTrue(world.hasLevel(LEVEL_1));
            Optional<LevelGrid> retrieved = world.getLevel(LEVEL_1);
            assertTrue(retrieved.isPresent());
            assertSame(grid, retrieved.get());
            assertSame(grid, world.getRequiredLevel(LEVEL_1));
        }

        @Test
        @DisplayName("Should throw when registering null LevelGrid")
        void shouldThrowOnNullLevelRegistration() {
            assertThrows(NullPointerException.class, () -> world.registerLevel(null));
        }

        @Test
        @DisplayName("Should throw when registering duplicate level ID")
        void shouldThrowOnDuplicateLevelId() {
            LevelGrid gridA = createGrid(LEVEL_1, 5, 5, floorId);
            LevelGrid gridB = createGrid(LEVEL_1, 10, 10, wallId);

            world.registerLevel(gridA);
            assertThrows(IllegalStateException.class, () -> world.registerLevel(gridB));
        }

        @Test
        @DisplayName("Should return empty Optional for unregistered level")
        void shouldReturnEmptyOptionalForUnregisteredLevel() {
            assertFalse(world.hasLevel("unknown_level"));
            assertTrue(world.getLevel("unknown_level").isEmpty());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when required level is missing")
        void shouldThrowWhenRequiredLevelMissing() {
            assertThrows(IllegalArgumentException.class, () -> world.getRequiredLevel("missing_level"));
        }

        @Test
        @DisplayName("Should return all registered levels as an unmodifiable collection")
        void shouldReturnUnmodifiableLevelCollection() {
            LevelGrid grid1 = createGrid(LEVEL_1, 5, 5, floorId);
            LevelGrid grid2 = createGrid(LEVEL_2, 6, 6, wallId);

            world.registerLevel(grid1);
            world.registerLevel(grid2);

            Collection<LevelGrid> all = world.getAllLevels();
            assertEquals(2, all.size());
            assertTrue(all.contains(grid1));
            assertTrue(all.contains(grid2));

            assertThrows(UnsupportedOperationException.class, () -> all.clear());
        }
    }

    @Nested
    @DisplayName("Bounds and Coordinate Queries")
    class BoundsQueryTests {

        @BeforeEach
        void registerTestLevel() {
            world.registerLevel(createGrid(LEVEL_1, 4, 3, floorId));
        }

        @Test
        @DisplayName("Should correctly verify coordinates within level bounds")
        void shouldCheckBoundsCorrectly() {
            assertTrue(world.isWithinBounds(LEVEL_1, 0, 0));
            assertTrue(world.isWithinBounds(LEVEL_1, 3, 2));
            assertTrue(world.isWithinBounds(LEVEL_1, new TileCoord(2, 1)));

            assertFalse(world.isWithinBounds(LEVEL_1, -1, 0));
            assertFalse(world.isWithinBounds(LEVEL_1, 0, -1));
            assertFalse(world.isWithinBounds(LEVEL_1, 4, 2));
            assertFalse(world.isWithinBounds(LEVEL_1, 3, 3));
            assertFalse(world.isWithinBounds(LEVEL_1, new TileCoord(10, 10)));
        }

        @Test
        @DisplayName("Should return false for bounds query on unregistered level")
        void shouldReturnFalseForMissingLevelBounds() {
            assertFalse(world.isWithinBounds("unknown_level", 0, 0));
            assertFalse(world.isWithinBounds("unknown_level", new TileCoord(0, 0)));
        }
    }

    @Nested
    @DisplayName("Tile and Definition Queries")
    class TileQueryTests {

        private LevelGrid grid;

        @BeforeEach
        void setUpLevels() {
            grid = createGrid(LEVEL_1, 4, 4, floorId);
            grid.setTileId(1, 1, wallId);
            world.registerLevel(grid);
        }

        @Test
        @DisplayName("Should retrieve raw tile IDs by coordinate and TileCoord")
        void shouldRetrieveTileIds() {
            assertEquals(floorId, world.getTileId(LEVEL_1, 0, 0));
            assertEquals(wallId, world.getTileId(LEVEL_1, 1, 1));
            assertEquals(wallId, world.getTileId(LEVEL_1, new TileCoord(1, 1)));
        }

        @Test
        @DisplayName("Should retrieve TileDefinition from palette via coordinates")
        void shouldRetrieveTileDefinitions() {
            TileDefinition defFloor = world.getTileDefinition(LEVEL_1, 0, 0);
            assertNotNull(defFloor);
            assertEquals("floor", defFloor.id());
            assertEquals("Stone Floor", defFloor.displayName());

            TileDefinition defWall = world.getTileDefinition(LEVEL_1, 1, 1);
            assertNotNull(defWall);
            assertEquals("wall", defWall.id());
            assertEquals("Stone Wall", defWall.displayName());

            TileDefinition defCoord = world.getTileDefinition(LEVEL_1, new TileCoord(1, 1));
            assertSame(defWall, defCoord);
        }

        @Test
        @DisplayName("Should reflect updated definitions if tile is mutated on LevelGrid")
        void shouldReflectLevelMutations() {
            assertEquals(floorId, world.getTileId(LEVEL_1, 2, 2));
            assertEquals("floor", world.getTileDefinition(LEVEL_1, 2, 2).id());

            grid.setTileId(2, 2, wallId);

            assertEquals(wallId, world.getTileId(LEVEL_1, 2, 2));
            assertEquals("wall", world.getTileDefinition(LEVEL_1, 2, 2).id());
        }

        @Test
        @DisplayName("Should throw when querying tile ID or definition on missing level")
        void shouldThrowOnMissingLevelQueries() {
            assertThrows(IllegalArgumentException.class, () -> world.getTileId("missing", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> world.getTileDefinition("missing", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> world.getTileDefinition("missing", new TileCoord(0, 0)));
        }

        @Test
        @DisplayName("Should throw IndexOutOfBoundsException when coordinates are out of bounds")
        void shouldThrowOutOfBoundsTileQueries() {
            assertThrows(IndexOutOfBoundsException.class, () -> world.getTileId(LEVEL_1, 10, 10));
            assertThrows(IndexOutOfBoundsException.class, () -> world.getTileDefinition(LEVEL_1, -1, 0));
        }
    }
}