package io.github.danilars111.server.domain.world;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.engine.GameEngine;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DataSizeUnit;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
public class GameWorldTest {
    private GameWorld world;
    private TileDefinition ground;
    private TileDefinition wall;
    private TileDefinition water;
    private GameEngine engine;
    private GameState gameState;
    private TileIndex tileIndex;

    @BeforeEach
    void setUp() {
        ground = new TileDefinition(
                "ground",
                "Ground",
                Set.of(TraversalMode.WALKING, TraversalMode.FLYING)
        );
        wall = new TileDefinition(
                "wall",
                "Wall"
        );
        water = new TileDefinition(
                "water",
                "Water",
                Set.of(TraversalMode.SWIMMING, TraversalMode.FLYING)
        );

        engine = new GameEngine();
        gameState = engine.getGameState();
        tileIndex = gameState.getTileIndex();
        tileIndex.register(ground);
        tileIndex.register(wall);
        tileIndex.register(water);

        world = new GameWorld(3, 3, ground.id(), tileIndex);
    }

    @Test
    @DisplayName("Adding a map grid larger than height * width throws IllegalArgumentException")
    void rejectsLargeMap() {
        String[] grid = new String[]{
                ground.id(), ground.id(), wall.id(),     // y = 0
                ground.id(), water.id(), wall.id(),     // y = 1
                ground.id(), ground.id(), ground.id()  // y = 2
        };
        assertThrows(IndexOutOfBoundsException.class, () -> new GameWorld(2, 2, grid, tileIndex));
    }

    @Test
    @DisplayName("Coordinates inside bounds return true, outside returns false")
    void checkBounds() {
        assertTrue(world.isWithinBounds(0, 0));
        assertTrue(world.isWithinBounds(2, 2));

        assertFalse(world.isWithinBounds(-1, 0));
        assertFalse(world.isWithinBounds(0, -1));
        assertFalse(world.isWithinBounds(3, 0));
        assertFalse(world.isWithinBounds(0, 3));
    }

    @Test
    @DisplayName("canPass checks tile traversability according to TraversalMode")
    void checksPassabilityByMode() {
        world.setTile(1, 1, water.id());
        world.setTile(2, 2, wall.id());

        // Ground at (0, 0)
        assertTrue(world.canPass(0, 0, Set.of(TraversalMode.WALKING)));
        assertFalse(world.canPass(0, 0, Set.of(TraversalMode.SWIMMING)));

        // Water at (1, 1)
        assertFalse(world.canPass(1, 1, Set.of(TraversalMode.WALKING)));
        assertTrue(world.canPass(1, 1, Set.of(TraversalMode.SWIMMING)));
        assertTrue(world.canPass(1, 1, Set.of(TraversalMode.FLYING)));

        // Wall at (2, 2)
        assertFalse(world.canPass(2, 2, Set.of(TraversalMode.WALKING)));
        assertFalse(world.canPass(2, 2, Set.of(TraversalMode.SWIMMING)));
        assertFalse(world.canPass(2, 2, Set.of(TraversalMode.FLYING)));
    }

    @Test
    @DisplayName("Out-of-bounds posiitons are always impassible")
    void outOfBoundsAreImpassible() {
        assertFalse(world.canPass(-1, 0, Set.of(TraversalMode.FLYING)));
        assertFalse(world.canPass(10, 10, Set.of(TraversalMode.FLYING)));
    }

    @Test
    @DisplayName("getTile and setTile correctly access world coordinates")
    void getSetTile() {
        TileDefinition swamp = new TileDefinition(
                "swamp",
                "Swamp",
                Set.of(TraversalMode.WALKING),
                Set.of("slow_movement")
        );

        tileIndex.register(swamp);

        world.setTile(0, 1, swamp.id());
        assertEquals(swamp, world.getTile(0, 1));
        assertTrue(world.getTile(0,1).hasEffect("slow_movement"));
    }

    @Test
    @DisplayName("Accessing out of bounds tiles throws IndexOutOfBoundsException")
    void outOfBoundsThrowsException() {
        assertThrows(IndexOutOfBoundsException.class, () -> world.getTile(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> world.setTile(5, 5, ground.id()));
    }
}
