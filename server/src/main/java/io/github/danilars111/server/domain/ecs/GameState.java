package io.github.danilars111.server.domain.ecs;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.EntityIndex;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.StepCostModifierIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GameState {
    private static final Logger LOGGER = System.getLogger(GameState.class.getName());

    private final AtomicInteger nextEntityId = new AtomicInteger(0);
    private final Queue<Integer> freeEntityIds = new ArrayDeque<>();
    private final Set<EntityId> entities = new HashSet<>();
    private final Map<EntityId, Map<Class<? extends Component>, Component>> componentsByEntity = new HashMap<>();

    private final SpatialIndex spatialIndex;
    private final TileIndex tileIndex;
    private final StepCostModifierIndex stepCostModifierIndex;
    private final EntityIndex entityIndex;

    public GameState(
            SpatialIndex spatialIndex,
            TileIndex tileIndex,
            StepCostModifierIndex stepCostModifierIndex,
            EntityIndex entityIndex
    ) {
        this.spatialIndex = Objects.requireNonNull(spatialIndex, "spatialIndex cannot be null");
        this.tileIndex = Objects.requireNonNull(tileIndex, "tileIndex cannot be null");
        this.stepCostModifierIndex = Objects.requireNonNull(stepCostModifierIndex, "stepCostModifierIndex cannot be null");
        this.entityIndex = Objects.requireNonNull(entityIndex, "entityIndex cannot be null");
    }

    public GameState() {
        this(
                new SpatialIndex(),
                new TileIndex(),
                new StepCostModifierIndex(),
                new EntityIndex()
        );
    }

    public SpatialIndex getSpatialIndex() { return spatialIndex; }
    public TileIndex getTileIndex() { return tileIndex; }
    public StepCostModifierIndex getStepCostModifierIndex() { return stepCostModifierIndex; }
    public EntityIndex getEntityIndex() { return entityIndex; }

    public EntityId createEntity() {
        Integer recycled = freeEntityIds.poll();
        int rawId = (recycled != null) ? recycled : nextEntityId.getAndIncrement();

        EntityId id = new EntityId(rawId);
        entities.add(id);
        componentsByEntity.put(id, new HashMap<>());
        entityIndex.registerEntity(id);

        LOGGER.log(Level.DEBUG, "Created Entity: {0} (recycled: {1})", id.value(), recycled != null);
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
        entityIndex.addComponent(entityId, component.getClass());
        LOGGER.log(Level.TRACE, "Added Component {0} to Entity {1}", component.getClass(), entityId.value());

        switch (component) {
            case PositionComponent pos -> {
                FootprintComponent footprint = (FootprintComponent) entityComponents.get(FootprintComponent.class);
                spatialIndex.updateEntity(entityId, pos, footprint);
            }
            case FootprintComponent footprint -> {
                PositionComponent pos = (PositionComponent) entityComponents.get(PositionComponent.class);
                if (pos != null) {
                    spatialIndex.updateEntity(entityId, pos, footprint);
                }
            }
            default -> {}
        }
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
        if (entityComponents != null && entityComponents.remove(componentClass) != null) {
            entityIndex.removeComponent(entityId, componentClass);
            LOGGER.log(Level.TRACE, "Removed Component {0} from Entity {1}", componentClass, entityId.value());

            if (componentClass == PositionComponent.class) {
                spatialIndex.remove(entityId);
            } else if (componentClass == FootprintComponent.class) {
                PositionComponent pos = (PositionComponent) entityComponents.get(PositionComponent.class);
                if (pos != null) {
                    spatialIndex.updateEntity(entityId, pos, null);
                }
            }
        }
    }

    public void destroyEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        if (!entities.contains(entityId)) {
            return;
        }

        entities.remove(entityId);
        componentsByEntity.remove(entityId);
        spatialIndex.remove(entityId);
        entityIndex.removeEntity(entityId);

        freeEntityIds.offer(entityId.value());
        LOGGER.log(Level.DEBUG, "Destroyed Entity: {0} (returned to free pool)", entityId.value());
    }

    @SafeVarargs
    public final Set<EntityId> getEntitiesWith(Class<? extends Component>... componentClasses) {
        if (componentClasses == null || componentClasses.length == 0) {
            return Collections.unmodifiableSet(entities);
        }
        return entityIndex.getEntitiesWith(componentClasses);
    }
}