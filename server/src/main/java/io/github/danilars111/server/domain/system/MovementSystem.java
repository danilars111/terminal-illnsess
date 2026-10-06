package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.PositionComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.system.interfaces.GameSystem;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MovementSystem implements GameSystem {

    private static final Logger LOGGER = System.getLogger(MovementSystem.class.getName());

    private record MoveIntent(int deltaX, int deltaY) {}

    private final Map<EntityId, MoveIntent> pendingMoves = new HashMap<>();

    @Override
    public void update(GameState gameState, long tick) {
        if(pendingMoves.isEmpty()) {
            return;
        }
        for(Map.Entry<EntityId, MoveIntent> entry : pendingMoves.entrySet()) {
            EntityId entityId = entry.getKey();
            MoveIntent moveIntent = entry.getValue();

            gameState.getComponent(entityId, PositionComponent.class).ifPresent(currentPosition -> {
                PositionComponent newPosition = currentPosition.translate(moveIntent.deltaX(), moveIntent.deltaY());
                gameState.addComponent(entityId, newPosition);
                LOGGER.log(Level.DEBUG, "Entity {0} moved from ({1}, {2}) to ({3}, {4}) on tick {5}",
                        entityId, currentPosition.getX(), currentPosition.getY(), newPosition.getX(), newPosition.getY(), tick);
            });
        }
        pendingMoves.clear();
    }

    public void requestMove(EntityId entityId, int deltaX, int deltaY) {
        Objects.requireNonNull(entityId, "EntityId cannot be null");
        pendingMoves.put(entityId, new MoveIntent(deltaX, deltaY));
        LOGGER.log(Level.DEBUG, "Registered move intent for entity {0}: ({1}, {2})", entityId.value(), deltaX, deltaY);
    }
}
