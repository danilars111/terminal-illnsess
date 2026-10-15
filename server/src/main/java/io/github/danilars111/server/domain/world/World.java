package io.github.danilars111.server.domain.world;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class World {

    private final Map<String, LevelGrid> levels = new HashMap<>();
    private final TilePalette tilePalette;
    private final TileIndex tileIndex;

    public World(TilePalette tilePalette, TileIndex tileIndex) {
        this.tilePalette = Objects.requireNonNull(tilePalette);
        this.tileIndex = Objects.requireNonNull(tileIndex);
    }

    public void registerLevel(LevelGrid level) {
        Objects.requireNonNull(level);
        if (levels.containsKey(level.getId())) {
            throw new IllegalStateException("Level with id " + level.getId() + " already exists");
        }
        levels.put(level.getId(), level);
    }

    public Optional<LevelGrid> getLevel(String id) {
        return Optional.ofNullable(levels.get(id));
    }

    public LevelGrid getRequiredLevel(String levelId) {
        LevelGrid grid = levels.get(levelId);
        if (grid == null) {
            throw new IllegalArgumentException("LevelGrid not registered in World: " + levelId);
        }
        return grid;
    }

    public boolean hasLevel(String id) {
        return levels.containsKey(id);
    }

    public Collection<LevelGrid> getAllLevels() {
        return Collections.unmodifiableCollection(levels.values());
    }

    public boolean isWithinBounds(String levelId, int x, int y) {
        LevelGrid level = levels.get(levelId);
        return level != null && level.isWithinBounds(x, y);
    }

    public boolean isWithinBounds(String levelId, TileCoord tileCoord) {
        return isWithinBounds(levelId, tileCoord.getX(), tileCoord.getY());
    }

    public short getTileId(String levelId, int x, int y) {
        return getRequiredLevel(levelId).getTileId(x, y);
    }

    public short getTileId(String levelId, TileCoord tileCoord) {
        return getTileId(levelId, tileCoord.getX(), tileCoord.getY());
    }

    public TileDefinition getTileDefinition(String levelId, int x, int y) {
        short tileId = getTileId(levelId, x, y);
        return tilePalette.get(tileId);
    }

    public TileDefinition getTileDefinition(String levelId, TileCoord coord) {
        return getTileDefinition(levelId, coord.getX(), coord.getY());
    }

    public TilePalette getTilePalette() {
        return tilePalette;
    }

    public TileIndex getTileIndex() {
        return tileIndex;
    }
}
