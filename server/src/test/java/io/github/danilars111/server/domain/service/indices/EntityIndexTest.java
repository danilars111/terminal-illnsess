package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityIndexTest {

    private EntityIndex index;

    private record PositionComp(int x, int y) implements Component {}
    private record VelocityComp(int dx, int dy) implements Component {}
    private record RenderComp(char glyph) implements Component {}
    private record HealthComp(int current, int max) implements Component {}

    @BeforeEach
    void setUp() {
        index = new EntityIndex();
    }

    @Nested
    @DisplayName("Registration Guardrails and Lifecycle")
    class RegistrationTests {

        @Test
        @DisplayName("Successfully registers a new entity")
        void registersNewEntity() {
            EntityId id = new EntityId(0);
            assertDoesNotThrow(() -> index.registerEntity(id));
        }

        @Test
        @DisplayName("Throws IllegalStateException when attempting to register an already active entity")
        void rejectsDuplicateEntityRegistration() {
            EntityId id = new EntityId(1);
            index.registerEntity(id);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> index.registerEntity(id)
            );
            assertTrue(ex.getMessage().contains("already registered"));
        }

        @Test
        @DisplayName("Throws NullPointerException when registering null entity ID")
        void rejectsNullEntityRegistration() {
            assertThrows(NullPointerException.class, () -> index.registerEntity(null));
        }

        @Test
        @DisplayName("Allows registering a recycled entity ID after it has been removed")
        void allowsRegisteringRecycledEntityAfterRemoval() {
            EntityId id = new EntityId(1);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);

            index.removeEntity(id);

            assertDoesNotThrow(() -> index.registerEntity(id));
            assertTrue(index.getEntitiesWith(PositionComp.class).isEmpty());
        }

        @Test
        @DisplayName("removeEntity clears entity across all component bitsets")
        void removeEntityClearsAllComponents() {
            EntityId id = new EntityId(5);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);
            index.addComponent(id, RenderComp.class);

            index.removeEntity(id);

            assertTrue(index.getEntitiesWith(PositionComp.class).isEmpty());
            assertTrue(index.getEntitiesWith(RenderComp.class).isEmpty());
        }

        @Test
        @DisplayName("removeEntity ignores unknown or unindexed entity without throwing")
        void removeUnknownEntityIsNoOp() {
            assertDoesNotThrow(() -> index.removeEntity(new EntityId(999)));
        }

        @Test
        @DisplayName("clear empties index completely")
        void clearEmptiesIndex() {
            EntityId e1 = new EntityId(0);
            EntityId e2 = new EntityId(1);
            index.registerEntity(e1);
            index.registerEntity(e2);
            index.addComponent(e1, PositionComp.class);
            index.addComponent(e2, VelocityComp.class);

            index.clear();

            assertTrue(index.getEntitiesWith(PositionComp.class).isEmpty());
            assertTrue(index.getEntitiesWith(VelocityComp.class).isEmpty());
            assertDoesNotThrow(() -> index.registerEntity(e1));
        }
    }

    @Nested
    @DisplayName("Component Attachment and Removal")
    class ComponentMutationTests {

        @Test
        @DisplayName("Adding component marks entity in queries")
        void addComponentMarksEntity() {
            EntityId id = new EntityId(2);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);

            assertEquals(Set.of(id), index.getEntitiesWith(PositionComp.class));
        }

        @Test
        @DisplayName("Adding the same component type multiple times is idempotent")
        void addSameComponentIsIdempotent() {
            EntityId id = new EntityId(2);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);
            index.addComponent(id, PositionComp.class);

            assertEquals(Set.of(id), index.getEntitiesWith(PositionComp.class));
        }

        @Test
        @DisplayName("Removing component detaches entity from query while preserving other entities")
        void removeComponentDetachesEntity() {
            EntityId e1 = new EntityId(1);
            EntityId e2 = new EntityId(2);
            index.registerEntity(e1);
            index.registerEntity(e2);

            index.addComponent(e1, PositionComp.class);
            index.addComponent(e2, PositionComp.class);

            index.removeComponent(e1, PositionComp.class);

            assertEquals(Set.of(e2), index.getEntitiesWith(PositionComp.class));
        }

        @Test
        @DisplayName("Removing an unassigned component type from an entity is a safe no-op")
        void removeUnassignedComponentIsNoOp() {
            EntityId id = new EntityId(1);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);

            assertDoesNotThrow(() -> index.removeComponent(id, VelocityComp.class));
            assertEquals(Set.of(id), index.getEntitiesWith(PositionComp.class));
        }

        @Test
        @DisplayName("Rejects null arguments when mutating components")
        void rejectsNullComponentArguments() {
            EntityId id = new EntityId(1);
            assertThrows(NullPointerException.class, () -> index.addComponent(null, PositionComp.class));
            assertThrows(NullPointerException.class, () -> index.addComponent(id, null));
            assertThrows(NullPointerException.class, () -> index.removeComponent(null, PositionComp.class));
            assertThrows(NullPointerException.class, () -> index.removeComponent(id, null));
        }
    }

    @Nested
    @DisplayName("Component Queries and Intersections")
    class QueryTests {

        @Test
        @DisplayName("Returns empty set when querying a component class that was never registered")
        void returnsEmptyWhenComponentNeverSeen() {
            assertTrue(index.getEntitiesWith(HealthComp.class).isEmpty());
        }

        @Test
        @DisplayName("Returns empty set when no component types are passed to query")
        void returnsEmptyWhenNoTypesQueried() {
            EntityId id = new EntityId(0);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);

            assertTrue(index.getEntitiesWith().isEmpty());
        }

        @Test
        @DisplayName("Matches entities across multiple component intersections")
        void matchesEntitiesHoldingAllComponents() {
            EntityId moverOnly = new EntityId(1);
            EntityId renderOnly = new EntityId(2);
            EntityId fullUnit = new EntityId(3);

            index.registerEntity(moverOnly);
            index.registerEntity(renderOnly);
            index.registerEntity(fullUnit);

            index.addComponent(moverOnly, PositionComp.class);
            index.addComponent(moverOnly, VelocityComp.class);

            index.addComponent(renderOnly, RenderComp.class);

            index.addComponent(fullUnit, PositionComp.class);
            index.addComponent(fullUnit, VelocityComp.class);
            index.addComponent(fullUnit, RenderComp.class);

            assertEquals(Set.of(moverOnly, fullUnit), index.getEntitiesWith(PositionComp.class, VelocityComp.class));
            assertEquals(Set.of(fullUnit), index.getEntitiesWith(PositionComp.class, VelocityComp.class, RenderComp.class));
            assertEquals(Set.of(renderOnly, fullUnit), index.getEntitiesWith(RenderComp.class));
        }

        @Test
        @DisplayName("Returns empty set when intersection has zero matching entities")
        void returnsEmptyWhenNoSharedMatches() {
            EntityId e1 = new EntityId(1);
            EntityId e2 = new EntityId(2);
            index.registerEntity(e1);
            index.registerEntity(e2);

            index.addComponent(e1, PositionComp.class);
            index.addComponent(e2, VelocityComp.class);

            assertTrue(index.getEntitiesWith(PositionComp.class, VelocityComp.class).isEmpty());
        }

        @Test
        @DisplayName("Returns empty set when one queried component has zero active entities")
        void returnsEmptyWhenOneComponentEmpty() {
            EntityId e1 = new EntityId(1);
            index.registerEntity(e1);
            index.addComponent(e1, PositionComp.class);

            assertTrue(index.getEntitiesWith(PositionComp.class, RenderComp.class).isEmpty());
        }

        @Test
        @DisplayName("Returned set is immutable")
        void returnedSetIsImmutable() {
            EntityId id = new EntityId(1);
            index.registerEntity(id);
            index.addComponent(id, PositionComp.class);

            Set<EntityId> result = index.getEntitiesWith(PositionComp.class);
            assertThrows(UnsupportedOperationException.class, () -> result.add(new EntityId(2)));
        }
    }
}