package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.foreign.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Minimal unit tests for distance_squared function.
 * Tests the Euclidean distance formula: d² = (x₁-x₀)² + (y₁-y₀)² + (z₁-z₀)²
 * 
 * Focuses on minimal coverage with maximum value:
 * - Exact values for key test cases
 * - Euclidean scaling property d(k*x) = k²*d(x) (catches all channel squaring bugs)
 * - Symmetry property d(a,b) = d(b,a)
 */
@DisplayName("Native distance_squared Tests")
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
    }
    
    @ParameterizedTest(name = "distance²({0},{1},{2} -> {3},{4},{5}) = {6}")
    @CsvSource({
        // Same point - distance should be 0
        "0, 0, 0, 0, 0, 0, 0.0",
        "255, 255, 255, 255, 255, 255, 0.0",
        
        // Single axis distances (tests all channels are squared)
        "0, 0, 0, 10, 0, 0, 100.0",
        "0, 0, 0, 0, 10, 0, 100.0",
        "0, 0, 0, 0, 0, 10, 100.0",
        
        // Catches the d3 vs d3*d3 bug specifically
        "0, 0, 0, 0, 0, 100, 10000.0",
        
        // 3D diagonal
        "0, 0, 0, 1, 1, 1, 3.0",
        
        // RGB corners
        "0, 0, 0, 255, 255, 255, 195075.0",
        "255, 0, 0, 0, 255, 0, 130050.0"
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
    void distanceIsSymmetric() {
        assumeTrue(distanceAvailable, "Native distance_squared not available");
        
        try (Arena arena = Arena.ofConfined()) {
            float[] a = {100, 50, 200};
            float[] b = {25, 150, 75};
            
            float dab = nativeLib.distanceSquared(arena, a, b);
            float dba = nativeLib.distanceSquared(arena, b, a);
            
            assertEquals(dab, dba, 0.001f, "Distance should be symmetric");
        }
    }
    
    @Test
    @DisplayName("Euclidean scaling property: d(k*x) = k²*d(x)")
    void euclideanScaling() {
        assumeTrue(distanceAvailable, "Native distance_squared not available");
        
        try (Arena arena = Arena.ofConfined()) {
            float[] origin = {0, 0, 0};
            float[] point = {10, 20, 30};
            
            float baseDist = nativeLib.distanceSquared(arena, origin, point);
            
            // Test scaling for all channels simultaneously
            for (float k : new float[]{2, 3, 5}) {
                float[] scaledPoint = {k * point[0], k * point[1], k * point[2]};
                float scaledDist = nativeLib.distanceSquared(arena, origin, scaledPoint);
                
                assertEquals(k * k * baseDist, scaledDist, 0.001f,
                    "d(" + k + "*x) should equal " + k + "²*d(x)");
            }
        }
    }
    
    @Test
    @DisplayName("Native matches Java reference implementation")
    void nativeMatchesJavaReference() {
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
}
