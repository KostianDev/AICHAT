package aichat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColorPoint Tests")
class ColorPointTest {

    @Nested
    @DisplayName("Construction and Basic Operations")
    class ConstructionTests {

        @Test
        @DisplayName("Should create point with correct components")
        void shouldCreatePointWithCorrectComponents() {
            ColorPoint point = new ColorPoint(100.5, 200.3, 50.7);
            
            assertEquals(100.5, point.c1(), 0.001);
            assertEquals(200.3, point.c2(), 0.001);
            assertEquals(50.7, point.c3(), 0.001);
        }

        @Test
        @DisplayName("Should create from RGB integer")
        void shouldCreateFromRgbInteger() {
            int rgb = 0xFF8040;  // R=255, G=128, B=64
            ColorPoint point = ColorPoint.fromRGB(rgb);
            
            assertEquals(255, point.c1(), 0.1);
            assertEquals(128, point.c2(), 0.1);
            assertEquals(64, point.c3(), 0.1);
        }

        @Test
        @DisplayName("Should convert to RGB integer")
        void shouldConvertToRgbInteger() {
            ColorPoint point = new ColorPoint(255, 128, 64);
            int rgb = point.toRGB();
            
            assertEquals(255, (rgb >> 16) & 0xFF);  // R
            assertEquals(128, (rgb >> 8) & 0xFF);   // G
            assertEquals(64, rgb & 0xFF);           // B
        }

        @Test
        @DisplayName("RGB round-trip should preserve values")
        void rgbRoundTripShouldPreserveValues() {
            int original = 0xABCDEF;
            ColorPoint point = ColorPoint.fromRGB(original);
            int result = point.toRGB();
            
            assertEquals(original, result);
        }
    }

    @Nested
    @DisplayName("Distance Calculations")
    class DistanceTests {

        @Test
        @DisplayName("Distance to self should be zero")
        void distanceToSelfShouldBeZero() {
            ColorPoint point = new ColorPoint(100, 150, 200);
            
            assertEquals(0, point.distanceTo(point), 0.001);
            assertEquals(0, point.distanceSquaredTo(point), 0.001);
        }

        @Test
        @DisplayName("Should calculate Euclidean distance correctly")
        void shouldCalculateEuclideanDistance() {
            ColorPoint p1 = new ColorPoint(0, 0, 0);
            ColorPoint p2 = new ColorPoint(3, 4, 0);
            
            assertEquals(5.0, p1.distanceTo(p2), 0.001);  // 3-4-5 triangle
        }

        @Test
        @DisplayName("Should calculate squared distance correctly")
        void shouldCalculateSquaredDistance() {
            ColorPoint p1 = new ColorPoint(0, 0, 0);
            ColorPoint p2 = new ColorPoint(3, 4, 0);
            
            assertEquals(25.0, p1.distanceSquaredTo(p2), 0.001);
        }

        @Test
        @DisplayName("Distance should be symmetric")
        void distanceShouldBeSymmetric() {
            ColorPoint p1 = new ColorPoint(100, 50, 200);
            ColorPoint p2 = new ColorPoint(150, 100, 100);
            
            assertEquals(p1.distanceTo(p2), p2.distanceTo(p1), 0.001);
        }
    }

    @Nested
    @DisplayName("Arithmetic Operations")
    class ArithmeticTests {

        @Test
        @DisplayName("Should add points correctly")
        void shouldAddPointsCorrectly() {
            ColorPoint p1 = new ColorPoint(10, 20, 30);
            ColorPoint p2 = new ColorPoint(5, 10, 15);
            
            ColorPoint sum = p1.add(p2);
            
            assertEquals(15, sum.c1(), 0.001);
            assertEquals(30, sum.c2(), 0.001);
            assertEquals(45, sum.c3(), 0.001);
        }

        @Test
        @DisplayName("Should divide by scalar correctly")
        void shouldDivideByScalarCorrectly() {
            ColorPoint point = new ColorPoint(100, 200, 300);
            
            ColorPoint divided = point.divide(2);
            
            assertEquals(50, divided.c1(), 0.001);
            assertEquals(100, divided.c2(), 0.001);
            assertEquals(150, divided.c3(), 0.001);
        }
    }

    @Nested
    @DisplayName("Hex String Conversion")
    class HexStringTests {

        @Test
        @DisplayName("Should convert to uppercase hex string")
        void shouldConvertToUppercaseHex() {
            ColorPoint point = new ColorPoint(255, 128, 0);
            
            assertEquals("#FF8000", point.toHexString());
        }

        @Test
        @DisplayName("Should pad with zeros")
        void shouldPadWithZeros() {
            ColorPoint point = new ColorPoint(1, 2, 3);
            
            assertEquals("#010203", point.toHexString());
        }

        @Test
        @DisplayName("Should clamp values for hex conversion")
        void shouldClampValuesForHex() {
            ColorPoint point = new ColorPoint(-10, 128, 300);
            String hex = point.toHexString();
            
            assertEquals("#0080FF", hex);  // Clamped to 0-255
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero values")
        void shouldHandleZeroValues() {
            ColorPoint black = new ColorPoint(0, 0, 0);
            
            assertEquals(0, black.toRGB());
            assertEquals("#000000", black.toHexString());
        }

        @Test
        @DisplayName("Should handle max values")
        void shouldHandleMaxValues() {
            ColorPoint white = new ColorPoint(255, 255, 255);
            
            assertEquals(0xFFFFFF, white.toRGB());
            assertEquals("#FFFFFF", white.toHexString());
        }

        @Test
        @DisplayName("Should handle fractional values")
        void shouldHandleFractionalValues() {
            ColorPoint point = new ColorPoint(127.5, 127.5, 127.5);
            int rgb = point.toRGB();
            
            // Should round to 128
            assertEquals(128, (rgb >> 16) & 0xFF);
            assertEquals(128, (rgb >> 8) & 0xFF);
            assertEquals(128, rgb & 0xFF);
        }
    }
}
