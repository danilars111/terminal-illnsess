package io.github.danilars111.server.domain.service.indices;

import io.github.danilars111.contracts.definition.world.TileDefinition;

import java.util.*;

/**
 * Registry and palette index for tiles.
 * Maps human-readable String IDs to contiguous short/integer palette IDs and TileDefinition objects.
 */
public class TileIndex {

    public static final short UNKNOWN_TILE_ID = -1;

    private final Map<String, Short> stringToId = new HashMap<>();
    private final List<TileDefinition> idToDefinition = new ArrayList<>();
    private final Map<String, TileDefinition> stringToDefinition = new HashMap<>();

    /**
     * Registers a tile definition into the palette.
     *
     * @param definition the tile definition to register
     * @return the assigned compact numeric palette ID
     */
    public synchronized short register(TileDefinition definition) {
        Objects.requireNonNull(definition, "TileDefinition cannot be null");
        String key = definition.id();

        if (stringToId.containsKey(key)) {
            short existingId = stringToId.get(key);
            idToDefinition.set(existingId, definition);
            stringToDefinition.put(key, definition);
            return existingId;
        }

        short assignedId = (short) idToDefinition.size();
        stringToId.put(key, assignedId);
        idToDefinition.add(definition);
        stringToDefinition.put(key, definition);
        return assignedId;
    }

    /**
     * Fast O(1) array lookup by compact numeric ID.
     */
    public TileDefinition get(short tileId) {
        if (tileId < 0 || tileId >= idToDefinition.size()) {
            throw new IndexOutOfBoundsException("Tile ID out of bounds: " + tileId);
        }
        return idToDefinition.get(tileId);
    }

    /**
     * Look up definition by human-readable string ID.
     */
    public Optional<TileDefinition> get(String stringId) {
        return Optional.ofNullable(stringToDefinition.get(stringId));
    }

    /**
     * Gets the compact numeric palette ID for a string ID.
     */
    public short getId(String stringId) {
        return stringToId.getOrDefault(stringId, UNKNOWN_TILE_ID);
    }

    public boolean contains(String stringId) {
        return stringToId.containsKey(stringId);
    }

    public boolean contains(short tileId) {
        return tileId >= 0 && tileId < idToDefinition.size();
    }

    public int size() {
        return idToDefinition.size();
    }

    public synchronized void clear() {
        stringToId.clear();
        idToDefinition.clear();
        stringToDefinition.clear();
    }
}