package aichat.color;

import aichat.model.ColorPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColorSpaceConverter Tests")
class ColorSpaceConverterTest {

    private static final double RGB_DELTA = 1.5;
    private static final double LAB_DELTA = 0.5;

    @Nested
    @DisplayName("RGB to LAB Conversion")
    class RgbToLabTests {

        @ParameterizedTest(name = "RGB({0},{1},{2}) -> LAB({3},{4},{5})")
        @CsvSource({
            // Reference values from colormine.org and Bruce Lindbloom
            "255, 255, 255, 100.0, 0.0, 0.0",      // White
            "0, 0, 0, 0.0, 0.0, 0.0",              // Black
            "255, 0, 0, 53.23, 80.11, 67.22",      // Red
            "0, 255, 0, 87.74, -86.18, 83.18",     // Green
            "0, 0, 255, 32.30, 79.20, -107.86",    // Blue
            "128, 128, 128, 53.59, 0.0, 0.0",      // Mid-gray
            "255, 255, 0, 97.14, -21.55, 94.48",   // Yellow
            "0, 255, 255, 91.11, -48.09, -14.13",  // Cyan
            "255, 0, 255, 60.32, 98.25, -60.84"    // Magenta
        })
        void testRgbToLabKnownValues(int r, int g, int b, 
                                      double expectedL, double expectedA, double expectedB) {
            ColorPoint rgb = new ColorPoint(r, g, b);
            ColorPoint lab = ColorSpaceConverter.rgbToLab(rgb);

            assertEquals(expectedL, lab.c1(), LAB_DELTA, 
                "L* component mismatch for RGB(" + r + "," + g + "," + b + ")");
            assertEquals(expectedA, lab.c2(), LAB_DELTA, 
                "a* component mismatch for RGB(" + r + "," + g + "," + b + ")");
            assertEquals(expectedB, lab.c3(), LAB_DELTA, 
                "b* component mismatch for RGB(" + r + "," + g + "," + b + ")");
        }

        @Test
        @DisplayName("Black should convert to L*=0")
        void testBlackHasZeroLuminance() {
            ColorPoint black = new ColorPoint(0, 0, 0);
            ColorPoint lab = ColorSpaceConverter.rgbToLab(black);

            assertEquals(0.0, lab.c1(), 0.01, "Black should have L*=0");
        }

        @Test
        @DisplayName("White should convert to L*=100")
        void testWhiteHasMaxLuminance() {
            ColorPoint white = new ColorPoint(255, 255, 255);
            ColorPoint lab = ColorSpaceConverter.rgbToLab(white);

            assertEquals(100.0, lab.c1(), 0.01, "White should have L*=100");
        }

        @Test
        @DisplayName("Neutral grays should have a*=0 and b*=0")
        void testNeutralGraysHaveZeroChroma() {
            int[] grayLevels = {0, 50, 100, 128, 150, 200, 255};
            
            for (int gray : grayLevels) {
                ColorPoint rgb = new ColorPoint(gray, gray, gray);
                ColorPoint lab = ColorSpaceConverter.rgbToLab(rgb);

                assertEquals(0.0, lab.c2(), 0.5, 
                    "Gray " + gray + " should have a*=0");
                assertEquals(0.0, lab.c3(), 0.5, 
                    "Gray " + gray + " should have b*=0");
            }
        }
    }

    @Nested
    @DisplayName("LAB to RGB Conversion")
    class LabToRgbTests {

        @ParameterizedTest(name = "LAB({0},{1},{2}) -> RGB({3},{4},{5})")
        @CsvSource({
            "100.0, 0.0, 0.0, 255, 255, 255",     // White
            "0.0, 0.0, 0.0, 0, 0, 0",             // Black
            "53.23, 80.11, 67.22, 255, 0, 0",     // Red
            "87.74, -86.18, 83.18, 0, 255, 0",    // Green
            "32.30, 79.20, -107.86, 0, 0, 255",   // Blue
            "53.59, 0.0, 0.0, 128, 128, 128"      // Mid-gray
        })
        void testLabToRgbKnownValues(double l, double a, double b,
                                      int expectedR, int expectedG, int expectedB) {
            ColorPoint lab = new ColorPoint(l, a, b);
            ColorPoint rgb = ColorSpaceConverter.labToRgb(lab);

            assertEquals(expectedR, rgb.c1(), RGB_DELTA,
                "R component mismatch for LAB(" + l + "," + a + "," + b + ")");
            assertEquals(expectedG, rgb.c2(), RGB_DELTA,
                "G component mismatch for LAB(" + l + "," + a + "," + b + ")");
            assertEquals(expectedB, rgb.c3(), RGB_DELTA,
                "B component mismatch for LAB(" + l + "," + a + "," + b + ")");
        }

        @Test
        @DisplayName("LAB to RGB should clamp output to valid range")
        void testOutputIsClamped() {
            // Extreme LAB values that would result in out-of-gamut RGB
            ColorPoint extremeLab = new ColorPoint(50, 128, 128);
            ColorPoint rgb = ColorSpaceConverter.labToRgb(extremeLab);

            assertTrue(rgb.c1() >= 0 && rgb.c1() <= 255, "R should be clamped");
            assertTrue(rgb.c2() >= 0 && rgb.c2() <= 255, "G should be clamped");
            assertTrue(rgb.c3() >= 0 && rgb.c3() <= 255, "B should be clamped");
        }
    }

    @Nested
    @DisplayName("Round-Trip Conversion")
    class RoundTripTests {

        @Test
        @DisplayName("RGB -> LAB -> RGB should preserve color within tolerance")
        void testRgbRoundTrip() {
            int[][] testColors = {
                {0, 0, 0}, {255, 255, 255}, {128, 128, 128},
                {255, 0, 0}, {0, 255, 0}, {0, 0, 255},
                {100, 150, 200}, {50, 100, 75}, {200, 180, 160}
            };

            for (int[] color : testColors) {
                ColorPoint original = new ColorPoint(color[0], color[1], color[2]);
                ColorPoint lab = ColorSpaceConverter.rgbToLab(original);
                ColorPoint recovered = ColorSpaceConverter.labToRgb(lab);

                assertEquals(original.c1(), recovered.c1(), RGB_DELTA,
                    "R value mismatch for " + original);
                assertEquals(original.c2(), recovered.c2(), RGB_DELTA,
                    "G value mismatch for " + original);
                assertEquals(original.c3(), recovered.c3(), RGB_DELTA,
                    "B value mismatch for " + original);
            }
        }

        @Test
        @DisplayName("LAB -> RGB -> LAB should preserve color within tolerance")
        void testLabRoundTrip() {
            double[][] testColors = {
                {0, 0, 0}, {100, 0, 0}, {50, 0, 0},
                {50, 50, 50}, {50, -50, 50}, {50, 50, -50},
                {75, 20, -30}
            };

            for (double[] color : testColors) {
                ColorPoint original = new ColorPoint(color[0], color[1], color[2]);
                ColorPoint rgb = ColorSpaceConverter.labToRgb(original);
                ColorPoint recovered = ColorSpaceConverter.rgbToLab(rgb);

                assertEquals(original.c1(), recovered.c1(), LAB_DELTA,
                    "L* value mismatch for " + original);
                assertEquals(original.c2(), recovered.c2(), LAB_DELTA,
                    "a* value mismatch for " + original);
                assertEquals(original.c3(), recovered.c3(), LAB_DELTA,
                    "b* value mismatch for " + original);
            }
        }
    }

    @Nested
    @DisplayName("Batch Conversion")
    class BatchConversionTests {

        @Test
        @DisplayName("Batch RGB to LAB should process all colors correctly")
        void testBatchRgbToLab() {
            List<ColorPoint> rgbColors = new ArrayList<>();
            rgbColors.add(new ColorPoint(255, 0, 0));
            rgbColors.add(new ColorPoint(0, 255, 0));
            rgbColors.add(new ColorPoint(0, 0, 255));

            List<ColorPoint> labColors = ColorSpaceConverter.rgbToLabBatch(rgbColors);

            assertEquals(3, labColors.size());
            
            // Verify red
            assertEquals(53.23, labColors.get(0).c1(), LAB_DELTA);
            // Verify green
            assertEquals(87.74, labColors.get(1).c1(), LAB_DELTA);
            // Verify blue
            assertEquals(32.30, labColors.get(2).c1(), LAB_DELTA);
        }

        @Test
        @DisplayName("Empty batch should return empty list")
        void testEmptyBatch() {
            List<ColorPoint> result = ColorSpaceConverter.rgbToLabBatch(new ArrayList<>());
            assertTrue(result.isEmpty());
            
            result = ColorSpaceConverter.labToRgbBatch(new ArrayList<>());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Null batch should return empty list")
        void testNullBatch() {
            List<ColorPoint> result = ColorSpaceConverter.rgbToLabBatch(null);
            assertTrue(result.isEmpty());
            
            result = ColorSpaceConverter.labToRgbBatch(null);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("CIEDE2000 Color Difference")
    class DeltaE2000Tests {

        @Test
        @DisplayName("Identical colors should have deltaE = 0")
        void testIdenticalColorsHaveZeroDelta() {
            ColorPoint color = ColorSpaceConverter.rgbToLab(new ColorPoint(128, 64, 192));
            double deltaE = ColorSpaceConverter.deltaE2000(color, color);
            
            assertEquals(0.0, deltaE, 0.001);
        }

        @Test
        @DisplayName("Perceptually different colors should have deltaE > 1")
        void testPerceptualDifference() {
            ColorPoint red = ColorSpaceConverter.rgbToLab(new ColorPoint(255, 0, 0));
            ColorPoint blue = ColorSpaceConverter.rgbToLab(new ColorPoint(0, 0, 255));
            
            double deltaE = ColorSpaceConverter.deltaE2000(red, blue);
            
            assertTrue(deltaE > 1.0, "Red and blue should be perceptually different");
            assertTrue(deltaE > 50, "Red and blue should have large deltaE");
        }

        @Test
        @DisplayName("Similar colors should have small deltaE")
        void testSimilarColors() {
            ColorPoint color1 = ColorSpaceConverter.rgbToLab(new ColorPoint(100, 100, 100));
            ColorPoint color2 = ColorSpaceConverter.rgbToLab(new ColorPoint(102, 100, 100));
            
            double deltaE = ColorSpaceConverter.deltaE2000(color1, color2);
            
            assertTrue(deltaE < 5.0, "Very similar colors should have small deltaE");
        }

        @Test
        @DisplayName("DeltaE should be symmetric")
        void testSymmetry() {
            ColorPoint lab1 = new ColorPoint(50, 25, -15);
            ColorPoint lab2 = new ColorPoint(60, -10, 30);
            
            double deltaE12 = ColorSpaceConverter.deltaE2000(lab1, lab2);
            double deltaE21 = ColorSpaceConverter.deltaE2000(lab2, lab1);
            
            assertEquals(deltaE12, deltaE21, 0.001, "DeltaE should be symmetric");
        }
    }
}
