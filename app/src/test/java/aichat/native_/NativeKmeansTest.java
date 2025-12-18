package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for kmeans_cluster native function.
 */
@DisplayName("Native kmeans_cluster Tests")
class NativeKmeansTest {
    
    private static NativeLibrary nativeLib;
    private static boolean available;
    
    @BeforeAll
    static void setup() {
        available = NativeLibrary.isAvailable();
        if (available) {
            nativeLib = NativeLibrary.getInstance();
        }
    }
    
    @ParameterizedTest(name = "k={0}")
    @ValueSource(ints = {2, 4, 8, 16})
    void returnsKCentroids(int k) {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = randomPoints(500, 42);
            
            float[] result = nativeLib.kmeansCluster(arena, points, k, 50, 1.0f, 42L);
            
            assertEquals(k * 3, result.length);
        }
    }
    
    @Test
    @DisplayName("Centroids within data range")
    void centroidsWithinRange() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Data in [50, 200]
            float[] points = new float[500 * 3];
            Random rand = new Random(42);
            for (int i = 0; i < points.length; i++) {
                points[i] = 50 + rand.nextFloat() * 150;
            }
            
            float[] centroids = nativeLib.kmeansCluster(arena, points, 5, 50, 1.0f, 42L);
            
            for (float v : centroids) {
                assertTrue(v >= 0 && v <= 255);
                assertTrue(v >= 30 && v <= 220, "Centroid should be roughly in data range");
            }
        }
    }
    
    @Test
    @DisplayName("Same seed = same centroids")
    void sameSeedSameResult() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = randomPoints(500, 42);
            
            float[] r1 = nativeLib.kmeansCluster(arena, points, 8, 50, 1.0f, 123L);
            float[] r2 = nativeLib.kmeansCluster(arena, points, 8, 50, 1.0f, 123L);
            
            assertArrayEquals(r1, r2, 0.001f);
        }
    }
    
    @Test
    @DisplayName("Converges on well-separated clusters")
    void convergesOnSeparatedClusters() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // 3 clusters at (50,50,50), (150,150,150), (200,50,200)
            float[] points = new float[300 * 3];
            Random rand = new Random(42);
            int idx = 0;
            
            for (int i = 0; i < 100; i++) {
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
            }
            for (int i = 0; i < 100; i++) {
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
            }
            for (int i = 0; i < 100; i++) {
                points[idx++] = 200 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 200 + rand.nextFloat() * 20 - 10;
            }
            
            float[] centroids = nativeLib.kmeansCluster(arena, points, 3, 50, 1.0f, 42L);
            
            // Sort by R and verify
            List<float[]> sorted = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                sorted.add(new float[]{centroids[i*3], centroids[i*3+1], centroids[i*3+2]});
            }
            sorted.sort(Comparator.comparingDouble(c -> c[0]));
            
            assertEquals(50, sorted.get(0)[0], 15);   // ~(50,50,50)
            assertEquals(150, sorted.get(1)[0], 15);  // ~(150,150,150)
            assertEquals(200, sorted.get(2)[0], 15);  // ~(200,50,200)
        }
    }
    
    @Test
    @DisplayName("k=1 returns mean")
    void k1ReturnsMean() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = { 0,0,0, 100,100,100, 200,200,200 };
            
            float[] centroids = nativeLib.kmeansCluster(arena, points, 1, 50, 1.0f, 42L);
            
            assertEquals(100, centroids[0], 1);
            assertEquals(100, centroids[1], 1);
            assertEquals(100, centroids[2], 1);
        }
    }
    
    @Test
    @DisplayName("Handles identical points")
    void identicalPoints() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = new float[100 * 3];
            Arrays.fill(points, 128);
            
            float[] centroids = nativeLib.kmeansCluster(arena, points, 5, 50, 1.0f, 42L);
            
            assertNotNull(centroids);
            assertEquals(15, centroids.length);
        }
    }
    
    @Test
    @DisplayName("k > 1 produces distinct centroids for varied data")
    void distinctCentroids() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = randomPoints(500, 42);
            
            float[] centroids = nativeLib.kmeansCluster(arena, points, 4, 50, 1.0f, 42L);
            
            // Verify centroids are not all identical
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                unique.add(String.format("%.0f,%.0f,%.0f", 
                    centroids[i*3], centroids[i*3+1], centroids[i*3+2]));
            }
            assertTrue(unique.size() > 1, "Centroids should be distinct for varied data");
        }
    }
    
    private float[] randomPoints(int n, long seed) {
        float[] points = new float[n * 3];
        Random rand = new Random(seed);
        for (int i = 0; i < points.length; i++) {
            points[i] = rand.nextFloat() * 255;
        }
        return points;
    }
}
