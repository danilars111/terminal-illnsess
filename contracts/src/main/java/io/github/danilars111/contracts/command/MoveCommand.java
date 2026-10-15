package io.github.danilars111.contracts.command;

import io.github.danilars111.contracts.command.interfaces.GameCommand;

public record MoveCommand(int entityId, int deltaX, int deltaY) implements GameCommand {

    public MoveCommand {
        if(entityId < 0) {
            throw new IllegalArgumentException("Entity ID must be greater or equal to 0");
        }
        if(deltaX == 0 && deltaY == 0) {
            throw new IllegalArgumentException("Deltas cannot both be zero");
        }
    }
}
