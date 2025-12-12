package aichat.visual;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Visual regression tests for image resynthesis.
 * These tests compare output images against golden references.
 * 
 * Note: These tests are disabled in Java-only mode (force.java=true) because
 * Java and Native implementations may produce slightly different results due to
 * floating-point precision differences. Use DifferentialClusteringTest for
 * cross-implementation validation.
 */
@DisplayName("Visual Regression Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIf("isForceJavaMode")
class VisualRegressionTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir").replace("/app", "");
    private static final String GOLDEN_DIR = "src/test/resources/golden";
    private static final String ACTUAL_DIR = PROJECT_ROOT + "/test-results/visual";
    private static final double THRESHOLD_PERCENT = 0.5;

    static boolean isForceJavaMode() {
        return Boolean.getBoolean("force.java");
    }

    private ImageHarmonyEngine rgbEngine;
    private ImageHarmonyEngine labEngine;

    @BeforeAll
    static void setupDirectories() throws IOException {
        Files.createDirectories(Path.of(GOLDEN_DIR));
        Files.createDirectories(Path.of(ACTUAL_DIR));
    }

    @BeforeEach
    void setUp() {
        rgbEngine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        labEngine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
    }

    @Test
    @Order(1)
    @DisplayName("Primary colors resynthesis - RGB model")
    void testPrimaryColorsResynthesisRgb() throws IOException {
        BufferedImage source = createPrimaryColorsImage();
        BufferedImage target = createSecondaryColorsImage();
        
        ColorPalette sourcePalette = rgbEngine.analyze(source, 3);
        ColorPalette targetPalette = rgbEngine.analyze(target, 3);
        BufferedImage result = rgbEngine.resynthesize(target, sourcePalette, targetPalette);
        
        assertVisualMatch("primary_rgb_resynth", result);
    }

    @Test
    @Order(2)
    @DisplayName("Gradient resynthesis - RGB model")
    void testGradientResynthesisRgb() throws IOException {
        BufferedImage source = createGradientImage(Color.RED, Color.BLUE);
        BufferedImage target = createGradientImage(Color.GREEN, Color.YELLOW);
        
        ColorPalette sourcePalette = rgbEngine.analyze(source, 4);
        ColorPalette targetPalette = rgbEngine.analyze(target, 4);
        BufferedImage result = rgbEngine.resynthesize(target, sourcePalette, targetPalette);
        
        assertVisualMatch("gradient_rgb_resynth", result);
    }

    @Test
    @Order(3)
    @DisplayName("Checkerboard pattern - RGB model")
    void testCheckerboardRgb() throws IOException {
        BufferedImage source = createCheckerboardImage(Color.BLACK, Color.WHITE);
        BufferedImage target = createCheckerboardImage(Color.DARK_GRAY, Color.LIGHT_GRAY);
        
        ColorPalette sourcePalette = rgbEngine.analyze(source, 2);
        ColorPalette targetPalette = rgbEngine.analyze(target, 2);
        BufferedImage result = rgbEngine.resynthesize(target, sourcePalette, targetPalette);
        
        assertVisualMatch("checkerboard_rgb_resynth", result);
    }

    @Test
    @Order(4)
    @DisplayName("Color bands resynthesis - CIELAB model")
    void testColorBandsLab() throws IOException {
        BufferedImage source = createColorBandsImage(
            Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN
        );
        BufferedImage target = createColorBandsImage(
            Color.CYAN, Color.BLUE, Color.MAGENTA, Color.PINK
        );
        
        ColorPalette sourcePalette = labEngine.analyze(source, 4);
        ColorPalette targetPalette = labEngine.analyze(target, 4);
        BufferedImage result = labEngine.resynthesize(target, sourcePalette, targetPalette);
        
        assertVisualMatch("bands_lab_resynth", result);
    }

    @Test
    @Order(5)
    @DisplayName("Radial gradient - CIELAB model")
    void testRadialGradientLab() throws IOException {
        BufferedImage source = createRadialGradientImage(Color.RED, Color.WHITE);
        BufferedImage target = createRadialGradientImage(Color.BLUE, Color.BLACK);
        
        ColorPalette sourcePalette = labEngine.analyze(source, 5);
        ColorPalette targetPalette = labEngine.analyze(target, 5);
        BufferedImage result = labEngine.resynthesize(target, sourcePalette, targetPalette);
        
        assertVisualMatch("radial_lab_resynth", result);
    }

    @Test
    @Order(6)
    @DisplayName("Palette extraction stability - RGB")
    void testPaletteExtractionStabilityRgb() throws IOException {
        BufferedImage image = createComplexTestImage();
        
        // Create palette visualization
        ColorPalette palette = rgbEngine.analyze(image, 8);
        BufferedImage paletteImage = renderPalette(palette);
        
        assertVisualMatch("palette_rgb_extraction", paletteImage);
    }

    @Test
    @Order(7)
    @DisplayName("Palette extraction stability - CIELAB")
    void testPaletteExtractionStabilityLab() throws IOException {
        BufferedImage image = createComplexTestImage();
        
        ColorPalette palette = labEngine.analyze(image, 8);
        BufferedImage paletteImage = renderPalette(palette);
        
        assertVisualMatch("palette_lab_extraction", paletteImage);
    }

    @Test
    @Order(8)
    @DisplayName("High color count k=16")
    void testHighColorCount() throws IOException {
        BufferedImage image = createRainbowImage();
        
        ColorPalette palette = rgbEngine.analyze(image, 16);
        BufferedImage paletteImage = renderPalette(palette);
        
        assertVisualMatch("palette_high_k", paletteImage);
    }

    // Visual assertion method
    private void assertVisualMatch(String testName, BufferedImage actual) throws IOException {
        File goldenFile = new File(GOLDEN_DIR, testName + ".png");
        File actualFile = new File(ACTUAL_DIR, testName + "_actual.png");
        File diffFile = new File(ACTUAL_DIR, testName + "_diff.png");
        
        // Save actual result
        ImageIO.write(actual, "PNG", actualFile);
        
        if (!goldenFile.exists()) {
            // First run - create golden master
            ImageIO.write(actual, "PNG", goldenFile);
            System.out.println("Created golden master: " + goldenFile.getPath());
            return; // Don't fail on first run
        }
        
        // Compare with golden master
        BufferedImage golden = ImageIO.read(goldenFile);
        
        ImageComparison comparison = new ImageComparison(golden, actual);
        comparison.setThreshold(10); // Pixel difference threshold
        comparison.setRectangleLineWidth(2);
        comparison.setDifferenceRectangleFilling(true, 20.0);
        
        ImageComparisonResult result = comparison.compareImages();
        
        // Save diff for debugging
        ImageComparisonUtil.saveImage(diffFile, result.getResult());
        
        double diffPercent = result.getDifferencePercent();
        
        if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
            if (diffPercent > THRESHOLD_PERCENT) {
                fail("Visual regression detected for '" + testName + "': " +
                     String.format("%.2f%%", diffPercent) + " difference (threshold: " + 
                     THRESHOLD_PERCENT + "%). See: " + diffFile.getPath());
            }
        }
    }

    // Image generation helpers

    private BufferedImage createPrimaryColorsImage() {
        BufferedImage img = new BufferedImage(150, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.GREEN);
        g.fillRect(50, 0, 50, 100);
        g.setColor(Color.BLUE);
        g.fillRect(100, 0, 50, 100);
        g.dispose();
        return img;
    }

    private BufferedImage createSecondaryColorsImage() {
        BufferedImage img = new BufferedImage(150, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, 50, 100);
        g.setColor(Color.MAGENTA);
        g.fillRect(50, 0, 50, 100);
        g.setColor(Color.YELLOW);
        g.fillRect(100, 0, 50, 100);
        g.dispose();
        return img;
    }

    private BufferedImage createGradientImage(Color start, Color end) {
        int w = 100, h = 100;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        
        for (int x = 0; x < w; x++) {
            float t = (float) x / w;
            int r = (int) (start.getRed() * (1 - t) + end.getRed() * t);
            int g = (int) (start.getGreen() * (1 - t) + end.getGreen() * t);
            int b = (int) (start.getBlue() * (1 - t) + end.getBlue() * t);
            int rgb = (r << 16) | (g << 8) | b;
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    private BufferedImage createCheckerboardImage(Color c1, Color c2) {
        int size = 100, cellSize = 10;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean isC1 = ((x / cellSize) + (y / cellSize)) % 2 == 0;
                img.setRGB(x, y, isC1 ? c1.getRGB() : c2.getRGB());
            }
        }
        return img;
    }

    private BufferedImage createColorBandsImage(Color... colors) {
        int w = 100, h = 100;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int bandHeight = h / colors.length;
        
        for (int i = 0; i < colors.length; i++) {
            for (int y = i * bandHeight; y < (i + 1) * bandHeight && y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setRGB(x, y, colors[i].getRGB());
                }
            }
        }
        return img;
    }

    private BufferedImage createRadialGradientImage(Color center, Color edge) {
        int size = 100;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int cx = size / 2, cy = size / 2;
        double maxDist = Math.sqrt(cx * cx + cy * cy);
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                float t = (float) Math.min(dist / maxDist, 1.0);
                
                int r = (int) (center.getRed() * (1 - t) + edge.getRed() * t);
                int g = (int) (center.getGreen() * (1 - t) + edge.getGreen() * t);
                int b = (int) (center.getBlue() * (1 - t) + edge.getBlue() * t);
                
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private BufferedImage createComplexTestImage() {
        int size = 100;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        
        // Background gradient
        for (int y = 0; y < size; y++) {
            int gray = (y * 200) / size + 30;
            g.setColor(new Color(gray, gray, gray));
            g.drawLine(0, y, size, y);
        }
        
        // Colored shapes
        g.setColor(Color.RED);
        g.fillOval(10, 10, 30, 30);
        g.setColor(Color.GREEN);
        g.fillRect(60, 10, 30, 30);
        g.setColor(Color.BLUE);
        g.fillOval(10, 60, 30, 30);
        g.setColor(Color.YELLOW);
        g.fillRect(60, 60, 30, 30);
        
        g.dispose();
        return img;
    }

    private BufferedImage createRainbowImage() {
        int w = 360, h = 50;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        
        for (int x = 0; x < w; x++) {
            float hue = (float) x / w;
            int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    private BufferedImage renderPalette(ColorPalette palette) {
        int n = palette.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (n + cols - 1) / cols;
        int cellSize = 50;
        
        BufferedImage img = new BufferedImage(
            cols * cellSize, rows * cellSize, BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g = img.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        
        for (int i = 0; i < n; i++) {
            int x = (i % cols) * cellSize;
            int y = (i / cols) * cellSize;
            
            var c = palette.getColor(i);
            g.setColor(new Color(
                clamp((int) c.c1()),
                clamp((int) c.c2()),
                clamp((int) c.c3())
            ));
            g.fillRect(x + 2, y + 2, cellSize - 4, cellSize - 4);
        }
        
        g.dispose();
        return img;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
