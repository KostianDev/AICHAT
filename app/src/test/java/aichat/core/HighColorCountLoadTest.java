package aichat.core;

import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.native_.NativeAccelerator;
import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load tests for high color count palettes (k=256, 512) on large images.
 * 
 * These tests validate:
 * 1. Scalability of clustering algorithm with high k values
 * 2. Memory efficiency with large palettes
 * 3. Resynthesis performance with/without LUT optimization
 * 4. Correctness under stress conditions
 * 
 * Test matrix:
 * - Image sizes: 2MP (1920x1080), 8MP (3840x2160), 16MP (4000x4000)
 * - Palette sizes: 256, 512 colors
 * - Color models: RGB, CIELAB
 */
@DisplayName("High Color Count Load Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HighColorCountLoadTest {

    private static final long SEED = 42L;

    @BeforeAll
    static void setup() {
        NativeAccelerator accel = NativeAccelerator.getInstance();
        System.out.println("=== Load Test Environment ===");
        System.out.println("Native acceleration: " + accel.isAvailable());
        System.out.println("SIMD support: " + accel.hasSIMD());
        System.out.println("OpenCL support: " + accel.hasOpenCL());
        System.out.println("Runtime CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println("=============================\n");
    }

    // ==================== 2MP Tests (1920x1080 - Full HD) ====================

    @Test
    @Order(1)
    @DisplayName("2MP RGB k=256 - Analysis and Resynthesis")
    void test2MP_256colors_RGB() {
        // Note: First test may be slower due to JIT warmup and OpenCL initialization
        runLoadTest(1920, 1080, 256, ColorModel.RGB, 
            /* maxAnalyzeMs */ 10000, 
            /* maxResynthMs */ 10000); // Allow more time for first/cold test
    }

    @Test
    @Order(2)
    @DisplayName("2MP CIELAB k=256 - Analysis and Resynthesis")
    void test2MP_256colors_CIELAB() {
        runLoadTest(1920, 1080, 256, ColorModel.CIELAB, 
            /* maxAnalyzeMs */ 15000, 
            /* maxResynthMs */ 3000);
    }

    @Test
    @Order(3)
    @DisplayName("2MP RGB k=512 - Analysis and Resynthesis")
    void test2MP_512colors_RGB() {
        runLoadTest(1920, 1080, 512, ColorModel.RGB, 
            /* maxAnalyzeMs */ 20000, 
            /* maxResynthMs */ 10000);
    }

    // ==================== 8MP Tests (3840x2160 - 4K UHD) ====================

    @Test
    @Order(4)
    @DisplayName("8MP RGB k=256 - Analysis and Resynthesis")
    void test8MP_256colors_RGB() {
        runLoadTest(3840, 2160, 256, ColorModel.RGB, 
            /* maxAnalyzeMs */ 30000, 
            /* maxResynthMs */ 5000);
    }

    @Test
    @Order(5)
    @DisplayName("8MP CIELAB k=256 - Full pipeline")
    void test8MP_256colors_CIELAB() {
        runLoadTest(3840, 2160, 256, ColorModel.CIELAB, 
            /* maxAnalyzeMs */ 45000, 
            /* maxResynthMs */ 8000);
    }

    @Test
    @Order(6)
    @DisplayName("8MP RGB k=512 - High palette load test")
    void test8MP_512colors_RGB() {
        runLoadTest(3840, 2160, 512, ColorModel.RGB, 
            /* maxAnalyzeMs */ 60000, 
            /* maxResynthMs */ 60000); // 512 colors bypasses LUT
    }

    // ==================== 16MP Tests (4000x4000) ====================

    @Test
    @Order(7)
    @DisplayName("16MP RGB k=256 - Maximum resolution test")
    void test16MP_256colors_RGB() {
        runLoadTest(4000, 4000, 256, ColorModel.RGB, 
            /* maxAnalyzeMs */ 60000, 
            /* maxResynthMs */ 10000);
    }

    @Test
    @Order(8)
    @DisplayName("16MP RGB k=512 - Stress test (no LUT)")
    void test16MP_512colors_RGB() {
        // This tests the direct search path (no LUT for k>256)
        runLoadTest(4000, 4000, 512, ColorModel.RGB, 
            /* maxAnalyzeMs */ 120000, 
            /* maxResynthMs */ 120000);
    }

    // ==================== Scalability Analysis ====================

    @Test
    @Order(10)
    @DisplayName("Scalability: k=64 vs k=256 on same image")
    void testScalabilityK() {
        BufferedImage source = createRealisticImage(2000, 2000, 1L);
        BufferedImage target = createRealisticImage(2000, 2000, 2L);
        
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, SEED);
        
        // k=64
        long start64 = System.nanoTime();
        ColorPalette src64 = engine.analyze(source, 64);
        ColorPalette tgt64 = engine.analyze(target, 64);
        engine.resynthesize(target, src64, tgt64);
        long time64 = System.nanoTime() - start64;
        
        // k=256
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
    @DisplayName("Memory pressure: Sequential large image processing")
    void testMemoryPressure() {
        ImageHarmonyEngine engine = new ImageHarmonyEngine(ColorModel.RGB, SEED);
        
        // Process 5 large images sequentially
        for (int i = 0; i < 5; i++) {
            BufferedImage img = createRealisticImage(3000, 3000, i);
            ColorPalette palette = engine.analyze(img, 128);
            
            assertEquals(128, palette.size(), "Iteration " + i + " failed");
        }
        
        // Force GC and wait
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        
        System.out.printf("Memory: used=%dMB, max=%dMB (%.1f%%)%n",
            usedMemory / 1024 / 1024, maxMemory / 1024 / 1024, usagePercent);
        
        // After processing and GC, should not use more than 80% of max heap
        assertTrue(usagePercent < 80,
            "Memory usage too high after processing: " + String.format("%.1f%%", usagePercent));
    }

    // ==================== Correctness under Load ====================

    @Test
    @Order(12)
    @DisplayName("Correctness: Palette colors are within valid RGB range")
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
    @DisplayName("Correctness: Resynthesis output dimensions match input")
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

    // ==================== Helper Methods ====================

    private void runLoadTest(int width, int height, int k, ColorModel model,
                            long maxAnalyzeMs, long maxResynthMs) {
        int megapixels = (width * height) / 1_000_000;
        String testId = String.format("%dMP %s k=%d", megapixels, model, k);
        
        System.out.printf("--- %s ---%n", testId);
        
        BufferedImage source = createRealisticImage(width, height, 1L);
        BufferedImage target = createRealisticImage(width, height, 2L);
        
        ImageHarmonyEngine engine = new ImageHarmonyEngine(model, SEED);
        
        // Warm-up
        if (megapixels <= 4) {
            BufferedImage warmup = createRealisticImage(500, 500, 0L);
            engine.analyze(warmup, Math.min(k, 64));
        }
        
        // Analyze
        long startAnalyze = System.nanoTime();
        ColorPalette srcPalette = engine.analyze(source, k);
        ColorPalette tgtPalette = engine.analyze(target, k);
        long analyzeMs = (System.nanoTime() - startAnalyze) / 1_000_000;
        
        assertEquals(k, srcPalette.size(), "Source palette size mismatch");
        assertEquals(k, tgtPalette.size(), "Target palette size mismatch");
        
        // Resynthesize
        long startResynth = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long resynthMs = (System.nanoTime() - startResynth) / 1_000_000;
        
        assertNotNull(result);
        assertEquals(width, result.getWidth());
        assertEquals(height, result.getHeight());
        
        double mpPerSec = megapixels / (resynthMs / 1000.0);
        
        System.out.printf("  Analyze: %dms (limit: %dms)%n", analyzeMs, maxAnalyzeMs);
        System.out.printf("  Resynth: %dms (limit: %dms) [%.1f MP/s]%n", 
            resynthMs, maxResynthMs, mpPerSec);
        
        assertTrue(analyzeMs < maxAnalyzeMs,
            testId + " analysis too slow: " + analyzeMs + "ms > " + maxAnalyzeMs + "ms");
        assertTrue(resynthMs < maxResynthMs,
            testId + " resynthesis too slow: " + resynthMs + "ms > " + maxResynthMs + "ms");
    }

    /**
     * Creates a realistic test image with multiple color regions and gradients.
     * Simulates a natural photograph with smooth transitions.
     */
    private BufferedImage createRealisticImage(int width, int height, long seed) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        
        // Create 5-8 color blobs
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
                // Blend based on distance to blobs
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
                
                // Add noise
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
