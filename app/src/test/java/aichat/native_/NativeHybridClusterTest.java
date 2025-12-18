package aichat.native_;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for hybrid_cluster native function.
 */
@DisplayName("Native hybrid_cluster Tests")
class NativeHybridClusterTest {
    
    private static NativeLibrary nativeLib;
    private static boolean available;
    
    // Default parameters matching production usage
    private static final int BLOCK_SIZE = 5000;
    private static final float DBSCAN_EPS = 30.0f;
    private static final int DBSCAN_MIN_PTS = 3;
    private static final int KMEANS_MAX_ITER = 50;
    private static final float KMEANS_THRESHOLD = 1.0f;
    
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
            
            float[] result = nativeLib.hybridCluster(arena, points, k, BLOCK_SIZE,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            assertEquals(k * 3, result.length);
        }
    }
    
    @Test
    @DisplayName("Small dataset delegates to K-Means (n <= blockSize*2)")
    void smallDatasetUsesKmeans() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // n=100 with blockSize=5000 -> definitely uses K-Means path
            float[] points = randomPoints(100, 42);
            int k = 4;
            
            float[] result = nativeLib.hybridCluster(arena, points, k, BLOCK_SIZE,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            assertEquals(k * 3, result.length);
            // Verify centroids are reasonable (within data range)
            for (float v : result) {
                assertTrue(v >= 0 && v <= 255);
            }
        }
    }
    
    @Test
    @DisplayName("Large dataset uses block-based processing")
    void largeDatasetUsesBlocks() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Use small blockSize to force block-based path
            int smallBlockSize = 100;
            float[] points = randomPoints(1000, 42);  // 10 blocks
            int k = 8;
            
            float[] result = nativeLib.hybridCluster(arena, points, k, smallBlockSize,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            assertEquals(k * 3, result.length);
            for (float v : result) {
                assertTrue(v >= 0 && v <= 255);
            }
        }
    }
    
    @Test
    @DisplayName("Same seed = same centroids")
    void sameSeedSameResult() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            float[] points = randomPoints(500, 42);
            int k = 8;
            long seed = 12345L;
            
            float[] r1 = nativeLib.hybridCluster(arena, points, k, BLOCK_SIZE,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, seed);
            float[] r2 = nativeLib.hybridCluster(arena, points, k, BLOCK_SIZE,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, seed);
            
            assertArrayEquals(r1, r2, 0.001f);
        }
    }
    
    @Test
    @DisplayName("Converges on well-separated clusters")
    void convergesOnSeparatedClusters() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // 3 well-separated clusters
            float[] points = new float[300 * 3];
            Random rand = new Random(42);
            int idx = 0;
            
            // Cluster at (50, 50, 50)
            for (int i = 0; i < 100; i++) {
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
            }
            // Cluster at (150, 150, 150)
            for (int i = 0; i < 100; i++) {
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
                points[idx++] = 150 + rand.nextFloat() * 20 - 10;
            }
            // Cluster at (200, 50, 200)
            for (int i = 0; i < 100; i++) {
                points[idx++] = 200 + rand.nextFloat() * 20 - 10;
                points[idx++] = 50 + rand.nextFloat() * 20 - 10;
                points[idx++] = 200 + rand.nextFloat() * 20 - 10;
            }
            
            float[] centroids = nativeLib.hybridCluster(arena, points, 3, BLOCK_SIZE,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            // Sort by R and verify
            List<float[]> sorted = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                sorted.add(new float[]{centroids[i*3], centroids[i*3+1], centroids[i*3+2]});
            }
            sorted.sort(Comparator.comparingDouble(c -> c[0]));
            
            assertEquals(50, sorted.get(0)[0], 15);
            assertEquals(150, sorted.get(1)[0], 15);
            assertEquals(200, sorted.get(2)[0], 15);
        }
    }
    
    @Test
    @DisplayName("Block processing produces distinct centroids")
    void blockProcessingProducesDistinctCentroids() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Force block-based path with small block size
            int smallBlockSize = 50;
            float[] points = randomPoints(500, 42);
            int k = 4;
            
            float[] centroids = nativeLib.hybridCluster(arena, points, k, smallBlockSize,
                DBSCAN_EPS, DBSCAN_MIN_PTS, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            // Verify centroids are distinct
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < k; i++) {
                unique.add(String.format("%.0f,%.0f,%.0f",
                    centroids[i*3], centroids[i*3+1], centroids[i*3+2]));
            }
            assertTrue(unique.size() > 1, "Centroids should be distinct");
        }
    }
    
    @Test
    @DisplayName("Handles k > representatives gracefully")
    void handlesKGreaterThanRepresentatives() {
        assumeTrue(available);
        
        try (Arena arena = Arena.ofConfined()) {
            // Very aggressive DBSCAN params to produce few representatives
            float[] points = randomPoints(100, 42);
            int k = 10;
            float tightEps = 5.0f;  // Very small eps -> few representatives
            
            float[] centroids = nativeLib.hybridCluster(arena, points, k, 20,
                tightEps, 5, KMEANS_MAX_ITER, KMEANS_THRESHOLD, 42L);
            
            // Should still return k centroids (algorithm fills from random points)
            assertEquals(k * 3, centroids.length);
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
