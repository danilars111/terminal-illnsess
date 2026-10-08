package io.github.danilars111.server.domain.component;

import io.github.danilars111.server.domain.component.FootprintComponent.Rotation;
import io.github.danilars111.server.domain.component.FootprintComponent.TileOffset;
import io.github.danilars111.server.domain.world.records.TileCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FootprintComponentTest {

    @Nested
    @DisplayName("Validation and Immutability")
    class ValidationTests {

        @Test
        @DisplayName("Rejects null or empty offset sets")
        void rejectsNullOrEmptyOffsets() {
            assertThrows(IllegalArgumentException.class, () -> new FootprintComponent(null));
            assertThrows(IllegalArgumentException.class, () -> new FootprintComponent(Set.of()));
        }

        @Test
        @DisplayName("Underlying offset set is immutable")
        void offsetsAreImmutable() {
            FootprintComponent footprint = FootprintComponent.singleTile();
            assertThrows(UnsupportedOperationException.class, () ->
                    footprint.relativeOffsets().add(new TileOffset(1, 1))
            );
        }

        @Test
        @DisplayName("Rejects invalid factory arguments")
        void rejectsInvalidFactoryArguments() {
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.rectangle(0, 5));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.rectangle(5, -1));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.centeredBox(-1));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.plus(-1));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.plus(2, 0));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.lShape(0, 2));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.lShape(2, 2, 3)); // thickness > dimension
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.tShape(0, 2));
            assertThrows(IllegalArgumentException.class, () -> FootprintComponent.circle(-1));
        }
    }

    @Nested
    @DisplayName("Single-Tile Footprints")
    class SingleTileTests {

        @Test
        @DisplayName("singleTile factory creates a 1x1 footprint at offset (0, 0)")
        void singleTileFactoryReturnsZeroOffset() {
            FootprintComponent footprint = FootprintComponent.singleTile();

            assertEquals(1, footprint.relativeOffsets().size());
            assertTrue(footprint.relativeOffsets().contains(new TileOffset(0, 0)));

            List<TileCoord> occupied = footprint.getOccupiedCoords(5, 10);
            assertEquals(1, occupied.size());
            assertEquals(new TileCoord(5, 10), occupied.getFirst());
        }

        @Test
        @DisplayName("Translates single-tile footprint across negative coordinates")
        void singleTileHandlesNegativeOrigin() {
            FootprintComponent footprint = FootprintComponent.singleTile();
            List<TileCoord> occupied = footprint.getOccupiedCoords(-3, -7);

            assertEquals(List.of(new TileCoord(-3, -7)), occupied);
        }
    }

    @Nested
    @DisplayName("Rectangular and Centered Box Footprints")
    class RectangleTests {

        @Test
        @DisplayName("rectangle(1, 1) returns singleTile equivalent")
        void rectangle1x1BehavesLikeSingleTile() {
            FootprintComponent footprint = FootprintComponent.rectangle(1, 1);
            assertEquals(Set.of(new TileOffset(0, 0)), footprint.relativeOffsets());
        }

        @Test
        @DisplayName("rectangle(2, 2) generates 4 offsets spanning (0,0) to (1,1)")
        void rectangle2x2GeneratesCorrectOffsets() {
            FootprintComponent footprint = FootprintComponent.rectangle(2, 2);

            assertEquals(4, footprint.relativeOffsets().size());
            assertTrue(footprint.relativeOffsets().containsAll(Set.of(
                    new TileOffset(0, 0),
                    new TileOffset(1, 0),
                    new TileOffset(0, 1),
                    new TileOffset(1, 1)
            )));

            List<TileCoord> occupied = footprint.getOccupiedCoords(10, 20);
            assertEquals(4, occupied.size());
            assertTrue(occupied.containsAll(List.of(
                    new TileCoord(10, 20),
                    new TileCoord(11, 20),
                    new TileCoord(10, 21),
                    new TileCoord(11, 21)
            )));
        }

        @Test
        @DisplayName("rectangle(3, 1) generates a 1-tall, 3-wide horizontal line")
        void asymmetricRectangleGeneratesLinearStrip() {
            FootprintComponent footprint = FootprintComponent.rectangle(3, 1);
            assertEquals(3, footprint.relativeOffsets().size());

            List<TileCoord> occupied = footprint.getOccupiedCoords(0, 0);
            assertEquals(List.of(
                    new TileCoord(0, 0),
                    new TileCoord(1, 0),
                    new TileCoord(2, 0)
            ), occupied);
        }

        @Test
        @DisplayName("centeredBox(0) returns singleTile equivalent")
        void centeredBoxZeroReturnsSingleTile() {
            FootprintComponent box = FootprintComponent.centeredBox(0);
            assertEquals(Set.of(new TileOffset(0, 0)), box.relativeOffsets());
        }

        @Test
        @DisplayName("centeredBox(1) generates a 3x3 footprint (9 tiles centered at 0,0)")
        void centeredBoxGeneratesCorrectOffsets() {
            FootprintComponent box = FootprintComponent.centeredBox(1);
            assertEquals(9, box.relativeOffsets().size());

            List<TileCoord> occupied = box.getOccupiedCoords(0, 0);
            assertTrue(occupied.contains(new TileCoord(-1, -1)));
            assertTrue(occupied.contains(new TileCoord(1, 1)));
            assertTrue(occupied.contains(new TileCoord(0, 0)));
        }
    }

    @Nested
    @DisplayName("Plus / Cross Footprints")
    class PlusTests {

        @Test
        @DisplayName("plus(0) returns singleTile equivalent")
        void plusZeroReturnsSingleTile() {
            FootprintComponent plus = FootprintComponent.plus(0);
            assertEquals(Set.of(new TileOffset(0, 0)), plus.relativeOffsets());
        }

        @Test
        @DisplayName("plus(1) generates a 5-tile cardinal cross with thickness 1")
        void plusGeneratesCardinalArms() {
            FootprintComponent plus = FootprintComponent.plus(1);
            assertEquals(5, plus.relativeOffsets().size());

            List<TileCoord> occupied = plus.getOccupiedCoords(10, 10);
            assertTrue(occupied.containsAll(List.of(
                    new TileCoord(10, 10), // Center
                    new TileCoord(10, 11), // North
                    new TileCoord(10, 9),  // South
                    new TileCoord(9, 10),  // West
                    new TileCoord(11, 10)  // East
            )));
            assertFalse(occupied.contains(new TileCoord(11, 11))); // Corner remains clear
        }

        @Test
        @DisplayName("plus(1, 2) accounts for beam thickness")
        void plusWithThicknessExpandsCore() {
            FootprintComponent thickPlus = FootprintComponent.plus(1, 2);
            // Arm length 1, thickness 2 means the core is 2x2 and each side extends 1 step
            assertTrue(thickPlus.relativeOffsets().size() > 5);
        }
    }

    @Nested
    @DisplayName("T-Shape and L-Shape Footprints")
    class BranchedShapeTests {

        @Test
        @DisplayName("tShape(1, 2) generates crossbar and downward stem")
        void tShapeGeneratesCorrectOffsets() {
            FootprintComponent tShape = FootprintComponent.tShape(1, 2);
            // Crossbar: (-1, 0), (0, 0), (1, 0); Stem: (0, -1), (0, -2) -> 5 tiles
            assertEquals(5, tShape.relativeOffsets().size());

            List<TileCoord> coords = tShape.getOccupiedCoords(5, 5);
            assertTrue(coords.containsAll(List.of(
                    new TileCoord(5, 5),
                    new TileCoord(4, 5),
                    new TileCoord(6, 5),
                    new TileCoord(5, 4),
                    new TileCoord(5, 3)
            )));
        }

        @Test
        @DisplayName("lShape(3, 2) generates horizontal base with vertical arm")
        void lShapeGeneratesCorrectOffsets() {
            FootprintComponent lShape = FootprintComponent.lShape(3, 2);
            // Horizontal: (0,0), (1,0), (2,0); Vertical: (0,1) -> 4 tiles
            assertEquals(4, lShape.relativeOffsets().size());

            List<TileCoord> coords = lShape.getOccupiedCoords(0, 0);
            assertTrue(coords.containsAll(List.of(
                    new TileCoord(0, 0),
                    new TileCoord(1, 0),
                    new TileCoord(2, 0),
                    new TileCoord(0, 1)
            )));
            assertFalse(coords.contains(new TileCoord(1, 1)));
        }

        @Test
        @DisplayName("lShape with thickness builds wider arms")
        void lShapeWithThicknessCoversArea() {
            FootprintComponent thickL = FootprintComponent.lShape(3, 3, 2);
            // Horizontal leg: [0..2] x [0..1] = 6 tiles
            // Vertical leg: [0..1] x [0..2] = 6 tiles
            // Union removes 2x2 overlapping corner (4 tiles) -> 6 + 6 - 4 = 8 tiles
            assertEquals(8, thickL.relativeOffsets().size());
        }
    }

    @Nested
    @DisplayName("Circle / Radial Footprints")
    class CircleTests {

        @Test
        @DisplayName("circle(0) returns singleTile equivalent")
        void circleZeroReturnsSingleTile() {
            FootprintComponent circle = FootprintComponent.circle(0);
            assertEquals(Set.of(new TileOffset(0, 0)), circle.relativeOffsets());
        }

        @Test
        @DisplayName("circle(1) produces a 5-tile disk omitting diagonal corners")
        void circleOmitsDiagonalsAtRadiusOne() {
            FootprintComponent circle = FootprintComponent.circle(1);
            // At r=1, (dx^2 + dy^2 <= 1): (0,0), (0,1), (0,-1), (1,0), (-1,0)
            assertEquals(5, circle.relativeOffsets().size());
            assertFalse(circle.relativeOffsets().contains(new TileOffset(1, 1)));
            assertFalse(circle.relativeOffsets().contains(new TileOffset(-1, -1)));
        }

        @Test
        @DisplayName("circle(2) adheres to squared Euclidean radius formula")
        void circleRadiusTwoComputesAccurately() {
            FootprintComponent circle = FootprintComponent.circle(2);
            // dx^2 + dy^2 <= 4. (0,0)=0, card 1=1, card 2=4, diag (1,1)=2 (all <= 4)
            // Points (2,1) have 2^2 + 1^2 = 5 > 4 (omitted)
            assertTrue(circle.relativeOffsets().contains(new TileOffset(1, 1)));
            assertFalse(circle.relativeOffsets().contains(new TileOffset(2, 1)));
            assertFalse(circle.relativeOffsets().contains(new TileOffset(2, 2)));
        }
    }

    @Nested
    @DisplayName("Rotation and Normalization")
    class RotationTests {

        @Test
        @DisplayName("rotateClockwise rotates (x, y) to (y, -x)")
        void rotatesClockwiseCorrectly() {
            FootprintComponent line = FootprintComponent.irregular(Set.of(
                    new TileOffset(0, 0),
                    new TileOffset(0, 1)
            ));

            FootprintComponent rotated = line.rotateClockwise();
            assertEquals(Set.of(
                    new TileOffset(0, 0),
                    new TileOffset(1, 0)
            ), rotated.relativeOffsets());
        }

        @Test
        @DisplayName("rotateCounterClockwise rotates (x, y) to (-y, x)")
        void rotatesCounterClockwiseCorrectly() {
            FootprintComponent line = FootprintComponent.irregular(Set.of(
                    new TileOffset(0, 0),
                    new TileOffset(0, 1)
            ));

            FootprintComponent rotated = line.rotateCounterClockwise();
            assertEquals(Set.of(
                    new TileOffset(0, 0),
                    new TileOffset(-1, 0)
            ), rotated.relativeOffsets());
        }

        @Test
        @DisplayName("rotated(Rotation) matches explicit directional steps")
        void rotatedEnumMatchesStepTransformations() {
            FootprintComponent original = FootprintComponent.lShape(3, 2);

            assertEquals(original.relativeOffsets(), original.rotated(Rotation.NONE).relativeOffsets());
            assertEquals(original.rotateClockwise().relativeOffsets(), original.rotated(Rotation.DEG_90).relativeOffsets());
            assertEquals(original.rotateClockwise().rotateClockwise().relativeOffsets(), original.rotated(Rotation.DEG_180).relativeOffsets());
            assertEquals(original.rotateCounterClockwise().relativeOffsets(), original.rotated(Rotation.DEG_270).relativeOffsets());
        }

        @Test
        @DisplayName("Full 360-degree rotation returns to original layout")
        void fullRotationCyclesBackToStart() {
            FootprintComponent shape = FootprintComponent.tShape(2, 3);
            FootprintComponent rotatedFull = shape
                    .rotateClockwise()
                    .rotateClockwise()
                    .rotateClockwise()
                    .rotateClockwise();

            assertEquals(shape.relativeOffsets(), rotatedFull.relativeOffsets());
        }

        @Test
        @DisplayName("normalized() shifts all offsets so minimum bounds start at (0, 0)")
        void normalizesNegativeBoundsToOrigin() {
            FootprintComponent cross = FootprintComponent.plus(1); // Spans [-1, 1] in both axes
            FootprintComponent normalized = cross.normalized();

            int minX = normalized.relativeOffsets().stream().mapToInt(TileOffset::dx).min().orElseThrow();
            int minY = normalized.relativeOffsets().stream().mapToInt(TileOffset::dy).min().orElseThrow();

            assertEquals(0, minX);
            assertEquals(0, minY);
            assertEquals(5, normalized.relativeOffsets().size());
            assertTrue(normalized.relativeOffsets().contains(new TileOffset(1, 1))); // Previous center (0,0) shifted to (1,1)
        }
    }
}