package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.contracts.definition.ai.pathfinding.StepCostModifierDefinition;
import io.github.danilars111.contracts.definition.ai.pathfinding.enums.StepCostModifierStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StepCostModifierIndexTest {

    private StepCostModifierIndex index;

    @BeforeEach
    void setUp() {
        index = new StepCostModifierIndex();
    }

    @Nested
    @DisplayName("Registration and Retrieval")
    class RegistrationTests {

        @Test
        @DisplayName("Successfully registers and retrieves a definition by id")
        void registerAndGet() {
            StepCostModifierDefinition def = new StepCostModifierDefinition(
                    "avoid_fire",
                    "Avoid Fire",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("cost", 5000L)
            );

            index.register(def);

            Optional<StepCostModifierDefinition> retrieved = index.get("avoid_fire");
            assertTrue(retrieved.isPresent());
            assertEquals(def, retrieved.get());
            assertTrue(index.contains("avoid_fire"));
        }

        @Test
        @DisplayName("Returns empty optional and false when definition is not found")
        void getNonExistent() {
            Optional<StepCostModifierDefinition> retrieved = index.get("unknown_mod");
            assertTrue(retrieved.isEmpty());
            assertFalse(index.contains("unknown_mod"));
        }

        @Test
        @DisplayName("Overwrites an existing definition when registering with the same id")
        void overwriteRegistration() {
            StepCostModifierDefinition def1 = new StepCostModifierDefinition(
                    "speed_buff",
                    "Speed Buff v1",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("multiplier", 2)
            );
            StepCostModifierDefinition def2 = new StepCostModifierDefinition(
                    "speed_buff",
                    "Speed Buff v2",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("multiplier", 3)
            );

            index.register(def1);
            index.register(def2);

            Optional<StepCostModifierDefinition> retrieved = index.get("speed_buff");
            assertTrue(retrieved.isPresent());
            assertEquals("Speed Buff v2", retrieved.get().name());
            assertEquals(3, retrieved.get().params().get("multiplier"));
        }

        @Test
        @DisplayName("Clearing removes all registered definitions")
        void clear() {
            index.register(new StepCostModifierDefinition("mod_1", "Mod 1", StepCostModifierStrategy.PLACEHOLDER, Map.of()));
            index.register(new StepCostModifierDefinition("mod_2", "Mod 2", StepCostModifierStrategy.PLACEHOLDER, Map.of()));

            index.clear();

            assertFalse(index.contains("mod_1"));
            assertFalse(index.contains("mod_2"));
            assertTrue(index.get("mod_1").isEmpty());
        }
    }

    @Nested
    @DisplayName("Parameter Resolution")
    class ParameterResolutionTests {

        @Test
        @DisplayName("Returns unmodifiable copy of definition params when overrides are null")
        void resolvesDefaultsWhenOverridesNull() {
            index.register(new StepCostModifierDefinition(
                    "hazard",
                    "Hazard",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("penalty", 1000L, "radius", 2)
            ));

            Map<String, Object> params = index.resolveEffectiveParams("hazard", null);

            assertEquals(1000L, params.get("penalty"));
            assertEquals(2, params.get("radius"));
            assertThrows(UnsupportedOperationException.class, () -> params.put("newKey", 123));
        }

        @Test
        @DisplayName("Returns definition params when overrides map is empty")
        void resolvesDefaultsWhenOverridesEmpty() {
            index.register(new StepCostModifierDefinition(
                    "hazard",
                    "Hazard",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("penalty", 1000L)
            ));

            Map<String, Object> params = index.resolveEffectiveParams("hazard", Map.of());

            assertEquals(1000L, params.get("penalty"));
        }

        @Test
        @DisplayName("Overrides take precedence over definition defaults and merge new keys")
        void mergesOverridesOverDefaults() {
            index.register(new StepCostModifierDefinition(
                    "flee",
                    "Flee Behavior",
                    StepCostModifierStrategy.PLACEHOLDER,
                    Map.of("distance", 5, "penalty", 2000L)
            ));

            Map<String, Object> overrides = Map.of(
                    "distance", 10,          // Overrides default
                    "targetTag", "HOSTILE"   // Added new parameter
            );

            Map<String, Object> effective = index.resolveEffectiveParams("flee", overrides);

            assertEquals(10, effective.get("distance"));
            assertEquals(2000L, effective.get("penalty"));
            assertEquals("HOSTILE", effective.get("targetTag"));
            assertThrows(UnsupportedOperationException.class, () -> effective.put("fail", true));
        }

        @Test
        @DisplayName("Returns safe copy of overrides if definition is not registered")
        void fallbackWhenDefinitionMissing() {
            Map<String, Object> overrides = Map.of("customPenalty", 500L);

            Map<String, Object> effective = index.resolveEffectiveParams("unregistered_mod", overrides);

            assertEquals(500L, effective.get("customPenalty"));
            assertThrows(UnsupportedOperationException.class, () -> effective.put("fail", true));
        }

        @Test
        @DisplayName("Returns empty map if definition is missing and overrides are null or empty")
        void fallbackWhenDefinitionMissingAndNoOverrides() {
            Map<String, Object> effectiveNull = index.resolveEffectiveParams("missing", null);
            Map<String, Object> effectiveEmpty = index.resolveEffectiveParams("missing", Map.of());

            assertTrue(effectiveNull.isEmpty());
            assertTrue(effectiveEmpty.isEmpty());
        }
    }
}