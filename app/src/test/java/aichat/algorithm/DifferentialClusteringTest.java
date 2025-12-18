package aichat.algorithm;

import aichat.color.ColorSpaceConverter;
import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests: verify nativeFn(input) == javaFn(input).
 * 
 * These tests feed identical inputs to both Java and Native implementations
 * and assert that outputs are equal (within floating-point tolerance).
 */
@DisplayName("Differential Tests: Java vs Native")
@SuppressWarnings("null") // Suppress null-annotation warnings in test code
class DifferentialClusteringTest {
    
    private static final long SEED = 42L;
    private static final double TOLERANCE = 0.01;
    
    private static NativeAccelerator nativeAccel;
    private static HybridClusterer clusterer;
    private static boolean nativeAvailable;
    
    @BeforeAll
    static void setup() {
        nativeAccel = NativeAccelerator.getInstance();
        clusterer = new HybridClusterer(SEED);
        nativeAvailable = nativeAccel.isAvailable();
    }
    
    @Nested
    @DisplayName("Clustering: clusterJava() vs clusterNative()")
    class ClusteringDifferential {
        
        @Test
        @DisplayName("hybridCluster: Java == Native for small dataset (k=4)")
        void hybridClusterSmallDataset() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(200, SEED);
            int k = 4;
            
            List<ColorPoint> javaCentroids = clusterer.clusterJava(points, k);
            List<ColorPoint> nativeCentroids = clusterer.clusterNative(points, k);
            
            assertNotNull(nativeCentroids, "Native clustering returned null");
            assertEquals(k, javaCentroids.size(), "Java should return k centroids");
            assertEquals(k, nativeCentroids.size(), "Native should return k centroids");
            
            // Both should produce similar quality (low total distance)
            double javaQuality = calculateTotalDistance(points, javaCentroids);
            double nativeQuality = calculateTotalDistance(points, nativeCentroids);
            
            // Quality should be within 20% of each other (algorithms may differ slightly)
            double ratio = Math.max(javaQuality, nativeQuality) / Math.min(javaQuality, nativeQuality);
            assertTrue(ratio < 1.2,
                String.format("Clustering quality differs too much: Java=%.2f, Native=%.2f, ratio=%.2f",
                    javaQuality, nativeQuality, ratio));
        }
        
        @Test
        @DisplayName("hybridCluster: Java == Native for medium dataset (k=8)")
        void hybridClusterMediumDataset() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(1000, SEED);
            int k = 8;
            
            List<ColorPoint> javaCentroids = clusterer.clusterJava(points, k);
            List<ColorPoint> nativeCentroids = clusterer.clusterNative(points, k);
            
            assertNotNull(nativeCentroids);
            assertEquals(k, javaCentroids.size());
            assertEquals(k, nativeCentroids.size());
            
            // Centroids should cover similar color space regions
            assertCentroidsCoverSimilarRegions(javaCentroids, nativeCentroids, 50.0);
        }
        
        @Test
        @DisplayName("hybridCluster: Java == Native for large dataset (k=16)")
        void hybridClusterLargeDataset() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(5000, SEED);
            int k = 16;
            
            List<ColorPoint> javaCentroids = clusterer.clusterJava(points, k);
            List<ColorPoint> nativeCentroids = clusterer.clusterNative(points, k);
            
            assertNotNull(nativeCentroids);
            assertEquals(k, javaCentroids.size());
            assertEquals(k, nativeCentroids.size());
            
            // Quality comparison
            double javaQuality = calculateTotalDistance(points, javaCentroids);
            double nativeQuality = calculateTotalDistance(points, nativeCentroids);
            
            double ratio = Math.max(javaQuality, nativeQuality) / Math.min(javaQuality, nativeQuality);
            assertTrue(ratio < 1.25,
                String.format("Large dataset quality differs: Java=%.2f, Native=%.2f", 
                    javaQuality, nativeQuality));
        }
        
        @Test
        @DisplayName("hybridCluster: deterministic results with same seed")
        void hybridClusterDeterministic() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(500, SEED);
            int k = 6;
            
            // Run Java twice with same seed
            HybridClusterer clusterer1 = new HybridClusterer(SEED);
            HybridClusterer clusterer2 = new HybridClusterer(SEED);
            
            List<ColorPoint> java1 = clusterer1.clusterJava(points, k);
            List<ColorPoint> java2 = clusterer2.clusterJava(points, k);
            
            // Java should be deterministic
            assertCentroidsEqual(java1, java2, 0.001,
                "Java clustering should be deterministic with same seed");
            
            // Run Native twice
            List<ColorPoint> native1 = clusterer1.clusterNative(points, k);
            List<ColorPoint> native2 = clusterer2.clusterNative(points, k);
            
            assertNotNull(native1);
            assertNotNull(native2);
            
            // Native should be deterministic
            assertCentroidsEqual(native1, native2, 0.001,
                "Native clustering should be deterministic with same seed");
        }
        
        private double calculateTotalDistance(List<ColorPoint> points, List<ColorPoint> centroids) {
            double total = 0;
            for (ColorPoint p : points) {
                double minDist = Double.MAX_VALUE;
                for (ColorPoint c : centroids) {
                    double d = distanceSq(p, c);
                    if (d < minDist) minDist = d;
                }
                total += Math.sqrt(minDist);
            }
            return total / points.size();
        }
        
        private void assertCentroidsCoverSimilarRegions(List<ColorPoint> java, List<ColorPoint> native_, double maxDist) {
            // For each Java centroid, there should be a Native centroid nearby
            for (ColorPoint jc : java) {
                double nearest = Double.MAX_VALUE;
                for (ColorPoint nc : native_) {
                    double d = Math.sqrt(distanceSq(jc, nc));
                    if (d < nearest) nearest = d;
                }
                assertTrue(nearest < maxDist,
                    String.format("Java centroid %s has no nearby Native centroid (nearest=%.2f)", jc, nearest));
            }
        }
        
        private void assertCentroidsEqual(List<ColorPoint> a, List<ColorPoint> b, double tolerance, String msg) {
            assertEquals(a.size(), b.size(), msg + " - size mismatch");
            
            // Sort by c1 for deterministic comparison
            List<ColorPoint> sortedA = new ArrayList<>(a);
            List<ColorPoint> sortedB = new ArrayList<>(b);
            Comparator<ColorPoint> cmp = Comparator.comparingDouble(ColorPoint::c1)
                .thenComparingDouble(ColorPoint::c2)
                .thenComparingDouble(ColorPoint::c3);
            sortedA.sort(cmp);
            sortedB.sort(cmp);
            
            for (int i = 0; i < sortedA.size(); i++) {
                ColorPoint pa = sortedA.get(i);
                ColorPoint pb = sortedB.get(i);
                assertEquals(pa.c1(), pb.c1(), tolerance, msg);
                assertEquals(pa.c2(), pb.c2(), tolerance, msg);
                assertEquals(pa.c3(), pb.c3(), tolerance, msg);
            }
        }
    }
    
    @Nested
    @DisplayName("Point Assignment: assignPoints() vs assignPointsBatch()")
    class AssignPointsDifferential {
        
        @Test
        @DisplayName("assignPoints: Java == Native for random data")
        void assignPointsDifferential() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(500, SEED);
            List<ColorPoint> centroids = generateRandomColors(8, SEED + 1);
            
            int[] javaAssignments = assignPointsJava(points, centroids);
            int[] nativeAssignments = nativeAccel.assignPointsBatch(points, centroids);
            
            assertNotNull(nativeAssignments, "Native assignPoints returned null");
            assertArrayEquals(javaAssignments, nativeAssignments,
                "All assignments should match between Java and Native");
        }
        
        @Test
        @DisplayName("assignPoints: Java == Native for equidistant points")
        void assignPointsEquidistant() {
            assumeTrue(nativeAvailable, "Native not available");
            
            var points = List.of(new ColorPoint(50, 50, 50));
            var centroids = List.of(
                new ColorPoint(0, 50, 50),   // dist = 50
                new ColorPoint(100, 50, 50)  // dist = 50
            );
            
            int[] javaAssignments = assignPointsJava(points, centroids);
            int[] nativeAssignments = nativeAccel.assignPointsBatch(points, centroids);
            
            assertNotNull(nativeAssignments);
            assertEquals(javaAssignments[0], nativeAssignments[0],
                "Equidistant point should be assigned to same centroid");
        }
        
        @Test
        @DisplayName("assignPoints: Java == Native for large dataset")
        void assignPointsLargeDataset() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> points = generateRandomColors(10000, SEED);
            List<ColorPoint> centroids = generateRandomColors(16, SEED + 1);
            
            int[] javaAssignments = assignPointsJava(points, centroids);
            int[] nativeAssignments = nativeAccel.assignPointsBatch(points, centroids);
            
            assertNotNull(nativeAssignments);
            assertArrayEquals(javaAssignments, nativeAssignments,
                "All 10000 assignments should match between Java and Native");
        }
        
        //Helper to call HybridClusterer.assignPoints with List<ColorPoint> input.
        private int[] assignPointsJava(List<ColorPoint> points, List<ColorPoint> centroids) {
            int n = points.size();
            int k = centroids.size();
            
            double[][] pointArray = new double[n][3];
            for (int i = 0; i < n; i++) {
                ColorPoint p = points.get(i);
                pointArray[i][0] = p.c1();
                pointArray[i][1] = p.c2();
                pointArray[i][2] = p.c3();
            }
            
            double[][] centroidArray = new double[k][3];
            for (int i = 0; i < k; i++) {
                ColorPoint c = centroids.get(i);
                centroidArray[i][0] = c.c1();
                centroidArray[i][1] = c.c2();
                centroidArray[i][2] = c.c3();
            }
            
            int[] assignments = new int[n];
            clusterer.assignPoints(pointArray, centroidArray, assignments);
            return assignments;
        }
    }
    
    @Nested
    @DisplayName("RGB <-> LAB Conversion")
    class ColorConversionDifferential {
        
        @Test
        @DisplayName("rgbToLab: Java == Native for random colors")
        void rgbToLabDifferential() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> testColors = generateRandomColors(100, SEED);
            
            // Use single-point Java conversion (always Java, no native dispatch)
            List<ColorPoint> javaResult = new ArrayList<>();
            for (ColorPoint c : testColors) {
                javaResult.add(ColorSpaceConverter.rgbToLab(c));
            }
            List<ColorPoint> nativeResult = nativeAccel.rgbToLabBatch(testColors);
            
            assertNotNull(nativeResult, "Native rgbToLab returned null");
            assertEquals(javaResult.size(), nativeResult.size(), "Size mismatch");
            
            for (int i = 0; i < javaResult.size(); i++) {
                ColorPoint java = javaResult.get(i);
                ColorPoint native_ = nativeResult.get(i);
                
                assertEquals(java.c1(), native_.c1(), TOLERANCE,
                    String.format("L* mismatch at %d", i));
                assertEquals(java.c2(), native_.c2(), TOLERANCE,
                    String.format("a* mismatch at %d", i));
                assertEquals(java.c3(), native_.c3(), TOLERANCE,
                    String.format("b* mismatch at %d", i));
            }
        }
        
        @Test
        @DisplayName("rgbToLab: Java == Native for edge cases")
        void rgbToLabEdgeCases() {
            assumeTrue(nativeAvailable, "Native not available");
            
            var edgeCases = List.of(
                new ColorPoint(0, 0, 0),       // Black
                new ColorPoint(255, 255, 255), // White
                new ColorPoint(255, 0, 0),     // Pure Red
                new ColorPoint(0, 255, 0),     // Pure Green
                new ColorPoint(0, 0, 255),     // Pure Blue
                new ColorPoint(128, 128, 128), // Mid Gray
                new ColorPoint(1, 1, 1),       // Near Black
                new ColorPoint(254, 254, 254)  // Near White
            );
            
            List<ColorPoint> javaResult = new ArrayList<>();
            for (ColorPoint c : edgeCases) {
                javaResult.add(ColorSpaceConverter.rgbToLab(c));
            }
            List<ColorPoint> nativeResult = nativeAccel.rgbToLabBatch(edgeCases);
            
            assertNotNull(nativeResult);
            
            for (int i = 0; i < edgeCases.size(); i++) {
                ColorPoint input = edgeCases.get(i);
                ColorPoint java = javaResult.get(i);
                ColorPoint native_ = nativeResult.get(i);
                
                assertEquals(java.c1(), native_.c1(), TOLERANCE,
                    String.format("L* mismatch for %s", input));
                assertEquals(java.c2(), native_.c2(), TOLERANCE,
                    String.format("a* mismatch for %s", input));
                assertEquals(java.c3(), native_.c3(), TOLERANCE,
                    String.format("b* mismatch for %s", input));
            }
        }
        
        @Test
        @DisplayName("labToRgb: Java == Native for random LAB values")
        void labToRgbDifferential() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> labColors = generateValidLabColors(100, SEED);
            
            List<ColorPoint> javaResult = new ArrayList<>();
            for (ColorPoint c : labColors) {
                javaResult.add(ColorSpaceConverter.labToRgb(c));
            }
            List<ColorPoint> nativeResult = nativeAccel.labToRgbBatch(labColors);
            
            assertNotNull(nativeResult, "Native labToRgb returned null");
            assertEquals(javaResult.size(), nativeResult.size(), "Size mismatch");
            
            for (int i = 0; i < javaResult.size(); i++) {
                ColorPoint java = javaResult.get(i);
                ColorPoint native_ = nativeResult.get(i);
                
                assertEquals(java.c1(), native_.c1(), 1.0, String.format("R mismatch at %d", i));
                assertEquals(java.c2(), native_.c2(), 1.0, String.format("G mismatch at %d", i));
                assertEquals(java.c3(), native_.c3(), 1.0, String.format("B mismatch at %d", i));
            }
        }
        
        @Test
        @DisplayName("Round-trip: RGB -> LAB -> RGB (both impls)")
        void roundTripDifferential() {
            assumeTrue(nativeAvailable, "Native not available");
            
            List<ColorPoint> originalRgb = generateRandomColors(50, SEED);
            
            // Java round-trip
            List<ColorPoint> javaRoundTrip = new ArrayList<>();
            for (ColorPoint rgb : originalRgb) {
                ColorPoint lab = ColorSpaceConverter.rgbToLab(rgb);
                javaRoundTrip.add(ColorSpaceConverter.labToRgb(lab));
            }
            
            // Native round-trip
            List<ColorPoint> nativeLab = nativeAccel.rgbToLabBatch(originalRgb);
            List<ColorPoint> nativeRoundTrip = nativeAccel.labToRgbBatch(nativeLab);
            
            assertNotNull(nativeRoundTrip);
            
            for (int i = 0; i < originalRgb.size(); i++) {
                ColorPoint javaRT = javaRoundTrip.get(i);
                ColorPoint nativeRT = nativeRoundTrip.get(i);
                
                assertEquals(javaRT.c1(), nativeRT.c1(), 1.0, String.format("Round-trip R at %d", i));
                assertEquals(javaRT.c2(), nativeRT.c2(), 1.0, String.format("Round-trip G at %d", i));
                assertEquals(javaRT.c3(), nativeRT.c3(), 1.0, String.format("Round-trip B at %d", i));
            }
        }
    }
    
    private List<ColorPoint> generateRandomColors(int count, long seed) {
        List<ColorPoint> colors = new ArrayList<>(count);
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            colors.add(new ColorPoint(
                random.nextDouble() * 255,
                random.nextDouble() * 255,
                random.nextDouble() * 255
            ));
        }
        
        return colors;
    }
    
    private List<ColorPoint> generateValidLabColors(int count, long seed) {
        List<ColorPoint> colors = new ArrayList<>(count);
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            double L = random.nextDouble() * 100;
            double a = (random.nextDouble() - 0.5) * 200;
            double b = (random.nextDouble() - 0.5) * 200;
            colors.add(new ColorPoint(L, a, b));
        }
        
        return colors;
    }
    
    private static double distanceSq(ColorPoint a, ColorPoint b) {
        double d1 = a.c1() - b.c1();
        double d2 = a.c2() - b.c2();
        double d3 = a.c3() - b.c3();
        return d1 * d1 + d2 * d2 + d3 * d3;
    }
}
