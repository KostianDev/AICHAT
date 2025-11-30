package aichat;

import aichat.algorithm.HybridClusterer;
import aichat.model.ColorPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Hybrid Clustering algorithm.
 */
class AppTest {

    @Test
    @DisplayName("Hybrid clustering should return requested number of clusters")
    void testHybridClusteringReturnsCorrectNumberOfClusters() {
        // Create synthetic data with 3 distinct color clusters
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(42);
        
        // Red cluster around (200, 50, 50)
        for (int i = 0; i < 100; i++) {
            points.add(new ColorPoint(
                200 + random.nextDouble() * 30 - 15,
                50 + random.nextDouble() * 20 - 10,
                50 + random.nextDouble() * 20 - 10
            ));
        }
        
        // Green cluster around (50, 200, 50)
        for (int i = 0; i < 100; i++) {
            points.add(new ColorPoint(
                50 + random.nextDouble() * 20 - 10,
                200 + random.nextDouble() * 30 - 15,
                50 + random.nextDouble() * 20 - 10
            ));
        }
        
        // Blue cluster around (50, 50, 200)
        for (int i = 0; i < 100; i++) {
            points.add(new ColorPoint(
                50 + random.nextDouble() * 20 - 10,
                50 + random.nextDouble() * 20 - 10,
                200 + random.nextDouble() * 30 - 15
            ));
        }
        
        HybridClusterer clusterer = new HybridClusterer();
        int k = 3;
        
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        assertNotNull(centroids, "Result should not be null");
        assertEquals(k, centroids.size(), "Should return exactly " + k + " centroids");
    }

    @Test
    @DisplayName("Centroids should be within valid RGB range")
    void testCentroidsAreWithinValidRange() {
        List<ColorPoint> points = createRandomPoints(150, 42);
        
        HybridClusterer clusterer = new HybridClusterer();
        int k = 4;
        
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        for (ColorPoint centroid : centroids) {
            assertTrue(centroid.c1() >= 0 && centroid.c1() <= 255, 
                "Centroid R value " + centroid.c1() + " should be in [0, 255]");
            assertTrue(centroid.c2() >= 0 && centroid.c2() <= 255, 
                "Centroid G value " + centroid.c2() + " should be in [0, 255]");
            assertTrue(centroid.c3() >= 0 && centroid.c3() <= 255, 
                "Centroid B value " + centroid.c3() + " should be in [0, 255]");
        }
    }

    @Test
    @DisplayName("Clustering should be deterministic with same seed")
    void testClusteringIsDeterministic() {
        List<ColorPoint> points = createRandomPoints(100, 42);
        
        long seed = 12345;
        HybridClusterer clusterer1 = new HybridClusterer(seed);
        HybridClusterer clusterer2 = new HybridClusterer(seed);
        int k = 3;
        
        List<ColorPoint> result1 = clusterer1.cluster(points, k);
        List<ColorPoint> result2 = clusterer2.cluster(points, k);
        
        // Centroids should be identical
        assertEquals(result1.size(), result2.size());
        for (int i = 0; i < result1.size(); i++) {
            ColorPoint c1 = result1.get(i);
            ColorPoint c2 = result2.get(i);
            assertEquals(c1.c1(), c2.c1(), 0.001, "R values should match");
            assertEquals(c1.c2(), c2.c2(), 0.001, "G values should match");
            assertEquals(c1.c3(), c2.c3(), 0.001, "B values should match");
        }
    }

    @Test
    @DisplayName("Clustering should handle minimum input size")
    void testClusteringWithMinimumInput() {
        List<ColorPoint> points = new ArrayList<>();
        points.add(new ColorPoint(100, 100, 100));
        points.add(new ColorPoint(200, 200, 200));
        
        HybridClusterer clusterer = new HybridClusterer();
        int k = 2;
        
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        assertNotNull(centroids);
        assertEquals(2, centroids.size());
    }

    @Test
    @DisplayName("Clustering should work with k larger than distinct colors")
    void testClusteringWithLargeK() {
        // Only 3 distinct colors but requesting 5 clusters
        List<ColorPoint> points = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            points.add(new ColorPoint(255, 0, 0));  // Red
            points.add(new ColorPoint(0, 255, 0));  // Green
            points.add(new ColorPoint(0, 0, 255));  // Blue
        }
        
        HybridClusterer clusterer = new HybridClusterer();
        int k = 5;
        
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        assertNotNull(centroids);
        // Should return k centroids (some may be duplicates or very close)
        assertEquals(k, centroids.size());
    }

    @Test
    @DisplayName("Clustering should find representative colors for distinct clusters")
    void testClusteringFindsSeparateColors() {
        List<ColorPoint> points = new ArrayList<>();
        
        // Create tight red cluster
        for (int i = 0; i < 50; i++) {
            points.add(new ColorPoint(255, 0, 0));
        }
        
        // Create tight blue cluster
        for (int i = 0; i < 50; i++) {
            points.add(new ColorPoint(0, 0, 255));
        }
        
        HybridClusterer clusterer = new HybridClusterer();
        List<ColorPoint> centroids = clusterer.cluster(points, 2);
        
        assertEquals(2, centroids.size(), "Should have 2 centroids");
        
        // Check that we have one red-ish and one blue-ish centroid
        boolean hasRed = false, hasBlue = false;
        for (ColorPoint c : centroids) {
            if (c.c1() > 200 && c.c2() < 50 && c.c3() < 50) hasRed = true;
            if (c.c1() < 50 && c.c2() < 50 && c.c3() > 200) hasBlue = true;
        }
        
        assertTrue(hasRed, "Should have a red centroid");
        assertTrue(hasBlue, "Should have a blue centroid");
    }

    @Test
    @DisplayName("Clustering large dataset should complete within reasonable time")
    void testClusteringPerformance() {
        // 10000 random points - should complete in reasonable time
        List<ColorPoint> points = createRandomPoints(10000, 42);
        
        HybridClusterer clusterer = new HybridClusterer();
        
        long start = System.currentTimeMillis();
        List<ColorPoint> centroids = clusterer.cluster(points, 8);
        long elapsed = System.currentTimeMillis() - start;
        
        assertNotNull(centroids);
        assertEquals(8, centroids.size());
        assertTrue(elapsed < 5000, "Clustering 10000 points should take less than 5 seconds, took " + elapsed + "ms");
    }

    /**
     * Helper method to create random color points.
     */
    private List<ColorPoint> createRandomPoints(int count, long seed) {
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            points.add(new ColorPoint(
                random.nextDouble() * 255,
                random.nextDouble() * 255,
                random.nextDouble() * 255
            ));
        }
        
        return points;
    }
}