package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.foreign.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fine-grained unit tests for assign_points_batch and find_nearest_centroid.
 * 
 * These tests address the critical gap: testing the minimum-finding loop logic
 * and parallel reduction logic, not just the simple distance_squared function.
 */
@DisplayName("Native assign_points_batch Tests")
class NativeAssignPointsTest {
    
    private static NativeLibrary nativeLib;
    private static boolean available;
    
    @BeforeAll
    static void setup() {
        available = NativeLibrary.isAvailable();
        if (available) {
            nativeLib = NativeLibrary.getInstance();
        }
    }
    
    @Nested
    @DisplayName("Exact Nearest-Centroid Logic")
    class ExactNearestTests {
        
        @Test
        @DisplayName("Single point equidistant from 2 centroids picks first")
        void equidistantPointPicksFirst() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // Point at (10, 10, 10)
                // Centroid 0 at (0, 10, 10) - distance² = 100
                // Centroid 1 at (20, 10, 10) - distance² = 100
                // Should pick centroid 0 (first)
                
                float[] points = {10, 10, 10};
                float[] centroids = {
                    0, 10, 10,
                    20, 10, 10
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(0, assignments[0], "Should pick first centroid when equidistant");
            }
        }
        
        @Test
        @DisplayName("Point clearly nearest to specific centroid")
        void clearNearestCentroid() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // Point at (5, 5, 5)
                // Centroid 0: (0, 0, 0) - distance² = 75
                // Centroid 1: (100, 100, 100) - distance² = 27075
                // Centroid 2: (10, 10, 10) - distance² = 75
                // Should pick centroid 0 (first among equals)
                
                float[] points = {5, 5, 5};
                float[] centroids = {
                    0, 0, 0,
                    100, 100, 100,
                    10, 10, 10
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(0, assignments[0], "Should pick nearest centroid (0)");
            }
        }
        
        @Test
        @DisplayName("Off-by-one detection: point much closer to centroid k-1")
        void noBoundaryError() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // Point at (255, 255, 255)
                // Centroids: (0, 0, 0), (50, 50, 50), (200, 200, 200), (254, 254, 254)
                // Should pick centroid 3 (k-1), not skip it due to off-by-one
                
                float[] points = {255, 255, 255};
                float[] centroids = {
                    0, 0, 0,
                    50, 50, 50,
                    200, 200, 200,
                    254, 254, 254
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(3, assignments[0], "Should pick last centroid (no off-by-one)");
            }
        }
        
        @ParameterizedTest(name = "RGB corner ({0},{1},{2}) -> centroid {3}")
        @CsvSource({
            "0, 0, 0, 0",       // Black -> centroid 0
            "255, 0, 0, 1",     // Red -> centroid 1
            "0, 255, 0, 2",     // Green -> centroid 2
            "0, 0, 255, 3",     // Blue -> centroid 3
            "255, 255, 0, 4",   // Yellow -> centroid 4
            "255, 0, 255, 5",   // Magenta -> centroid 5
            "0, 255, 255, 6",   // Cyan -> centroid 6
            "255, 255, 255, 7"  // White -> centroid 7
        })
        void rgbCornersAssignedCorrectly(float r, float g, float b, int expectedCentroid) {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // 8 centroids at RGB cube corners
                float[] centroids = {
                    0, 0, 0,         // 0: Black
                    255, 0, 0,       // 1: Red
                    0, 255, 0,       // 2: Green
                    0, 0, 255,       // 3: Blue
                    255, 255, 0,     // 4: Yellow
                    255, 0, 255,     // 5: Magenta
                    0, 255, 255,     // 6: Cyan
                    255, 255, 255    // 7: White
                };
                
                float[] point = {r, g, b};
                
                int[] assignments = nativeLib.assignPointsBatch(arena, point, centroids);
                
                assertEquals(expectedCentroid, assignments[0],
                    String.format("Point (%f,%f,%f) should assign to centroid %d",
                        r, g, b, expectedCentroid));
            }
        }
    }
    
    @Nested
    @DisplayName("Batch Assignment Logic")
    class BatchTests {
        
        @Test
        @DisplayName("Multiple points assigned correctly in batch")
        void batchAssignment() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                // 3 points, 3 centroids
                float[] points = {
                    5, 5, 5,        // Nearest to centroid 0
                    55, 55, 55,     // Nearest to centroid 1
                    205, 205, 205   // Nearest to centroid 2
                };
                float[] centroids = {
                    0, 0, 0,
                    50, 50, 50,
                    200, 200, 200
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(0, assignments[0], "Point 0 -> centroid 0");
                assertEquals(1, assignments[1], "Point 1 -> centroid 1");
                assertEquals(2, assignments[2], "Point 2 -> centroid 2");
            }
        }
        
        @Test
        @DisplayName("Point at exact centroid location")
        void pointAtCentroidLocation() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] points = {10, 10, 10};
                float[] centroids = {
                    10, 10, 10,  // Exact match
                    100, 100, 100
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(0, assignments[0], "Should assign to exact-match centroid");
            }
        }
        
        @Test
        @DisplayName("Multiple points with different nearest centroids")
        void multiplePointsDifferentCentroids() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                float[] points = {
                    10, 10, 10,     // Nearest to centroid 0 (dist² = 300)
                    90, 90, 90,     // Nearest to centroid 1 (dist² = 300)
                    210, 210, 210   // Nearest to centroid 2 (dist² = 300)
                };
                float[] centroids = {
                    0, 0, 0,
                    100, 100, 100,
                    200, 200, 200
                };
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(0, assignments[0], "Point 0 -> centroid 0");
                assertEquals(1, assignments[1], "Point 1 -> centroid 1");
                assertEquals(2, assignments[2], "Point 2 -> centroid 2");
            }
        }
        
        @Test
        @DisplayName("Parallel reduction: large batch produces valid assignments")
        void largeParallelBatch() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            try (Arena arena = Arena.ofConfined()) {
                int n = 10000;
                Random rand = new Random(12345);
                
                float[] points = new float[n * 3];
                float[] centroids = {
                    0, 0, 0,
                    128, 128, 128,
                    255, 255, 255
                };
                
                // Generate random points
                for (int i = 0; i < n * 3; i++) {
                    points[i] = rand.nextFloat() * 255;
                }
                
                int[] assignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                assertEquals(n, assignments.length, "Should return assignment for each point");
                
                // Verify all assignments are valid
                for (int i = 0; i < n; i++) {
                    assertTrue(assignments[i] >= 0 && assignments[i] < 3,
                        "Assignment " + i + " should be 0-2, got " + assignments[i]);
                }
            }
        }
    }
    
    @Nested
    @DisplayName("Java Reference Comparison")
    class DifferentialTests {
        
        @Test
        @DisplayName("Native matches Java reference implementation")
        void nativeMatchesJava() {
            assumeTrue(available, "Native assign_points_batch not available");
            
            Random rand = new Random(42);
            int n = 500;
            int k = 10;
            
            float[] points = new float[n * 3];
            float[] centroids = new float[k * 3];
            
            for (int i = 0; i < n * 3; i++) {
                points[i] = rand.nextFloat() * 255;
            }
            for (int i = 0; i < k * 3; i++) {
                centroids[i] = rand.nextFloat() * 255;
            }
            
            // Java reference implementation
            int[] javaAssignments = new int[n];
            for (int i = 0; i < n; i++) {
                float px = points[i * 3];
                float py = points[i * 3 + 1];
                float pz = points[i * 3 + 2];
                
                int nearest = 0;
                float minDist = Float.MAX_VALUE;
                
                for (int j = 0; j < k; j++) {
                    float cx = centroids[j * 3];
                    float cy = centroids[j * 3 + 1];
                    float cz = centroids[j * 3 + 2];
                    
                    float dx = px - cx;
                    float dy = py - cy;
                    float dz = pz - cz;
                    float dist = dx * dx + dy * dy + dz * dz;
                    
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = j;
                    }
                }
                
                javaAssignments[i] = nearest;
            }
            
            // Native implementation
            try (Arena arena = Arena.ofConfined()) {
                int[] nativeAssignments = nativeLib.assignPointsBatch(arena, points, centroids);
                
                // Compare results
                assertArrayEquals(javaAssignments, nativeAssignments,
                    "Native and Java should produce identical assignments");
            }
        }
    }
}
