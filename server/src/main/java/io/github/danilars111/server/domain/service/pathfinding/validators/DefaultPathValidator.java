package io.github.danilars111.server.domain.service.pathfinding.validators;

import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.movement.PathFollowComponent;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidationResult;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.PathValidator;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.List;
import java.util.Objects;

public class DefaultPathValidator implements PathValidator {

    private final StepCostCalculator costCalculator;

    public DefaultPathValidator(StepCostCalculator costCalculator) {
        this.costCalculator = Objects.requireNonNull(costCalculator);
    }

    @Override
    public PathValidationResult validate(
            EntityId entityId,
            PathFollowComponent pathFollow,
            FootprintComponent footprint,
            int lookaheadSteps) {

        if(pathFollow.isComplete()) {
            return new PathValidationResult.Valid();
        }

        List<TileCoord> waypoints = pathFollow.getWaypoints();
        List<Long> expectedCosts = pathFollow.getExpectedCostUnits();
        int currentIdx = pathFollow.getCurrentIndex();

        int maxIdx = Math.min(waypoints.size(), currentIdx + Math.max(1, lookaheadSteps));

        for (int i = currentIdx; i < maxIdx; i++) {
            TileCoord from = waypoints.get(i - 1);
            TileCoord to = waypoints.get(i);

            long actualCost = costCalculator.calculateStepCost(entityId, from, to, footprint);

            // Hard block check
            if (actualCost == EngineTime.INFINITE || actualCost <= 0L ) {
                return new PathValidationResult.Blocked(to, i);
            }

            // Cost Inflation Check (e.g. environment hazard spawned)
            if (i < expectedCosts.size()) {
                long plannedCost = expectedCosts.get(i);
                if  (actualCost > plannedCost) {
                    return new PathValidationResult.CostInflated(to, plannedCost, actualCost);
                }
            }
        }
        return new  PathValidationResult.Valid();
    }
}
