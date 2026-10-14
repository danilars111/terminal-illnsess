package io.github.danilars111.server.domain.service.pathfinding.cost;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;
import io.github.danilars111.server.domain.component.ai.PathfindingModifierComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.StepCostModifierIndex;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.service.pathfinding.interfaces.StepCostCalculator;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Map;
import java.util.Objects;

public class StandardStepCostCalculator implements StepCostCalculator {

    private final GameState gameState;
    private final MovementRuleService movementRuleService;
    private final StepCostModifierIndex modifierIndex;

    public StandardStepCostCalculator(
            GameState gameState,
            MovementRuleService movementRuleService
    ) {
        this.gameState = Objects.requireNonNull(gameState, "GameState cannot be null");
        this.movementRuleService = Objects.requireNonNull(movementRuleService, "MovementRuleService cannot be null");
        this.modifierIndex = gameState.getStepCostModifierIndex();
    }


    @Override
    public long calculateStepCost(EntityId entityId, TileCoord from, TileCoord to) {
        // 1. Mandatory physics & movement rules check (terrain, bounds, diagonal pinch, dynamic colliders)
        if (!movementRuleService.canTraverseStep(gameState, entityId, from, to)) {
            return EngineTime.INFINITE;
        }

        // 2. Base physical cost (1000 orthogonal, 1414 diagonal)
        boolean isDiagonal = (from.getX() != to.getX()) && (from.getY() != to.getY());
        long totalCost = isDiagonal ? EngineTime.SQRT_2 : EngineTime.TICK_SCALE;

        // 3. Inspect entity-specific modifiers from ECS
        PathfindingModifierComponent modifierComp = gameState.getComponent(entityId, PathfindingModifierComponent.class)
                .orElse(null);

        if (modifierComp == null || modifierComp.activeModifiers().isEmpty()) {
            return totalCost;
        }

        for (Map.Entry<String, Map<String, Object>> entry : modifierComp.activeModifiers().entrySet()) {
            String modifierId = entry.getKey();
            Map<String, Object> overrides = entry.getValue();

            StepCostModifierDefinition def = modifierIndex.get(modifierId).orElse(null);
            if (def == null) {
                continue;
            }

            Map<String, Object> effectiveParams = modifierIndex.resolveEffectiveParams(modifierId, overrides);

            // Hook for future strategy execution once StepCostModifierStrategy has values:
            long additionalCost = evaluateStrategy(def, effectiveParams, entityId, from, to);
            if (additionalCost == EngineTime.INFINITE) {
                return EngineTime.INFINITE;
            }
            totalCost += Math.max(0L, additionalCost);
        }

        return totalCost;
    }

    private long evaluateStrategy(
            StepCostModifierDefinition def,
            Map<String, Object> effectiveParams,
            EntityId entityId,
            TileCoord from,
            TileCoord to
    ) {
        // Empty placeholder until StepCostModifierStrategy enum variants are introduced
        return 0L;
    }
}