package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record TraversalComponent(Set<TraversalMode> activeModes) implements Component {

    public TraversalComponent {
        activeModes = (activeModes != null) ? Set.copyOf(activeModes) : Set.of();
    }
    public static TraversalComponent of(TraversalMode mode) {
        return new TraversalComponent(Set.of(mode));
    }
    public static TraversalComponent of(Set<TraversalMode> modes) {
        return new TraversalComponent(modes);
    }

    public static TraversalComponent empty() { return new TraversalComponent(Set.of());}

    public boolean hasMode(TraversalMode mode) {
        return activeModes.contains(mode);
    }

    public TraversalComponent withAdded(TraversalMode mode) {
        Objects.requireNonNull(mode, "Mode cannot be null");
        if(activeModes.contains(mode)) { return this;}
        Set<TraversalMode> newModes = new HashSet<>(activeModes);
        newModes.add(mode);
        return new TraversalComponent(newModes);
    }
    public TraversalComponent withRemoved(TraversalMode mode) {
        Objects.requireNonNull(mode, "Mode cannot be null");
        if(!activeModes.contains(mode)) { return this;}
        Set<TraversalMode> newModes = new HashSet<>(activeModes);
        newModes.remove(mode);
        return new TraversalComponent(newModes);
    }

    public static Set<TraversalMode> getActiveModesFrom(GameState state, EntityId id) {
        return state.getComponent(id, TraversalComponent.class).map(TraversalComponent::activeModes).orElse(Set.of());
    }
}
