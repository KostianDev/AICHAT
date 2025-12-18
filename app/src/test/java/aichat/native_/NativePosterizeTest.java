package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.foreign.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for posterize_image native function.
 * Posterization replaces each pixel with the nearest palette color.
 */
@DisplayName("Native posterize_image Tests")
class NativePosterizeTest {
    
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
    @DisplayName("Single pixel posterizes to nearest palette color")
    void singlePixelPosterize() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0xFF8040 };  // RGB(255, 128, 64) - reddish
            float[] palette = { 255, 0, 0, 0, 0, 255 };  // Red and Blue
            
            int[] result = nativeLib.posterizeImage(arena, pixels, 1, 1, palette, palette);
            
            assertEquals(0xFF0000, result[0] & 0xFFFFFF, "Should map to red");
        }
    }
    
    @Test
    @DisplayName("Output contains only palette colors")
    void outputContainsOnlyPaletteColors() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = new int[100];
            Random rand = new Random(42);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = rand.nextInt(0xFFFFFF);
            }
            
            float[] palette = { 0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0, 255 };
            Set<Integer> expected = Set.of(0x000000, 0xFF0000, 0x00FF00, 0x0000FF);
            
            int[] result = nativeLib.posterizeImage(arena, pixels, 10, 10, palette, palette);
            
            for (int i = 0; i < result.length; i++) {
                assertTrue(expected.contains(result[i] & 0xFFFFFF),
                    String.format("Pixel %d has unexpected color", i));
            }
        }
    }
    
    @Test
    @DisplayName("Palette mapping: target->source index preserved")
    void paletteMappingApplied() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0xFF0000 };  // Red pixel
            float[] target = { 255, 0, 0 };  // Target: red at 0
            float[] source = { 0, 255, 0 };  // Source: green at 0
            
            int[] result = nativeLib.posterizeImage(arena, pixels, 1, 1, target, source);
            
            assertEquals(0x00FF00, result[0] & 0xFFFFFF, "Red -> green via mapping");
        }
    }
    
    @ParameterizedTest(name = "Pixel ({0},{1},{2}) -> index {3}")
    @CsvSource({
        "0, 0, 0, 0",         // Black -> Black
        "255, 255, 255, 1",   // White -> White
        "200, 50, 50, 2",     // Near red -> Red
        "50, 200, 50, 3",     // Near green -> Green
        "50, 50, 200, 4",     // Near blue -> Blue
    })
    void nearestNeighborSelection(int r, int g, int b, int expectedIndex) {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { (r << 16) | (g << 8) | b };
            
            // 5-color target palette
            float[] target = { 0,0,0, 255,255,255, 255,0,0, 0,255,0, 0,0,255 };
            // Distinguishable source colors
            float[] source = { 10,10,10, 245,245,245, 240,20,20, 20,240,20, 20,20,240 };
            
            int[] result = nativeLib.posterizeImage(arena, pixels, 1, 1, target, source);
            
            int expR = (int) source[expectedIndex * 3];
            int expG = (int) source[expectedIndex * 3 + 1];
            int expB = (int) source[expectedIndex * 3 + 2];
            assertEquals((expR << 16) | (expG << 8) | expB, result[0] & 0xFFFFFF);
        }
    }
    
    @Test
    @DisplayName("All RGB channels contribute to distance")
    void allChannelsContribute() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Three centroids differ only in one channel each
            float[] target = { 0,128,128, 128,0,128, 128,128,0 };
            float[] source = { 255,0,0, 0,255,0, 0,0,255 };  // R, G, B
            
            // Pixel close to centroid 0 (low R)
            int[] px0 = { 0x108080 };
            assertEquals(0xFF0000, nativeLib.posterizeImage(arena, px0, 1, 1, target, source)[0] & 0xFFFFFF);
            
            // Pixel close to centroid 1 (low G)
            int[] px1 = { 0x801080 };
            assertEquals(0x00FF00, nativeLib.posterizeImage(arena, px1, 1, 1, target, source)[0] & 0xFFFFFF);
            
            // Pixel close to centroid 2 (low B)
            int[] px2 = { 0x808010 };
            assertEquals(0x0000FF, nativeLib.posterizeImage(arena, px2, 1, 1, target, source)[0] & 0xFFFFFF);
        }
    }
    
    @Test
    @DisplayName("Single color palette maps everything to it")
    void singleColorPalette() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] pixels = { 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000 };
            float[] palette = { 128, 128, 128 };
            
            int[] result = nativeLib.posterizeImage(arena, pixels, 5, 1, palette, palette);
            
            for (int p : result) {
                assertEquals(0x808080, p & 0xFFFFFF);
            }
        }
    }
}
