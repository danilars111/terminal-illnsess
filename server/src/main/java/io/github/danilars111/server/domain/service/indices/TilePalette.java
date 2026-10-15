package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.server.domain.data.DefinitionRepository;

import java.util.Objects;

public class TilePalette {

    private volatile TileDefinition[] definitions = new TileDefinition[0];

    public void rebuild(TileIndex tileIndex, DefinitionRepository<TileDefinition> repo) {
        Objects.requireNonNull(tileIndex, "tileIndex cannot be null");
        Objects.requireNonNull(repo, "repo cannot be null");

        TileDefinition[] newArray = new TileDefinition[tileIndex.size()];
        for (short i = 0; i < tileIndex.size(); i++) {
    //        String key = tileIndex.getStringId(i);
    //        newArray[i] = repo.getRequired(key);
        }
        this.definitions = newArray;
    }

    public TileDefinition get(short tileId) {
        return definitions[tileId];
    }

    public int size() {
        return definitions.length;
    }
}