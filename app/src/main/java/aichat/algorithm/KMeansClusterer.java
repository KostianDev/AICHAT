package aichat.algorithm;

import aichat.model.ColorPoint;

import java.util.*;

/**
 * K-Means clustering algorithm implementation.
 * Efficient for spherical clusters but sensitive to initialization.
 */
public class KMeansClusterer implements ClusteringStrategy {
    
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.001;
    
    private final Random random;
    
    public KMeansClusterer() {
        this.random = new Random();
    }
    
    public KMeansClusterer(long seed) {
        this.random = new Random(seed);
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
        
        List<ColorPoint> centroids = initializeCentroids(points, k);
        int[] assignments = new int[points.size()];
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean changed = assignPoints(points, centroids, assignments);
            List<ColorPoint> newCentroids = updateCentroids(points, assignments, k);
            
            if (hasConverged(centroids, newCentroids) || !changed) {
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
                    double dist = points.get(j).distanceTo(centroid);
                    minDist = Math.min(minDist, dist);
                }
                distances[j] = minDist * minDist;
                totalDistance += distances[j];
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
            double distance = point.distanceTo(centroids.get(i));
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
                newCentroids.add(new ColorPoint(0, 0, 0));
            }
        }
        
        return newCentroids;
    }
    
    private boolean hasConverged(List<ColorPoint> old, List<ColorPoint> current) {
        for (int i = 0; i < old.size(); i++) {
            if (old.get(i).distanceTo(current.get(i)) > CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String getName() {
        return "K-Means";
    }
}
