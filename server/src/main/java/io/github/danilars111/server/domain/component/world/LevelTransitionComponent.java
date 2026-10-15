package io.github.danilars111.server.domain.component.world;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Objects;

public record LevelTransitionComponent(
        String targetLevelId,
        TileCoord targetCoord,
        boolean requiredConfirmation
) implements Component {

    public LevelTransitionComponent {
        Objects.requireNonNull(targetLevelId);
        Objects.requireNonNull(targetCoord);
    }
}
