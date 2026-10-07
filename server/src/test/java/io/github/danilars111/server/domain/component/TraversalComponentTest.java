package io.github.danilars111.server.domain.component;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TraversalComponentTest {

    @Test
    @DisplayName("Defaults to empty when initialized with null")
    public void defaultsToEmptyWhenInitializedWithNull() {
        TraversalComponent component = new TraversalComponent(null);
        assertTrue(component.activeModes().isEmpty());
    }

    @Test
    @DisplayName("Factory creates component with only correct mode")
    public void factoryCreatesComponentWithOnlyCorrectMode() {
        TraversalComponent walking = TraversalComponent.of(TraversalMode.WALKING);
        TraversalComponent flying = TraversalComponent.of(TraversalMode.FLYING);
        TraversalComponent swimming = TraversalComponent.of(TraversalMode.SWIMMING);

        assertTrue(walking.activeModes().contains(TraversalMode.WALKING));
        assertTrue(flying.activeModes().contains(TraversalMode.FLYING));
        assertTrue(swimming.activeModes().contains(TraversalMode.SWIMMING));
    }

    @Test
    @DisplayName("Defensively copies input set to prevent external mutation")
    void enforceImmutability() {
        Set<TraversalMode> modes = new HashSet<>();
        modes.add(TraversalMode.WALKING);

        TraversalComponent component = new TraversalComponent(modes);
        modes.add(TraversalMode.FLYING);

        assertTrue(component.activeModes().contains(TraversalMode.WALKING));
        assertFalse(component.activeModes().contains(TraversalMode.FLYING));
        assertThrows(UnsupportedOperationException.class, () ->
                component.activeModes().add(TraversalMode.SWIMMING));
    }

    @Test
    @DisplayName("withAdded returns a new instance containing the added mode without modifying original")
    void addsModeImmutably() {
        TraversalComponent component = new TraversalComponent(Set.of(TraversalMode.WALKING));
        TraversalComponent updated = component.withAdded(TraversalMode.FLYING);

        assertTrue(updated.activeModes().contains(TraversalMode.WALKING));
        assertTrue(updated.activeModes().contains(TraversalMode.FLYING));
        assertFalse(component.activeModes().contains(TraversalMode.FLYING));
    }

    @Test
    @DisplayName("withRemoved returns a new instance containing the removed mode without modifying original")
    void removesModeImmutably() {
        TraversalComponent component = new TraversalComponent(Set.of(TraversalMode.WALKING, TraversalMode.FLYING));
        TraversalComponent updated = component.withRemoved(TraversalMode.FLYING);

        assertTrue(updated.activeModes().contains(TraversalMode.WALKING));
        assertFalse(updated.activeModes().contains(TraversalMode.FLYING));
        assertTrue(component.activeModes().contains(TraversalMode.FLYING));
    }
}
