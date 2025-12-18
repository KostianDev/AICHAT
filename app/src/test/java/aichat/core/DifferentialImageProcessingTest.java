package aichat.core;

import aichat.model.ColorPalette;
import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;
import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests for posterize and resynthesize.
 */
@DisplayName("Differential Tests: Posterize & Resynthesize")
class DifferentialImageProcessingTest {
    
    private static final long SEED = 42L;
    private static NativeAccelerator nativeAccel;
    private static ImageHarmonyEngine engine;
    private static boolean nativeAvailable;
    
    @BeforeAll
    static void setup() {
        nativeAccel = NativeAccelerator.getInstance();
        engine = new ImageHarmonyEngine();
        nativeAvailable = nativeAccel.isAvailable();
    }
    
    @Nested
    @DisplayName("Posterize: Java == Native")
    class PosterizeDifferential {
        
        @Test
        @DisplayName("Small image posterization matches")
        void smallImagePosterize() {
            assumeTrue(nativeAvailable);
            
            BufferedImage image = createRandomImage(50, 50, SEED);
            ColorPalette palette = createTestPalette();
            
            // Java result
            BufferedImage javaResult = engine.posterizeJava(image, palette, palette);
            
            // Native result
            int[] pixels = image.getRGB(0, 0, 50, 50, null, 0, 50);
            int[] nativeResult = nativeAccel.posterizeImage(pixels, 50, 50, palette, palette);
            
            assertNotNull(nativeResult);
            assertPixelsMatch(javaResult, nativeResult, 50, 50, 0, "Posterize");
        }
        
        @Test
        @DisplayName("Posterize with palette mapping matches")
        void posterizeWithMapping() {
            assumeTrue(nativeAvailable);
            
            BufferedImage image = createRandomImage(30, 30, SEED);
            ColorPalette targetPalette = createTestPalette();
            ColorPalette sourcePalette = createDifferentPalette();
            
            BufferedImage javaResult = engine.posterizeJava(image, sourcePalette, targetPalette);
            
            int[] pixels = image.getRGB(0, 0, 30, 30, null, 0, 30);
            int[] nativeResult = nativeAccel.posterizeImage(pixels, 30, 30, targetPalette, sourcePalette);
            
            assertNotNull(nativeResult);
            assertPixelsMatch(javaResult, nativeResult, 30, 30, 0, "Posterize with mapping");
        }
    }
    
    @Nested
    @DisplayName("Resynthesize: Java == Native")
    class ResynthesizeDifferential {
        
        @Test
        @DisplayName("Small image resynthesis matches")
        void smallImageResynthesize() {
            assumeTrue(nativeAvailable);
            
            BufferedImage image = createRandomImage(50, 50, SEED);
            ColorPalette palette = createTestPalette();
            
            // Java result
            BufferedImage javaResult = engine.resynthesizeJava(image, palette, palette);
            
            // Native result
            int[] pixels = image.getRGB(0, 0, 50, 50, null, 0, 50);
            int[] nativeResult = nativeAccel.resynthesizeImage(pixels, 50, 50, palette, palette);
            
            assertNotNull(nativeResult);
            // Allow tolerance of 1 for floating point rounding
            assertPixelsMatch(javaResult, nativeResult, 50, 50, 1, "Resynthesize");
        }
        
        @Test
        @DisplayName("Resynthesize with palette mapping matches")
        void resynthesizeWithMapping() {
            assumeTrue(nativeAvailable);
            
            BufferedImage image = createRandomImage(30, 30, SEED);
            ColorPalette targetPalette = createTestPalette();
            ColorPalette sourcePalette = createDifferentPalette();
            
            BufferedImage javaResult = engine.resynthesizeJava(image, sourcePalette, targetPalette);
            
            int[] pixels = image.getRGB(0, 0, 30, 30, null, 0, 30);
            int[] nativeResult = nativeAccel.resynthesizeImage(pixels, 30, 30, targetPalette, sourcePalette);
            
            assertNotNull(nativeResult);
            assertPixelsMatch(javaResult, nativeResult, 30, 30, 1, "Resynthesize with mapping");
        }
        
        @Test
        @DisplayName("Edge colors clamp correctly")
        void edgeColorsClamping() {
            assumeTrue(nativeAvailable);
            
            // Create image with extreme values that will cause clamping
            BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    // White and black pixels
                    image.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFF : 0x000000);
                }
            }
            
            // Palette that will cause large offsets
            ColorPalette palette = new ColorPalette(List.of(
                new ColorPoint(128, 128, 128)  // Gray centroid
            ));
            
            BufferedImage javaResult = engine.resynthesizeJava(image, palette, palette);
            
            int[] pixels = image.getRGB(0, 0, 10, 10, null, 0, 10);
            int[] nativeResult = nativeAccel.resynthesizeImage(pixels, 10, 10, palette, palette);
            
            assertNotNull(nativeResult);
            assertPixelsMatch(javaResult, nativeResult, 10, 10, 1, "Clamping");
        }
    }
    
    // Helper methods
    
    private BufferedImage createRandomImage(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rand.nextInt(0xFFFFFF));
            }
        }
        return image;
    }
    
    private ColorPalette createTestPalette() {
        return new ColorPalette(List.of(
            new ColorPoint(0, 0, 0),       // Black
            new ColorPoint(255, 0, 0),     // Red
            new ColorPoint(0, 255, 0),     // Green
            new ColorPoint(0, 0, 255),     // Blue
            new ColorPoint(255, 255, 0),   // Yellow
            new ColorPoint(255, 0, 255),   // Magenta
            new ColorPoint(0, 255, 255),   // Cyan
            new ColorPoint(255, 255, 255)  // White
        ));
    }
    
    private ColorPalette createDifferentPalette() {
        return new ColorPalette(List.of(
            new ColorPoint(30, 30, 30),
            new ColorPoint(200, 50, 50),
            new ColorPoint(50, 200, 50),
            new ColorPoint(50, 50, 200),
            new ColorPoint(200, 200, 50),
            new ColorPoint(200, 50, 200),
            new ColorPoint(50, 200, 200),
            new ColorPoint(220, 220, 220)
        ));
    }
    
    private void assertPixelsMatch(BufferedImage javaResult, int[] nativeResult, 
                                   int width, int height, int tolerance, String context) {
        int mismatches = 0;
        int maxDiff = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int javaRgb = javaResult.getRGB(x, y) & 0xFFFFFF;
                int nativeRgb = nativeResult[idx] & 0xFFFFFF;
                
                int jr = (javaRgb >> 16) & 0xFF;
                int jg = (javaRgb >> 8) & 0xFF;
                int jb = javaRgb & 0xFF;
                
                int nr = (nativeRgb >> 16) & 0xFF;
                int ng = (nativeRgb >> 8) & 0xFF;
                int nb = nativeRgb & 0xFF;
                
                int diff = Math.max(Math.abs(jr - nr), Math.max(Math.abs(jg - ng), Math.abs(jb - nb)));
                if (diff > tolerance) {
                    mismatches++;
                    maxDiff = Math.max(maxDiff, diff);
                }
            }
        }
        
        assertEquals(0, mismatches,
            String.format("%s: %d pixel mismatches (max diff=%d, tolerance=%d)", 
                context, mismatches, maxDiff, tolerance));
    }
}
