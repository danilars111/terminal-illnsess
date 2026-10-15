package io.github.danilars111.server.domain.world.records;

import io.github.danilars111.server.domain.data.DefinitionRegistry;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;

public record GameWorldContext(
        DefinitionRegistry registry,
        TileIndex tileIndex,
        TilePalette tilePalette
) {}