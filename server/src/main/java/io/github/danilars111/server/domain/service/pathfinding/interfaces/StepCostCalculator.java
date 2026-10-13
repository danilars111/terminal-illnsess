package io.github.danilars111.server.domain.service.pathfinding.interfaces;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.world.records.TileCoord;

@FunctionalInterface
public interface StepCostCalculator {
    /**
     * Calculates the cost of moving from one tile to another in sub-tick units.
     * @return Cost in EngineTime.TICK_SCALE units, or EngineTime.INFINITE if impassable/blocked.
     */
    long calculateStepCost(EntityId entityId, TileCoord from, TileCoord to, FootprintComponent footprint);
}