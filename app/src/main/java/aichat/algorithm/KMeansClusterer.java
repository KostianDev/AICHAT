package aichat.algorithm;

import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

import java.util.*;

/**
 * K-Means clustering algorithm implementation with native acceleration.
 * Uses SIMD-optimized C code via Panama FFI when available.
 * Falls back to pure Java implementation if native library is not loaded.
 * 
 * Uses deterministic seeding for reproducible results.
 */
public class KMeansClusterer implements ClusteringStrategy {
    
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.001;
    private static final long DEFAULT_SEED = 42L;
    
    private final Random random;
    private final long seed;
    private final NativeAccelerator nativeAccelerator;
    
    public KMeansClusterer() {
        this(DEFAULT_SEED);
    }
    
    public KMeansClusterer(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    @Override
    public List<ColorPoint> cluster(List<ColorPoint> points, int k) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        
        k = Math.min(k, points.size());
        
        // Try native implementation first
        if (nativeAccelerator.isAvailable()) {
            List<ColorPoint> nativeResult = nativeAccelerator.kmeansCluster(
                points, k, MAX_ITERATIONS, CONVERGENCE_THRESHOLD, seed
            );
            if (nativeResult != null && !nativeResult.isEmpty()) {
                return nativeResult;
            }
        }
        
        // Fallback to Java implementation
        return clusterJava(points, k);
    }
    
    /**
     * Pure Java K-Means implementation (fallback).
     */
    private List<ColorPoint> clusterJava(List<ColorPoint> points, int k) {
        List<ColorPoint> centroids = initializeCentroids(points, k);
        int[] assignments = new int[points.size()];
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = assignPoints(points, centroids, assignments);
            List<ColorPoint> newCentroids = updateCentroids(points, assignments, centroids.size());
            
            if (hasConverged(centroids, newCentroids) || !changed) {
                centroids = newCentroids;
                break;
            }
            
            centroids = newCentroids;
        }
        
        return centroids;
    }
    
    /**
     * K-means++ initialization for better initial centroid selection.
     */
    private List<ColorPoint> initializeCentroids(List<ColorPoint> points, int k) {
        List<ColorPoint> centroids = new ArrayList<>(k);
        
        centroids.add(points.get(random.nextInt(points.size())));
        
        for (int i = 1; i < k; i++) {
            double[] distances = new double[points.size()];
            double totalDistance = 0;
            
            for (int j = 0; j < points.size(); j++) {
                double minDist = Double.MAX_VALUE;
                for (ColorPoint centroid : centroids) {
                    double dist = points.get(j).distanceSquaredTo(centroid);
                    minDist = Math.min(minDist, dist);
                }
                distances[j] = minDist;
                totalDistance += minDist;
            }
            
            double threshold = random.nextDouble() * totalDistance;
            double cumulative = 0;
            for (int j = 0; j < points.size(); j++) {
                cumulative += distances[j];
                if (cumulative >= threshold) {
                    centroids.add(points.get(j));
                    break;
                }
            }
            
            if (centroids.size() <= i) {
                centroids.add(points.get(random.nextInt(points.size())));
            }
        }
        
        return centroids;
    }
    
    private boolean assignPoints(List<ColorPoint> points, List<ColorPoint> centroids, int[] assignments) {
        boolean changed = false;
        
        // Try native batch assignment
        if (nativeAccelerator.isAvailable()) {
            int[] nativeAssignments = nativeAccelerator.assignPointsBatch(points, centroids);
            if (nativeAssignments != null) {
                for (int i = 0; i < points.size(); i++) {
                    if (assignments[i] != nativeAssignments[i]) {
                        assignments[i] = nativeAssignments[i];
                        changed = true;
                    }
                }
                return changed;
            }
        }
        
        // Java fallback
        for (int i = 0; i < points.size(); i++) {
            int nearest = findNearestCentroid(points.get(i), centroids);
            if (assignments[i] != nearest) {
                assignments[i] = nearest;
                changed = true;
            }
        }
        
        return changed;
    }
    
    private int findNearestCentroid(ColorPoint point, List<ColorPoint> centroids) {
        int nearest = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < centroids.size(); i++) {
            double distance = point.distanceSquaredTo(centroids.get(i));
            if (distance < minDistance) {
                minDistance = distance;
                nearest = i;
            }
        }
        
        return nearest;
    }
    
    private List<ColorPoint> updateCentroids(List<ColorPoint> points, int[] assignments, int k) {
        List<ColorPoint> newCentroids = new ArrayList<>(k);
        
        for (int cluster = 0; cluster < k; cluster++) {
            double sumC1 = 0, sumC2 = 0, sumC3 = 0;
            int count = 0;
            
            for (int i = 0; i < points.size(); i++) {
                if (assignments[i] == cluster) {
                    ColorPoint p = points.get(i);
                    sumC1 += p.c1();
                    sumC2 += p.c2();
                    sumC3 += p.c3();
                    count++;
                }
            }
            
            if (count > 0) {
                newCentroids.add(new ColorPoint(sumC1 / count, sumC2 / count, sumC3 / count));
            } else {
                // Reinitialize empty cluster with a random point
                newCentroids.add(points.get(random.nextInt(points.size())));
            }
        }
        
        return newCentroids;
    }
    
    private boolean hasConverged(List<ColorPoint> old, List<ColorPoint> current) {
        if (old.size() != current.size()) {
            return false;
        }
        for (int i = 0; i < old.size(); i++) {
            if (old.get(i).distanceSquaredTo(current.get(i)) > CONVERGENCE_THRESHOLD * CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String getName() {
        return "K-Means" + (nativeAccelerator.isAvailable() ? " (Native)" : "");
    }
}
