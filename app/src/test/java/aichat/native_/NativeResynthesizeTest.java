package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for resynthesize_image native function.
 * Resynthesis transfers pixel offsets from target to source palette.
 */
@DisplayName("Native resynthesize_image Tests")
class NativeResynthesizeTest {
    
    private static NativeLibrary nativeLib;
    private static boolean available;
    
    @BeforeAll
    static void setup() {
        available = NativeLibrary.isAvailable();
        if (available) {
            nativeLib = NativeLibrary.getInstance();
        }
    }
    
    @Test
    @DisplayName("Pixel at centroid produces exact source color")
    void pixelAtCentroidProducesExactSource() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0x808080 };  // Exactly at target
            float[] target = { 128, 128, 128 };
            float[] source = { 255, 0, 0 };  // Red
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 1, 1, target, source);
            
            assertEquals(0xFF0000, result[0] & 0xFFFFFF, "Zero offset -> exact source");
        }
    }
    
    @ParameterizedTest(name = "Offset from ({0},{1},{2}) target={3},{4},{5} source={6},{7},{8}")
    @CsvSource({
        "150, 150, 150, 128, 128, 128, 100, 100, 100, 122, 122, 122",  // +22 uniform offset
        "100, 100, 100, 128, 128, 128, 200, 200, 200, 172, 172, 172",  // -28 uniform offset
        "128, 128, 128, 128, 128, 128, 64, 64, 64, 64, 64, 64",        // Zero offset
        "180, 100, 50, 128, 128, 128, 100, 100, 100, 152, 72, 22",     // Different offset per channel (+52, -28, -78)
    })
    void offsetTransferred(int pr, int pg, int pb, int tr, int tg, int tb,
                          int sr, int sg, int sb, int er, int eg, int eb) {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { (pr << 16) | (pg << 8) | pb };
            float[] target = { tr, tg, tb };
            float[] source = { sr, sg, sb };
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 1, 1, target, source);
            
            int rr = (result[0] >> 16) & 0xFF;
            int rg = (result[0] >> 8) & 0xFF;
            int rb = result[0] & 0xFF;
            
            assertEquals(er, rr, 2, "R mismatch");
            assertEquals(eg, rg, 2, "G mismatch");
            assertEquals(eb, rb, 2, "B mismatch");
        }
    }
    
    @Test
    @DisplayName("Clamps to 255 maximum")
    void clampToMax() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Offset +127, source 200 -> raw 327 -> clamp 255
            int[] pixels = { 0xFFFFFF };
            float[] target = { 128, 128, 128 };
            float[] source = { 200, 200, 200 };
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 1, 1, target, source);
            
            assertEquals(0xFFFFFF, result[0] & 0xFFFFFF, "Should clamp to 255");
        }
    }
    
    @Test
    @DisplayName("Clamps to 0 minimum")
    void clampToMin() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Offset -128, source 50 -> raw -78 -> clamp 0
            int[] pixels = { 0x000000 };
            float[] target = { 128, 128, 128 };
            float[] source = { 50, 50, 50 };
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 1, 1, target, source);
            
            assertEquals(0x000000, result[0] & 0xFFFFFF, "Should clamp to 0");
        }
    }
    
    @Test
    @DisplayName("Pixel selects nearest centroid from multiple")
    void correctNearestSelection() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0xC80000 };  // (200, 0, 0) - close to red
            
            float[] target = { 0,0,0, 255,0,0, 0,255,0 };
            float[] source = { 50,50,50, 100,50,50, 50,100,50 };
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 1, 1, target, source);
            
            // Nearest is red (index 1), offset ~(-55,0,0), result ~(45,50,50)
            int r = (result[0] >> 16) & 0xFF;
            assertEquals(45, r, 5, "Should use red centroid");
        }
    }
    
    @Test
    @DisplayName("Different pixels use different centroids")
    void differentCentroids() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0x000000, 0xFF0000, 0x00FF00 };
            float[] target = { 0,0,0, 255,0,0, 0,255,0 };
            float[] source = { 50,50,50, 200,100,100, 100,200,100 };
            
            int[] result = nativeLib.resynthesizeImage(arena, pixels, 3, 1, target, source);
            
            // Black pixel -> centroid 0 -> gray
            assertEquals(50, (result[0] >> 16) & 0xFF, 2);
            // Red pixel -> centroid 1 -> pinkish
            assertEquals(200, (result[1] >> 16) & 0xFF, 2);
            // Green pixel -> centroid 2 -> light green
            assertEquals(200, (result[2] >> 8) & 0xFF, 2);
        }
    }
}
