package io.github.danilars111.server.domain.service.movement;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.EntityManager;
import io.github.danilars111.server.domain.ecs.GameState;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MovementRuleServiceTest {

    private static final String LEVEL_ID = "test_level";
    private static final int WORLD_SIZE = 20;

    private GameState state;
    private EntityId mover;
    private World world;
    private LevelGrid levelGrid;

    private short grassId;
    private short wallId;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING),
            Set.of()
    );

    private final TileDefinition wall = new TileDefinition(
            "wall",
            "Wall",
            Set.of(),
            Set.of()
    );

    @BeforeEach
    void setUp() {
        DefinitionRepository<TileDefinition> tileRepo = new DefinitionRepository<>();
        tileRepo.replaceAll(Map.of(
                grass.id(), grass,
                wall.id(), wall
        ));

        TileIndex tileIndex = new TileIndex();
        grassId = tileIndex.register(grass.id());
        wallId = tileIndex.register(wall.id());

        TilePalette tilePalette = new TilePalette();
        tilePalette.rebuild(tileIndex, tileRepo);

        world = new World(tilePalette, tileIndex);

        short[] initialTiles = new short[WORLD_SIZE * WORLD_SIZE];
        for (int i = 0; i < initialTiles.length; i++) {
            initialTiles[i] = grassId;
        }
        levelGrid = new LevelGrid(LEVEL_ID, WORLD_SIZE, WORLD_SIZE, initialTiles);
        world.registerLevel(levelGrid);

        state = new GameState(new EntityManager(), new SpatialIndex(), world);

        mover = state.entityManager().createEntity();
        state.entityManager().addComponent(mover, new PositionComponent(LEVEL_ID, 5, 5));
        state.entityManager().addComponent(mover, TraversalComponent.of(TraversalMode.WALKING));
        state.entityManager().addComponent(mover, FootprintComponent.singleTile());
    }

    private void setTileImpassable(TileCoord tile) {
        levelGrid.setTileId(tile, wallId);
    }

    private void setTilePassable(TileCoord tile) {
        levelGrid.setTileId(tile, grassId);
    }

    @Nested
    @DisplayName("Step bound rules")
    class StepBoundRules {

        @Test
        @DisplayName("Allows valid orthogonal single-tile moves")
        void allowValidOrthogonalSingleTileMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(6, 5)), "Right step");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(4, 5)), "Left step");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(5, 6)), "Down step");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(5, 4)), "Up step");
        }

        @Test
        @DisplayName("Allows valid diagonal single-tile moves")
        void allowValidDiagonalSingleTileMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(6, 6)), "Diagonal step Up-Right");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(4, 6)), "Diagonal step Up-Left");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(4, 4)), "Diagonal step Down-Left");
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(6, 4)), "Diagonal step Down-Right");
        }

        @Test
        @DisplayName("Rejects zero-delta moves (stepping onto same tile)")
        void rejectsZeroDeltaMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(5, 5)));
        }

        @Test
        @DisplayName("Rejects steps larger than 1 tile in any direction")
        void rejectsTeleportOrMultiTileSteps() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(7, 5)), "Right step");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(3, 5)), "Left step");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(5, 7)), "Down step");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(5, 3)), "Up step");

            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(7, 7)), "Diagonal step Up-Right");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(3, 7)), "Diagonal step Up-Left");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(3, 3)), "Diagonal step Down-Left");
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(7, 3)), "Diagonal step Down-Right");
        }
    }

    @Nested
    @DisplayName("Terrain and Collider Blocking")
    class ObstacleBlocking {

        @Test
        @DisplayName("Rejects movement onto impassable static terrain")
        void rejectsStepOntoImpassableTerrain() {
            TileCoord impassableTile = new TileCoord(6, 5);
            setTileImpassable(impassableTile);
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, new TileCoord(5, 5), impassableTile));
        }

        @Test
        @DisplayName("Rejects movement occupied by another entity with a ColliderComponent")
        void rejectsStepOntoDynamicCollider() {
            EntityId rock = state.entityManager().createEntity();
            state.entityManager().addComponent(rock, new PositionComponent(LEVEL_ID, 6, 5));
            state.entityManager().addComponent(rock, new ColliderComponent());

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, new TileCoord(5, 5), new TileCoord(6, 5)));
        }

        @Test
        @DisplayName("Allows movement onto an entity without a ColliderComponent")
        void allowsMovementOntoDynamicNonCollider() {
            EntityId itemDrop = state.entityManager().createEntity();
            state.entityManager().addComponent(itemDrop, new PositionComponent(LEVEL_ID, 6, 5));

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, new TileCoord(5, 5), new TileCoord(6, 5)));
        }
    }

    @Test
    @DisplayName("Ignores self when checking colliders (entity does not collide with its own body)")
    void entityDoesNotCollideWithSelf() {
        state.entityManager().addComponent(mover, FootprintComponent.rectangle(3, 3));
        state.entityManager().addComponent(mover, new ColliderComponent());
        MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

        assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, new TileCoord(5, 5), new TileCoord(6, 5)));
    }

    @Nested
    @DisplayName("Diagonal Traversal Modes")
    class DiagonalModes {
        private final TileCoord from = new TileCoord(5, 5);
        private final TileCoord to = new TileCoord(6, 6);
        private final TileCoord flankA = new TileCoord(6, 5);
        private final TileCoord flankB = new TileCoord(5, 6);

        @Test
        @DisplayName("DiagonalMode.NONE rejects all diagonal moves, even if open")
        void diagonalModeNoneRejectsAllDiagonals() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, new TileCoord(6, 5)), "Cardinals still allowed");
        }

        @Test
        @DisplayName("DiagonalMode.FULL allows diagonal move even if both flanks are impassable")
        void diagonalModeFullAllowsThroughPinchedCorners() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.FULL);

            setTileImpassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(flankB);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTilePassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));
        }

        @Test
        @DisplayName("DiagonalMode.STRICT rejects diagonal if either flank is blocked")
        void diagonalModeStrictEnforcesBothFlanksOpen() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.STRICT);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(flankA);
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(flankB);
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTilePassable(flankA);
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));
        }

        @Test
        @DisplayName("DiagonalMode.PERMISSIVE allows single-flank cut, but rejects double-flank pinch")
        void diagonalModePermissiveRejectsOnlyPinchedCorners() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(flankB);
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTilePassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));
        }
    }

    @Nested
    @DisplayName("NOCLIP Traversal")
    class Noclip {
        @Test
        @DisplayName("NOCLIP ignores terrain and colliders during canTraverseStep")
        void noclipBypassesAllObstacles() {
            state.entityManager().addComponent(mover, TraversalComponent.of(TraversalMode.NOCLIP));

            TileCoord target = new TileCoord(6, 6);
            setTileImpassable(target);
            setTileImpassable(new TileCoord(6, 5));
            setTileImpassable(new TileCoord(5, 6));

            EntityId rock = state.entityManager().createEntity();
            state.entityManager().addComponent(rock, new PositionComponent(LEVEL_ID, target));
            state.entityManager().addComponent(rock, new ColliderComponent());

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, new TileCoord(5, 5), target));
        }

        @Test
        @DisplayName("isFootprintBlocked returns false for NOCLIP entities on blocked tiles")
        void isFootprintBlockedReturnsFalseForNoClip() {
            state.entityManager().addComponent(mover, TraversalComponent.of(TraversalMode.NOCLIP));
            TileCoord target = new TileCoord(6, 6);
            setTileImpassable(target);

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

            assertFalse(service.isFootprintBlocked(state, mover, LEVEL_ID, target));
        }
    }

    @Nested
    @DisplayName("Multi-Tile Footprint & Placement")
    class MultiTileFootprints {
        @Test
        @DisplayName("isFootprintBlocked returns true if any tile in the footprint is impassable")
        void detectsImpassableSubTileInFootprint() {
            state.entityManager().addComponent(mover, FootprintComponent.rectangle(2, 2));
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

            assertFalse(service.isFootprintBlocked(state, mover, LEVEL_ID, new TileCoord(5, 5)), "Clear footprint");

            setTileImpassable(new TileCoord(6, 6));
            assertTrue(service.isFootprintBlocked(state, mover, LEVEL_ID, new TileCoord(5, 5)), "Blocked sub-tile");
        }

        @Test
        @DisplayName("canTraverseStep evaluates passability across the entire footprint at destination")
        void verifiesWholeFootprintAtDestination() {
            state.entityManager().addComponent(mover, FootprintComponent.rectangle(2, 2));
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

            TileCoord from = new TileCoord(5, 5);
            TileCoord to = new TileCoord(6, 5);

            assertTrue(service.canTraverseStep(state, mover, LEVEL_ID, from, to));

            setTileImpassable(new TileCoord(7, 6));
            assertFalse(service.canTraverseStep(state, mover, LEVEL_ID, from, to));
        }
    }
}