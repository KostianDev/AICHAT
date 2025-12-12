package aichat.core;

import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.native_.NativeAccelerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for high color count palettes (k=256, 512) on large images.
 * Performance tests (Order 1-9) require native acceleration.
 * Correctness tests (Order 10+) run in all modes.
 */
@DisplayName("High Color Count Load Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HighColorCountLoadTest {

    private static final long SEED = 42L;
    private static final Path OUTPUT_DIR = Path.of("test-results/performance");
    private static final Path CSV_FILE = OUTPUT_DIR.resolve("performance-results.csv");
    private static final Path JSON_FILE = OUTPUT_DIR.resolve("performance-results.json");
    
    private static final List<PerformanceResult> results = new ArrayList<>();
    
    static boolean isForceJavaMode() {
        return Boolean.getBoolean("force.java");
    }
    
    record PerformanceResult(
        String testId,
        int widthPx,
        int heightPx,
        int megapixels,
        int paletteSize,
        String colorModel,
        long analyzeMs,
        long resynthesizeMs,
        double mpPerSecond,
        boolean nativeEnabled,
        boolean simdEnabled,
        boolean openclEnabled,
        String timestamp
    ) {}

    @BeforeAll
    static void setup() throws IOException {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        System.out.println("=== Load Test Environment ===");
        System.out.println("Native acceleration: " + accel.isAvailable());
        System.out.println("SIMD support: " + accel.hasSIMD());
        System.out.println("OpenCL support: " + accel.hasOpenCL());
        System.out.println("Runtime CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println("Force Java mode: " + NativeAccelerator.isForceJava());
        System.out.println("=============================\n");
        
        // Prepare output directory
        Files.createDirectories(OUTPUT_DIR);
    }
    
    @AfterAll
    static void exportResults() throws IOException {
        if (results.isEmpty()) {
            System.out.println("No performance results to export");
            return;
        }
        
        // Export CSV
        StringBuilder csv = new StringBuilder();
        csv.append("test_id,width_px,height_px,megapixels,palette_size,color_model,");
        csv.append("analyze_ms,resynthesize_ms,mp_per_second,native_enabled,simd_enabled,opencl_enabled,timestamp\n");
        
        for (PerformanceResult r : results) {
            csv.append(String.format("%s,%d,%d,%d,%d,%s,%d,%d,%.2f,%b,%b,%b,%s%n",
                r.testId, r.widthPx, r.heightPx, r.megapixels, r.paletteSize, r.colorModel,
                r.analyzeMs, r.resynthesizeMs, r.mpPerSecond,
                r.nativeEnabled, r.simdEnabled, r.openclEnabled, r.timestamp));
        }
        
        Files.writeString(CSV_FILE, csv.toString(), 
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("CSV results exported to: " + CSV_FILE.toAbsolutePath());
        
        // Export JSON
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generated\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
        json.append("  \"environment\": {\n");
        NativeAccelerator accel = NativeAccelerator.getInstance();
        json.append("    \"native_available\": ").append(accel.isAvailable()).append(",\n");
        json.append("    \"simd_enabled\": ").append(accel.hasSIMD()).append(",\n");
        json.append("    \"opencl_enabled\": ").append(accel.hasOpenCL()).append(",\n");
        json.append("    \"force_java\": ").append(NativeAccelerator.isForceJava()).append(",\n");
        json.append("    \"cpu_cores\": ").append(Runtime.getRuntime().availableProcessors()).append(",\n");
        json.append("    \"max_memory_mb\": ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append("\n");
        json.append("  },\n");
        json.append("  \"results\": [\n");
        
        for (int i = 0; i < results.size(); i++) {
            PerformanceResult r = results.get(i);
            json.append("    {\n");
            json.append("      \"test_id\": \"").append(r.testId).append("\",\n");
            json.append("      \"dimensions\": { \"width\": ").append(r.widthPx).append(", \"height\": ").append(r.heightPx).append(" },\n");
            json.append("      \"megapixels\": ").append(r.megapixels).append(",\n");
            json.append("      \"palette_size\": ").append(r.paletteSize).append(",\n");
            json.append("      \"color_model\": \"").append(r.colorModel).append("\",\n");
            json.append("      \"timing\": {\n");
            json.append("        \"analyze_ms\": ").append(r.analyzeMs).append(",\n");
            json.append("        \"resynthesize_ms\": ").append(r.resynthesizeMs).append(",\n");
            json.append("        \"mp_per_second\": ").append(String.format("%.2f", r.mpPerSecond)).append("\n");
            json.append("      },\n");
            json.append("      \"timestamp\": \"").append(r.timestamp).append("\"\n");
            json.append("    }").append(i < results.size() - 1 ? "," : "").append("\n");
        }
        
        json.append("  ]\n");
        json.append("}\n");
        
        Files.writeString(JSON_FILE, json.toString(),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("JSON results exported to: " + JSON_FILE.toAbsolutePath());
        
        System.out.println("\n=== Performance Results Summary ===");
        System.out.printf("%-25s %8s %8s %10s%n", "Test", "Analyze", "Resynth", "MP/s");
        System.out.println("-".repeat(55));
        for (PerformanceResult r : results) {
            System.out.printf("%-25s %7dms %7dms %9.1f%n",
                r.testId, r.analyzeMs, r.resynthesizeMs, r.mpPerSecond);
        }
        System.out.println("=".repeat(55));
    }

    @Test
    @Order(1)
    @DisplayName("2MP RGB k=256")
    @DisabledIf("isForceJavaMode")
    void test2MP_256colors_RGB() {
        // Note: First test may be slower due to JIT warmup and OpenCL initialization
        runLoadTest(1920, 1080, 256, ColorModel.RGB, 
            /* maxAnalyzeMs */ 10000, 
            /* maxResynthMs */ 10000); // Allow more time for first/cold test
    }

    @Test
    @Order(2)
    @DisplayName("2MP CIELAB k=256 - Analysis and Resynthesis")
    @DisabledIf("isForceJavaMode")
    void test2MP_256colors_CIELAB() {
        runLoadTest(1920, 1080, 256, ColorModel.CIELAB, 
            /* maxAnalyzeMs */ 15000, 
            /* maxResynthMs */ 3000);
    }

    @Test
    @Order(3)
    @DisplayName("2MP RGB k=512 - Analysis and Resynthesis")
    @DisabledIf("isForceJavaMode")
    void test2MP_512colors_RGB() {
        runLoadTest(1920, 1080, 512, ColorModel.RGB, 20000, 10000);
    }

    @Test
    @Order(4)
    @DisplayName("8MP RGB k=256")
    @DisabledIf("isForceJavaMode")
    void test8MP_256colors_RGB() {
        runLoadTest(3840, 2160, 256, ColorModel.RGB, 30000, 5000);
    }

    @Test
    @Order(5)
    @DisplayName("8MP CIELAB k=256")
    @DisabledIf("isForceJavaMode")
    void test8MP_256colors_CIELAB() {
        runLoadTest(3840, 2160, 256, ColorModel.CIELAB, 45000, 8000);
    }

    @Test
    @Order(6)
    @DisplayName("8MP RGB k=512")
    @DisabledIf("isForceJavaMode")
    void test8MP_512colors_RGB() {
        runLoadTest(3840, 2160, 512, ColorModel.RGB, 60000, 60000);
    }

    @Test
    @Order(7)
    @DisplayName("16MP RGB k=256")
    @DisabledIf("isForceJavaMode")
    void test16MP_256colors_RGB() {
        runLoadTest(4000, 4000, 256, ColorModel.RGB, 60000, 10000);
    }

    @Test
    @Order(8)
    @DisplayName("16MP RGB k=512")
    @DisabledIf("isForceJavaMode")
    void test16MP_512colors_RGB() {
        runLoadTest(4000, 4000, 512, ColorModel.RGB, 120000, 120000);
    }

    @Test
    @Order(10)
    @DisplayName("Scalability: k=64 vs k=256")
    void testScalabilityK() {
        BufferedImage source = createRealisticImage(2000, 2000, 1L);
        BufferedImage target = createRealisticImage(2000, 2000, 2L);
        
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, SEED);
        
        long start64 = System.nanoTime();
        ColorPalette src64 = engine.analyze(source, 64);
        ColorPalette tgt64 = engine.analyze(target, 64);
        engine.resynthesize(target, src64, tgt64);
        long time64 = System.nanoTime() - start64;
        
        long start256 = System.nanoTime();
        ColorPalette src256 = engine.analyze(source, 256);
        ColorPalette tgt256 = engine.analyze(target, 256);
        engine.resynthesize(target, src256, tgt256);
        long time256 = System.nanoTime() - start256;
        
        double ratio = (double) time256 / time64;
        
        System.out.printf("Scalability: k=64 took %.2fms, k=256 took %.2fms (ratio: %.2fx)%n",
            time64 / 1e6, time256 / 1e6, ratio);
        
        // k=256 should not take more than 10x k=64
        assertTrue(ratio < 10.0, 
            "k=256 should not be more than 10x slower than k=64, was " + ratio + "x");
    }

    @Test
    @Order(11)
    @DisplayName("Memory pressure test")
    void testMemoryPressure() {
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, SEED);
        
        for (int i = 0; i < 5; i++) {
            BufferedImage img = createRealisticImage(3000, 3000, i);
            ColorPalette palette = engine.analyze(img, 128);
            assertEquals(128, palette.size(), "Iteration " + i + " failed");
        }
        
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        
        System.out.printf("Memory: used=%dMB, max=%dMB (%.1f%%)%n",
            usedMemory / 1024 / 1024, maxMemory / 1024 / 1024, usagePercent);
        
        assertTrue(usagePercent < 80,
            "Memory usage too high after processing: " + String.format("%.1f%%", usagePercent));
    }

    @Test
    @Order(12)
    @DisplayName("Palette colors are within valid RGB range")
    void testPaletteColorValidity() {
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.CIELAB, SEED);
        BufferedImage image = createRealisticImage(2000, 2000, 123L);
        
        for (int k : new int[]{128, 256, 512}) {
            ColorPalette palette = engine.analyze(image, k);
            
            assertEquals(k, palette.size(), "Palette should have " + k + " colors");
            
            for (int i = 0; i < palette.size(); i++) {
                var color = palette.getColor(i);
                assertTrue(color.c1() >= 0 && color.c1() <= 255,
                    "R out of range at k=" + k + ": " + color.c1());
                assertTrue(color.c2() >= 0 && color.c2() <= 255,
                    "G out of range at k=" + k + ": " + color.c2());
                assertTrue(color.c3() >= 0 && color.c3() <= 255,
                    "B out of range at k=" + k + ": " + color.c3());
            }
        }
    }

    @Test
    @Order(13)
    @DisplayName("Resynthesis dimensions match input")
    void testResynthesisDimensions() {
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, SEED);
        
        int[][] sizes = {{1920, 1080}, {3840, 2160}, {4000, 4000}};
        
        for (int[] size : sizes) {
            BufferedImage source = createRealisticImage(size[0], size[1], 1L);
            BufferedImage target = createRealisticImage(size[0], size[1], 2L);
            
            ColorPalette srcPalette = engine.analyze(source, 256);
            ColorPalette tgtPalette = engine.analyze(target, 256);
            
            BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
            
            assertEquals(size[0], result.getWidth(), 
                "Width mismatch for " + size[0] + "x" + size[1]);
            assertEquals(size[1], result.getHeight(), 
                "Height mismatch for " + size[0] + "x" + size[1]);
        }
    }

    private void runLoadTest(int width, int height, int k, ColorModel model,
                            long maxAnalyzeMs, long maxResynthMs) {
        int megapixels = (width * height) / 1_000_000;
        String testId = String.format("%dMP %s k=%d", megapixels, model, k);
        System.out.printf("--- %s ---%n", testId);
        
        BufferedImage source = createRealisticImage(width, height, 1L);
        BufferedImage target = createRealisticImage(width, height, 2L);
        ImageHarmonyEngine engine = new ImageHarmonyEngine(model, SEED);
        
        if (megapixels <= 4) {
            BufferedImage warmup = createRealisticImage(500, 500, 0L);
            engine.analyze(warmup, Math.min(k, 64));
        }
        
        long startAnalyze = System.nanoTime();
        ColorPalette srcPalette = engine.analyze(source, k);
        ColorPalette tgtPalette = engine.analyze(target, k);
        long analyzeMs = (System.nanoTime() - startAnalyze) / 1_000_000;
        
        assertEquals(k, srcPalette.size(), "Source palette size mismatch");
        assertEquals(k, tgtPalette.size(), "Target palette size mismatch");
        
        long startResynth = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long resynthMs = (System.nanoTime() - startResynth) / 1_000_000;
        
        assertNotNull(result);
        assertEquals(width, result.getWidth());
        assertEquals(height, result.getHeight());
        
        double mpPerSec = resynthMs > 0 ? megapixels / (resynthMs / 1000.0) : 0;
        
        System.out.printf("  Analyze: %dms (limit: %dms)%n", analyzeMs, maxAnalyzeMs);
        System.out.printf("  Resynth: %dms (limit: %dms) [%.1f MP/s]%n", 
            resynthMs, maxResynthMs, mpPerSec);
        
        NativeAccelerator accel = NativeAccelerator.getInstance();
        results.add(new PerformanceResult(
            testId, width, height, megapixels, k, model.name(),
            analyzeMs, resynthMs, mpPerSec,
            accel.isAvailable(), accel.hasSIMD(), accel.hasOpenCL(),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ));
        
        assertTrue(analyzeMs < maxAnalyzeMs,
            testId + " analysis too slow: " + analyzeMs + "ms > " + maxAnalyzeMs + "ms");
        assertTrue(resynthMs < maxResynthMs,
            testId + " resynthesis too slow: " + resynthMs + "ms > " + maxResynthMs + "ms");
    }

    private BufferedImage createRealisticImage(int width, int height, long seed) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        
        int numBlobs = 5 + rnd.nextInt(4);
        int[] blobX = new int[numBlobs];
        int[] blobY = new int[numBlobs];
        int[][] blobColor = new int[numBlobs][3];
        
        for (int i = 0; i < numBlobs; i++) {
            blobX[i] = rnd.nextInt(width);
            blobY[i] = rnd.nextInt(height);
            blobColor[i][0] = rnd.nextInt(256);
            blobColor[i][1] = rnd.nextInt(256);
            blobColor[i][2] = rnd.nextInt(256);
        }
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double totalWeight = 0;
                double r = 0, g = 0, b = 0;
                
                for (int i = 0; i < numBlobs; i++) {
                    double dx = x - blobX[i];
                    double dy = y - blobY[i];
                    double dist = Math.sqrt(dx * dx + dy * dy) + 1;
                    double weight = 1.0 / (dist * dist);
                    
                    r += blobColor[i][0] * weight;
                    g += blobColor[i][1] * weight;
                    b += blobColor[i][2] * weight;
                    totalWeight += weight;
                }
                
                int noise = rnd.nextInt(16) - 8;
                int finalR = clamp((int)(r / totalWeight) + noise);
                int finalG = clamp((int)(g / totalWeight) + noise);
                int finalB = clamp((int)(b / totalWeight) + noise);
                
                img.setRGB(x, y, (finalR << 16) | (finalG << 8) | finalB);
            }
        }
        
        return img;
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
