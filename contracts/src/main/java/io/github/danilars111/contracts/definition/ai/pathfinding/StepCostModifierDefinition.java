package io.github.danilars111.contracts.definition.ai.pathfinding;

import io.github.danilars111.contracts.definition.ai.pathfinding.enums.StepCostModifierStrategy;
import io.github.danilars111.contracts.definition.annotations.DefinitionPath;
import io.github.danilars111.contracts.definition.interfaces.Definition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable contract definition representing a pathfinding cost modifier loaded from JSON.
 * Serves as the baseline template for behavioral, environmental, and spatial path costs.
 *
 * @param id       Unique identifier for the modifier (e.g., "avoid_fire", "prefer_roads")
 * @param name     Human-readable name used for logging and tooling
 * @param strategy Evaluation strategy key mapping to engine logic (e.g., "TILE_COMPONENT_MATCH")
 * @param params   Default configuration parameters passed into the strategy
 */
@DefinitionPath("pathfinding/modifiers")
public record StepCostModifierDefinition(
        String id,
        String name,
        StepCostModifierStrategy strategy,
        Map<String, Object> params
) implements Definition {
    public StepCostModifierDefinition {
        Objects.requireNonNull(id, "Modifier id cannot be null");
        Objects.requireNonNull(strategy, "Modifier strategy cannot be null");
        params = (params != null) ? Collections.unmodifiableMap(params) : Map.of();
        name = (name != null) ? name : id;
    }
}

