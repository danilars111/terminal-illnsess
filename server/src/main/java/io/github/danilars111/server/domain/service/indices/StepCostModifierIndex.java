package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StepCostModifierIndex {
    private final Map<String, StepCostModifierDefinition> definitions = new HashMap<>();

    public void register(StepCostModifierDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public Optional<StepCostModifierDefinition> get(String modifierId) {
        return Optional.ofNullable(definitions.get(modifierId));
    }

    public boolean contains(String modifierId) {
        return definitions.containsKey(modifierId);
    }

    public void clear() {
        definitions.clear();
    }

    /**
     * Resolves the effective parameters for a modifier on a specific entity step,
     * merging definition defaults with any instance overrides.
     */
    public Map<String, Object> resolveEffectiveParams(String modifierId, Map<String, Object> overrides) {
        StepCostModifierDefinition definition = definitions.get(modifierId);
        if (definition == null) {
            return overrides != null ? Map.copyOf(overrides) : Map.of();
        }

        if (overrides == null || overrides.isEmpty()) {
            return definition.params();
        }

        Map<String, Object> merged = new HashMap<>(definition.params());
        merged.putAll(overrides);
        return Collections.unmodifiableMap(merged);
    }
}