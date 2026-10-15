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

public class EntityManagerTest {

    private EntityManager entityManager;

    private record PositionComponent(int x, int y) implements Component {}
    private record HealthComponent(int current, int max) implements Component {}

    @BeforeAll
    static void enableLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.SEVERE);

        for (var handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                handler.setLevel(Level.SEVERE);
            }
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = new EntityManager();
    }

    @Test
    @DisplayName("Creating an entity generates incrementing sequential EntityIds")
    void createEntityGeneratesSequentialIds() {
        EntityId id1 = entityManager.createEntity();
        EntityId id2 = entityManager.createEntity();
        EntityId id3 = entityManager.createEntity();

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
        EntityId entityId = entityManager.createEntity();
        PositionComponent position = new PositionComponent(5, 10);

        entityManager.addComponent(entityId, position);

        Optional<PositionComponent> retrievedPosition = entityManager.getComponent(entityId, PositionComponent.class);
        assertTrue(retrievedPosition.isPresent());
        assertEquals(position, retrievedPosition.get());

        entityManager.removeComponent(entityId, PositionComponent.class);
        Optional<PositionComponent> removedPosition = entityManager.getComponent(entityId, PositionComponent.class);
        assertFalse(removedPosition.isPresent());
    }

    @Test
    @DisplayName("Can query entities possessing matching component types")
    void queryEntitiesWithMatchingComponents() {
        EntityId warrior = entityManager.createEntity();
        EntityId ghost = entityManager.createEntity();
        EntityId rock = entityManager.createEntity();

        entityManager.addComponent(warrior, new PositionComponent(1, 1));
        entityManager.addComponent(warrior, new HealthComponent(100, 100));

        entityManager.addComponent(ghost, new HealthComponent(50, 50));
        entityManager.addComponent(rock, new PositionComponent(10, 10));

        Set<EntityId> withPosition = entityManager.getEntitiesWith(PositionComponent.class);
        assertEquals(Set.of(warrior, rock), withPosition);

        Set<EntityId> withHealth = entityManager.getEntitiesWith(HealthComponent.class);
        assertEquals(Set.of(warrior, ghost), withHealth);

        Set<EntityId> withPositionAndHealth = entityManager.getEntitiesWith(PositionComponent.class, HealthComponent.class);
        assertEquals(Set.of(warrior), withPositionAndHealth);
    }

    @Test
    @DisplayName("Destroying an entity cleans up all attached components")
    void destroyEntityCleansUp() {
        EntityId entity = entityManager.createEntity();
        entityManager.addComponent(entity, new PositionComponent(1, 1));

        entityManager.destroyEntity(entity);

        assertFalse(entityManager.getComponent(entity, PositionComponent.class).isPresent());
        assertTrue(entityManager.getEntitiesWith(PositionComponent.class).isEmpty());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when adding component to nonexisting entity")
    void rejectsAddingComponentToNonexistingEntity() {
        EntityId invalid = new EntityId(999);
        assertThrows(IllegalArgumentException.class, () ->
                entityManager.addComponent(invalid, new PositionComponent(0, 0))
        );
    }

    @Test
    @DisplayName("Destroying an entity recycles its ID on subsequent creation")
    void recyclesDestroyedEntityIds() {
        EntityId first = entityManager.createEntity();
        EntityId second = entityManager.createEntity();

        assertEquals(0, first.value());
        assertEquals(1, second.value());

        entityManager.destroyEntity(first);

        EntityId recycled = entityManager.createEntity();
        assertEquals(0, recycled.value());

        EntityId nextNew = entityManager.createEntity();
        assertEquals(2, nextNew.value());
    }

    @Test
    @DisplayName("getEntitiesWith reflects component additions, removals, and entity destruction")
    void getEntitiesWithReflectsLifecycle() {
        EntityId e1 = entityManager.createEntity();
        EntityId e2 = entityManager.createEntity();

        PositionComponent pos = new PositionComponent(5, 5);
        entityManager.addComponent(e1, pos);
        entityManager.addComponent(e2, pos);

        assertEquals(Set.of(e1, e2), entityManager.getEntitiesWith(PositionComponent.class));

        entityManager.removeComponent(e1, PositionComponent.class);
        assertEquals(Set.of(e2), entityManager.getEntitiesWith(PositionComponent.class));

        entityManager.destroyEntity(e2);
        assertTrue(entityManager.getEntitiesWith(PositionComponent.class).isEmpty());
    }

    @Test
    @DisplayName("Recycled entity does not inherit previous entity components in index")
    void recycledEntityStartsCleanInIndex() {
        EntityId initial = entityManager.createEntity();
        entityManager.addComponent(initial, new PositionComponent(1, 1));
        assertEquals(Set.of(initial), entityManager.getEntitiesWith(PositionComponent.class));

        entityManager.destroyEntity(initial);
        assertTrue(entityManager.getEntitiesWith(PositionComponent.class).isEmpty());

        EntityId recycled = entityManager.createEntity();
        assertEquals(initial.value(), recycled.value());
        assertTrue(entityManager.getEntitiesWith(PositionComponent.class).isEmpty());
    }
}