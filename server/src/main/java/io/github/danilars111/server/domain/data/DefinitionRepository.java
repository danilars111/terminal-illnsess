package io.github.danilars111.server.domain.data;

import io.github.danilars111.contracts.definition.interfaces.Definition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DefinitionRepository<T extends Definition> {

    public volatile Map<String, T> definitions = Map.of();

    public void replaceAll(Map<String, T> newDefinitions) {
        Objects.requireNonNull(newDefinitions, "New definitions cannot be null");
        this.definitions = Map.copyOf(newDefinitions);
    }

    public Optional<T> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public T getRequired(String id) {
        T def = definitions.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Uknown definition ID: " + id);
        }
        return def;
    }

    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    public int size() {
        return definitions.size();
    }

    public Map<String, T> getAll() {
        return Map.copyOf(definitions);
    }
}
