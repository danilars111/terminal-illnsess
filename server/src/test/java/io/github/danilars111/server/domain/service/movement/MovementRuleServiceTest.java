package io.github.danilars111.server.domain.service.movement;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.ColliderComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MovementRuleServiceTest {

    private static final int WORLD_SIZE = 20;

    private GameState state;
    private EntityId mover;
    private GameWorld world;

    private final TileDefinition grass = new TileDefinition(
            "grass",
            "Grass",
            Set.of(TraversalMode.WALKING, TraversalMode.FLYING)
    );

    private final TileDefinition wall = new TileDefinition(
            "wall",
            "Wall",
            Set.of() // Passable by nothing (except NOCLIP via engine rules)
    );

    @BeforeEach
    void setUp() {
        state = new GameState();
        mover = state.createEntity();

        TileIndex tileIndex = state.getTileIndex();
        tileIndex.register(grass);
        tileIndex.register(wall);

        state.addComponent(mover, new PositionComponent(5, 5));
        state.addComponent(mover, TraversalComponent.of(TraversalMode.WALKING));
        state.addComponent(mover, FootprintComponent.singleTile());

        world = new GameWorld(WORLD_SIZE, WORLD_SIZE, tileIndex.getId(grass.id()), tileIndex);
    }

    // Helper to mark a tile impassible in the real GameWorld
    private void setTileImpassable(TileCoord tile) {
        world.setTile(tile, wall.id());
    }

    private void setTilePassable(TileCoord tile) {
        world.setTile(tile, grass.id());
    }

    @Nested
    @DisplayName("Step bound rules")
    class StepBoundRules {

        @Test
        @DisplayName("Allows valid orthogonal single-tile moves")
        void allowValidOrthogonalSingleTileMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(6, 5)), "Right step");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(4, 5)), "Left step");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(5, 6)), "Down step");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(5, 4)), "Up step");

        }

        @Test
        @DisplayName("Allows valid diagonal single-tile moves")
        void allowValidDiagonalSingleTileMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(6, 6)), "Diagonal step Up-Right");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(4, 6)), "Diagonal step Up-Left");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(4, 4)), "Diagonal step Down-Left");
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(6, 4)), "Diagonal step Down-Right");
        }

        @Test
        @DisplayName("Rejects zero-delta moves (stepping onto same tile")
        void rejectsZeroDeltaMoves() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(5, 5)));
        }

        @Test
        @DisplayName("Rejects steps larger than 1 tile in any direction")
        void rejectsTeleportOrMultiTileSteps() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            TileCoord from = new TileCoord(5, 5);

            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(7, 5)), "Right step");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(3, 5)), "Left step");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(5, 7)), "Down step");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(5, 3)), "Up step");

            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(7, 7)), "Diagonal step Up-Right");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(3, 7)), "Diagonal step Up-Left");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(3, 3)), "Diagonal step Down-Left");
            assertFalse(service.canTraverseStep(state, mover, from, new TileCoord(7, 3)), "Diagonal step Down-Right");
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

            assertFalse(service.canTraverseStep(state, mover, new TileCoord(5, 5), impassableTile));
        }

        @Test
        @DisplayName("Rejects movement occupied by another entity with a ColliderComponent")
        void rejectsStepOntoDynamicCollider() {
            EntityId rock = state.createEntity();
            state.addComponent(rock, new PositionComponent(6, 5));
            state.addComponent(rock, new ColliderComponent());

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            assertFalse(service.canTraverseStep(state, mover, new TileCoord(5, 5), new TileCoord(6, 5)));
        }

        @Test
        @DisplayName("Allows movement onto an entity without a ColliderComponent")
        void allowsMovementOntoDynamicNonCollider() {
            EntityId itemDrop = state.createEntity();
            state.addComponent(itemDrop, new PositionComponent(6, 5));

            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
            assertTrue(service.canTraverseStep(state, mover, new TileCoord(5, 5), new TileCoord(6, 5)));
        }
    }

    @Test
    @DisplayName("Ignores self when checking colliders (entity does not collide with its own body)")
    void entityDoesNotCollideWithSelf() {
        state.addComponent(mover, FootprintComponent.rectangle(3, 3));
        state.addComponent(mover, new ColliderComponent());
        MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

        // Shifting footprint anchor across coordinates mover partially overlaps
        assertTrue(service.canTraverseStep(state, mover, new TileCoord(5, 5), new TileCoord(6, 5)));
    }
    @Nested
    @DisplayName("Diagonal Traversal Modes")
    class DiagonalModes {
        private final TileCoord from = new TileCoord(5, 5);
        private final TileCoord to = new TileCoord(6, 6);
        private final TileCoord flankA = new TileCoord(6, 5); // x + dx, y
        private final TileCoord flankB = new TileCoord(5, 6); // x, y + dy

        @Test
        @DisplayName("DiagonalMode.NONE rejects all diagonal moves, even if open")
        void diagonalModeNoneRejectsAllDiagonals() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

            assertFalse(service.canTraverseStep(state, mover, from, to));
            assertTrue(service.canTraverseStep(state, mover, from, new TileCoord(6, 5)), "Cardinals still allowed");
        }

        @Test
        @DisplayName("DiagonalMode.FULL allows diagonal move even if both flanks are impassable")
        void diagonalModeFullAllowsThroughPinchedCorners() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.FULL);

            setTileImpassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, from, to));

            setTileImpassable(flankB);
            assertTrue(service.canTraverseStep(state, mover, from, to));

            setTilePassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, from, to));
        }

        @Test
        @DisplayName("DiagonalMode.STRICT rejects diagonal if either flank is blocked")
        void diagonalModeStrictEnforcesBothFlanksOpen() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.STRICT);

            // Both flanks open
            assertTrue(service.canTraverseStep(state, mover, from, to));

            // Flank A blocked -> reject
            setTileImpassable(flankA);
            assertFalse(service.canTraverseStep(state, mover, from, to));

            // Both flanks blocked
            setTileImpassable(flankB);
            assertFalse(service.canTraverseStep(state, mover, from, to));

            // Flank A open, Flank B blocked -> reject
            setTilePassable(flankA);
            assertFalse(service.canTraverseStep(state, mover, from, to));
        }

        @Test
        @DisplayName("DiagonalMode.PERMISSIVE allows single-flank cut, but rejects double-flank pinch")
        void diagonalModePermissiveRejectsOnlyPinchedCorners() {
            MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

            // Both open
            assertTrue(service.canTraverseStep(state, mover, from, to));

            // Cutting corner with one flank blocked is permitted
            setTileImpassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, from, to));

            // Squeezing between two blocked flanks is rejected (pinched corner)
            setTileImpassable(flankB);
            assertFalse(service.canTraverseStep(state, mover, from, to));

            // Cutting corner with other flank blocked is permitted
            setTilePassable(flankA);
            assertTrue(service.canTraverseStep(state, mover, from, to));
        }

        @Nested
        @DisplayName("NOCLIP Traversal")
        class Noclip {
            @Test
            @DisplayName("NOCLIP ignores terrain and colliders during canTraverseStep")
            void noclipBypassesAllObstacles() {
                state.addComponent(mover, TraversalComponent.of(TraversalMode.NOCLIP));

                // Block destination with both terrain and solid collider
                TileCoord target = new TileCoord(6, 6);
                setTileImpassable(target);
                setTileImpassable(new TileCoord(6, 5));
                setTileImpassable(new TileCoord(5, 6));

                EntityId rock = state.createEntity();
                state.addComponent(rock, new PositionComponent(target));
                state.addComponent(rock, new ColliderComponent());

                MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

                assertTrue(service.canTraverseStep(state, mover, new TileCoord(5, 5), target));
            }

            @Test
            @DisplayName("isFootprintBlocked returns false for NOCLIP entities on blocked tiles")
            void isFootprintBlockedReturnsFalseForNoClip() {
                state.addComponent(mover, TraversalComponent.of(TraversalMode.NOCLIP));
                TileCoord target = new TileCoord(6, 6);
                setTileImpassable(target);

                MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.NONE);

                assertFalse(service.isFootprintBlocked(state, mover, target));
            }
        }
        @Nested
        @DisplayName("Multi-Tile Footprint & Placement")
        class MultiTileFootprints {
            @Test
            @DisplayName("isFootprintBlocked returns true if any tile in the footprint is impassable")
            void detectsImpassableSubTileInFootprint() {
                // 2x2 footprint anchored at (5, 5) covers (5,5), (6,5), (5,6), (6,6)
                state.addComponent(mover, FootprintComponent.rectangle(2, 2));
                MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

                assertFalse(service.isFootprintBlocked(state, mover, new TileCoord(5, 5)), "Clear footprint");

                // Block far corner of footprint (6, 6)
                setTileImpassable(new TileCoord(6, 6));
                assertTrue(service.isFootprintBlocked(state, mover, new TileCoord(5, 5)), "Blocked sub-tile");
            }
            @Test
            @DisplayName("canTraverseStep evaluates passability across the entire footprint at destination")
            void verifiesWholeFootprintAtDestination() {
                state.addComponent(mover, FootprintComponent.rectangle(2, 2));
                MovementRuleService service = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);

                TileCoord from = new TileCoord(5, 5);
                TileCoord to = new TileCoord(6, 5); // Destination covers (6,5), (7,5), (6,6), (7,6)

                assertTrue(service.canTraverseStep(state, mover, from, to));

                // Obstruct (7, 6) which touches only the edge of the new destination
                setTileImpassable(new TileCoord(7, 6));
                assertFalse(service.canTraverseStep(state, mover, from, to));
            }
        }

    }
}
