package aichat;

import aichat.core.ImageHarmonyEngine;
import aichat.core.ImageHarmonyEngine.ColorModel;
import aichat.model.ColorPalette;
import aichat.native_.NativeAccelerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for large image resynthesis.
 * These tests verify that native acceleration meets performance targets.
 * 
 * Disabled in Java-only mode (force.java=true) because Java fallback
 * is intentionally slower and would fail performance thresholds.
 */
@DisplayName("Large Image Resynthesis Performance Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledIf("isForceJavaMode")
class LargeImageResynthesisTest {
    
    private static ImageHarmonyEngine engine;
    
    static boolean isForceJavaMode() {
        return Boolean.getBoolean("force.java");
    }
    
    @BeforeAll
    static void setup() {
        engine = new ImageHarmonyEngine(ColorModel.RGB, 42L);
        System.out.println("Native available: " + NativeAccelerator.getInstance().isAvailable());
        System.out.println("SIMD: " + NativeAccelerator.getInstance().hasSIMD());
    }
    
    private BufferedImage createGradientImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(123);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = ((x + y) * 127) / (width + height) + rnd.nextInt(20);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }
    
    @Test
    @Order(1)
    @DisplayName("Resynthesis 1000x1000 (1MP) with 64 colors")
    void resynthesis1MP_64colors() {
        BufferedImage source = createGradientImage(1000, 1000);
        BufferedImage target = createGradientImage(1000, 1000);
        
        ColorPalette srcPalette = engine.analyze(source, 64);
        ColorPalette tgtPalette = engine.analyze(target, 64);
        
        // Warm up
        engine.resynthesize(target, srcPalette, tgtPalette);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(1000, result.getWidth());
        assertEquals(1000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        System.out.printf("1MP, 64 colors: %.2fms (%.2f MP/s)%n", ms, 1.0 / (ms / 1000));
        
        assertTrue(ms < 500, "Should complete in under 500ms, took " + ms + "ms");
    }
    
    @Test
    @Order(2)
    @DisplayName("Resynthesis 2000x2000 (4MP) with 128 colors")
    void resynthesis4MP_128colors() {
        BufferedImage source = createGradientImage(2000, 2000);
        BufferedImage target = createGradientImage(2000, 2000);
        
        ColorPalette srcPalette = engine.analyze(source, 128);
        ColorPalette tgtPalette = engine.analyze(target, 128);
        
        // Warm up
        engine.resynthesize(target, srcPalette, tgtPalette);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(2000, result.getWidth());
        assertEquals(2000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        System.out.printf("4MP, 128 colors: %.2fms (%.2f MP/s)%n", ms, 4.0 / (ms / 1000));
        
        assertTrue(ms < 1000, "Should complete in under 1s, took " + ms + "ms");
    }
    
    @Test
    @Order(3)
    @DisplayName("Resynthesis 4000x4000 (16MP) with 256 colors")
    void resynthesis16MP_256colors() {
        BufferedImage source = createGradientImage(4000, 4000);
        BufferedImage target = createGradientImage(4000, 4000);
        
        ColorPalette srcPalette = engine.analyze(source, 256);
        ColorPalette tgtPalette = engine.analyze(target, 256);
        
        // Warm up
        engine.resynthesize(target, srcPalette, tgtPalette);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(4000, result.getWidth());
        assertEquals(4000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        System.out.printf("16MP, 256 colors: %.2fms (%.2f MP/s)%n", ms, 16.0 / (ms / 1000));
        
        assertTrue(ms < 5000, "Should complete in under 5s, took " + ms + "ms");
    }
    
    @Test
    @Order(4)
    @DisplayName("Resynthesis 4000x4000 (16MP) with 512 colors")
    void resynthesis16MP_512colors() {
        BufferedImage source = createGradientImage(4000, 4000);
        BufferedImage target = createGradientImage(4000, 4000);
        
        ColorPalette srcPalette = engine.analyze(source, 512);
        ColorPalette tgtPalette = engine.analyze(target, 512);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(4000, result.getWidth());
        assertEquals(4000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        System.out.printf("16MP, 512 colors (no LUT): %.2fms (%.2f MP/s)%n", ms, 16.0 / (ms / 1000));
        
        // 512 colors uses direct search, expect slower but still reasonable
        assertTrue(ms < 30000, "Should complete in under 30s, took " + ms + "ms");
    }
    
    @Test
    @Order(5)
    @DisplayName("Tiled resynthesis for large image (simulated 8K)")
    void tiledResynthesis() {
        // 8000x6000 = 48MP - will trigger tiled processing
        BufferedImage source = createGradientImage(4000, 3000); // 12MP for speed
        BufferedImage target = createGradientImage(4000, 3000);
        
        ColorPalette srcPalette = engine.analyze(source, 64);
        ColorPalette tgtPalette = engine.analyze(target, 64);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(4000, result.getWidth());
        assertEquals(3000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        System.out.printf("12MP, 64 colors: %.2fms (%.2f MP/s)%n", ms, 12.0 / (ms / 1000));
        
        assertTrue(ms < 5000, "Should complete in under 5s, took " + ms + "ms");
    }
    
    @Test
    @Order(6)
    @DisplayName("Very large simulated image (>16MP triggers tiling)")
    void veryLargeImageTiling() {
        // Create 5000x4000 = 20MP image (triggers tiled processing at 16MP threshold)
        BufferedImage source = createGradientImage(5000, 4000);
        BufferedImage target = createGradientImage(5000, 4000);
        
        ColorPalette srcPalette = engine.analyze(source, 64);
        ColorPalette tgtPalette = engine.analyze(target, 64);
        
        long start = System.nanoTime();
        BufferedImage result = engine.resynthesize(target, srcPalette, tgtPalette);
        long elapsed = System.nanoTime() - start;
        
        assertNotNull(result);
        assertEquals(5000, result.getWidth());
        assertEquals(4000, result.getHeight());
        
        double ms = elapsed / 1_000_000.0;
        double mpPerSec = 20.0 / (ms / 1000);
        System.out.printf("20MP (tiled), 64 colors: %.2fms (%.2f MP/s)%n", ms, mpPerSec);
        
        assertTrue(ms < 10000, "Should complete in under 10s, took " + ms + "ms");
        assertTrue(mpPerSec > 5, "Should achieve at least 5 MP/s");
    }
}
