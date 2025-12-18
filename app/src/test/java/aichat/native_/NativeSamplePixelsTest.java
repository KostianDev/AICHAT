package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for sample_pixels native function.
 * Reservoir sampling to select a random subset of pixels.
 */
@DisplayName("Native sample_pixels Tests")
class NativeSamplePixelsTest {
    
    private static NativeLibrary nativeLib;
    private static boolean available;
    
    @BeforeAll
    static void setup() {
        available = NativeLibrary.isAvailable();
        if (available) {
            nativeLib = NativeLibrary.getInstance();
        }
    }
    
    @ParameterizedTest(name = "Sample {0} from 1000")
    @ValueSource(ints = {1, 10, 100, 500})
    void exactSampleSize(int sampleSize) {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[1000 * 3];
            Random rand = new Random(42);
            for (int i = 0; i < input.length; i++) {
                input[i] = rand.nextFloat() * 255;
            }
            
            float[] result = nativeLib.samplePixels(arena, input, sampleSize, 42L);
            
            assertEquals(sampleSize * 3, result.length);
        }
    }
    
    @Test
    @DisplayName("Returns all when sample >= input")
    void returnAllWhenSmallInput() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[50 * 3];
            for (int i = 0; i < input.length; i++) {
                input[i] = (float) i;
            }
            
            float[] result = nativeLib.samplePixels(arena, input, 100, 42L);
            
            assertTrue(result.length <= 50 * 3);
        }
    }
    
    @Test
    @DisplayName("Same seed = same result")
    void sameSeedSameResult() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[1000 * 3];
            Random rand = new Random(42);
            for (int i = 0; i < input.length; i++) {
                input[i] = rand.nextFloat() * 255;
            }
            
            float[] result1 = nativeLib.samplePixels(arena, input, 100, 12345L);
            float[] result2 = nativeLib.samplePixels(arena, input, 100, 12345L);
            
            assertArrayEquals(result1, result2, 0.001f);
        }
    }
    
    @Test
    @DisplayName("Different seeds = different results")
    void differentSeedsDifferent() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] input = new float[1000 * 3];
            Random rand = new Random(42);
            for (int i = 0; i < input.length; i++) {
                input[i] = rand.nextFloat() * 255;
            }
            
            float[] result1 = nativeLib.samplePixels(arena, input, 100, 1L);
            float[] result2 = nativeLib.samplePixels(arena, input, 100, 2L);
            
            assertFalse(Arrays.equals(result1, result2));
        }
    }
    
    @Test
    @DisplayName("Sampled values exist in input")
    void valuesFromInputSet() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Distinct identifiable values
            float[] input = new float[100 * 3];
            Set<String> inputPoints = new HashSet<>();
            
            for (int i = 0; i < 100; i++) {
                float r = i * 2.5f;
                float g = i * 2.0f;
                float b = i * 1.5f;
                input[i * 3] = r;
                input[i * 3 + 1] = g;
                input[i * 3 + 2] = b;
                inputPoints.add(String.format("%.1f,%.1f,%.1f", r, g, b));
            }
            
            float[] result = nativeLib.samplePixels(arena, input, 50, 42L);
            
            for (int i = 0; i < result.length / 3; i++) {
                String point = String.format("%.1f,%.1f,%.1f",
                    result[i * 3], result[i * 3 + 1], result[i * 3 + 2]);
                assertTrue(inputPoints.contains(point), "Sampled point not in input");
            }
        }
    }
    
    @Test
    @DisplayName("Image sampling extracts RGB correctly")
    void imageRgbExtraction() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            int[] image = { 0xFF0000, 0x00FF00, 0x0000FF };
            
            float[] result = nativeLib.samplePixelsFromImage(arena, image, 3, 42L);
            
            Set<String> colors = new HashSet<>();
            for (int i = 0; i < result.length / 3; i++) {
                colors.add(String.format("%d,%d,%d",
                    (int) result[i * 3], (int) result[i * 3 + 1], (int) result[i * 3 + 2]));
            }
            
            assertTrue(colors.contains("255,0,0") || colors.contains("0,255,0") || colors.contains("0,0,255"));
        }
    }
}
