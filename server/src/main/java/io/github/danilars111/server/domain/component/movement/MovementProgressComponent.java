package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.component.interfaces.Component;

public class MovementProgressComponent implements Component {

    private long accumulatedUnits;

    public MovementProgressComponent(long initialUnits) {
        this.accumulatedUnits = Math.max(0L, initialUnits);
    }

    public MovementProgressComponent() {
        this(0L);
    }

    public long getAccumulatedUnits() {
        return accumulatedUnits;
    }

    public void addUnits(long units) {
        if (units > 0) {
            this.accumulatedUnits += units;
        }
    }

    public void consumeUnits(long units) {
        this.accumulatedUnits = Math.max(0L, this.accumulatedUnits - units);
    }

    public void reset() {
        this.accumulatedUnits = 0L;
    }
}