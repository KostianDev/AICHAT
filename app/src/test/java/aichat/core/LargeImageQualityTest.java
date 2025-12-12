package aichat.core;

import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.export.PaletteExporter;
import aichat.export.PaletteExporter.ExportFormat;
import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import org.junit.jupiter.api.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Large Image Quality Tests")
class LargeImageQualityTest {

    private static final String PROJECT_ROOT = System.getProperty("user.dir").replace("/app", "");
    private static final Path OUTPUT_DIR = Path.of(PROJECT_ROOT, "test-results/large-image");
    
    @BeforeAll
    static void setup() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
    }

    @Test
    @DisplayName("16K image with 512 colors - quality check")
    void testLargeImageHighK() {
        // Simulate a 16K image (8192x8192 = 67 megapixels)
        // We'll use 4096x4096 to keep test fast but still large enough
        int width = 4096;
        int height = 4096;
        int k = 512;
        
        System.out.println("Creating " + width + "x" + height + " test image (" + 
            String.format("%.1f", (long)width * height / 1_000_000.0) + " MP)...");
        
        BufferedImage largeImage = createComplexGradientImage(width, height);
        
        System.out.println("Extracting " + k + " colors with reservoir sampling...");
        long startTime = System.currentTimeMillis();
        
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        ColorPalette palette = engine.analyze(largeImage, k);
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Extraction completed in " + elapsed + " ms");
        
        List<ColorPoint> colors = palette.getColors();
        
        // Quality checks
        System.out.println("Running quality checks...");
        
        // 1. Check we got the requested number of colors (or close to it)
        assertTrue(colors.size() >= k * 0.9, 
            "Should extract at least 90% of requested colors, got " + colors.size() + "/" + k);
        System.out.println("✓ Color count: " + colors.size() + "/" + k);
        
        // 2. Check all colors are valid RGB
        for (ColorPoint cp : colors) {
            assertTrue(cp.c1() >= 0 && cp.c1() <= 255, "R component out of range: " + cp.c1());
            assertTrue(cp.c2() >= 0 && cp.c2() <= 255, "G component out of range: " + cp.c2());
            assertTrue(cp.c3() >= 0 && cp.c3() <= 255, "B component out of range: " + cp.c3());
        }
        System.out.println("✓ All colors have valid RGB values [0-255]");
        
        // 3. Check color diversity (colors should be reasonably spread)
        double minDistance = Double.MAX_VALUE;
        double maxDistance = 0;
        double totalDistance = 0;
        int comparisons = 0;
        
        // Sample pairs for performance
        Random rnd = new Random(42);
        for (int i = 0; i < Math.min(1000, colors.size()); i++) {
            int idx1 = rnd.nextInt(colors.size());
            int idx2 = rnd.nextInt(colors.size());
            if (idx1 != idx2) {
                double dist = colors.get(idx1).distanceTo(colors.get(idx2));
                minDistance = Math.min(minDistance, dist);
                maxDistance = Math.max(maxDistance, dist);
                totalDistance += dist;
                comparisons++;
            }
        }
        
        double avgDistance = totalDistance / comparisons;
        System.out.println("✓ Color diversity - min: " + String.format("%.1f", minDistance) + 
            ", avg: " + String.format("%.1f", avgDistance) + 
            ", max: " + String.format("%.1f", maxDistance));
        
        // Average distance should be reasonable (not all same color)
        assertTrue(avgDistance > 20, "Colors are too similar, avg distance: " + avgDistance);
        
        // 4. Check uniqueness (no exact duplicates)
        Set<String> uniqueHex = new HashSet<>();
        for (ColorPoint cp : colors) {
            uniqueHex.add(cp.toHexString());
        }
        double uniquePercent = 100.0 * uniqueHex.size() / colors.size();
        System.out.println("✓ Unique colors: " + uniqueHex.size() + "/" + colors.size() + 
            " (" + String.format("%.1f", uniquePercent) + "%)");
        
        // At least 80% should be unique
        assertTrue(uniquePercent > 80, "Too many duplicate colors: " + uniquePercent + "% unique");
        
        // 5. Performance check
        assertTrue(elapsed < 10000, "Extraction took too long: " + elapsed + " ms (max 10s)");
        System.out.println("✓ Performance: " + elapsed + " ms (< 10s threshold)");
        
        System.out.println("\n✅ All quality checks passed!");
    }

    @Test
    @DisplayName("Export large palette to all formats")
    void testExportLargePalette() throws IOException {
        // Create a moderately large image
        BufferedImage image = createComplexGradientImage(2048, 2048);
        
        ImageHarmonyEngine rgbEngine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        ImageHarmonyEngine labEngine = new ImageHarmonyEngine(ColorModel.CIELAB, 42L);
        
        ColorPalette rgbPalette = rgbEngine.analyze(image, 256);
        ColorPalette labPalette = labEngine.analyze(image, 256);
        
        File outputDir = OUTPUT_DIR.toFile();
        
        // Export RGB palette as GPL
        File gplFile = PaletteExporter.exportPalette(rgbPalette, ColorModel.RGB, ExportFormat.OPTIMAL, outputDir);
        assertTrue(gplFile.exists(), "GPL file should exist");
        assertTrue(gplFile.length() > 0, "GPL file should not be empty");
        
        String gplContent = Files.readString(gplFile.toPath());
        assertTrue(gplContent.contains("GIMP Palette"), "GPL should have GIMP header");
        
        long gplColorLines = gplContent.lines()
            .filter(line -> line.matches("\\s*\\d+\\s+\\d+\\s+\\d+.*"))
            .count();
        System.out.println("GPL export: " + gplColorLines + " colors, " + gplFile.length() + " bytes");
        assertTrue(gplColorLines >= 200, "GPL should have at least 200 color entries");
        
        // Export LAB palette as CSV
        File csvFile = PaletteExporter.exportPalette(labPalette, ColorModel.CIELAB, ExportFormat.OPTIMAL, outputDir);
        assertTrue(csvFile.exists(), "CSV file should exist");
        
        String csvContent = Files.readString(csvFile.toPath());
        assertTrue(csvContent.contains("L,a,b"), "CSV should have LAB header");
        
        long csvDataLines = csvContent.lines().skip(1).count(); // Skip header
        System.out.println("CSV export: " + csvDataLines + " colors, " + csvFile.length() + " bytes");
        assertTrue(csvDataLines >= 200, "CSV should have at least 200 color entries");
        
        // Export as PNG
        File pngFile = PaletteExporter.exportPalette(rgbPalette, ColorModel.RGB, ExportFormat.PNG_IMAGE, outputDir);
        assertTrue(pngFile.exists(), "PNG file should exist");
        
        BufferedImage pngImage = ImageIO.read(pngFile);
        assertNotNull(pngImage, "PNG should be readable");
        System.out.println("PNG export: " + pngImage.getWidth() + "x" + pngImage.getHeight() + 
            ", " + pngFile.length() + " bytes");
        
        // PNG should be reasonably sized (16 colors per row × 100px = 1600px max width for 256 colors)
        assertTrue(pngImage.getWidth() <= 1600, "PNG width should be reasonable");
        assertTrue(pngImage.getHeight() >= 100, "PNG should have at least one row of colors");
        
        System.out.println("\n✅ All export formats working correctly!");
    }

    @Test
    @DisplayName("Sampling consistency - same seed produces same results")
    void testSamplingConsistency() {
        BufferedImage image = createComplexGradientImage(1024, 1024);
        
        ImageHarmonyEngine engine1 = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        ImageHarmonyEngine engine2 = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        
        ColorPalette palette1 = engine1.analyze(image, 64);
        ColorPalette palette2 = engine2.analyze(image, 64);
        
        List<ColorPoint> colors1 = palette1.getColors();
        List<ColorPoint> colors2 = palette2.getColors();
        
        assertEquals(colors1.size(), colors2.size(), "Same seed should produce same palette size");
        
        for (int i = 0; i < colors1.size(); i++) {
            assertEquals(colors1.get(i).toHexString(), colors2.get(i).toHexString(),
                "Color at index " + i + " should match");
        }
        
        System.out.println("✅ Sampling is deterministic with same seed");
    }

    @Test
    @DisplayName("Different seeds produce different but valid palettes")
    void testDifferentSeedsProduceDifferentResults() {
        BufferedImage image = createComplexGradientImage(1024, 1024);
        
        ImageHarmonyEngine engine1 = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        ImageHarmonyEngine engine2 = new ImageHarmonyEngine(ColorModel.RGB, 123L);
        
        ColorPalette palette1 = engine1.analyze(image, 32);
        ColorPalette palette2 = engine2.analyze(image, 32);
        
        // Palettes should be different
        int matchingColors = 0;
        for (ColorPoint c1 : palette1.getColors()) {
            for (ColorPoint c2 : palette2.getColors()) {
                if (c1.toHexString().equals(c2.toHexString())) {
                    matchingColors++;
                    break;
                }
            }
        }
        
        double matchPercent = 100.0 * matchingColors / palette1.getColors().size();
        System.out.println("Different seeds: " + matchingColors + "/" + palette1.getColors().size() + 
            " colors match (" + String.format("%.1f", matchPercent) + "%)");
        
        // Some overlap is expected (both capture dominant colors), but not 100%
        assertTrue(matchPercent < 90, "Different seeds should produce mostly different palettes");
        
        System.out.println("✅ Different seeds produce varied palettes");
    }

    private BufferedImage createComplexGradientImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create a complex color pattern
                float hue = (float) x / width;
                float saturation = 0.3f + 0.7f * (float) y / height;
                float brightness = 0.5f + 0.5f * (float) ((x + y) % 256) / 255f;
                
                // Add some variation
                hue = (hue + (float)(Math.sin(x * 0.01) * 0.1)) % 1.0f;
                if (hue < 0) hue += 1.0f;
                
                int rgb = Color.HSBtoRGB(hue, saturation, brightness);
                img.setRGB(x, y, rgb);
            }
        }
        
        return img;
    }
}
