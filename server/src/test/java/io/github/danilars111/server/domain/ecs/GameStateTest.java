package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.world.LevelGrid;
import io.github.danilars111.server.domain.world.World;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    private static final String LEVEL_ID = "test_level";

    private EntityManager entityManager;
    private SpatialIndex spatialIndex;
    private World world;
    private GameState gameState;

    @BeforeEach
    void setUp() {
        entityManager = new EntityManager();
        spatialIndex = new SpatialIndex();

        TileDefinition floor = new TileDefinition("floor", "Floor", Set.of(TraversalMode.WALKING), Set.of());
        DefinitionRepository<TileDefinition> repo = new DefinitionRepository<>();
        repo.replaceAll(Map.of(floor.id(), floor));

        TileIndex tileIndex = new TileIndex();
        short floorId = tileIndex.register(floor.id());

        TilePalette palette = new TilePalette();
        palette.rebuild(tileIndex, repo);

        world = new World(palette, tileIndex);
        world.registerLevel(new LevelGrid(LEVEL_ID, 10, 10, new short[100]));

        gameState = new GameState(entityManager, spatialIndex, world);
    }

    @Nested
    @DisplayName("Aggregate Construction & Invariants")
    class InvariantsTests {

        @Test
        @DisplayName("Rejects null constructor arguments")
        void rejectsNullConstructorArguments() {
            assertThrows(NullPointerException.class, () -> new GameState(null, spatialIndex, world));
            assertThrows(NullPointerException.class, () -> new GameState(entityManager, null, world));
            assertThrows(NullPointerException.class, () -> new GameState(entityManager, spatialIndex, null));
        }

        @Test
        @DisplayName("Exposes constituent domain aggregates")
        void exposesAggregates() {
            assertSame(entityManager, gameState.entityManager());
            assertSame(spatialIndex, gameState.spatialIndex());
            assertSame(world, gameState.world());
        }
    }

    @Nested
    @DisplayName("Automatic Spatial Lifecycle Synchronization")
    class SpatialSyncTests {

        @Test
        @DisplayName("Adding PositionComponent automatically indexes entity in SpatialIndex")
        void addingPositionUpdatesSpatialIndex() {
            EntityId id = entityManager.createEntity();
            TileCoord target = new TileCoord(3, 4);

            entityManager.addComponent(id, new PositionComponent(LEVEL_ID, target));

            Set<EntityId> found = spatialIndex.getEntitiesAt(LEVEL_ID, target);
            assertEquals(Set.of(id), found);
        }

        @Test
        @DisplayName("Updating PositionComponent moves entity in SpatialIndex")
        void updatingPositionMovesEntityInSpatialIndex() {
            EntityId id = entityManager.createEntity();
            TileCoord from = new TileCoord(2, 2);
            TileCoord to = new TileCoord(2, 3);

            entityManager.addComponent(id, new PositionComponent(LEVEL_ID, from));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_ID, from));

            entityManager.addComponent(id, new PositionComponent(LEVEL_ID, to));

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_ID, from).isEmpty(), "Old tile should be cleared");
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_ID, to), "New tile should contain entity");
        }

        @Test
        @DisplayName("Adding FootprintComponent expands entity presence across multiple tiles")
        void addingFootprintExpandsSpatialOccupancy() {
            EntityId boss = entityManager.createEntity();
            TileCoord origin = new TileCoord(5, 5);

            entityManager.addComponent(boss, new PositionComponent(LEVEL_ID, origin));
            entityManager.addComponent(boss, FootprintComponent.rectangle(2, 2));

            assertEquals(Set.of(boss), spatialIndex.getEntitiesAt(LEVEL_ID, new TileCoord(5, 5)));
            assertEquals(Set.of(boss), spatialIndex.getEntitiesAt(LEVEL_ID, new TileCoord(6, 5)));
            assertEquals(Set.of(boss), spatialIndex.getEntitiesAt(LEVEL_ID, new TileCoord(5, 6)));
            assertEquals(Set.of(boss), spatialIndex.getEntitiesAt(LEVEL_ID, new TileCoord(6, 6)));
        }

        @Test
        @DisplayName("Removing PositionComponent clears entity from SpatialIndex")
        void removingPositionClearsSpatialIndex() {
            EntityId id = entityManager.createEntity();
            TileCoord coord = new TileCoord(1, 1);

            entityManager.addComponent(id, new PositionComponent(LEVEL_ID, coord));
            assertFalse(spatialIndex.getEntitiesAt(LEVEL_ID, coord).isEmpty());

            entityManager.removeComponent(id, PositionComponent.class);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_ID, coord).isEmpty());
        }

        @Test
        @DisplayName("Destroying entity via EntityManager automatically evicts it from SpatialIndex")
        void destroyingEntityEvictsFromSpatialIndex() {
            EntityId id = entityManager.createEntity();
            TileCoord coord = new TileCoord(7, 8);

            entityManager.addComponent(id, new PositionComponent(LEVEL_ID, coord));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_ID, coord));

            entityManager.destroyEntity(id);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_ID, coord).isEmpty());
        }
    }
}