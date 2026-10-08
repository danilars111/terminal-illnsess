package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;

import java.util.Set;

public class CooldownSystem implements GameSystem {
    public void update(GameState state, long tick) {
        Set<EntityId> entities = state.getEntitiesWith(CooldownComponent.class);
        if(entities.isEmpty()) {
            return;
        }

        for(EntityId entityId : entities) {
            state.getComponent(entityId, CooldownComponent.class).ifPresent((cooldownComponent) ->{
                CooldownComponent updated = cooldownComponent.tick();
                state.addComponent(entityId, updated);
            });
        }
    }
}
