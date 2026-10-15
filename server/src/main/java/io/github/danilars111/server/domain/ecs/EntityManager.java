package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.listener.ComponentChangeListener;
import io.github.danilars111.server.domain.ecs.listener.EntityDestroyedListener;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.EntityIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityManager {

    private final AtomicInteger nextEntityId = new AtomicInteger(0);
    private final Queue<Integer> freeEntityIds = new ArrayDeque<>();
    private final Set<EntityId> entities = new HashSet<>();
    private final Map<EntityId, Map<Class<? extends Component>, Component>> componentsByEntity = new HashMap<>();
    private final EntityIndex entityIndex;

    private final Map<Class<? extends Component>, List<ComponentChangeListener<?>>> componentListeners = new HashMap<>();
    private final List<EntityDestroyedListener> entityDestroyedListeners = new ArrayList<>();

    public EntityManager(EntityIndex entityIndex) {
        this.entityIndex = Objects.requireNonNull(entityIndex, "entityIndex cannot be null");
    }

    public EntityManager() {
        this(new EntityIndex());
    }

    public <T extends Component> void addComponentListener(
            Class<T> componentClass,
            ComponentChangeListener<T> listener) {
        Objects.requireNonNull(componentClass, "componentClass cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        componentListeners.computeIfAbsent(componentClass, k -> new ArrayList<>()).add(listener);
    }

    public void addEntityDestroyedListener(EntityDestroyedListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        entityDestroyedListeners.add(listener);
    }

    public EntityId createEntity() {
        Integer recycled = freeEntityIds.poll();
        int rawId = (recycled != null) ? recycled : nextEntityId.getAndIncrement();

        EntityId entityId = new EntityId(rawId);
        entities.add(entityId);
        componentsByEntity.put(entityId, new HashMap<>());
        entityIndex.registerEntity(entityId);
        return entityId;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> void addComponent(EntityId entityId, T component) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(component, "component cannot be null");

        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if (entityComponents == null || !entities.contains(entityId)) {
            throw new IllegalArgumentException("Entity does not exist: " + entityId.value());
        }

        entityComponents.put(component.getClass(), component);
        entityIndex.addComponent(entityId, component.getClass());

        List<ComponentChangeListener<?>> listeners = componentListeners.get(component.getClass());
        if (listeners != null) {
            for (ComponentChangeListener<?> listener : listeners) {
                ((ComponentChangeListener<T>) listener).onComponentAddedOrReplaced(entityId, component);
            }
        }
    }

    public <T extends Component> Optional<T> getComponent(EntityId entityId, Class<T> componentClass) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(componentClass, "componentClass cannot be null");
        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if (entityComponents == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(componentClass.cast(entityComponents.get(componentClass)));
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> Optional<T> removeComponent(EntityId entityId, Class<T> componentClass) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(componentClass, "componentClass cannot be null");

        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if (entityComponents == null) {
            return Optional.empty();
        }
        Component removed = entityComponents.remove(componentClass);
        if (removed != null) {
            entityIndex.removeComponent(entityId, componentClass);

            List<ComponentChangeListener<?>> listeners = componentListeners.get(componentClass);
            if (listeners != null) {
                for (ComponentChangeListener<?> listener : listeners) {
                    ((ComponentChangeListener<T>) listener).onComponentRemoved(entityId, (T) removed);
                }
            }
            return Optional.of(componentClass.cast(removed));
        }
        return Optional.empty();
    }

    public boolean destroyEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        if (!entities.remove(entityId)) {
            return false;
        }
        componentsByEntity.remove(entityId);
        entityIndex.removeEntity(entityId);
        freeEntityIds.add(entityId.value());

        for (EntityDestroyedListener listener : entityDestroyedListeners) {
            listener.onEntityDestroyed(entityId);
        }
        return true;
    }

    public boolean exists(EntityId entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        return entities.contains(entityId);
    }

    public final Set<EntityId> getEntitiesWith(Class<? extends Component>... componentClasses) {
        if (componentClasses == null || componentClasses.length == 0) {
            return Collections.unmodifiableSet(entities);
        }
        return entityIndex.getEntitiesWith(componentClasses);
    }

    public EntityIndex getEntityIndex() {
        return entityIndex;
    }
}
