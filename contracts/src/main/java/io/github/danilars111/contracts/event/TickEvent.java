package io.github.danilars111.contracts.event;

public record TickEvent(long tick) {
    public TickEvent {
        if(tick < 1) {
            throw new IllegalArgumentException("Tick must be greater than 0");
        }
    }
}
