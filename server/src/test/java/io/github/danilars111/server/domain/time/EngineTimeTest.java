package io.github.danilars111.server.domain.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EngineTimeTest {

    @Test
    @DisplayName("Converts ticks to scaled millitick units accurately")
    void convertsTicksToUnits() {
        assertEquals(0L, EngineTime.ticksToUnits(0));
        assertEquals(1_000L, EngineTime.ticksToUnits(1));
        assertEquals(4_000L , EngineTime.ticksToUnits(4));
        assertEquals(50_000L, EngineTime.ticksToUnits(50));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "999, 0",
            "1000, 1",
            "1001, 1",
            "1999, 1",
            "2000, 2",
            "3500, 3"
    })
    @DisplayName("Floor truncates down to whole ticks")
    void floorTruncatesToWholeTicks(long units, long expectedTicks) {
        assertEquals(expectedTicks, EngineTime.unitsToTicksFloor(units));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "999, 1",
            "1000, 1",
            "1001, 2",
            "3000, 3",
            "3001, 4",
            "3999, 4"
    })
    @DisplayName("Ceil rounds up any fraction to next whole tick without bumping exact multiples")
    void ceilRoundsUpAnyFraction(long units, long expectedTicks) {
        assertEquals(expectedTicks, EngineTime.unitsToTicksCeil(units));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "499, 0",
            "500, 1",
            "999, 1",
            "1000, 1",
            "1499, 1",
            "1500, 2",
            "3499, 3",
            "3500, 4"
    })
    @DisplayName("Round applies half-up rounding accurately")
    void roundAppliesHalfUpRounding(long units, long expectedTicks) {
        assertEquals(expectedTicks, EngineTime.unitsToTicksRound(units));
    }

    @Test
    @DisplayName("Handles INFINITE sentinel safely across all functions")
    void preservesInfiniteSentinel() {
        assertEquals(EngineTime.INFINITE, EngineTime.unitsToTicksFloor(EngineTime.INFINITE));
        assertEquals(EngineTime.INFINITE, EngineTime.unitsToTicksCeil(EngineTime.INFINITE));
        assertEquals(EngineTime.INFINITE, EngineTime.unitsToTicksRound(EngineTime.INFINITE));
    }
}
