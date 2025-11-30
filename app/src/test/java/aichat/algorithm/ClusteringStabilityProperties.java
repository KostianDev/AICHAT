package aichat.algorithm;

import aichat.model.ColorPoint;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-Based Tests for clustering stability and correctness.
 * 
 * These tests verify mathematical properties that should always hold
 * regardless of input data:
 * 
 * 1. Stability: Same input + same seed = same output
 * 2. Cluster separation: Well-separated input data should yield distinct clusters
 * 3. Bounds: All centroids within input data bounds
 * 4. Convergence: Algorithm should find reasonable local minimum
 */
class ClusteringStabilityProperties {

    private static final long FIXED_SEED = 42L;

    // ==================== Stability Properties ====================

    @Property(tries = 50)
    @Label("Clustering is deterministic: same seed produces identical results")
    void clusteringIsDeterministic(
            @ForAll @IntRange(min = 100, max = 500) int numPoints,
            @ForAll @IntRange(min = 2, max = 16) int k,
            @ForAll @IntRange(min = 1, max = 999999) int seed) {
        
        List<ColorPoint> points = generateRandomPoints(numPoints, FIXED_SEED);
        
        HybridClusterer clusterer1 = new HybridClusterer(seed);
        HybridClusterer clusterer2 = new HybridClusterer(seed);
        
        List<ColorPoint> result1 = clusterer1.cluster(points, k);
        List<ColorPoint> result2 = clusterer2.cluster(points, k);
        
        assertEquals(result1.size(), result2.size(), 
            "Deterministic clustering should produce same number of centroids");
        
        for (int i = 0; i < result1.size(); i++) {
            ColorPoint c1 = result1.get(i);
            ColorPoint c2 = result2.get(i);
            
            assertEquals(c1.c1(), c2.c1(), 0.001, 
                "R component should be identical at index " + i);
            assertEquals(c1.c2(), c2.c2(), 0.001, 
                "G component should be identical at index " + i);
            assertEquals(c1.c3(), c2.c3(), 0.001, 
                "B component should be identical at index " + i);
        }
    }

    // ==================== Cluster Separation Properties ====================

    @Property(tries = 30)
    @Label("Well-separated clusters are correctly identified")
    void wellSeparatedClustersAreIdentified(
            @ForAll @IntRange(min = 2, max = 6) int numClusters) {
        
        // Generate clearly separated clusters
        List<ColorPoint> points = generateSeparatedClusters(numClusters, 50, FIXED_SEED);
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, numClusters);
        
        assertEquals(numClusters, centroids.size(),
            "Should find exactly " + numClusters + " clusters");
        
        // Each centroid should be close to one of the original cluster centers
        double[][] expectedCenters = generateExpectedCenters(numClusters);
        
        for (double[] expected : expectedCenters) {
            ColorPoint expectedPoint = new ColorPoint(expected[0], expected[1], expected[2]);
            
            boolean foundMatch = false;
            for (ColorPoint centroid : centroids) {
                if (centroid.distanceTo(expectedPoint) < 40) { // Within reasonable distance
                    foundMatch = true;
                    break;
                }
            }
            
            assertTrue(foundMatch,
                "Should find centroid near expected center " + Arrays.toString(expected));
        }
    }

    @Property(tries = 20)
    @Label("Dominant color should always be in palette")
    void dominantColorIsAlwaysInPalette(
            @ForAll @IntRange(min = 2, max = 10) int k) {
        
        // Create data with one very dominant color (80% of points)
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(FIXED_SEED);
        
        // Dominant: dark blue
        ColorPoint dominant = new ColorPoint(30, 30, 200);
        for (int i = 0; i < 800; i++) {
            points.add(new ColorPoint(
                dominant.c1() + random.nextGaussian() * 5,
                dominant.c2() + random.nextGaussian() * 5,
                dominant.c3() + random.nextGaussian() * 5
            ));
        }
        
        // Minority: random colors
        for (int i = 0; i < 200; i++) {
            points.add(new ColorPoint(
                random.nextDouble() * 255,
                random.nextDouble() * 255,
                random.nextDouble() * 255
            ));
        }
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        // At least one centroid should be close to the dominant color
        boolean foundDominant = centroids.stream()
            .anyMatch(c -> c.distanceTo(dominant) < 30);
        
        assertTrue(foundDominant,
            "Dominant color should be represented in palette with k=" + k);
    }

    // ==================== Bounds Properties ====================

    @Property
    @Label("All centroids are within valid RGB bounds [0, 255]")
    void centroidsWithinValidBounds(
            @ForAll @IntRange(min = 50, max = 300) int numPoints,
            @ForAll @IntRange(min = 2, max = 20) int k) {
        
        List<ColorPoint> points = generateRandomPoints(numPoints, FIXED_SEED);
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        for (ColorPoint c : centroids) {
            assertTrue(c.c1() >= 0 && c.c1() <= 255,
                "R component out of bounds: " + c.c1());
            assertTrue(c.c2() >= 0 && c.c2() <= 255,
                "G component out of bounds: " + c.c2());
            assertTrue(c.c3() >= 0 && c.c3() <= 255,
                "B component out of bounds: " + c.c3());
        }
    }

    @Property
    @Label("Centroids are within convex hull of input points")
    void centroidsWithinConvexHull(
            @ForAll @IntRange(min = 100, max = 300) int numPoints,
            @ForAll @IntRange(min = 2, max = 10) int k) {
        
        List<ColorPoint> points = generateRandomPoints(numPoints, FIXED_SEED);
        
        // Find bounding box of input
        double minR = Double.MAX_VALUE, maxR = Double.MIN_VALUE;
        double minG = Double.MAX_VALUE, maxG = Double.MIN_VALUE;
        double minB = Double.MAX_VALUE, maxB = Double.MIN_VALUE;
        
        for (ColorPoint p : points) {
            minR = Math.min(minR, p.c1()); maxR = Math.max(maxR, p.c1());
            minG = Math.min(minG, p.c2()); maxG = Math.max(maxG, p.c2());
            minB = Math.min(minB, p.c3()); maxB = Math.max(maxB, p.c3());
        }
        
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        // Allow small margin for numerical precision
        double margin = 1.0;
        
        for (ColorPoint c : centroids) {
            assertTrue(c.c1() >= minR - margin && c.c1() <= maxR + margin,
                "R centroid outside input bounds: " + c.c1() + " not in [" + minR + ", " + maxR + "]");
            assertTrue(c.c2() >= minG - margin && c.c2() <= maxG + margin,
                "G centroid outside input bounds: " + c.c2() + " not in [" + minG + ", " + maxG + "]");
            assertTrue(c.c3() >= minB - margin && c.c3() <= maxB + margin,
                "B centroid outside input bounds: " + c.c3() + " not in [" + minB + ", " + maxB + "]");
        }
    }

    // ==================== Quality Properties ====================

    @Property(tries = 30)
    @Label("Clustering reduces within-cluster variance")
    void clusteringReducesVariance(
            @ForAll @IntRange(min = 200, max = 500) int numPoints,
            @ForAll @IntRange(min = 4, max = 16) int k) {
        
        List<ColorPoint> points = generateRandomPoints(numPoints, FIXED_SEED);
        
        // Calculate variance before clustering (distance to overall mean)
        ColorPoint overallMean = calculateMean(points);
        double beforeVariance = points.stream()
            .mapToDouble(p -> p.distanceSquaredTo(overallMean))
            .average().orElse(0);
        
        // Cluster and calculate within-cluster variance
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        List<ColorPoint> centroids = clusterer.cluster(points, k);
        
        double afterVariance = points.stream()
            .mapToDouble(p -> {
                // Find nearest centroid
                return centroids.stream()
                    .mapToDouble(c -> p.distanceSquaredTo(c))
                    .min().orElse(Double.MAX_VALUE);
            })
            .average().orElse(0);
        
        // Clustering should reduce variance (or at least not increase it much)
        assertTrue(afterVariance <= beforeVariance * 1.1,
            "Clustering should not significantly increase variance: before=" + 
            beforeVariance + ", after=" + afterVariance);
    }

    @Property(tries = 20)
    @Label("More clusters means lower or equal within-cluster distance")
    void moreClustersReduceDistance(
            @ForAll @IntRange(min = 300, max = 500) int numPoints) {
        
        List<ColorPoint> points = generateRandomPoints(numPoints, FIXED_SEED);
        HybridClusterer clusterer = new HybridClusterer(FIXED_SEED);
        
        int k1 = 4;
        int k2 = 8;
        
        double avgDist1 = averageDistanceToNearestCentroid(points, clusterer.cluster(points, k1));
        double avgDist2 = averageDistanceToNearestCentroid(points, clusterer.cluster(points, k2));
        
        // More clusters should give same or lower average distance
        assertTrue(avgDist2 <= avgDist1 * 1.1,
            "k=" + k2 + " should have <= distance than k=" + k1 + 
            ": " + avgDist2 + " vs " + avgDist1);
    }

    // ==================== Helper Methods ====================

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

    private List<ColorPoint> generateSeparatedClusters(int numClusters, int pointsPerCluster, long seed) {
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(seed);
        
        double[][] centers = generateExpectedCenters(numClusters);
        
        for (int c = 0; c < numClusters; c++) {
            for (int p = 0; p < pointsPerCluster; p++) {
                points.add(new ColorPoint(
                    centers[c][0] + random.nextGaussian() * 8,
                    centers[c][1] + random.nextGaussian() * 8,
                    centers[c][2] + random.nextGaussian() * 8
                ));
            }
        }
        
        return points;
    }

    private double[][] generateExpectedCenters(int numClusters) {
        double[][] centers = new double[numClusters][3];
        
        // Space centers far apart
        for (int c = 0; c < numClusters; c++) {
            double offset = (double) c / numClusters;
            centers[c][0] = 40 + offset * 170; // R: 40-210
            centers[c][1] = 40 + ((offset + 0.3) % 1.0) * 170; // G: shifted
            centers[c][2] = 40 + ((offset + 0.6) % 1.0) * 170; // B: shifted more
        }
        
        return centers;
    }

    private ColorPoint calculateMean(List<ColorPoint> points) {
        double r = 0, g = 0, b = 0;
        for (ColorPoint p : points) {
            r += p.c1();
            g += p.c2();
            b += p.c3();
        }
        return new ColorPoint(r / points.size(), g / points.size(), b / points.size());
    }

    private double averageDistanceToNearestCentroid(List<ColorPoint> points, List<ColorPoint> centroids) {
        return points.stream()
            .mapToDouble(p -> centroids.stream()
                .mapToDouble(c -> p.distanceTo(c))
                .min().orElse(0))
            .average().orElse(0);
    }
}
