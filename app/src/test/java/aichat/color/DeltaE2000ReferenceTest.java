package aichat.color;

import aichat.model.ColorPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reference-based tests for CIEDE2000 (DeltaE 2000) color difference formula.
 * 
 * These tests use known reference values from:
 * 1. Sharma, Wencheng & Dalal (2005) - "The CIEDE2000 Color-Difference Formula"
 *    https://www.ece.rochester.edu/~gsharma/ciede2000/
 * 2. Bruce Lindbloom's Color Calculator
 *    http://www.brucelindbloom.com/
 * 
 * The test data covers:
 * - Standard test pairs from the original CIEDE2000 paper
 * - Edge cases (neutral grays, high chroma, low chroma)
 * - Hue quadrant transitions
 */
@DisplayName("CIEDE2000 Reference Tests")
class DeltaE2000ReferenceTest {

    // Tolerance for CIEDE2000 calculations
    // Original formula specifies precision to 4 decimal places
    private static final double DELTA_E_TOLERANCE = 0.01;

    @Nested
    @DisplayName("Sharma et al. Reference Values")
    class SharmaReferenceTests {

        /**
         * Test pairs from Sharma, Wencheng & Dalal (2005) paper.
         * Table: Test data for CIEDE2000 implementation verification.
         * 
         * Format: L1, a1, b1, L2, a2, b2, expected DeltaE2000
         */
        @ParameterizedTest(name = "Pair {index}: LAB({0},{1},{2}) vs LAB({3},{4},{5}) = {6}")
        @CsvSource({
            // Pair 1: Low chroma, similar lightness
            "50.0, 2.6772, -79.7751, 50.0, 0.0, -82.7485, 2.0425",
            // Pair 2: Low chroma
            "50.0, 3.1571, -77.2803, 50.0, 0.0, -82.7485, 2.8615",
            // Pair 3: Low chroma
            "50.0, 2.8361, -74.0200, 50.0, 0.0, -82.7485, 3.4412",
            // Pair 4: High chroma near neutral
            "50.0, -1.3802, -84.2814, 50.0, 0.0, -82.7485, 1.0000",
            // Pair 5: High chroma near neutral
            "50.0, -1.1848, -84.8006, 50.0, 0.0, -82.7485, 1.0000",
            // Pair 6: High chroma near neutral
            "50.0, -0.9009, -85.5211, 50.0, 0.0, -82.7485, 1.0000",
            // Pair 7: Bright colors
            "50.0, 0.0, 0.0, 50.0, -1.0, 2.0, 2.3669",
            // Pair 8: Near gray
            "50.0, -1.0, 2.0, 50.0, 0.0, 0.0, 2.3669",
            // Pair 9: 
            "50.0, 2.49, -0.001, 50.0, -2.49, 0.0009, 7.1792",
            // Pair 10:
            "50.0, 2.49, -0.001, 50.0, -2.49, 0.001, 7.1792",
            // Pair 11:
            "50.0, 2.49, -0.001, 50.0, -2.49, 0.0011, 7.2195",
            // Pair 12: 
            "50.0, 2.49, -0.001, 50.0, -2.49, 0.0012, 7.2195",
            // Pair 13:
            "50.0, -0.001, 2.49, 50.0, 0.0009, -2.49, 4.8045",
            // Pair 14:
            "50.0, -0.001, 2.49, 50.0, 0.001, -2.49, 4.8045",
            // Pair 15:
            "50.0, -0.001, 2.49, 50.0, 0.0011, -2.49, 4.7461",
            // Pair 16: Large color difference
            "50.0, 2.5, 0.0, 73.0, 25.0, -18.0, 27.1492",
            // Pair 17: Large color difference
            "50.0, 2.5, 0.0, 61.0, -5.0, 29.0, 22.8977",
            // Pair 18: Large color difference
            "50.0, 2.5, 0.0, 56.0, -27.0, -3.0, 31.9030",
            // Pair 19: Very large difference
            "50.0, 2.5, 0.0, 58.0, 24.0, 15.0, 19.4535",
            // Pair 20: Lightness difference
            "50.0, 2.5, 0.0, 50.0, 3.1736, 0.5854, 1.0000",
            // Pair 21: Lightness difference
            "50.0, 2.5, 0.0, 50.0, 3.2972, 0.0, 1.0000",
            // Pair 22: Lightness difference
            "50.0, 2.5, 0.0, 50.0, 1.8634, 0.5757, 1.0000",
            // Pair 23: Lightness difference
            "50.0, 2.5, 0.0, 50.0, 3.2592, 0.335, 1.0000",
            // Pair 24: Different quadrants
            "60.2574, -34.0099, 36.2677, 60.4626, -34.1751, 39.4387, 1.2644",
            // Pair 25: Different quadrants
            "63.0109, -31.0961, -5.8663, 62.8187, -29.7946, -4.0864, 1.2630",
            // Pair 26: Near-neutral
            "61.2901, 3.7196, -5.3901, 61.4292, 2.248, -4.962, 1.8731",
            // Pair 27: High chroma
            "35.0831, -44.1164, 3.7933, 35.0232, -40.0716, 1.5901, 1.8645",
            // Pair 28: High chroma blue-purple
            "22.7233, 20.0904, -46.694, 23.0331, 14.973, -42.5619, 2.0373",
            // Pair 29: Medium colors
            "36.4612, 47.858, 18.3852, 36.2715, 50.5065, 21.2231, 1.4146",
            // Pair 30: Red-orange region
            "90.8027, -2.0831, 1.441, 91.1528, -1.6435, 0.0447, 1.4441",
            // Pair 31: Near white
            "90.9257, -0.5406, -0.9208, 88.6381, -0.8985, -0.7239, 1.5381",
            // Pair 32: Saturated
            "6.7747, -0.2908, -2.4247, 5.8714, -0.0985, -2.2286, 0.6377",
            // Pair 33: Dark colors
            "2.0776, 0.0795, -1.135, 0.9033, -0.0636, -0.5514, 0.9082",
            // Pair 34: Very dark
            "0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0"
        })
        void testSharmaReferencePairs(double l1, double a1, double b1,
                                       double l2, double a2, double b2,
                                       double expectedDeltaE) {
            ColorPoint lab1 = new ColorPoint(l1, a1, b1);
            ColorPoint lab2 = new ColorPoint(l2, a2, b2);
            
            double actualDeltaE = ColorSpaceConverter.deltaE2000(lab1, lab2);
            
            assertEquals(expectedDeltaE, actualDeltaE, DELTA_E_TOLERANCE,
                String.format("DeltaE mismatch for LAB(%.4f,%.4f,%.4f) vs LAB(%.4f,%.4f,%.4f): " +
                    "expected %.4f, got %.4f", l1, a1, b1, l2, a2, b2, expectedDeltaE, actualDeltaE));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Identical colors should have DeltaE = 0")
        void testIdenticalColors() {
            double[][] testCases = {
                {0, 0, 0},           // Black
                {100, 0, 0},         // White
                {50, 0, 0},          // Mid-gray
                {50, 80, 67},        // Saturated red
                {50, -50, 50},       // Green-yellow
                {50, 25, -80}        // Blue
            };
            
            for (double[] lab : testCases) {
                ColorPoint color = new ColorPoint(lab[0], lab[1], lab[2]);
                double deltaE = ColorSpaceConverter.deltaE2000(color, color);
                
                assertEquals(0.0, deltaE, 0.0001,
                    "Identical colors should have DeltaE=0: " + color);
            }
        }

        @Test
        @DisplayName("DeltaE is symmetric: deltaE(a,b) = deltaE(b,a)")
        void testSymmetry() {
            ColorPoint[][] pairs = {
                {new ColorPoint(50, 0, 0), new ColorPoint(60, 10, 10)},
                {new ColorPoint(25, 50, -30), new ColorPoint(75, -20, 40)},
                {new ColorPoint(0, 0, 0), new ColorPoint(100, 0, 0)},
                {new ColorPoint(50, 80, 67), new ColorPoint(50, -50, -50)}
            };
            
            for (ColorPoint[] pair : pairs) {
                double deltaE12 = ColorSpaceConverter.deltaE2000(pair[0], pair[1]);
                double deltaE21 = ColorSpaceConverter.deltaE2000(pair[1], pair[0]);
                
                assertEquals(deltaE12, deltaE21, 0.0001,
                    "DeltaE should be symmetric for " + pair[0] + " and " + pair[1]);
            }
        }

        @Test
        @DisplayName("Neutral grays (a*=0, b*=0) should have valid DeltaE")
        void testNeutralGrays() {
            // Pure gray to pure gray - only lightness differs
            ColorPoint gray1 = new ColorPoint(30, 0, 0);
            ColorPoint gray2 = new ColorPoint(70, 0, 0);
            
            double deltaE = ColorSpaceConverter.deltaE2000(gray1, gray2);
            
            assertTrue(deltaE > 0, "Different grays should have positive DeltaE");
            // For neutral grays, DeltaE is dominated by lightness difference
            // L* difference of 40 should give significant deltaE
            assertTrue(deltaE > 10 && deltaE < 50,
                "DeltaE for grays L*=30 and L*=70 should be between 10-50, was " + deltaE);
        }

        @Test
        @DisplayName("High chroma colors should be distinguishable")
        void testHighChroma() {
            // Two saturated colors with same L* but opposite hues
            ColorPoint saturatedRed = new ColorPoint(50, 80, 60);
            ColorPoint saturatedCyan = new ColorPoint(50, -40, -40);
            
            double deltaE = ColorSpaceConverter.deltaE2000(saturatedRed, saturatedCyan);
            
            // Should be a large difference
            assertTrue(deltaE > 50,
                "Opposite hue saturated colors should have large DeltaE, was " + deltaE);
        }

        @Test
        @DisplayName("Very similar colors should have small DeltaE")
        void testVerySimilarColors() {
            ColorPoint color1 = new ColorPoint(50, 25, 25);
            ColorPoint color2 = new ColorPoint(50.1, 25.1, 25.1);
            
            double deltaE = ColorSpaceConverter.deltaE2000(color1, color2);
            
            assertTrue(deltaE < 1.0,
                "Very similar colors should have DeltaE < 1, was " + deltaE);
        }
    }

    @Nested
    @DisplayName("Just Noticeable Difference (JND)")
    class JndTests {
        
        // In CIEDE2000, JND is approximately 1.0
        private static final double JND = 1.0;

        @Test
        @DisplayName("Colors with DeltaE < 1 are perceptually similar")
        void testBelowJnd() {
            // These should all be below JND
            ColorPoint base = new ColorPoint(50, 20, 10);
            ColorPoint similar = new ColorPoint(50.3, 20.2, 10.1);
            
            double deltaE = ColorSpaceConverter.deltaE2000(base, similar);
            
            assertTrue(deltaE < JND,
                "Colors should be below JND threshold: " + deltaE);
        }

        @Test
        @DisplayName("Colors with DeltaE > 2 are clearly distinguishable")
        void testAboveJnd() {
            ColorPoint base = new ColorPoint(50, 20, 10);
            ColorPoint different = new ColorPoint(55, 25, 15);
            
            double deltaE = ColorSpaceConverter.deltaE2000(base, different);
            
            assertTrue(deltaE > 2,
                "Colors should be clearly distinguishable: " + deltaE);
        }
    }

    @Nested  
    @DisplayName("Perceptual Uniformity")
    class PerceptualUniformityTests {

        @Test
        @DisplayName("Similar perceptual differences should have similar DeltaE")
        void testUniformity() {
            // Moving same "distance" in different parts of color space
            // should give roughly similar DeltaE values
            
            // Change in lightness
            ColorPoint darkGray = new ColorPoint(20, 0, 0);
            ColorPoint lightGray = new ColorPoint(30, 0, 0);
            double deltaL = ColorSpaceConverter.deltaE2000(darkGray, lightGray);
            
            ColorPoint midGray = new ColorPoint(50, 0, 0);
            ColorPoint brightGray = new ColorPoint(60, 0, 0);
            double deltaL2 = ColorSpaceConverter.deltaE2000(midGray, brightGray);
            
            // Both should be in similar range (not perfectly uniform due to CIEDE2000 corrections)
            double ratio = deltaL / deltaL2;
            assertTrue(ratio > 0.5 && ratio < 2.0,
                "Similar L* changes should give similar DeltaE: " + deltaL + " vs " + deltaL2);
        }
    }
}
