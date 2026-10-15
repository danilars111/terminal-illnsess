package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {

    private GameState gameState;

    private record PositionComponent(int x, int y) implements Component {}
    private record HealthComponent(int current, int max) implements Component {}

    @BeforeAll
    static void enableLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE); // Enables System.Logger Level.DEBUG

        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.SEVERE);
            }
        }
    }

    @BeforeEach
    void setUp() {
        gameState = new GameState();
    }

    @Test
    @DisplayName("Creating an entity generates incrementing sequential EntityIds")
    void createEntityGeneratesSequentialIds() {
        EntityId id1 = gameState.createEntity();
        EntityId id2 = gameState.createEntity();
        EntityId id3 = gameState.createEntity();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);

        assertEquals(0L, id1.value());
        assertEquals(1L, id2.value());
        assertEquals(2L, id3.value());
    }

    @Test
    @DisplayName("Can attach, retrieve and remove components from an entity")
    void componentLifecycle() {
        EntityId entityId = gameState.createEntity();
        PositionComponent position = new PositionComponent(5, 10);

        gameState.addComponent(entityId, position);

        Optional<PositionComponent> retrievedPosition = gameState.getComponent(entityId, PositionComponent.class);
        assertTrue(retrievedPosition.isPresent());
        assertEquals(position, retrievedPosition.get());

        gameState.removeComponent(entityId, PositionComponent.class);
        Optional<PositionComponent> removedPosition = gameState.getComponent(entityId, PositionComponent.class);
        assertFalse(removedPosition.isPresent());
    }

    @Test
    @DisplayName("Can Query Entities possessing matching component types")
    void queryEntitiesWithMatchingComponents() {
        EntityId warrior = gameState.createEntity();
        EntityId ghost = gameState.createEntity();
        EntityId rock = gameState.createEntity();

        gameState.addComponent(warrior, new PositionComponent(1, 1));
        gameState.addComponent(warrior, new HealthComponent(100, 100));

        gameState.addComponent(ghost, new HealthComponent(50, 50));
        gameState.addComponent(rock, new PositionComponent(10, 10));

        Set<EntityId> withPosition = gameState.getEntitiesWith(PositionComponent.class);
        assertEquals(Set.of(warrior, rock), withPosition);

        Set<EntityId> withHealth = gameState.getEntitiesWith(HealthComponent.class);
        assertEquals(Set.of(warrior, ghost), withHealth);

        Set<EntityId> withPositionAndHealth = gameState.getEntitiesWith(PositionComponent.class, HealthComponent.class);
        assertEquals(Set.of(warrior), withPositionAndHealth);
    }

    @Test
    @DisplayName("Destroying an entity cleans up all attached components")
    void destroyEntityCleansUp() {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, new PositionComponent(1, 1));

        gameState.destroyEntity(entity);

        assertFalse(gameState.getComponent(entity, PositionComponent.class).isPresent());
        assertTrue(gameState.getEntitiesWith(PositionComponent.class).isEmpty());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when adding component to nonexisting entity")
    void rejectsAddingComponentToNonexistingEntity() {
        EntityId invalid = new EntityId(999);
        assertThrows(IllegalArgumentException.class, () ->
                gameState.addComponent(invalid, new PositionComponent(0, 0))
        );
    }

    @Test
    @DisplayName("Destroying an entity recycles its ID on subsequent creation")
    void recyclesDestroyedEntityIds() {
        EntityId first = gameState.createEntity();
        EntityId second = gameState.createEntity();

        assertEquals(0, first.value());
        assertEquals(1, second.value());

        gameState.destroyEntity(first);

        EntityId recycled = gameState.createEntity();
        assertEquals(0, recycled.value());

        EntityId nextNew = gameState.createEntity();
        assertEquals(2, nextNew.value());
    }

    @Test
    @DisplayName("getEntitiesWith reflects component additions, removals, and entity destruction")
    void getEntitiesWithReflectsLifecycle() {
        EntityId e1 = gameState.createEntity();
        EntityId e2 = gameState.createEntity();

        PositionComponent pos = new PositionComponent(5, 5);
        gameState.addComponent(e1, pos);
        gameState.addComponent(e2, pos);

        assertEquals(Set.of(e1, e2), gameState.getEntitiesWith(PositionComponent.class));

        gameState.removeComponent(e1, PositionComponent.class);
        assertEquals(Set.of(e2), gameState.getEntitiesWith(PositionComponent.class));

        gameState.destroyEntity(e2);
        assertTrue(gameState.getEntitiesWith(PositionComponent.class).isEmpty());
    }

    @Test
    @DisplayName("Recycled entity does not inherit previous entity components in index")
    void recycledEntityStartsCleanInIndex() {
        EntityId initial = gameState.createEntity();
        gameState.addComponent(initial, new PositionComponent(1, 1));
        assertEquals(Set.of(initial), gameState.getEntitiesWith(PositionComponent.class));

        gameState.destroyEntity(initial);
        assertTrue(gameState.getEntitiesWith(PositionComponent.class).isEmpty());

        EntityId recycled = gameState.createEntity();
        assertEquals(initial.value(), recycled.value());
        assertTrue(gameState.getEntitiesWith(PositionComponent.class).isEmpty());
    }
}
