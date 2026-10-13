package io.github.danilars111.server.domain.time;

public final class EngineTime {

    private EngineTime() {}

    /**
     * Fixed-point sub-tick resolution scale
     * 1 discrete server tick = 1 000 time units (milliticks)
     */
    public static final int TICK_SCALE = 1_000;

    /**
     *  Sqrt(2) scaled to TICK_SCALE (~1.4142 * 1000)
     */
    public  static final int SQRT_2 = 1_414;

    /**
     * Representation of impossible, blocked or infinite duration.
     */
    public static final long INFINITE = Long.MAX_VALUE;

    public static long ticksToUnits(long ticks) {
        return ticks * TICK_SCALE;
    }

    public static long unitsToTicksCeil(long units) {
        if (units >= INFINITE - (TICK_SCALE - 1)) return INFINITE;
        return (units + TICK_SCALE - 1) / TICK_SCALE;
    }

    public static long unitsToTicksRound(long units) {
        if (units >= INFINITE - (TICK_SCALE / 2)) return INFINITE;
        return (units + (TICK_SCALE / 2)) / TICK_SCALE;
    }

    public static long unitsToTicksFloor(long units) {
        if (units == INFINITE) return INFINITE;
        return units / TICK_SCALE;
    }
}
