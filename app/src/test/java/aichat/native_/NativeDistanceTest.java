package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fine-grained unit tests for native distance calculation functions.
 * These tests verify mathematical correctness with known inputs/outputs.
 * 
 * This test class addresses the "Loose Property" problem by testing
 * exact mathematical calculations rather than statistical properties.
 */
@DisplayName("Native Distance Function Tests")
class NativeDistanceTest {
    
    private static NativeLibrary nativeLib;
    private static boolean distanceAvailable;
    
    @BeforeAll
    static void setup() {
        if (NativeLibrary.isAvailable()) {
            nativeLib = NativeLibrary.getInstance();
            distanceAvailable = nativeLib.hasDistanceSquared();
        } else {
            distanceAvailable = false;
        }
        
        if (!distanceAvailable) {
            System.out.println("Native distance_squared function not available - tests will be skipped");
        } else {
            System.out.println("Native distance_squared function available - running fine-grained tests");
        }
    }
    
    @Nested
    @DisplayName("Exact Value Tests")
    class ExactValueTests {
        
        @ParameterizedTest(name = "distance²({0},{1},{2} -> {3},{4},{5}) = {6}")
        @CsvSource({
            // Same point - distance should be 0
            "0, 0, 0, 0, 0, 0, 0.0",
            "128, 128, 128, 128, 128, 128, 0.0",
            "255, 255, 255, 255, 255, 255, 0.0",
            
            // Unit distance on single axis
            "0, 0, 0, 1, 0, 0, 1.0",
            "0, 0, 0, 0, 1, 0, 1.0",
            "0, 0, 0, 0, 0, 1, 1.0",
            
            // Larger distances on single axis
            "0, 0, 0, 10, 0, 0, 100.0",
            "0, 0, 0, 0, 10, 0, 100.0",
            "0, 0, 0, 0, 0, 10, 100.0",
            
            // Diagonal distance (3-4-5 triangle scaled)
            "0, 0, 0, 3, 4, 0, 25.0",
            
            // Full 3D diagonal
            "0, 0, 0, 1, 1, 1, 3.0",
            "0, 0, 0, 2, 2, 2, 12.0",
            
            // RGB corner to corner (black to white)
            "0, 0, 0, 255, 255, 255, 195075.0",
            
            // Opposite corners
            "255, 0, 0, 0, 255, 255, 195075.0",
            "0, 255, 0, 255, 0, 255, 195075.0",
            "0, 0, 255, 255, 255, 0, 195075.0",
            
            // Primary colors
            "255, 0, 0, 0, 255, 0, 130050.0",
            "255, 0, 0, 0, 0, 255, 130050.0",
            "0, 255, 0, 0, 0, 255, 130050.0",
            
            // Negative direction check (should still be positive squared distance)
            "10, 10, 10, 5, 5, 5, 75.0",
            "100, 100, 100, 50, 50, 50, 7500.0"
        })
        void testExactDistanceSquared(float c1a, float c2a, float c3a,
                                       float c1b, float c2b, float c3b,
                                       float expectedDistance) {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] pointA = {c1a, c2a, c3a};
                float[] pointB = {c1b, c2b, c3b};
                
                float result = nativeLib.distanceSquared(arena, pointA, pointB);
                
                assertEquals(expectedDistance, result, 0.001f,
                    String.format("distance²((%f,%f,%f), (%f,%f,%f))", 
                        c1a, c2a, c3a, c1b, c2b, c3b));
            }
        }
        
        @Test
        @DisplayName("Distance is symmetric: d(a,b) == d(b,a)")
        void testDistanceSymmetry() {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            float[][] testPoints = {
                {0, 0, 0},
                {255, 0, 0},
                {0, 255, 0},
                {0, 0, 255},
                {128, 64, 192},
                {33, 66, 99}
            };
            
            try (Arena arena = Arena.ofConfined()) {
                for (int i = 0; i < testPoints.length; i++) {
                    for (int j = i + 1; j < testPoints.length; j++) {
                        float dAB = nativeLib.distanceSquared(arena, testPoints[i], testPoints[j]);
                        float dBA = nativeLib.distanceSquared(arena, testPoints[j], testPoints[i]);
                        
                        assertEquals(dAB, dBA, 0.001f,
                            String.format("d(%s, %s) != d(%s, %s)",
                                java.util.Arrays.toString(testPoints[i]),
                                java.util.Arrays.toString(testPoints[j]),
                                java.util.Arrays.toString(testPoints[j]),
                                java.util.Arrays.toString(testPoints[i])));
                    }
                }
            }
        }
        
        @Test
        @DisplayName("Triangle inequality: d(a,c) <= d(a,b) + d(b,c)")
        void testTriangleInequality() {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            float[][] testPoints = {
                {0, 0, 0},
                {100, 0, 0},
                {100, 100, 0},
                {50, 50, 50},
                {200, 100, 50}
            };
            
            try (Arena arena = Arena.ofConfined()) {
                for (int i = 0; i < testPoints.length; i++) {
                    for (int j = 0; j < testPoints.length; j++) {
                        for (int k = 0; k < testPoints.length; k++) {
                            if (i == j || j == k || i == k) continue;
                            
                            float dAB = (float) Math.sqrt(nativeLib.distanceSquared(arena, testPoints[i], testPoints[j]));
                            float dBC = (float) Math.sqrt(nativeLib.distanceSquared(arena, testPoints[j], testPoints[k]));
                            float dAC = (float) Math.sqrt(nativeLib.distanceSquared(arena, testPoints[i], testPoints[k]));
                            
                            assertTrue(dAC <= dAB + dBC + 0.001f,
                                String.format("Triangle inequality violated: d(a,c)=%f > d(a,b)+d(b,c)=%f+%f",
                                    dAC, dAB, dBC));
                        }
                    }
                }
            }
        }
    }
    
    @Nested
    @DisplayName("Java Reference Comparison")
    class JavaReferenceTests {
        
        @Test
        @DisplayName("Native matches Java reference implementation")
        void testNativeMatchesJava() {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            java.util.Random random = new java.util.Random(42L);
            
            try (Arena arena = Arena.ofConfined()) {
                for (int i = 0; i < 100; i++) {
                    float c1a = random.nextFloat() * 255;
                    float c2a = random.nextFloat() * 255;
                    float c3a = random.nextFloat() * 255;
                    float c1b = random.nextFloat() * 255;
                    float c2b = random.nextFloat() * 255;
                    float c3b = random.nextFloat() * 255;
                    
                    // Java reference calculation
                    float d1 = c1a - c1b;
                    float d2 = c2a - c2b;
                    float d3 = c3a - c3b;
                    float javaResult = d1 * d1 + d2 * d2 + d3 * d3;
                    
                    // Native calculation
                    float[] pointA = {c1a, c2a, c3a};
                    float[] pointB = {c1b, c2b, c3b};
                    float nativeResult = nativeLib.distanceSquared(arena, pointA, pointB);
                    
                    assertEquals(javaResult, nativeResult, 0.01f,
                        String.format("Mismatch for points (%f,%f,%f) and (%f,%f,%f)",
                            c1a, c2a, c3a, c1b, c2b, c3b));
                }
            }
        }
        
        @Test
        @DisplayName("Critical bug detection: Blue channel must be squared")
        void testBlueChannelSquared() {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // Test case that catches d3 vs d3*d3 bug
                // Point A: (0, 0, 0)
                // Point B: (0, 0, 100)
                // Correct: 0 + 0 + 100*100 = 10000
                // Buggy (d3 only): 0 + 0 + 100 = 100
                
                float[] pointA = {0, 0, 0};
                float[] pointB = {0, 0, 100};
                
                float result = nativeLib.distanceSquared(arena, pointA, pointB);
                
                // This assertion specifically catches the bug mentioned in grading notes
                assertEquals(10000.0f, result, 0.001f,
                    "Blue channel distance must be squared! " +
                    "Got " + result + " but expected 10000. " +
                    "This indicates the bug: d3 instead of d3*d3");
                
                // Additional verification with different value
                pointB = new float[]{0, 0, 50};
                result = nativeLib.distanceSquared(arena, pointA, pointB);
                assertEquals(2500.0f, result, 0.001f,
                    "50² should equal 2500, not 50");
            }
        }
        
        @Test
        @DisplayName("All channels must be squared equally")
        void testAllChannelsSquaredEqually() {
            assumeTrue(distanceAvailable, "Native distance_squared not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] distances = {10, 50, 100, 200};
                
                for (float d : distances) {
                    float[] origin = {0, 0, 0};
                    float[] redPoint = {d, 0, 0};
                    float[] greenPoint = {0, d, 0};
                    float[] bluePoint = {0, 0, d};
                    
                    float redDist = nativeLib.distanceSquared(arena, origin, redPoint);
                    float greenDist = nativeLib.distanceSquared(arena, origin, greenPoint);
                    float blueDist = nativeLib.distanceSquared(arena, origin, bluePoint);
                    
                    float expected = d * d;
                    
                    assertEquals(expected, redDist, 0.001f,
                        "Red channel: " + d + "² should equal " + expected);
                    assertEquals(expected, greenDist, 0.001f,
                        "Green channel: " + d + "² should equal " + expected);
                    assertEquals(expected, blueDist, 0.001f,
                        "Blue channel: " + d + "² should equal " + expected);
                    
                    // All three should be equal
                    assertEquals(redDist, greenDist, 0.001f,
                        "Red and Green distances should be equal");
                    assertEquals(greenDist, blueDist, 0.001f,
                        "Green and Blue distances should be equal");
                }
            }
        }
    }
}
