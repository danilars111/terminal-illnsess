package io.github.danilars111.server.domain.component.movement;

import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PathFollowComponent {
    private final List<TileCoord> waypoints;
    private final List<Long> expectedCostUnits;
    private int currentIndex;

    public PathFollowComponent(List<TileCoord> waypoints, List<Long> expectedCostUnits, int currentIndex) {
        Objects.requireNonNull(waypoints);
        this.waypoints = List.copyOf(waypoints);
        this.expectedCostUnits = expectedCostUnits != null
                ? List.copyOf(expectedCostUnits) : List.of();
        if(currentIndex < 0) {
            throw new IllegalArgumentException("currentIndex cannot be negative");
        }
        this.currentIndex = currentIndex;
    }

    //Default Index = 1: The path returned by the pathfinder starts with the entity's current position at index 0.
    // Index 1 is the immediate next tile the entity needs to step onto.
    public PathFollowComponent(List<TileCoord> waypoints, List<Long> expectedCostUnits) {
        this(waypoints, expectedCostUnits, 1);
    }

    public PathFollowComponent(List<TileCoord> waypoints) {
        this(waypoints, List.of(), 1);
    }

    public List<TileCoord> getWaypoints() {
        return List.copyOf(waypoints);
    }

    public List<Long> getExpectedCostUnits() {
        return List.copyOf(expectedCostUnits);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Optional<TileCoord> getCurrentTarget() {
        if(isComplete()) {
            return Optional.empty();
        }
        return Optional.of(waypoints.get(currentIndex));
    }

    public Optional<Long> getExpectedCostForCurrentStep() {
        if (currentIndex < expectedCostUnits.size()) {
            return Optional.of(expectedCostUnits.get(currentIndex));
        }
        return Optional.empty();
    }
    public void advance() {
        if(!isComplete()) {
            currentIndex++;
        }
    }

    public boolean isComplete() {
        return currentIndex >= waypoints.size();
    }

    public Optional<TileCoord> getFinalDestination() {
        if(waypoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(waypoints.getLast());
    }

    public int getRemainingSteps() {
        return Math.max(0, waypoints.size() - currentIndex);
    }
}
