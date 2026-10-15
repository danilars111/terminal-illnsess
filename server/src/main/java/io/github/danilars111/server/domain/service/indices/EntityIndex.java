package io.github.danilars111.server.domain.service.indices;


import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  High-performance bitset index mapping component types to entity IDs
 *  Encapsulates component type IDs internally so callers only work with Class literals
 */
public class EntityIndex {

    private  final Map<Class<? extends Component>, Integer> componentTypeIds = new ConcurrentHashMap<>();
    private final AtomicInteger nextTypeId = new AtomicInteger(0);

    // One BitSet per component type ID: bit N represents entityId.value()
    private final Map<Integer, BitSet> componentBitSets = new HashMap<>();

    // Tracks which component type IDs each entity currently holds for fast removal
    private final Map<EntityId, BitSet> entityComponentMasks = new HashMap<>();

    private int getOrAssignTypeId(Class<? extends Component> componentClass) {
        return componentTypeIds.computeIfAbsent(componentClass, k -> nextTypeId.getAndIncrement());
    }

    public void registerEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "entity cannot be null");
        if (entityComponentMasks.containsKey(entityId)) {
            throw new IllegalStateException("Entity is already registered: " + entityId.value());
        }
        entityComponentMasks.put(entityId, new BitSet());
    }

    public void addComponent(EntityId entityId, Class<? extends Component> componentClass) {
        Objects.requireNonNull(entityId, "entity cannot be null");
        Objects.requireNonNull(componentClass, "componentClass cannot be null");

        int typeId = getOrAssignTypeId(componentClass);
        int bitIndex = entityId.value();

        componentBitSets.computeIfAbsent(typeId, k -> new BitSet()).set(bitIndex);
        entityComponentMasks.computeIfAbsent(entityId, k -> new BitSet()).set(typeId);
    }

    public void removeComponent(EntityId entityId, Class<? extends Component> componentClass) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(componentClass, "componentClass cannot be null");

        Integer typeId = componentTypeIds.get(componentClass);
        if (typeId == null) {
            return;
        }

        int bitIndex = entityId.value();
        BitSet compSet = componentBitSets.get(typeId);
        if (compSet != null) {
            compSet.clear(bitIndex);
        }

        BitSet mask = entityComponentMasks.get(entityId);
        if (mask != null) {
            mask.clear(bitIndex);
        }
    }

    public void removeEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        BitSet mask = entityComponentMasks.remove(entityId);
        if (mask == null || mask.isEmpty()) {
            return;
        }
        int bitIndex = entityId.value();
        for (int typeId = mask.nextSetBit(0); typeId >= 0; typeId = mask.nextSetBit(typeId + 1)) {
            BitSet compSet = componentBitSets.get(typeId);
            if (compSet != null) {
                compSet.clear(bitIndex);
            }
        }
    }

    @SafeVarargs
    public final Set<EntityId> getEntitiesWith(Class<? extends Component>... componentClasses) {
        if (componentClasses == null || componentClasses.length == 0) {
            return Set.of();
        }
        // Check if any requested component type has never been registererd/assigned
        for (Class<? extends Component> clazz : componentClasses) {
            if (!componentTypeIds.containsKey(clazz)) {
                return Set.of();
            }
        }

        int firstTypeId = getOrAssignTypeId(componentClasses[0]);
        BitSet firstSet = componentBitSets.get(firstTypeId);
        if (firstSet == null || firstSet.isEmpty()) {
            return Set.of();
        }

        BitSet result = (BitSet) firstSet.clone();
        for (int i = 1; i < componentClasses.length; i++) {
            int typeId = getOrAssignTypeId(componentClasses[i]);
            BitSet nextSet = componentBitSets.get(typeId);
            if (nextSet == null || nextSet.isEmpty()) {
                return Set.of();
            }
            result.and(nextSet);
            if (result.isEmpty()) {
                return Set.of();
            }
        }

        Set<EntityId> matched = new LinkedHashSet<>(result.cardinality());
        for (int i = result.nextSetBit(0); i >= 0; i = result.nextSetBit(i + 1)) {
            matched.add(new EntityId(i));
        }

        return Collections.unmodifiableSet(matched);
    }

    public void clear() {
        componentBitSets.clear();
        entityComponentMasks.clear();
    }
}
