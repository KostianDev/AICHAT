package aichat.core;

import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageHarmonyEngine Tests")
class ImageHarmonyEngineTest {

    private static final int TEST_IMAGE_SIZE = 100;

    @Nested
    @DisplayName("RGB Color Model")
    class RgbModelTests {

        private ImageHarmonyEngine engine;

        @BeforeEach
        void setUp() {
            engine = new ImageHarmonyEngine(ColorModel.RGB);
        }

        @Test
        @DisplayName("Should extract correct number of colors from image")
        void shouldExtractCorrectColorCount() {
            BufferedImage image = createTestImage(
                Color.RED, Color.GREEN, Color.BLUE
            );
            
            ColorPalette palette = engine.analyze(image, 3);
            
            assertEquals(3, palette.size());
        }

        @Test
        @DisplayName("Should find primary colors in simple image")
        void shouldFindPrimaryColors() {
            BufferedImage image = createSolidColorImage(Color.RED);
            
            ColorPalette palette = engine.analyze(image, 1);
            
            assertEquals(1, palette.size());
            ColorPoint color = palette.getColor(0);
            assertTrue(color.c1() > 200, "Should detect red");
            assertTrue(color.c2() < 50, "Should have low green");
            assertTrue(color.c3() < 50, "Should have low blue");
        }

        @Test
        @DisplayName("Should handle grayscale images")
        void shouldHandleGrayscaleImages() {
            BufferedImage image = createGradientImage();
            
            ColorPalette palette = engine.analyze(image, 4);
            
            assertEquals(4, palette.size());
            // All colors should be roughly grayscale
            for (ColorPoint c : palette.getColors()) {
                double maxDiff = Math.max(
                    Math.abs(c.c1() - c.c2()),
                    Math.max(Math.abs(c.c2() - c.c3()), Math.abs(c.c1() - c.c3()))
                );
                assertTrue(maxDiff < 30, "Grayscale colors should have similar RGB values");
            }
        }

        @Test
        @DisplayName("Should report RGB color model")
        void shouldReportColorModel() {
            assertEquals(ColorModel.RGB, engine.getColorModel());
        }
    }

    @Nested
    @DisplayName("CIELAB Color Model")
    class CielabModelTests {

        private ImageHarmonyEngine engine;

        @BeforeEach
        void setUp() {
            engine = new ImageHarmonyEngine(ColorModel.CIELAB);
        }

        @Test
        @DisplayName("Should extract colors using CIELAB space")
        void shouldExtractColorsInCielab() {
            BufferedImage image = createTestImage(
                Color.RED, Color.BLUE
            );
            
            ColorPalette palette = engine.analyze(image, 2);
            
            assertEquals(2, palette.size());
            // Palette should be returned in RGB
            for (ColorPoint c : palette.getColors()) {
                assertTrue(c.c1() >= 0 && c.c1() <= 255);
                assertTrue(c.c2() >= 0 && c.c2() <= 255);
                assertTrue(c.c3() >= 0 && c.c3() <= 255);
            }
        }

        @Test
        @DisplayName("Should report CIELAB color model")
        void shouldReportColorModel() {
            assertEquals(ColorModel.CIELAB, engine.getColorModel());
        }
    }

    @Nested
    @DisplayName("Resynthesis")
    class ResynthesisTests {

        @Test
        @DisplayName("Resynthesis should produce image of same dimensions")
        void resynthesisShouldPreserveDimensions() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            BufferedImage source = createTestImage(Color.RED, Color.BLUE);
            BufferedImage target = createTestImage(Color.GREEN, Color.YELLOW);
            
            ColorPalette sourcePalette = engine.analyze(source, 2);
            ColorPalette targetPalette = engine.analyze(target, 2);
            
            BufferedImage result = engine.resynthesize(target, sourcePalette, targetPalette);
            
            assertEquals(target.getWidth(), result.getWidth());
            assertEquals(target.getHeight(), result.getHeight());
        }

        @Test
        @DisplayName("Resynthesis should change image colors")
        void resynthesisShouldChangeColors() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            BufferedImage source = createSolidColorImage(Color.RED);
            BufferedImage target = createSolidColorImage(Color.BLUE);
            
            ColorPalette sourcePalette = engine.analyze(source, 1);
            ColorPalette targetPalette = engine.analyze(target, 1);
            
            BufferedImage result = engine.resynthesize(target, sourcePalette, targetPalette);
            
            // Result should have colors closer to source palette (red)
            int centerRgb = result.getRGB(TEST_IMAGE_SIZE / 2, TEST_IMAGE_SIZE / 2);
            int r = (centerRgb >> 16) & 0xFF;
            int b = centerRgb & 0xFF;
            
            assertTrue(r > b, "Result should have more red than blue");
        }

        @Test
        @DisplayName("Resynthesis with identical palettes preserves image")
        void resynthesisWithSamePalettePreservesImage() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            BufferedImage image = createTestImage(Color.RED, Color.BLUE, Color.GREEN);
            
            ColorPalette palette = engine.analyze(image, 3);
            
            BufferedImage result = engine.resynthesize(image, palette, palette);
            
            // Result should be very similar to original
            double avgDiff = calculateAveragePixelDifference(image, result);
            assertTrue(avgDiff < 10, "Same palette resynthesis should preserve image, diff=" + avgDiff);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very small images")
        void shouldHandleSmallImages() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            BufferedImage tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            tiny.setRGB(0, 0, Color.RED.getRGB());
            tiny.setRGB(1, 0, Color.GREEN.getRGB());
            tiny.setRGB(0, 1, Color.BLUE.getRGB());
            tiny.setRGB(1, 1, Color.WHITE.getRGB());
            
            ColorPalette palette = engine.analyze(tiny, 4);
            
            assertNotNull(palette);
            assertEquals(4, palette.size());
        }

        @Test
        @DisplayName("Should handle single-color images")
        void shouldHandleSingleColorImages() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            BufferedImage solid = createSolidColorImage(Color.MAGENTA);
            
            ColorPalette palette = engine.analyze(solid, 3);
            
            assertNotNull(palette);
            // All centroids should be similar (the same color repeated)
            for (ColorPoint c : palette.getColors()) {
                assertTrue(c.c1() > 200 && c.c3() > 200, "Should be magenta-ish");
            }
        }

        @Test
        @DisplayName("Should use default seed for reproducibility")
        void shouldBeReproducibleWithDefaultSeed() {
            BufferedImage image = createRandomImage(50, 50, 12345);
            
            ImageHarmonyEngine e1 = new ImageHarmonyEngine(ColorModel.RGB);
            ImageHarmonyEngine e2 = new ImageHarmonyEngine(ColorModel.RGB);
            
            ColorPalette p1 = e1.analyze(image, 4);
            ColorPalette p2 = e2.analyze(image, 4);
            
            assertEquals(p1.size(), p2.size());
            for (int i = 0; i < p1.size(); i++) {
                ColorPoint c1 = p1.getColor(i);
                ColorPoint c2 = p2.getColor(i);
                assertEquals(c1.c1(), c2.c1(), 0.1);
                assertEquals(c1.c2(), c2.c2(), 0.1);
                assertEquals(c1.c3(), c2.c3(), 0.1);
            }
        }
    }

    @Nested
    @DisplayName("Algorithm Reporting")
    class AlgorithmReportingTests {

        @Test
        @DisplayName("Should report algorithm name")
        void shouldReportAlgorithmName() {
            ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB);
            
            String name = engine.getAlgorithmName();
            
            assertNotNull(name);
            assertFalse(name.isEmpty());
        }
    }

    // Helper methods

    private BufferedImage createTestImage(Color... colors) {
        int width = TEST_IMAGE_SIZE;
        int height = TEST_IMAGE_SIZE;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        int stripeWidth = width / colors.length;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int colorIndex = Math.min(x / stripeWidth, colors.length - 1);
                image.setRGB(x, y, colors[colorIndex].getRGB());
            }
        }
        
        return image;
    }

    private BufferedImage createSolidColorImage(Color color) {
        BufferedImage image = new BufferedImage(
            TEST_IMAGE_SIZE, TEST_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB
        );
        
        for (int y = 0; y < TEST_IMAGE_SIZE; y++) {
            for (int x = 0; x < TEST_IMAGE_SIZE; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        
        return image;
    }

    private BufferedImage createGradientImage() {
        BufferedImage image = new BufferedImage(
            TEST_IMAGE_SIZE, TEST_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB
        );
        
        for (int y = 0; y < TEST_IMAGE_SIZE; y++) {
            for (int x = 0; x < TEST_IMAGE_SIZE; x++) {
                int gray = (x * 255) / TEST_IMAGE_SIZE;
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        
        return image;
    }

    private BufferedImage createRandomImage(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(seed);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = random.nextInt(0xFFFFFF);
                image.setRGB(x, y, rgb);
            }
        }
        
        return image;
    }

    private double calculateAveragePixelDifference(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();
        double totalDiff = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                
                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;
                
                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;
                
                totalDiff += Math.sqrt(
                    (r1-r2)*(r1-r2) + (g1-g2)*(g1-g2) + (b1-b2)*(b1-b2)
                );
            }
        }
        
        return totalDiff / (width * height);
    }
}
