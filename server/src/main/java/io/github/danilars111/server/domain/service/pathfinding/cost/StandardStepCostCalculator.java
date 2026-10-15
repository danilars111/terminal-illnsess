package io.github.danilars111.server.domain.service.pathfinding.cost;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;
import io.github.danilars111.server.domain.component.ai.PathfindingModifierComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
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
            MovementRuleService movementRuleService,
            StepCostModifierIndex modifierIndex
    ) {
        this.gameState = Objects.requireNonNull(gameState, "GameState cannot be null");
        this.movementRuleService = Objects.requireNonNull(movementRuleService, "MovementRuleService cannot be null");
        this.modifierIndex = Objects.requireNonNull(modifierIndex, "StepCostModifierIndex cannot be null");
    }

    public StandardStepCostCalculator(
            GameState gameState,
            MovementRuleService movementRuleService
    ) {
        this(gameState, movementRuleService, new StepCostModifierIndex());
    }

    @Override
    public long calculateStepCost(EntityId entityId, TileCoord from, TileCoord to) {
        String levelId = gameState.entityManager().getComponent(entityId, PositionComponent.class)
                .map(PositionComponent::levelId)
                .orElse(null);

        if (levelId == null) {
            return EngineTime.INFINITE;
        }

        // 1. Mandatory physics and movement rules check on the active level
        if (!movementRuleService.canTraverseStep(gameState, entityId, levelId, from, to)) {
            return EngineTime.INFINITE;
        }

        // 2. Base physical cost (1000 orthogonal, 1414 diagonal)
        boolean isDiagonal = (from.getX() != to.getX()) && (from.getY() != to.getY());
        long totalCost = isDiagonal ? EngineTime.SQRT_2 : EngineTime.TICK_SCALE;

        // 3. Inspect entity-specific modifiers from ECS
        PathfindingModifierComponent modifierComp = gameState.entityManager()
                .getComponent(entityId, PathfindingModifierComponent.class)
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
        return 0L;
    }
}