package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.ecs.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;



public class GameState {

    private static final Logger LOGGER = System.getLogger(GameState.class.getName());

    private final AtomicLong nextEntityId = new AtomicLong(0);
    private final Set<EntityId> entities = new HashSet<>();
    private final Map<EntityId, Map<Class<? extends Component>, Component>> componentsByEntity = new HashMap<>();

    public EntityId createEntity() {
        EntityId id = new EntityId(nextEntityId.getAndIncrement());
        entities.add(id);
        componentsByEntity.put(id, new HashMap<>());
        LOGGER.log(Level.DEBUG, "Created Entity: {0}", id.value());
        return id;
    }

    public <T extends Component> void addComponent(EntityId entityId, T component) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        Objects.requireNonNull(component, "Component cannot be null");

        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if (entityComponents == null) {
            throw new IllegalArgumentException("Entity does not exist: " + entityId.value());
        }
        entityComponents.put(component.getClass(), component);
        LOGGER.log(Level.TRACE, "Added Component {0} to Entity {1}", component.getClass(), entityId.value());
    }

    public <T extends Component> Optional<T> getComponent(EntityId entityId, Class<T> componentClass) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        Objects.requireNonNull(componentClass, "ComponentClass cannot be null");

        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if (entityComponents == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(componentClass.cast(entityComponents.get(componentClass)));
    }

    public <T extends Component> void removeComponent(EntityId entityId, Class<T> componentClass) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        Objects.requireNonNull(componentClass, "ComponentClass cannot be null");

        Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
        if(entityComponents != null) {
            entityComponents.remove(componentClass);
            LOGGER.log(Level.TRACE, "Removed Component {0} from Entity {1}", componentClass, entityId.value());
        }
    }

    public void destroyEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        entities.remove(entityId);
        componentsByEntity.remove(entityId);
        LOGGER.log(Level.DEBUG, "Destroyed Entity: {0}", entityId.value());
    }

    @SafeVarargs
    public final Set<EntityId> getEntitiesWith(Class<? extends Component>... componentClasses) {
        if (componentClasses == null || componentClasses.length == 0) {
            return Collections.unmodifiableSet(entities);
        }

        return entities.stream()
                .filter(entityId -> {
                    Map<Class<? extends Component>, Component> entityComponents = componentsByEntity.get(entityId);
                    if(entityComponents == null) {
                        return false;
                    }
                    for(Class<? extends Component> componentClass : componentClasses) {
                        if(!entityComponents.containsKey(componentClass)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toUnmodifiableSet());
    }
}
