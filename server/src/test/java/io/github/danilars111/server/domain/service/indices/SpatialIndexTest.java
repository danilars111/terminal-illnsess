package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpatialIndexTest {

    private static final String LEVEL_1 = "dungeon_1";
    private static final String LEVEL_2 = "dungeon_2";

    private SpatialIndex spatialIndex;

    @BeforeEach
    void setUp() {
        spatialIndex = new SpatialIndex();
    }

    @Nested
    @DisplayName("Single-Tile Entity Operations")
    class SingleTileTests {

        @Test
        @DisplayName("Registers entity at default single tile when footprint is null")
        void registersSingleTileWhenFootprintNull() {
            EntityId entityId = new EntityId(1);
            PositionComponent pos = new PositionComponent(LEVEL_1, 5, 5);

            spatialIndex.updateEntity(entityId, pos, null);

            assertEquals(Set.of(entityId), spatialIndex.getEntitiesAt(LEVEL_1, 5, 5));
            assertEquals(Set.of(new TileCoord(5, 5)), spatialIndex.getFootprintOf(entityId));
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 5, 6).isEmpty());
        }

        @Test
        @DisplayName("Separates entities on different levels with same coordinates")
        void separatesEntitiesOnDifferentLevels() {
            EntityId idLevel1 = new EntityId(1);
            EntityId idLevel2 = new EntityId(2);

            spatialIndex.updateEntity(idLevel1, new PositionComponent(LEVEL_1, 5, 5), null);
            spatialIndex.updateEntity(idLevel2, new PositionComponent(LEVEL_2, 5, 5), null);

            assertEquals(Set.of(idLevel1), spatialIndex.getEntitiesAt(LEVEL_1, 5, 5));
            assertEquals(Set.of(idLevel2), spatialIndex.getEntitiesAt(LEVEL_2, 5, 5));
        }

        @Test
        @DisplayName("Multiple entities can share the same tile on the same level")
        void supportsMultipleEntitiesOnSameTile() {
            EntityId id1 = new EntityId(1);
            EntityId id2 = new EntityId(2);
            PositionComponent pos = new PositionComponent(LEVEL_1, 3, 4);

            spatialIndex.updateEntity(id1, pos, null);
            spatialIndex.updateEntity(id2, pos, null);

            Set<EntityId> occupants = spatialIndex.getEntitiesAt(LEVEL_1, 3, 4);
            assertEquals(2, occupants.size());
            assertTrue(occupants.containsAll(Set.of(id1, id2)));
        }

        @Test
        @DisplayName("Updating position cleans up old tile occupancy")
        void movingEntityCleansUpOldTile() {
            EntityId id = new EntityId(1);
            PositionComponent pos1 = new PositionComponent(LEVEL_1, 2, 2);
            PositionComponent pos2 = new PositionComponent(LEVEL_1, 3, 2);

            spatialIndex.updateEntity(id, pos1, null);
            spatialIndex.updateEntity(id, pos2, null);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 2, 2).isEmpty());
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 3, 2));
            assertEquals(Set.of(new TileCoord(3, 2)), spatialIndex.getFootprintOf(id));
        }

        @Test
        @DisplayName("Updating position across levels cleans up old level occupancy")
        void movingEntityAcrossLevelsCleansUpOldLevel() {
            EntityId id = new EntityId(1);
            PositionComponent pos1 = new PositionComponent(LEVEL_1, 2, 2);
            PositionComponent pos2 = new PositionComponent(LEVEL_2, 2, 2);

            spatialIndex.updateEntity(id, pos1, null);
            spatialIndex.updateEntity(id, pos2, null);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 2, 2).isEmpty());
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_2, 2, 2));
        }

        @Test
        @DisplayName("Removing entity clears both tile lookup and footprint mapping")
        void removingEntityCleansIndex() {
            EntityId id = new EntityId(1);
            PositionComponent pos = new PositionComponent(LEVEL_1, 0, 0);

            spatialIndex.updateEntity(id, pos, null);
            spatialIndex.remove(id);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 0, 0).isEmpty());
            assertTrue(spatialIndex.getFootprintOf(id).isEmpty());
        }
    }

    @Nested
    @DisplayName("Multi-Tile and Footprint Operations")
    class MultiTileTests {

        @Test
        @DisplayName("Registers 2x2 rectangular footprint across all 4 coordinates")
        void registersRectangleFootprint() {
            EntityId id = new EntityId(10);
            PositionComponent pos = new PositionComponent(LEVEL_1, 10, 10);
            FootprintComponent footprint = FootprintComponent.rectangle(2, 2);

            spatialIndex.updateEntity(id, pos, footprint);

            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 10, 10));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 11, 10));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 10, 11));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 11, 11));
            assertEquals(4, spatialIndex.getFootprintOf(id).size());
        }

        @Test
        @DisplayName("Registers irregular footprint like L-shape correctly")
        void registersIrregularFootprint() {
            EntityId id = new EntityId(20);
            PositionComponent pos = new PositionComponent(LEVEL_1, 0, 0);
            FootprintComponent lShape = FootprintComponent.lShape(3, 2);

            spatialIndex.updateEntity(id, pos, lShape);

            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 0, 0));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 1, 0));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 2, 0));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 0, 1));
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 1, 1).isEmpty());
        }

        @Test
        @DisplayName("Moving a multi-tile entity frees trailing cells")
        void movingLargeEntityCleansUpTrailingTiles() {
            EntityId id = new EntityId(30);
            FootprintComponent box = FootprintComponent.rectangle(2, 1);

            spatialIndex.updateEntity(id, new PositionComponent(LEVEL_1, 0, 0), box);
            spatialIndex.updateEntity(id, new PositionComponent(LEVEL_1, 1, 0), box);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 0, 0).isEmpty());
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 1, 0));
            assertEquals(Set.of(id), spatialIndex.getEntitiesAt(LEVEL_1, 2, 0));
        }

        @Test
        @DisplayName("Removing multi-tile entity clears all associated coordinates")
        void removingMultiTileCleansAllTiles() {
            EntityId id = new EntityId(40);
            FootprintComponent plus = FootprintComponent.plus(1);

            spatialIndex.updateEntity(id, new PositionComponent(LEVEL_1, 5, 5), plus);
            spatialIndex.remove(id);

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 5, 5).isEmpty());
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 5, 6).isEmpty());
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 5, 4).isEmpty());
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 4, 5).isEmpty());
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 6, 5).isEmpty());
            assertTrue(spatialIndex.getFootprintOf(id).isEmpty());
        }
    }

    @Nested
    @DisplayName("Radius Spatial Queries")
    class RadiusQueryTests {

        @Test
        @DisplayName("Finds entities within Euclidean radius and excludes outside points")
        void findsEntitiesWithinEuclideanRadius() {
            EntityId center = new EntityId(1);
            EntityId cardinalIn = new EntityId(2);
            EntityId diagonalOut = new EntityId(3);

            spatialIndex.updateEntity(center, new PositionComponent(LEVEL_1, 0, 0), null);
            spatialIndex.updateEntity(cardinalIn, new PositionComponent(LEVEL_1, 1, 0), null);
            spatialIndex.updateEntity(diagonalOut, new PositionComponent(LEVEL_1, 1, 1), null);

            List<EntityId> inRadius = spatialIndex.getEntitiesInRadius(LEVEL_1, 0, 0, 1);

            assertEquals(2, inRadius.size());
            assertTrue(inRadius.contains(center));
            assertTrue(inRadius.contains(cardinalIn));
            assertFalse(inRadius.contains(diagonalOut));
        }

        @Test
        @DisplayName("Does not return entities in radius on a different level")
        void excludesEntitiesOnDifferentLevelInRadius() {
            EntityId level1Entity = new EntityId(1);
            EntityId level2Entity = new EntityId(2);

            spatialIndex.updateEntity(level1Entity, new PositionComponent(LEVEL_1, 0, 0), null);
            spatialIndex.updateEntity(level2Entity, new PositionComponent(LEVEL_2, 0, 0), null);

            List<EntityId> resultsLevel1 = spatialIndex.getEntitiesInRadius(LEVEL_1, 0, 0, 1);
            assertEquals(1, resultsLevel1.size());
            assertEquals(level1Entity, resultsLevel1.getFirst());
        }

        @Test
        @DisplayName("Multi-tile entity covering multiple cells in radius is only returned once")
        void multiTileEntityIsNotDuplicatedInRadiusQuery() {
            EntityId largeBoss = new EntityId(99);
            FootprintComponent bossFootprint = FootprintComponent.centeredBox(1);

            spatialIndex.updateEntity(largeBoss, new PositionComponent(LEVEL_1, 0, 0), bossFootprint);

            List<EntityId> results = spatialIndex.getEntitiesInRadius(LEVEL_1, 0, 0, 2);

            assertEquals(1, results.size(), "Boss must only appear once in query results");
            assertEquals(largeBoss, results.getFirst());
        }

        @Test
        @DisplayName("Returns empty list when no entities exist in radius")
        void returnsEmptyWhenNoneFound() {
            spatialIndex.updateEntity(new EntityId(1), new PositionComponent(LEVEL_1, 10, 10), null);
            List<EntityId> results = spatialIndex.getEntitiesInRadius(LEVEL_1, 0, 0, 2);
            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Collection Protection and Clear")
    class ProtectionTests {

        @Test
        @DisplayName("Returned sets are unmodifiable")
        void returnedSetsAreImmutable() {
            EntityId id = new EntityId(1);
            spatialIndex.updateEntity(id, new PositionComponent(LEVEL_1, 0, 0), null);

            assertThrows(UnsupportedOperationException.class, () ->
                    spatialIndex.getEntitiesAt(LEVEL_1, 0, 0).add(new EntityId(2))
            );
            assertThrows(UnsupportedOperationException.class, () ->
                    spatialIndex.getFootprintOf(id).add(new TileCoord(1, 1))
            );
        }

        @Test
        @DisplayName("clear() empties all tile and entity mappings")
        void clearEmptiesEntireIndex() {
            spatialIndex.updateEntity(new EntityId(1), new PositionComponent(LEVEL_1, 0, 0), null);
            spatialIndex.updateEntity(new EntityId(2), new PositionComponent(LEVEL_1, 5, 5), FootprintComponent.rectangle(2, 2));

            spatialIndex.clear();

            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 0, 0).isEmpty());
            assertTrue(spatialIndex.getEntitiesAt(LEVEL_1, 5, 5).isEmpty());
            assertTrue(spatialIndex.getFootprintOf(new EntityId(1)).isEmpty());
            assertTrue(spatialIndex.getFootprintOf(new EntityId(2)).isEmpty());
        }
    }
}