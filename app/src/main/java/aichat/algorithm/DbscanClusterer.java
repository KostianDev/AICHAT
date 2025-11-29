package aichat.algorithm;

import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

import java.util.*;

/**
 * Optimized DBSCAN clustering implementation for color data.
 * 
 * Based on "DBSCAN Revisited, Revisited" (Schubert et al., 2017).
 * 
 * Key optimizations:
 * 1. Grid-based spatial index for O(1) average neighbor lookup
 * 2. Proper eps selection using k-distance graph heuristic
 * 3. Efficient seed queue using BitSet for visited tracking
 * 4. Native acceleration when available
 * 
 * For 3D color space with appropriate eps (10-50), achieves near-linear performance.
 */
public class DbscanClusterer implements ClusteringStrategy {
    
    private static final int NOISE = -1;
    private static final int UNCLASSIFIED = -2;
    
    // Default parameters based on research recommendations
    // For 3D data: minPts = 2*dim = 6, but 4-10 works well for color clustering
    private static final int DEFAULT_MIN_PTS = 6;
    
    private double eps;
    private int minPts;
    private final NativeAccelerator nativeAccelerator;
    
    public DbscanClusterer() {
        this.eps = 0;  // Will be calculated adaptively
        this.minPts = DEFAULT_MIN_PTS;
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    public DbscanClusterer(double eps, int minPts) {
        this.eps = eps;
        this.minPts = minPts;
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    @Override
    public List<ColorPoint> cluster(List<ColorPoint> points, int k) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Try native implementation first
        if (nativeAccelerator.isAvailable()) {
            List<ColorPoint> result = clusterNative(points, k);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        
        // Java fallback
        return clusterJava(points, k);
    }
    
    private List<ColorPoint> clusterNative(List<ColorPoint> points, int k) {
        // Calculate adaptive eps if not set
        double adaptiveEps = this.eps;
        if (adaptiveEps <= 0) {
            adaptiveEps = nativeAccelerator.dbscanCalculateEps(points, minPts, 100, 42);
            if (adaptiveEps <= 0) {
                adaptiveEps = 25.0;  // Fallback default
            }
        }
        
        NativeAccelerator.DbscanResult result = nativeAccelerator.dbscanCluster(points, adaptiveEps, minPts);
        if (result == null) {
            return null;
        }
        
        List<ColorPoint> centroids = result.centroids();
        if (centroids.isEmpty() && !points.isEmpty()) {
            // Fallback: compute mean of all points
            centroids = List.of(computeMean(points));
        }
        
        return adjustCentroidsToK(centroids, k, points);
    }
    
    private List<ColorPoint> clusterJava(List<ColorPoint> points, int k) {
        int n = points.size();
        
        // Calculate adaptive eps using k-distance heuristic
        double adaptiveEps = this.eps;
        if (adaptiveEps <= 0) {
            adaptiveEps = calculateAdaptiveEps(points);
        }
        this.eps = adaptiveEps;  // Store for reference
        
        // Build spatial grid for fast neighbor queries
        SpatialGrid grid = new SpatialGrid(points, adaptiveEps);
        
        // Initialize labels
        int[] labels = new int[n];
        Arrays.fill(labels, UNCLASSIFIED);
        
        double epsSq = adaptiveEps * adaptiveEps;
        int clusterId = 0;
        
        // Seed queue and visited tracking
        Deque<Integer> seedQueue = new ArrayDeque<>();
        BitSet inQueue = new BitSet(n);
        
        // Main DBSCAN loop
        for (int i = 0; i < n; i++) {
            if (labels[i] != UNCLASSIFIED) continue;
            
            // Find neighbors using grid
            List<Integer> neighbors = grid.rangeQuery(points, i, epsSq);
            
            if (neighbors.size() < minPts) {
                labels[i] = NOISE;
                continue;
            }
            
            // Start new cluster
            labels[i] = clusterId;
            
            // Initialize seed queue with neighbors
            seedQueue.clear();
            inQueue.clear();
            for (int neighbor : neighbors) {
                if (neighbor != i) {
                    seedQueue.addLast(neighbor);
                    inQueue.set(neighbor);
                }
            }
            
            // Expand cluster
            while (!seedQueue.isEmpty()) {
                int q = seedQueue.pollFirst();
                
                if (labels[q] == NOISE) {
                    labels[q] = clusterId;  // Border point
                }
                
                if (labels[q] != UNCLASSIFIED) continue;
                
                labels[q] = clusterId;
                
                List<Integer> qNeighbors = grid.rangeQuery(points, q, epsSq);
                
                if (qNeighbors.size() >= minPts) {
                    // q is a core point
                    for (int neighbor : qNeighbors) {
                        if (!inQueue.get(neighbor) && 
                            (labels[neighbor] == UNCLASSIFIED || labels[neighbor] == NOISE)) {
                            seedQueue.addLast(neighbor);
                            inQueue.set(neighbor);
                        }
                    }
                }
            }
            
            clusterId++;
        }
        
        // Calculate centroids
        List<ColorPoint> centroids = calculateCentroids(points, labels, clusterId);
        
        if (centroids.isEmpty() && !points.isEmpty()) {
            centroids = List.of(computeMean(points));
        }
        
        return adjustCentroidsToK(centroids, k, points);
    }
    
    /**
     * Calculate adaptive epsilon using k-distance graph heuristic.
     * Based on the original DBSCAN paper (Ester et al., 1996).
     */
    private double calculateAdaptiveEps(List<ColorPoint> points) {
        int n = points.size();
        if (n <= minPts) {
            return 25.0;
        }
        
        int k = minPts - 1;
        if (k < 1) k = 1;
        if (k >= n) k = n - 1;
        
        // Sample points for efficiency
        int sampleSize = Math.min(100, n);
        Random random = new Random(42);
        
        double[] kDistances = new double[sampleSize];
        
        for (int s = 0; s < sampleSize; s++) {
            int idx = random.nextInt(n);
            ColorPoint p = points.get(idx);
            
            // Compute distances to all points and find k-th smallest
            double[] distances = new double[n];
            for (int j = 0; j < n; j++) {
                distances[j] = p.distanceTo(points.get(j));
            }
            
            // Partial sort to find k-th element
            Arrays.sort(distances);
            kDistances[s] = distances[k];
        }
        
        // Sort k-distances and find elbow (around 85th percentile)
        Arrays.sort(kDistances);
        int elbowIdx = (int)(sampleSize * 0.85);
        double eps = kDistances[elbowIdx];
        
        // Clamp to reasonable range for color data (0-255 RGB)
        return Math.max(5.0, Math.min(100.0, eps));
    }
    
    private List<ColorPoint> calculateCentroids(List<ColorPoint> points, int[] labels, int numClusters) {
        if (numClusters == 0) {
            return new ArrayList<>();
        }
        
        double[] sums = new double[numClusters * 3];
        int[] counts = new int[numClusters];
        
        for (int i = 0; i < points.size(); i++) {
            int cluster = labels[i];
            if (cluster >= 0 && cluster < numClusters) {
                ColorPoint p = points.get(i);
                sums[cluster * 3] += p.c1();
                sums[cluster * 3 + 1] += p.c2();
                sums[cluster * 3 + 2] += p.c3();
                counts[cluster]++;
            }
        }
        
        List<ColorPoint> centroids = new ArrayList<>(numClusters);
        for (int c = 0; c < numClusters; c++) {
            if (counts[c] > 0) {
                centroids.add(new ColorPoint(
                    sums[c * 3] / counts[c],
                    sums[c * 3 + 1] / counts[c],
                    sums[c * 3 + 2] / counts[c]
                ));
            }
        }
        
        return centroids;
    }
    
    private ColorPoint computeMean(List<ColorPoint> points) {
        double sumC1 = 0, sumC2 = 0, sumC3 = 0;
        for (ColorPoint p : points) {
            sumC1 += p.c1();
            sumC2 += p.c2();
            sumC3 += p.c3();
        }
        int n = points.size();
        return new ColorPoint(sumC1 / n, sumC2 / n, sumC3 / n);
    }
    
    /**
     * Adjusts the number of centroids to match the requested k.
     * Merges closest centroids if too many, adds furthest points if too few.
     */
    private List<ColorPoint> adjustCentroidsToK(List<ColorPoint> centroids, int k, List<ColorPoint> allPoints) {
        List<ColorPoint> result = new ArrayList<>(centroids);
        Random random = new Random(42);
        
        // Merge closest centroids if we have too many
        while (result.size() > k && result.size() > 1) {
            double minDist = Double.MAX_VALUE;
            int mergeI = 0, mergeJ = 1;
            
            for (int i = 0; i < result.size(); i++) {
                for (int j = i + 1; j < result.size(); j++) {
                    double dist = result.get(i).distanceTo(result.get(j));
                    if (dist < minDist) {
                        minDist = dist;
                        mergeI = i;
                        mergeJ = j;
                    }
                }
            }
            
            ColorPoint p1 = result.get(mergeI);
            ColorPoint p2 = result.get(mergeJ);
            ColorPoint merged = new ColorPoint(
                (p1.c1() + p2.c1()) / 2,
                (p1.c2() + p2.c2()) / 2,
                (p1.c3() + p2.c3()) / 2
            );
            
            result.remove(mergeJ);
            result.set(mergeI, merged);
        }
        
        // If we have fewer centroids than k, find furthest points from existing centroids
        while (result.size() < k && !allPoints.isEmpty()) {
            double maxMinDist = -1;
            ColorPoint furthest = null;
            
            for (ColorPoint p : allPoints) {
                double minDist = Double.MAX_VALUE;
                for (ColorPoint c : result) {
                    double dist = p.distanceTo(c);
                    if (dist < minDist) {
                        minDist = dist;
                    }
                }
                if (minDist > maxMinDist) {
                    maxMinDist = minDist;
                    furthest = p;
                }
            }
            
            if (furthest != null && maxMinDist > 1.0) {
                result.add(furthest);
            } else {
                // Random point if no good candidate
                result.add(allPoints.get(random.nextInt(allPoints.size())));
            }
        }
        
        return result;
    }
    
    public double getEps() { return eps; }
    public void setEps(double eps) { this.eps = eps; }
    public int getMinPts() { return minPts; }
    public void setMinPts(int minPts) { this.minPts = minPts; }
    
    @Override
    public String getName() {
        return "DBSCAN" + (nativeAccelerator.isAvailable() ? " (Native)" : "");
    }
    
    // =========================================================================
    // Grid-based Spatial Index
    // =========================================================================
    
    /**
     * 3D Grid for efficient spatial queries.
     * For small eps (typical in color clustering), range queries
     * only need to check 27 neighboring cells (3x3x3).
     */
    private static class SpatialGrid {
        private final List<List<Integer>> cells;
        private final int gridSize;
        private final double cellSize;
        private final double minC1, minC2, minC3;
        
        SpatialGrid(List<ColorPoint> points, double eps) {
            // Find bounding box
            double minC1 = Double.MAX_VALUE, maxC1 = -Double.MAX_VALUE;
            double minC2 = Double.MAX_VALUE, maxC2 = -Double.MAX_VALUE;
            double minC3 = Double.MAX_VALUE, maxC3 = -Double.MAX_VALUE;
            
            for (ColorPoint p : points) {
                if (p.c1() < minC1) minC1 = p.c1();
                if (p.c1() > maxC1) maxC1 = p.c1();
                if (p.c2() < minC2) minC2 = p.c2();
                if (p.c2() > maxC2) maxC2 = p.c2();
                if (p.c3() < minC3) minC3 = p.c3();
                if (p.c3() > maxC3) maxC3 = p.c3();
            }
            
            // Padding
            this.minC1 = minC1 - eps;
            this.minC2 = minC2 - eps;
            this.minC3 = minC3 - eps;
            
            this.cellSize = eps;
            
            double range = Math.max(Math.max(maxC1 - minC1, maxC2 - minC2), maxC3 - minC3) + 2 * eps;
            this.gridSize = Math.max(1, Math.min(256, (int) Math.ceil(range / eps)));
            
            int totalCells = gridSize * gridSize * gridSize;
            this.cells = new ArrayList<>(totalCells);
            for (int i = 0; i < totalCells; i++) {
                cells.add(new ArrayList<>());
            }
            
            // Insert points
            for (int i = 0; i < points.size(); i++) {
                ColorPoint p = points.get(i);
                int cellIdx = getCellIndex(p);
                cells.get(cellIdx).add(i);
            }
        }
        
        private int getCellIndex(ColorPoint p) {
            int x = gridCoord(p.c1(), minC1);
            int y = gridCoord(p.c2(), minC2);
            int z = gridCoord(p.c3(), minC3);
            return x * gridSize * gridSize + y * gridSize + z;
        }
        
        private int gridCoord(double val, double min) {
            int coord = (int) ((val - min) / cellSize);
            if (coord < 0) coord = 0;
            if (coord >= gridSize) coord = gridSize - 1;
            return coord;
        }
        
        /**
         * Returns indices of all points within eps distance of the given point.
         */
        List<Integer> rangeQuery(List<ColorPoint> points, int pointIdx, double epsSq) {
            ColorPoint p = points.get(pointIdx);
            List<Integer> result = new ArrayList<>();
            
            int cx = gridCoord(p.c1(), minC1);
            int cy = gridCoord(p.c2(), minC2);
            int cz = gridCoord(p.c3(), minC3);
            
            // Check 3x3x3 neighboring cells
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx;
                if (nx < 0 || nx >= gridSize) continue;
                
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = cy + dy;
                    if (ny < 0 || ny >= gridSize) continue;
                    
                    for (int dz = -1; dz <= 1; dz++) {
                        int nz = cz + dz;
                        if (nz < 0 || nz >= gridSize) continue;
                        
                        int cellIdx = nx * gridSize * gridSize + ny * gridSize + nz;
                        for (int j : cells.get(cellIdx)) {
                            ColorPoint q = points.get(j);
                            double d1 = p.c1() - q.c1();
                            double d2 = p.c2() - q.c2();
                            double d3 = p.c3() - q.c3();
                            double distSq = d1*d1 + d2*d2 + d3*d3;
                            
                            if (distSq <= epsSq) {
                                result.add(j);
                            }
                        }
                    }
                }
            }
            
            return result;
        }
    }
}
