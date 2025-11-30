package aichat.algorithm;

import aichat.model.ColorPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HybridClusterer Tests")
class HybridClustererTest {

    private static final long FIXED_SEED = 42L;
    private HybridClusterer clusterer;

    @BeforeEach
    void setUp() {
        clusterer = new HybridClusterer(FIXED_SEED);
    }

    @Nested
    @DisplayName("Basic Clustering Behavior")
    class BasicClusteringTests {

        @Test
        @DisplayName("Should return requested number of clusters")
        void shouldReturnRequestedClusterCount() {
            List<ColorPoint> points = createDistinctClusters(3, 50);
            
            List<ColorPoint> centroids = clusterer.cluster(points, 3);
            
            assertEquals(3, centroids.size());
        }

        @Test
        @DisplayName("Should handle k=1 correctly")
        void shouldHandleSingleCluster() {
            List<ColorPoint> points = createRandomPoints(100, FIXED_SEED);
            
            List<ColorPoint> centroids = clusterer.cluster(points, 1);
            
            assertEquals(1, centroids.size());
        }

        @Test
        @DisplayName("Should return empty list for empty input")
        void shouldHandleEmptyInput() {
            List<ColorPoint> centroids = clusterer.cluster(new ArrayList<>(), 5);
            
            assertTrue(centroids.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for null input")
        void shouldHandleNullInput() {
            List<ColorPoint> centroids = clusterer.cluster(null, 5);
            
            assertTrue(centroids.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for k <= 0")
        void shouldHandleInvalidK() {
            List<ColorPoint> points = createRandomPoints(50, FIXED_SEED);
            
            assertTrue(clusterer.cluster(points, 0).isEmpty());
            assertTrue(clusterer.cluster(points, -1).isEmpty());
        }

        @Test
        @DisplayName("Should handle k larger than data size")
        void shouldHandleKLargerThanDataSize() {
            List<ColorPoint> points = new ArrayList<>();
            points.add(new ColorPoint(100, 100, 100));
            points.add(new ColorPoint(200, 200, 200));
            
            List<ColorPoint> centroids = clusterer.cluster(points, 10);
            
            assertEquals(2, centroids.size());
        }
    }

    @Nested
    @DisplayName("Centroid Quality")
    class CentroidQualityTests {

        @Test
        @DisplayName("Centroids should be within valid RGB range")
        void centroidsShouldBeInValidRange() {
            List<ColorPoint> points = createRandomPoints(200, FIXED_SEED);
            
            List<ColorPoint> centroids = clusterer.cluster(points, 5);
            
            for (ColorPoint c : centroids) {
                assertTrue(c.c1() >= 0 && c.c1() <= 255, "R out of range: " + c.c1());
                assertTrue(c.c2() >= 0 && c.c2() <= 255, "G out of range: " + c.c2());
                assertTrue(c.c3() >= 0 && c.c3() <= 255, "B out of range: " + c.c3());
            }
        }

        @Test
        @DisplayName("Should find distinct cluster centers for separated data")
        void shouldFindDistinctCenters() {
            List<ColorPoint> points = new ArrayList<>();
            
            // Red cluster
            for (int i = 0; i < 50; i++) {
                points.add(new ColorPoint(250, 10, 10));
            }
            // Green cluster
            for (int i = 0; i < 50; i++) {
                points.add(new ColorPoint(10, 250, 10));
            }
            // Blue cluster
            for (int i = 0; i < 50; i++) {
                points.add(new ColorPoint(10, 10, 250));
            }
            
            List<ColorPoint> centroids = clusterer.cluster(points, 3);
            
            assertEquals(3, centroids.size());
            
            // Verify we have one centroid near each primary color
            boolean hasRed = false, hasGreen = false, hasBlue = false;
            for (ColorPoint c : centroids) {
                if (c.c1() > 200 && c.c2() < 100 && c.c3() < 100) hasRed = true;
                if (c.c1() < 100 && c.c2() > 200 && c.c3() < 100) hasGreen = true;
                if (c.c1() < 100 && c.c2() < 100 && c.c3() > 200) hasBlue = true;
            }
            
            assertTrue(hasRed, "Should find red cluster center");
            assertTrue(hasGreen, "Should find green cluster center");
            assertTrue(hasBlue, "Should find blue cluster center");
        }

        @Test
        @DisplayName("Centroids should represent data distribution")
        void centroidsShouldRepresentDistribution() {
            List<ColorPoint> points = new ArrayList<>();
            
            // Dominant dark colors (80%)
            for (int i = 0; i < 80; i++) {
                points.add(new ColorPoint(30 + Math.random() * 20, 
                                          30 + Math.random() * 20, 
                                          30 + Math.random() * 20));
            }
            // Bright accent (20%)
            for (int i = 0; i < 20; i++) {
                points.add(new ColorPoint(220 + Math.random() * 35, 
                                          220 + Math.random() * 35, 
                                          220 + Math.random() * 35));
            }
            
            List<ColorPoint> centroids = clusterer.cluster(points, 2);
            
            assertEquals(2, centroids.size());
            
            // Should have one dark and one bright centroid
            ColorPoint darker = centroids.get(0).c1() < centroids.get(1).c1() 
                ? centroids.get(0) : centroids.get(1);
            ColorPoint brighter = centroids.get(0).c1() > centroids.get(1).c1() 
                ? centroids.get(0) : centroids.get(1);
            
            assertTrue(darker.c1() < 100, "Dark centroid should be dark");
            assertTrue(brighter.c1() > 150, "Bright centroid should be bright");
        }
    }

    @Nested
    @DisplayName("Determinism")
    class DeterminismTests {

        @Test
        @DisplayName("Same seed should produce identical results")
        void sameSeedShouldProduceIdenticalResults() {
            List<ColorPoint> points = createRandomPoints(100, FIXED_SEED);
            
            HybridClusterer c1 = new HybridClusterer(12345L);
            HybridClusterer c2 = new HybridClusterer(12345L);
            
            List<ColorPoint> result1 = c1.cluster(points, 4);
            List<ColorPoint> result2 = c2.cluster(points, 4);
            
            assertEquals(result1.size(), result2.size());
            for (int i = 0; i < result1.size(); i++) {
                ColorPoint p1 = result1.get(i);
                ColorPoint p2 = result2.get(i);
                assertEquals(p1.c1(), p2.c1(), 0.001);
                assertEquals(p1.c2(), p2.c2(), 0.001);
                assertEquals(p1.c3(), p2.c3(), 0.001);
            }
        }

        @Test
        @DisplayName("Different seeds may produce different results")
        void differentSeedsMayProduceDifferentResults() {
            List<ColorPoint> points = createRandomPoints(100, FIXED_SEED);
            
            HybridClusterer c1 = new HybridClusterer(111L);
            HybridClusterer c2 = new HybridClusterer(999L);
            
            List<ColorPoint> result1 = c1.cluster(points, 4);
            List<ColorPoint> result2 = c2.cluster(points, 4);
            
            // At least size should match
            assertEquals(result1.size(), result2.size());
            
            // Note: Results might occasionally match by chance
        }
    }

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle 10k points in reasonable time")
        void shouldHandleLargeDataset() {
            List<ColorPoint> points = createRandomPoints(10000, FIXED_SEED);
            
            long start = System.currentTimeMillis();
            List<ColorPoint> centroids = clusterer.cluster(points, 8);
            long elapsed = System.currentTimeMillis() - start;
            
            assertEquals(8, centroids.size());
            assertTrue(elapsed < 5000, 
                "10k points should cluster in < 5s, took " + elapsed + "ms");
        }

        @Test
        @DisplayName("Should scale reasonably with cluster count")
        void shouldScaleWithClusterCount() {
            List<ColorPoint> points = createRandomPoints(5000, FIXED_SEED);
            
            long time4 = measureClusteringTime(points, 4);
            long time16 = measureClusteringTime(points, 16);
            
            // k=16 should not take more than 10x the time of k=4
            assertTrue(time16 < time4 * 10,
                "k=16 (" + time16 + "ms) should not be >>10x k=4 (" + time4 + "ms)");
        }

        private long measureClusteringTime(List<ColorPoint> points, int k) {
            long start = System.currentTimeMillis();
            clusterer.cluster(points, k);
            return System.currentTimeMillis() - start;
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle all identical points")
        void shouldHandleIdenticalPoints() {
            List<ColorPoint> points = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                points.add(new ColorPoint(128, 128, 128));
            }
            
            List<ColorPoint> centroids = clusterer.cluster(points, 3);
            
            assertEquals(3, centroids.size());
            // All centroids should be essentially the same
            for (ColorPoint c : centroids) {
                assertEquals(128, c.c1(), 1.0);
                assertEquals(128, c.c2(), 1.0);
                assertEquals(128, c.c3(), 1.0);
            }
        }

        @Test
        @DisplayName("Should handle very small input (2 points)")
        void shouldHandleMinimalInput() {
            List<ColorPoint> points = new ArrayList<>();
            points.add(new ColorPoint(0, 0, 0));
            points.add(new ColorPoint(255, 255, 255));
            
            List<ColorPoint> centroids = clusterer.cluster(points, 2);
            
            assertEquals(2, centroids.size());
        }

        @Test
        @DisplayName("Should handle extreme color values")
        void shouldHandleExtremeValues() {
            List<ColorPoint> points = new ArrayList<>();
            points.add(new ColorPoint(0, 0, 0));
            points.add(new ColorPoint(255, 255, 255));
            points.add(new ColorPoint(0, 255, 0));
            points.add(new ColorPoint(255, 0, 255));
            
            for (int i = 0; i < 50; i++) {
                points.add(new ColorPoint(0, 0, 0));
                points.add(new ColorPoint(255, 255, 255));
            }
            
            List<ColorPoint> centroids = clusterer.cluster(points, 4);
            
            assertNotNull(centroids);
            assertEquals(4, centroids.size());
        }
    }

    // Helper methods

    private List<ColorPoint> createRandomPoints(int count, long seed) {
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

    private List<ColorPoint> createDistinctClusters(int numClusters, int pointsPerCluster) {
        List<ColorPoint> points = new ArrayList<>();
        Random random = new Random(FIXED_SEED);
        
        for (int c = 0; c < numClusters; c++) {
            // Create cluster center
            double centerR = 50 + c * (200.0 / numClusters);
            double centerG = 50 + ((c + 1) % numClusters) * (200.0 / numClusters);
            double centerB = 50 + ((c + 2) % numClusters) * (200.0 / numClusters);
            
            // Add points around center
            for (int p = 0; p < pointsPerCluster; p++) {
                points.add(new ColorPoint(
                    centerR + random.nextGaussian() * 10,
                    centerG + random.nextGaussian() * 10,
                    centerB + random.nextGaussian() * 10
                ));
            }
        }
        return points;
    }
}
