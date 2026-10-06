package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.ecs.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {

    private GameState gameState;

    private record PositionComponent(int x, int y) implements Component {}
    private record HealthComponent(int current, int max) implements Component {}

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
        EntityId invalid = new EntityId(999L);
        assertThrows(IllegalArgumentException.class, () ->
                gameState.addComponent(invalid, new PositionComponent(0, 0))
        );
    }
}
