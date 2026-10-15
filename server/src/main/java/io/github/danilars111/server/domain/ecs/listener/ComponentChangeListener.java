package io.github.danilars111.server.domain.ecs.listener;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.ecs.records.EntityId;

public interface ComponentChangeListener<T extends Component> {
    void onComponentAddedOrReplaced(EntityId entityId, T component);
    void onComponentRemoved(EntityId entityId, T component);
}
