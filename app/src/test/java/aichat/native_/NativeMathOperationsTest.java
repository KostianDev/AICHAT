package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Native Math Operations Tests")
class NativeMathOperationsTest {

    private static NativeLibrary nativeLib;
    private static boolean nativeAvailable;

    @BeforeAll
    static void setup() {
        nativeAvailable = NativeLibrary.isAvailable();
        if (nativeAvailable) {
            nativeLib = NativeLibrary.getInstance();
        }
        System.out.println("Native library available: " + nativeAvailable);
    }

    @Nested
    @DisplayName("RGB to LAB Conversion")
    class RgbToLabTests {
        
        static Stream<Arguments> knownRgbToLabValues() {
            return Stream.of(
                Arguments.of(0, 0, 0, 0.0, 0.0, 0.0, "Black"),
                // White
                Arguments.of(255, 255, 255, 100.0, 0.0, 0.0, "White"),
                // Primary Red
                Arguments.of(255, 0, 0, 53.23, 80.11, 67.22, "Red"),
                // Primary Green
                Arguments.of(0, 255, 0, 87.74, -86.18, 83.18, "Green"),
                // Primary Blue
                Arguments.of(0, 0, 255, 32.30, 79.20, -107.86, "Blue"),
                // Mid Gray (important for gamma correctness)
                Arguments.of(128, 128, 128, 53.59, 0.0, 0.0, "Mid Gray"),
                // Dark Gray (tests low-value gamma branch)
                Arguments.of(10, 10, 10, 2.74, 0.0, 0.0, "Dark Gray"),
                // Secondary colors
                Arguments.of(255, 255, 0, 97.14, -21.55, 94.48, "Yellow"),
                Arguments.of(0, 255, 255, 91.11, -48.09, -14.13, "Cyan"),
                Arguments.of(255, 0, 255, 60.32, 98.25, -60.84, "Magenta"),
                // Quarter tones (important for gamma curve accuracy)
                Arguments.of(64, 64, 64, 27.09, 0.0, 0.0, "Quarter Gray"),
                Arguments.of(191, 191, 191, 77.70, 0.0, 0.0, "Three-Quarter Gray")
            );
        }

        @ParameterizedTest(name = "{6}: RGB({0},{1},{2}) -> LAB({3},{4},{5})")
        @MethodSource("knownRgbToLabValues")
        @DisplayName("Known RGB to LAB values")
        void testKnownRgbToLab(int r, int g, int b, 
                               double expectedL, double expectedA, double expectedB,
                               String colorName) {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] rgb = {r, g, b};
                float[] lab = nativeLib.rgbToLabBatch(arena, rgb);
                
                assertEquals(expectedL, lab[0], 1.0,
                    colorName + " L* mismatch");
                assertEquals(expectedA, lab[1], 1.5,
                    colorName + " a* mismatch");
                assertEquals(expectedB, lab[2], 1.5,
                    colorName + " b* mismatch");
            }
        }

        @Test
        @DisplayName("sRGB linearization threshold (0.04045)")
        void testSrgbLinearizationThreshold() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] rgbBelow = {10, 10, 10};
                float[] rgbAbove = {12, 12, 12};
                
                float[] labBelow = nativeLib.rgbToLabBatch(arena, rgbBelow);
                float[] labAbove = nativeLib.rgbToLabBatch(arena, rgbAbove);
                
                // Both should produce valid results
                assertTrue(labBelow[0] > 0, "Below threshold should have positive L*");
                assertTrue(labAbove[0] > labBelow[0], "Above threshold should have higher L*");
                
                // Neutral grays should have a*≈0 and b*≈0
                assertEquals(0.0, labBelow[1], 0.5, "Gray should have a* ≈ 0");
                assertEquals(0.0, labBelow[2], 0.5, "Gray should have b* ≈ 0");
            }
        }

        @Test
        @DisplayName("LAB f() function threshold (epsilon = 0.008856)")
        void testLabFunctionThreshold() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] veryDark = {1, 1, 1};
                float[] dark = {5, 5, 5};
                
                float[] labVeryDark = nativeLib.rgbToLabBatch(arena, veryDark);
                float[] labDark = nativeLib.rgbToLabBatch(arena, dark);
                
                assertTrue(labVeryDark[0] > 0 && labVeryDark[0] < 5,
                    "Very dark should have small positive L*");
                assertTrue(labDark[0] > labVeryDark[0],
                    "Darker should have lower L*");
            }
        }

        @Test
        @DisplayName("XYZ matrix coefficients sum to correct values")
        void testXyzMatrixCoefficients() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] white = {255, 255, 255};
                float[] lab = nativeLib.rgbToLabBatch(arena, white);
                
                assertEquals(100.0, lab[0], 0.5, "White should have L* = 100");
                assertEquals(0.0, lab[1], 0.5, "White should have a* = 0");
                assertEquals(0.0, lab[2], 0.5, "White should have b* = 0");
            }
        }

        @Test
        @DisplayName("Each RGB channel contributes correctly to L*")
        void testChannelContributionToLuminance() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] red = {255, 0, 0};
                float[] green = {0, 255, 0};
                float[] blue = {0, 0, 255};
                
                float[] labRed = nativeLib.rgbToLabBatch(arena, red);
                float[] labGreen = nativeLib.rgbToLabBatch(arena, green);
                float[] labBlue = nativeLib.rgbToLabBatch(arena, blue);
                
                assertTrue(labGreen[0] > labRed[0], "Green should have higher L* than Red");
                assertTrue(labGreen[0] > labBlue[0], "Green should have higher L* than Blue");
                
                assertEquals(53.23, labRed[0], 2.0, "Red L*");
                assertEquals(87.74, labGreen[0], 2.0, "Green L*");
                assertEquals(32.30, labBlue[0], 2.0, "Blue L*");
            }
        }
    }

    @Nested
    @DisplayName("LAB to RGB Conversion")
    class LabToRgbTests {
        
        static Stream<Arguments> knownLabToRgbValues() {
            return Stream.of(
                Arguments.of(0.0, 0.0, 0.0, 0, 0, 0, "Black"),
                Arguments.of(100.0, 0.0, 0.0, 255, 255, 255, "White"),
                Arguments.of(53.23, 80.11, 67.22, 255, 0, 0, "Red"),
                Arguments.of(87.74, -86.18, 83.18, 0, 255, 0, "Green"),
                Arguments.of(32.30, 79.20, -107.86, 0, 0, 255, "Blue"),
                Arguments.of(53.59, 0.0, 0.0, 128, 128, 128, "Mid Gray"),
                Arguments.of(50.0, 0.0, 0.0, 119, 119, 119, "L*=50 Gray")
            );
        }

        @ParameterizedTest(name = "{6}: LAB({0},{1},{2}) -> RGB({3},{4},{5})")
        @MethodSource("knownLabToRgbValues")
        @DisplayName("Known LAB to RGB values")
        void testKnownLabToRgb(double l, double a, double b,
                               int expectedR, int expectedG, int expectedB,
                               String colorName) {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] lab = {(float)l, (float)a, (float)b};
                float[] rgb = nativeLib.labToRgbBatch(arena, lab);
                
                assertEquals(expectedR, rgb[0], 3.0,
                    colorName + " R mismatch");
                assertEquals(expectedG, rgb[1], 3.0,
                    colorName + " G mismatch");
                assertEquals(expectedB, rgb[2], 3.0,
                    colorName + " B mismatch");
            }
        }

        @Test
        @DisplayName("LAB f_inv() function threshold")
        void testLabInverseThreshold() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] labLow = {5, 0, 0};
                float[] labMid = {50, 0, 0};
                
                float[] rgbLow = nativeLib.labToRgbBatch(arena, labLow);
                float[] rgbMid = nativeLib.labToRgbBatch(arena, labMid);
                
                assertTrue(rgbLow[0] < rgbMid[0], "Lower L* should give lower R");
                assertTrue(rgbLow[0] >= 0, "RGB should be non-negative");
            }
        }

        @Test
        @DisplayName("Output clamping to [0, 255]")
        void testOutputClamping() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] extremeLab = {50, 128, 128};
                float[] rgb = nativeLib.labToRgbBatch(arena, extremeLab);
                
                assertTrue(rgb[0] >= 0 && rgb[0] <= 255, "R should be clamped");
                assertTrue(rgb[1] >= 0 && rgb[1] <= 255, "G should be clamped");
                assertTrue(rgb[2] >= 0 && rgb[2] <= 255, "B should be clamped");
            }
        }

        @Test
        @DisplayName("Inverse sRGB gamma (linear to sRGB)")
        void testInverseSrgbGamma() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] lab = {50, 0, 0};
                float[] rgb = nativeLib.labToRgbBatch(arena, lab);
                
                assertEquals(rgb[0], rgb[1], 1.0, "Gray R should equal G");
                assertEquals(rgb[1], rgb[2], 1.0, "Gray G should equal B");
                assertTrue(rgb[0] > 100 && rgb[0] < 130,
                    "L*=50 should produce mid-range gray, got " + rgb[0]);
            }
        }
    }

    @Nested
    @DisplayName("Round-Trip Conversion")
    class RoundTripTests {
        
        @Test
        @DisplayName("RGB -> LAB -> RGB preserves color")
        void testRgbRoundTrip() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                int[][] testColors = {
                    {0, 0, 0},
                    {255, 255, 255},
                    {128, 128, 128},
                    {255, 0, 0},
                    {0, 255, 0},
                    {0, 0, 255},
                    {100, 150, 200},
                    {37, 89, 142}
                };
                
                for (int[] color : testColors) {
                    float[] rgb = {color[0], color[1], color[2]};
                    float[] lab = nativeLib.rgbToLabBatch(arena, rgb);
                    float[] rgbBack = nativeLib.labToRgbBatch(arena, lab);
                    
                    assertEquals(color[0], rgbBack[0], 2.0,
                        String.format("R mismatch for RGB(%d,%d,%d)", color[0], color[1], color[2]));
                    assertEquals(color[1], rgbBack[1], 2.0,
                        String.format("G mismatch for RGB(%d,%d,%d)", color[0], color[1], color[2]));
                    assertEquals(color[2], rgbBack[2], 2.0,
                        String.format("B mismatch for RGB(%d,%d,%d)", color[0], color[1], color[2]));
                }
            }
        }

        @Test
        @DisplayName("LAB -> RGB -> LAB preserves color (in-gamut)")
        void testLabRoundTrip() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                double[][] testLab = {
                    {0, 0, 0},
                    {100, 0, 0},
                    {50, 0, 0},
                    {50, 20, 20},
                    {70, -30, 50}
                };
                
                for (double[] color : testLab) {
                    float[] lab = {(float)color[0], (float)color[1], (float)color[2]};
                    float[] rgb = nativeLib.labToRgbBatch(arena, lab);
                    float[] labBack = nativeLib.rgbToLabBatch(arena, rgb);
                    
                    assertEquals(color[0], labBack[0], 2.0,
                        String.format("L* mismatch for LAB(%.1f,%.1f,%.1f)", color[0], color[1], color[2]));
                    assertEquals(color[1], labBack[1], 3.0,
                        String.format("a* mismatch for LAB(%.1f,%.1f,%.1f)", color[0], color[1], color[2]));
                    assertEquals(color[2], labBack[2], 3.0,
                        String.format("b* mismatch for LAB(%.1f,%.1f,%.1f)", color[0], color[1], color[2]));
                }
            }
        }
    }

    @Nested
    @DisplayName("Batch Processing")
    class BatchTests {
        
        @Test
        @DisplayName("Batch conversion produces same results as single")
        void testBatchConsistency() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] rgbBatch = {
                    255, 0, 0,
                    0, 255, 0,
                    0, 0, 255
                };
                
                float[] labBatch = nativeLib.rgbToLabBatch(arena, rgbBatch);
                
                float[] red = nativeLib.rgbToLabBatch(arena, new float[]{255, 0, 0});
                float[] green = nativeLib.rgbToLabBatch(arena, new float[]{0, 255, 0});
                float[] blue = nativeLib.rgbToLabBatch(arena, new float[]{0, 0, 255});
                
                assertEquals(red[0], labBatch[0], 0.001f, "Red L* should match");
                assertEquals(red[1], labBatch[1], 0.001f, "Red a* should match");
                assertEquals(red[2], labBatch[2], 0.001f, "Red b* should match");
                
                assertEquals(green[0], labBatch[3], 0.001f, "Green L* should match");
                assertEquals(green[1], labBatch[4], 0.001f, "Green a* should match");
                assertEquals(green[2], labBatch[5], 0.001f, "Green b* should match");
                
                assertEquals(blue[0], labBatch[6], 0.001f, "Blue L* should match");
                assertEquals(blue[1], labBatch[7], 0.001f, "Blue a* should match");
                assertEquals(blue[2], labBatch[8], 0.001f, "Blue b* should match");
            }
        }

        @Test
        @DisplayName("Large batch parallel processing is deterministic")
        void testLargeBatchDeterminism() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                int n = 2000;
                float[] rgb = new float[n * 3];
                for (int i = 0; i < n; i++) {
                    rgb[i * 3] = (i * 17) % 256;
                    rgb[i * 3 + 1] = (i * 31) % 256;
                    rgb[i * 3 + 2] = (i * 47) % 256;
                }
                
                float[] lab1 = nativeLib.rgbToLabBatch(arena, rgb);
                float[] lab2 = nativeLib.rgbToLabBatch(arena, rgb);
                
                for (int i = 0; i < lab1.length; i++) {
                    assertEquals(lab1[i], lab2[i], 0.0001f,
                        "Batch results should be deterministic at index " + i);
                }
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Near-zero values don't cause division by zero")
        void testNearZeroValues() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] nearZero = {0.001f, 0.001f, 0.001f};
                float[] lab = nativeLib.rgbToLabBatch(arena, nearZero);
                
                assertFalse(Float.isNaN(lab[0]), "L* should not be NaN");
                assertFalse(Float.isNaN(lab[1]), "a* should not be NaN");
                assertFalse(Float.isNaN(lab[2]), "b* should not be NaN");
                assertFalse(Float.isInfinite(lab[0]), "L* should not be infinite");
            }
        }

        @Test
        @DisplayName("Maximum values don't overflow")
        void testMaxValues() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] max = {255, 255, 255};
                float[] lab = nativeLib.rgbToLabBatch(arena, max);
                
                assertTrue(lab[0] <= 100.5, "L* should not exceed 100");
                assertFalse(Float.isInfinite(lab[0]), "L* should not overflow");
            }
        }

        @Test  
        @DisplayName("Negative LAB values handled correctly")
        void testNegativeLabValues() {
            assumeTrue(nativeAvailable, "Native library not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] greenLab = {87.74f, -86.18f, 83.18f};
                float[] cyanLab = {91.11f, -48.09f, -14.13f};
                
                float[] greenRgb = nativeLib.labToRgbBatch(arena, greenLab);
                float[] cyanRgb = nativeLib.labToRgbBatch(arena, cyanLab);
                
                assertEquals(0, greenRgb[0], 5, "Green R");
                assertEquals(255, greenRgb[1], 5, "Green G");
                assertEquals(0, greenRgb[2], 5, "Green B");
                
                assertEquals(0, cyanRgb[0], 5, "Cyan R");
                assertEquals(255, cyanRgb[1], 5, "Cyan G");
                assertEquals(255, cyanRgb[2], 5, "Cyan B");
            }
        }
    }
}
