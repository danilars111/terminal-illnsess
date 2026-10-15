package io.github.danilars111.contracts.definition.world;

import io.github.danilars111.contracts.definition.annotations.DefinitionPath;
import io.github.danilars111.contracts.definition.interfaces.Definition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;

import java.util.Objects;
import java.util.Set;

@DefinitionPath("tiles")
public record TileDefinition(
        String id,
        String displayName,
        Set<TraversalMode> traversalModes,
        Set<String> effectIds
) implements Definition {
    public TileDefinition {
        Objects.requireNonNull(id, "Id cannot be null");
        Objects.requireNonNull(displayName, "DisplayName cannot be null");

        traversalModes = (traversalModes != null) ? Set.copyOf(traversalModes) : Set.of();
        effectIds = (effectIds != null) ? Set.copyOf(effectIds) : Set.of();

    }

    public TileDefinition(String id, String displayName) {
        this(id, displayName, Set.of(), Set.of());
    }
    public TileDefinition(String id,
                          String displayName,
                          Set<TraversalMode> traversalModes) {
        this(id, displayName, traversalModes, Set.of());
    }

    public boolean isTraversable(TraversalMode mode) {
        return traversalModes.contains(mode);
    }

    public boolean hasEffect(String effectId) {
        return effectIds.contains(effectId);
    }
}
