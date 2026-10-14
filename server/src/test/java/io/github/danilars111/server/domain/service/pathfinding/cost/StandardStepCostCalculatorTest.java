package io.github.danilars111.server.domain.service.pathfinding.cost;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;
import io.github.danilars111.contracts.definition.ai.pathfinding.enums.StepCostModifierStrategy;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import io.github.danilars111.server.domain.component.FootprintComponent;
import io.github.danilars111.server.domain.component.ai.PathfindingModifierComponent;
import io.github.danilars111.server.domain.component.movement.PositionComponent;
import io.github.danilars111.server.domain.component.movement.TraversalComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.StepCostModifierIndex;
import io.github.danilars111.server.domain.service.movement.MovementRuleService;
import io.github.danilars111.server.domain.time.EngineTime;
import io.github.danilars111.server.domain.world.GameWorld;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardStepCostCalculatorTest {

    private GameState gameState;
    private EntityId mover;
    private MovementRuleService movementRuleService;
    private StepCostModifierIndex modifierIndex;
    private StandardStepCostCalculator calculator;

    private final TileDefinition walkableTile = new TileDefinition(
            "walkable",
            "Walkable",
            Set.of(TraversalMode.WALKING)
    );

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        mover = gameState.createEntity();
        gameState.addComponent(mover, new PositionComponent(5, 5));
        gameState.addComponent(mover, TraversalComponent.of(TraversalMode.WALKING));
        gameState.addComponent(mover, FootprintComponent.singleTile());

        TileDefinition[] grid = new TileDefinition[100];
        Arrays.fill(grid, walkableTile);
        GameWorld world = new GameWorld(10, 10, grid);

        movementRuleService = new MovementRuleService(world, MovementRuleService.DiagonalMode.PERMISSIVE);
        modifierIndex = new StepCostModifierIndex();
        calculator = new StandardStepCostCalculator(gameState, movementRuleService, modifierIndex);
    }

    @Test
    @DisplayName("Returns TICK_SCALE (1000) for orthogonal steps when movement is allowed")
    void returnsTickScaleForOrthogonalStep() {
        TileCoord from = new TileCoord(5, 5);
        TileCoord to = new TileCoord(6, 5);

        long cost = calculator.calculateStepCost(mover, from, to);

        assertEquals(EngineTime.TICK_SCALE, cost);
    }

    @Test
    @DisplayName("Returns SQRT_2 (1414) for diagonal steps when movement is allowed")
    void returnsSqrt2ForDiagonalStep() {
        TileCoord from = new TileCoord(5, 5);
        TileCoord to = new TileCoord(6, 6);

        long cost = calculator.calculateStepCost(mover, from, to);

        assertEquals(EngineTime.SQRT_2, cost);
    }

    @Test
    @DisplayName("Returns INFINITE when MovementRuleService rejects the traversal")
    void returnsInfiniteWhenMovementRuleServiceRejects() {
        TileCoord from = new TileCoord(5, 5);
        TileCoord to = new TileCoord(7, 5); // Distance > 1 tile is illegal

        long cost = calculator.calculateStepCost(mover, from, to);

        assertEquals(EngineTime.INFINITE, cost);
    }

    @Test
    @DisplayName("Returns base cost when entity has no PathfindingModifierComponent")
    void returnsBaseCostWithoutModifierComponent() {
        TileCoord from = new TileCoord(5, 5);
        TileCoord to = new TileCoord(5, 6);

        long cost = calculator.calculateStepCost(mover, from, to);

        assertEquals(EngineTime.TICK_SCALE, cost);
    }

    @Test
    @DisplayName("Safely ignores modifier IDs in component that are not present in ModifierIndex")
    void ignoresUnregisteredModifierDefinitions() {
        gameState.addComponent(mover, PathfindingModifierComponent.of("unknown_mod_1", "unknown_mod_2"));

        TileCoord from = new TileCoord(5, 5);
        TileCoord to = new TileCoord(5, 6);

        long cost = calculator.calculateStepCost(mover, from, to);

        assertEquals(EngineTime.TICK_SCALE, cost);
    }

    @Test
    @DisplayName("ModifierIndex correctly merges definition defaults with instance overrides")
    void resolvesEffectiveParamsCorrectly() {
        StepCostModifierDefinition def = new StepCostModifierDefinition(
                "hazard_avoidance",
                "Avoid Hazard",
                StepCostModifierStrategy.PLACEHOLDER,
                Map.of("penalty", 5000L, "radius", 3)
        );
        modifierIndex.register(def);

        // Instance overrides only the radius, keeps default penalty
        Map<String, Object> overrides = Map.of("radius", 5, "extraTag", "CUSTOM");
        Map<String, Object> effective = modifierIndex.resolveEffectiveParams("hazard_avoidance", overrides);

        assertEquals(5000L, effective.get("penalty"));
        assertEquals(5, effective.get("radius"));
        assertEquals("CUSTOM", effective.get("extraTag"));
    }

    @Test
    @DisplayName("ModifierIndex fallback returns pure defaults when overrides are empty or null")
    void resolvesEffectiveParamsWithNoOverrides() {
        StepCostModifierDefinition def = new StepCostModifierDefinition(
                "hazard_avoidance",
                "Avoid Hazard",
                StepCostModifierStrategy.PLACEHOLDER,
                Map.of("penalty", 5000L)
        );
        modifierIndex.register(def);

        Map<String, Object> effectiveEmpty = modifierIndex.resolveEffectiveParams("hazard_avoidance", Map.of());
        Map<String, Object> effectiveNull = modifierIndex.resolveEffectiveParams("hazard_avoidance", null);

        assertEquals(5000L, effectiveEmpty.get("penalty"));
        assertEquals(5000L, effectiveNull.get("penalty"));
    }

    @Test
    @DisplayName("ModifierIndex fallback returns provided overrides if definition is missing")
    void resolvesOverridesWhenDefinitionMissing() {
        Map<String, Object> overrides = Map.of("customKey", 123);
        Map<String, Object> effective = modifierIndex.resolveEffectiveParams("missing_def", overrides);

        assertEquals(123, effective.get("customKey"));
    }
}