package io.github.danilars111.server.domain.ecs.records;

public record EntityId(long value) {
    public EntityId {
        if (value < 0) {
            throw new IllegalArgumentException("EntityId must be positive");
        }
    }
}