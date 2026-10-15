package io.github.danilars111.server.domain.service.indices;

import java.util.*;

public class TileIndex {

    public static final short UNKNOWN_TILE_ID = -1;

    private final Map<String, Short> stringToId = new HashMap<>();
    private final List<String> idToString = new ArrayList<>();

    public synchronized short register(String stringId) {
        Objects.requireNonNull(stringId, "Tile String ID cannot be null");
        if (stringToId.containsKey(stringId)) {
            return stringToId.get(stringId);
        }

        short assignedId = (short) idToString.size();
        stringToId.put(stringId, assignedId);
        idToString.add(stringId);
        return assignedId;
    }

    public short getId(String stringId) {
        return stringToId.getOrDefault(stringId, UNKNOWN_TILE_ID);
    }

    public String getStringId(short tileId) {
        if (tileId < 0 || tileId >= idToString.size()) {
            throw new IndexOutOfBoundsException("Tile ID out of bounds: " + tileId);
        }
        return idToString.get(tileId);
    }

    public boolean contains(String stringId) {
        return stringToId.containsKey(stringId);
    }

    public boolean contains(short tileId) {
        return tileId >= 0 && tileId < idToString.size();
    }

    public int size() {
        return idToString.size();
    }
}