package io.github.danilars111.server.domain.component;

import io.github.danilars111.server.domain.component.interfaces.Component;
import io.github.danilars111.server.domain.world.records.TileCoord;

import java.util.*;
import java.util.stream.Collectors;

public record FootprintComponent(Set<TileOffset> relativeOffsets) implements Component {

    public enum Rotation {
        NONE,
        DEG_90,
        DEG_180,
        DEG_270
    }

    public record TileOffset(int dx, int dy) implements Comparable<TileOffset> {
        @Override
        public int compareTo(TileOffset other) {
            int cmpY = Integer.compare(this.dy, other.dy);
            if (cmpY != 0) {
                return cmpY;
            }
            return Integer.compare(this.dx, other.dx);
        }
    }

    public FootprintComponent {
        if (relativeOffsets == null || relativeOffsets.isEmpty()) {
            throw new IllegalArgumentException("Footprint cannot be empty");
        }
        // Always store in canonical row-major order: y ascending, then x ascending
        Set<TileOffset> sorted = relativeOffsets.stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        relativeOffsets = Collections.unmodifiableSet(sorted);
    }

    // --- FACTORY METHODS ---

    public static FootprintComponent singleTile() {
        return new FootprintComponent(Set.of(new TileOffset(0, 0)));
    }

    public static FootprintComponent rectangle(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        if (width == 1 && height == 1) {
            return singleTile();
        }
        Set<TileOffset> offsets = new LinkedHashSet<>(width * height);
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                offsets.add(new TileOffset(dx, dy));
            }
        }
        return new FootprintComponent(offsets);
    }

    public static FootprintComponent centeredBox(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        if (radius == 0) {
            return singleTile();
        }
        int diameter = radius * 2 + 1;
        Set<TileOffset> offsets = new LinkedHashSet<>(diameter * diameter);
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                offsets.add(new TileOffset(dx, dy));
            }
        }
        return new FootprintComponent(offsets);
    }

    public static FootprintComponent plus(int armLength, int thickness) {
        if (armLength < 0 || thickness <= 0) {
            throw new IllegalArgumentException("Arm length must be non-negative and thickness must be positive");
        }
        if (armLength == 0 && thickness == 1) {
            return singleTile();
        }

        Set<TileOffset> offsets = new HashSet<>();
        int halfThickness = thickness / 2;
        int minT = -halfThickness;
        int maxT = minT + thickness - 1;

        int minArm = minT - armLength;
        int maxArm = maxT + armLength;

        // Horizontal beam
        for (int x = minArm; x <= maxArm; x++) {
            for (int y = minT; y <= maxT; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }

        // Vertical beam
        for (int x = minT; x <= maxT; x++) {
            for (int y = minArm; y <= maxArm; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }

        return new FootprintComponent(offsets);
    }

    public static FootprintComponent plus(int armLength) {
        return plus(armLength, 1);
    }

    public static FootprintComponent tShape(int armLength, int stemLength, int thickness) {
        if (armLength <= 0 || stemLength <= 0 || thickness <= 0) {
            throw new IllegalArgumentException("Dimensions and thickness must be positive");
        }

        Set<TileOffset> offsets = new HashSet<>();
        int halfThickness = thickness / 2;
        int minT = -halfThickness;
        int maxT = minT + thickness - 1;

        int minX = minT - armLength;
        int maxX = maxT + armLength;

        // Crossbar
        for (int x = minX; x <= maxX; x++) {
            for (int y = 0; y < thickness; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }

        // Stem extending downwards
        for (int x = minT; x <= maxT; x++) {
            for (int y = -stemLength; y < 0; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }

        return new FootprintComponent(offsets);
    }

    public static FootprintComponent tShape(int armLength, int stemLength) {
        return tShape(armLength, stemLength, 1);
    }

    public static FootprintComponent lShape(int width, int height, int thickness) {
        if (width <= 0 || height <= 0 || thickness <= 0) {
            throw new IllegalArgumentException("Dimensions and thickness must be positive");
        }
        if (thickness > width || thickness > height) {
            throw new IllegalArgumentException("Thickness cannot exceed arm length");
        }

        Set<TileOffset> offsets = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < thickness; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }
        for (int x = 0; x < thickness; x++) {
            for (int y = 0; y < height; y++) {
                offsets.add(new TileOffset(x, y));
            }
        }

        return new FootprintComponent(offsets);
    }

    public static FootprintComponent lShape(int width, int height) {
        return lShape(width, height, 1);
    }

    public static FootprintComponent circle(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        if (radius == 0) {
            return singleTile();
        }

        int radiusSquared = radius * radius;
        Set<TileOffset> offsets = new HashSet<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radiusSquared) {
                    offsets.add(new TileOffset(dx, dy));
                }
            }
        }
        return new FootprintComponent(offsets);
    }

    public static FootprintComponent irregular(Set<TileOffset> offsets) {
        return new FootprintComponent(offsets);
    }

    // --- ROTATION & TRANSFORM ---

    public FootprintComponent rotateClockwise() {
        Set<TileOffset> rotated = new HashSet<>(relativeOffsets.size());
        for (TileOffset offset : relativeOffsets) {
            rotated.add(new TileOffset(offset.dy(), -offset.dx()));
        }
        return new FootprintComponent(rotated);
    }

    public FootprintComponent rotateCounterClockwise() {
        Set<TileOffset> rotated = new HashSet<>(relativeOffsets.size());
        for (TileOffset offset : relativeOffsets) {
            rotated.add(new TileOffset(-offset.dy(), offset.dx()));
        }
        return new FootprintComponent(rotated);
    }

    public FootprintComponent rotated(Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case DEG_90 -> rotateClockwise();
            case DEG_180 -> {
                Set<TileOffset> rotated = new HashSet<>(relativeOffsets.size());
                for (TileOffset offset : relativeOffsets) {
                    rotated.add(new TileOffset(-offset.dx(), -offset.dy()));
                }
                yield new FootprintComponent(rotated);
            }
            case DEG_270 -> rotateCounterClockwise();
        };
    }

    public FootprintComponent normalized() {
        int minX = relativeOffsets.stream().mapToInt(TileOffset::dx).min().orElse(0);
        int minY = relativeOffsets.stream().mapToInt(TileOffset::dy).min().orElse(0);

        if (minX == 0 && minY == 0) {
            return this;
        }

        Set<TileOffset> normalizedOffsets = new HashSet<>(relativeOffsets.size());
        for (TileOffset offset : relativeOffsets) {
            normalizedOffsets.add(new TileOffset(offset.dx() - minX, offset.dy() - minY));
        }
        return new FootprintComponent(normalizedOffsets);
    }

    // --- QUERY / TRANSLATION ---

    public List<TileCoord> getOccupiedCoords(int originX, int originY) {
        List<TileCoord> coords = new ArrayList<>(relativeOffsets.size());
        for (TileOffset offset : relativeOffsets) {
            coords.add(new TileCoord(originX + offset.dx(), originY + offset.dy()));
        }
        return coords;
    }
}