package io.github.danilars111.server.domain.component.ai;

import io.github.danilars111.server.domain.component.interfaces.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record PathfindingModifierComponent(
        Map<String, Map<String, Object>> activeModifiers
) implements Component {

    public PathfindingModifierComponent {
        if (activeModifiers == null || activeModifiers.isEmpty()) {
            activeModifiers = Map.of();
        } else {
            Map<String, Map<String, Object>> defensiveCopy = new HashMap<>();
            activeModifiers.forEach((id, params) -> {
                defensiveCopy.put(id, params != null ? Map.copyOf(params) : Map.of());
            });
            activeModifiers = Collections.unmodifiableMap(defensiveCopy);
        }
    }

    public static PathfindingModifierComponent empty() {
        return new PathfindingModifierComponent(Map.of());
    }

    public static PathfindingModifierComponent of(String... modifierIds) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (String id : modifierIds) {
            map.put(id, Map.of());
        }
        return new PathfindingModifierComponent(map);
    }

    public PathfindingModifierComponent withModifier(String modifierId) {
        return this.withModifier(modifierId, Map.of());
    }

    public PathfindingModifierComponent withModifier(String modifierId, Map<String, Object> paramOverrides) {
        Objects.requireNonNull(modifierId, "modifier id cannot be null");
        Map<String, Map<String, Object>> updated = new HashMap<>(activeModifiers);
        updated.put(modifierId, paramOverrides != null ?Map.copyOf(paramOverrides) : Map.of());
        return new PathfindingModifierComponent(updated);
    }

    public Set<String> getActiveModifiers() {
        return activeModifiers.keySet();
    }

    public Map<String, Object> getOverrides(String modifierId) {
        return activeModifiers.getOrDefault(modifierId, Map.of());
    }
}
