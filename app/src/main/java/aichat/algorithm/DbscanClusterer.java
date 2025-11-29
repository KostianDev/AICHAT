package aichat.algorithm;

import aichat.model.ColorPoint;

import java.util.*;

/**
 * DBSCAN (Density-Based Spatial Clustering of Applications with Noise) implementation.
 * Can find arbitrarily shaped clusters and identify noise points.
 */
public class DbscanClusterer implements ClusteringStrategy {
    
    private static final int NOISE = -1;
    private static final int UNCLASSIFIED = -2;
    
    private double eps;
    private int minPts;
    
    public DbscanClusterer() {
        this.eps = 30.0;
        this.minPts = 10;
    }
    
    public DbscanClusterer(double eps, int minPts) {
        this.eps = eps;
        this.minPts = minPts;
    }
    
    @Override
    public List<ColorPoint> cluster(List<ColorPoint> points, int k) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        
        this.eps = calculateAdaptiveEps(points, k);
        
        int[] labels = new int[points.size()];
        Arrays.fill(labels, UNCLASSIFIED);
        
        int clusterId = 0;
        
        for (int i = 0; i < points.size(); i++) {
            if (labels[i] != UNCLASSIFIED) {
                continue;
            }
            
            List<Integer> neighbors = findNeighbors(points, i);
            
            if (neighbors.size() < minPts) {
                labels[i] = NOISE;
            } else {
                expandCluster(points, labels, i, neighbors, clusterId);
                clusterId++;
            }
        }
        
        return calculateCentroids(points, labels, clusterId);
    }
    
    private double calculateAdaptiveEps(List<ColorPoint> points, int k) {
        int sampleSize = Math.min(100, points.size());
        Random random = new Random(42);
        
        double totalDist = 0;
        int count = 0;
        
        for (int i = 0; i < sampleSize; i++) {
            int idx = random.nextInt(points.size());
            ColorPoint p = points.get(idx);
            
            List<Double> distances = new ArrayList<>();
            for (int j = 0; j < points.size(); j++) {
                if (j != idx) {
                    distances.add(p.distanceTo(points.get(j)));
                }
            }
            Collections.sort(distances);
            
            if (distances.size() >= minPts) {
                totalDist += distances.get(minPts - 1);
                count++;
            }
        }
        
        return count > 0 ? (totalDist / count) * 1.5 : 30.0;
    }
    
    private List<Integer> findNeighbors(List<ColorPoint> points, int pointIndex) {
        List<Integer> neighbors = new ArrayList<>();
        ColorPoint point = points.get(pointIndex);
        
        for (int i = 0; i < points.size(); i++) {
            if (point.distanceTo(points.get(i)) <= eps) {
                neighbors.add(i);
            }
        }
        
        return neighbors;
    }
    
    private void expandCluster(List<ColorPoint> points, int[] labels, 
                               int pointIndex, List<Integer> neighbors, int clusterId) {
        labels[pointIndex] = clusterId;
        
        Queue<Integer> seedQueue = new LinkedList<>(neighbors);
        
        while (!seedQueue.isEmpty()) {
            int current = seedQueue.poll();
            
            if (labels[current] == NOISE) {
                labels[current] = clusterId;
            }
            
            if (labels[current] != UNCLASSIFIED) {
                continue;
            }
            
            labels[current] = clusterId;
            
            List<Integer> currentNeighbors = findNeighbors(points, current);
            
            if (currentNeighbors.size() >= minPts) {
                for (int neighbor : currentNeighbors) {
                    if (labels[neighbor] == UNCLASSIFIED || labels[neighbor] == NOISE) {
                        seedQueue.add(neighbor);
                    }
                }
            }
        }
    }
    
    private List<ColorPoint> calculateCentroids(List<ColorPoint> points, int[] labels, int numClusters) {
        List<ColorPoint> centroids = new ArrayList<>();
        
        for (int cluster = 0; cluster < numClusters; cluster++) {
            double sumC1 = 0, sumC2 = 0, sumC3 = 0;
            int count = 0;
            
            for (int i = 0; i < points.size(); i++) {
                if (labels[i] == cluster) {
                    ColorPoint p = points.get(i);
                    sumC1 += p.c1();
                    sumC2 += p.c2();
                    sumC3 += p.c3();
                    count++;
                }
            }
            
            if (count > 0) {
                centroids.add(new ColorPoint(sumC1 / count, sumC2 / count, sumC3 / count));
            }
        }
        
        if (centroids.isEmpty() && !points.isEmpty()) {
            double sumC1 = 0, sumC2 = 0, sumC3 = 0;
            for (ColorPoint p : points) {
                sumC1 += p.c1();
                sumC2 += p.c2();
                sumC3 += p.c3();
            }
            centroids.add(new ColorPoint(
                sumC1 / points.size(), 
                sumC2 / points.size(), 
                sumC3 / points.size()
            ));
        }
        
        return centroids;
    }
    
    public double getEps() {
        return eps;
    }
    
    public void setEps(double eps) {
        this.eps = eps;
    }
    
    public int getMinPts() {
        return minPts;
    }
    
    public void setMinPts(int minPts) {
        this.minPts = minPts;
    }
    
    @Override
    public String getName() {
        return "DBSCAN";
    }
}
