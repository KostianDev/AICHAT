package aichat.algorithm;

import aichat.model.ColorPoint;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HybridClustererProperties {

    private static final long FIXED_SEED = 42L;

    @Property
    @Label("Clustering always returns exactly k centroids for sufficient data")
    void clusteringReturnsExactlyKCentroids(
            @ForAll @IntRange(min = 2, max = 20) int k) {
        
        // Create enough points (at least 10x k)
        int n = k * 10;
        List<ColorPoint> points = generateRandomPoints(n, FIXED_SEED);
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        assertEquals(k, centroids.size(),
            "Should return exactly " + k + " centroids for " + n + " points");
    }

    @Property
    @Label("All centroids are within valid RGB bounds [0, 255]")
    void centroidsAreWithinValidBounds(
            @ForAll @IntRange(min = 50, max = 500) int n,
            @ForAll @IntRange(min = 2, max = 10) int k) {
        
        List<ColorPoint> points = generateRandomPoints(n, FIXED_SEED);
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        for (ColorPoint c : centroids) {
            assertTrue(c.c1() >= 0 && c.c1() <= 255,
                "R component " + c.c1() + " out of bounds");
            assertTrue(c.c2() >= 0 && c.c2() <= 255,
                "G component " + c.c2() + " out of bounds");
            assertTrue(c.c3() >= 0 && c.c3() <= 255,
                "B component " + c.c3() + " out of bounds");
        }
    }

    @Property
    @Label("Clustering is deterministic with same seed")
    void clusteringIsDeterministic(
            @ForAll @IntRange(min = 50, max = 200) int n,
            @ForAll @IntRange(min = 2, max = 8) int k,
            @ForAll @IntRange(min = 1, max = 1000000) int seed) {
        
        List<ColorPoint> points = generateRandomPoints(n, FIXED_SEED);
        
        HybridClusterer c1 = new HybridClusterer(seed);
        HybridClusterer c2 = new HybridClusterer(seed);
        
        List<ColorPoint> result1 = c1.cluster(points, k);
        List<ColorPoint> result2 = c2.cluster(points, k);
        
        assertEquals(result1.size(), result2.size());
        for (int i = 0; i < result1.size(); i++) {
            ColorPoint p1 = result1.get(i);
            ColorPoint p2 = result2.get(i);
            assertEquals(p1.c1(), p2.c1(), 0.001, "Mismatch at centroid " + i);
            assertEquals(p1.c2(), p2.c2(), 0.001, "Mismatch at centroid " + i);
            assertEquals(p1.c3(), p2.c3(), 0.001, "Mismatch at centroid " + i);
        }
    }

    @Property(tries = 20)
    @Label("Clustering finds well-separated clusters correctly")
    void clusteringFindsWellSeparatedClusters(
            @ForAll @IntRange(min = 2, max = 5) int numClusters) {
        
        // Create clearly separated clusters
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(FIXED_SEED);
        
        // Generate cluster centers far apart
        double[][] centers = new double[numClusters][3];
        for (int c = 0; c < numClusters; c++) {
            centers[c][0] = 30 + c * (200.0 / numClusters);
            centers[c][1] = 30 + ((c + 1) % numClusters) * (200.0 / numClusters);
            centers[c][2] = 30 + ((c + 2) % numClusters) * (200.0 / numClusters);
        }
        
        // Add tight points around each center
        for (int c = 0; c < numClusters; c++) {
            for (int p = 0; p < 30; p++) {
                points.add(new ColorPoint(
                    centers[c][0] + random.nextGaussian() * 5,
                    centers[c][1] + random.nextGaussian() * 5,
                    centers[c][2] + random.nextGaussian() * 5
                ));
            }
        }
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, numClusters);
        
        assertEquals(numClusters, centroids.size());
        
        // Each centroid should be close to one of the original centers
        for (int c = 0; c < numClusters; c++) {
            ColorPoint expectedCenter = new ColorPoint(centers[c][0], centers[c][1], centers[c][2]);
            
            boolean foundMatch = false;
            for (ColorPoint centroid : centroids) {
                if (centroid.distanceTo(expectedCenter) < 30) {
                    foundMatch = true;
                    break;
                }
            }
            
            assertTrue(foundMatch, 
                "Should find centroid near expected center " + expectedCenter);
        }
    }

    @Property
    @Label("Empty input returns empty result")
    void emptyInputReturnsEmptyResult(
            @ForAll @IntRange(min = 1, max = 20) int k) {
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> result = clusterer.cluster(new ArrayList<>(), k);
        
        assertTrue(result.isEmpty());
    }

    @Property
    @Label("Centroids minimize within-cluster distance")
    void centroidsMinimizeWithinClusterDistance(
            @ForAll @IntRange(min = 100, max = 300) int n,
            @ForAll @IntRange(min = 3, max = 6) int k) {
        
        List<ColorPoint> points = generateRandomPoints(n, FIXED_SEED);
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        // Calculate total within-cluster distance
        double totalDistance = 0;
        for (ColorPoint point : points) {
            double minDist = Double.MAX_VALUE;
            for (ColorPoint centroid : centroids) {
                minDist = Math.min(minDist, point.distanceTo(centroid));
            }
            totalDistance += minDist;
        }
        
        double avgDistance = totalDistance / n;
        
        // Average distance to nearest centroid should be reasonable
        // For random data in [0,255]^3, max distance is sqrt(3*255^2) ≈ 441
        // With k clusters, we expect average to be much smaller
        double maxExpectedAvg = 441.0 / Math.sqrt(k);
        
        assertTrue(avgDistance < maxExpectedAvg,
            "Average distance " + avgDistance + " exceeds expected " + maxExpectedAvg);
    }

    // Helper method
    private List<ColorPoint> generateRandomPoints(int count, long seed) {
        List<ColorPoint> points = new ArrayList<>(count);
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
