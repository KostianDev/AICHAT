package aichat.algorithm;

import aichat.model.ColorPoint;
import aichat.native_.NativeAccelerator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class HybridClusterer implements ClusteringStrategy {
    
    private static final int DEFAULT_BLOCK_SIZE = 1000;
    private static final int DEFAULT_MIN_PTS = 3;
    private static final int KMEANS_MAX_ITERATIONS = 50;
    private static final double KMEANS_THRESHOLD = 1.0;
    private static final int PARALLEL_THRESHOLD = 500;
    
    private final int blockSize;
    private final int minPts;
    private final long seed;
    private final NativeAccelerator nativeAccelerator;
    
    public HybridClusterer() {
        this(DEFAULT_BLOCK_SIZE, DEFAULT_MIN_PTS, 42L);
    }
    
    public HybridClusterer(long seed) {
        this(DEFAULT_BLOCK_SIZE, DEFAULT_MIN_PTS, seed);
    }
    
    public HybridClusterer(int blockSize, int minPts, long seed) {
        this.blockSize = blockSize;
        this.minPts = minPts;
        this.seed = seed;
        this.nativeAccelerator = NativeAccelerator.getInstance();
    }
    
    @Override
    public List<ColorPoint> cluster(List<ColorPoint> points, int k) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (k <= 0) return Collections.emptyList();
        if (k >= points.size()) {
            return new ArrayList<>(points.subList(0, Math.min(k, points.size())));
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

    public List<ColorPoint> clusterNative(List<ColorPoint> points, int k) {
        return nativeAccelerator.hybridCluster(points, k, blockSize, minPts, seed);
    }

    public List<ColorPoint> clusterJava(List<ColorPoint> points, int k) {
        int n = points.size();
        
        // For very small datasets, use K-Means directly
        if (n <= blockSize * 2) {
            return kmeansCluster(points, k);
        }
        
        // Calculate adaptive eps
        float eps = calculateAdaptiveEps(points);
        
        // Phase 1: Extract representatives from each block using DBSCAN
        List<ColorPoint> representatives = extractRepresentatives(points, eps);
        
        // Ensure we have enough representatives
        if (representatives.size() < k) {
            Random random = new Random(seed);
            while (representatives.size() < k) {
                representatives.add(points.get(random.nextInt(n)));
            }
        }
        
        // Phase 2: Apply K-Means on representatives
        return kmeansCluster(representatives, k);
    }
    
    private List<ColorPoint> extractRepresentatives(List<ColorPoint> points, float eps) {
        int n = points.size();
        int numBlocks = (n + blockSize - 1) / blockSize;
        
        // Convert to array for efficient access
        double[][] pointArray = new double[n][3];
        for (int i = 0; i < n; i++) {
            ColorPoint p = points.get(i);
            pointArray[i][0] = p.c1();
            pointArray[i][1] = p.c2();
            pointArray[i][2] = p.c3();
        }
        
        // Process blocks (parallel for large datasets)
        // Use an array of lists to preserve deterministic ordering
        @SuppressWarnings("unchecked")
        List<ColorPoint>[] blockResults = new List[numBlocks];
        
        if (n > PARALLEL_THRESHOLD && numBlocks > 4) {
            IntStream.range(0, numBlocks).parallel().forEach(b -> {
                blockResults[b] = processBlock(pointArray, b, n, eps);
            });
        } else {
            for (int b = 0; b < numBlocks; b++) {
                blockResults[b] = processBlock(pointArray, b, n, eps);
            }
        }
        
        // Collect results in deterministic order
        List<ColorPoint> allRepresentatives = new ArrayList<>();
        for (int b = 0; b < numBlocks; b++) {
            if (blockResults[b] != null) {
                allRepresentatives.addAll(blockResults[b]);
            }
        }
        
        return allRepresentatives;
    }
    
    private List<ColorPoint> processBlock(double[][] allPoints, int blockIndex, int totalPoints, float eps) {
        int start = blockIndex * blockSize;
        int end = Math.min(start + blockSize, totalPoints);
        int blockN = end - start;
        
        if (blockN == 0) return Collections.emptyList();
        
        double epsSq = eps * eps;
        
        // Labels: -2 = unclassified, -1 = noise, 0+ = cluster
        int[] labels = new int[blockN];
        Arrays.fill(labels, -2);
        
        // Queue for BFS expansion
        int[] queue = new int[blockN];
        
        int clusterId = 0;
        
        for (int i = 0; i < blockN; i++) {
            if (labels[i] != -2) continue;
            
            // Count neighbors (simple O(n) scan)
            int neighbors = 0;
            double[] pi = allPoints[start + i];
            for (int j = 0; j < blockN; j++) {
                double[] pj = allPoints[start + j];
                double d0 = pi[0] - pj[0];
                double d1 = pi[1] - pj[1];
                double d2 = pi[2] - pj[2];
                if (d0*d0 + d1*d1 + d2*d2 <= epsSq) neighbors++;
            }
            
            if (neighbors < minPts) {
                labels[i] = -1;  // Noise
                continue;
            }
            
            // Start new cluster with BFS
            labels[i] = clusterId;
            int queueStart = 0, queueEnd = 0;
            
            // Add all neighbors to queue
            for (int j = 0; j < blockN; j++) {
                if (j != i && labels[j] == -2) {
                    double[] pj = allPoints[start + j];
                    double d0 = pi[0] - pj[0];
                    double d1 = pi[1] - pj[1];
                    double d2 = pi[2] - pj[2];
                    if (d0*d0 + d1*d1 + d2*d2 <= epsSq) {
                        queue[queueEnd++] = j;
                        labels[j] = -3;  // In queue
                    }
                }
            }
            
            while (queueStart < queueEnd) {
                int q = queue[queueStart++];
                
                if (labels[q] == -1) {
                    labels[q] = clusterId;  // Border point
                    continue;
                }
                
                labels[q] = clusterId;
                
                // Count q's neighbors
                double[] pq = allPoints[start + q];
                int qNeighbors = 0;
                for (int j = 0; j < blockN; j++) {
                    double[] pj = allPoints[start + j];
                    double d0 = pq[0] - pj[0];
                    double d1 = pq[1] - pj[1];
                    double d2 = pq[2] - pj[2];
                    if (d0*d0 + d1*d1 + d2*d2 <= epsSq) qNeighbors++;
                }
                
                if (qNeighbors >= minPts) {
                    // Add q's unvisited neighbors to queue
                    for (int j = 0; j < blockN; j++) {
                        if (labels[j] == -2) {
                            double[] pj = allPoints[start + j];
                            double d0 = pq[0] - pj[0];
                            double d1 = pq[1] - pj[1];
                            double d2 = pq[2] - pj[2];
                            if (d0*d0 + d1*d1 + d2*d2 <= epsSq) {
                                queue[queueEnd++] = j;
                                labels[j] = -3;
                            }
                        }
                    }
                }
            }
            
            clusterId++;
        }
        
        // Calculate representatives: cluster centroids + noise points
        List<ColorPoint> representatives = new ArrayList<>();
        
        // Cluster centroids
        for (int c = 0; c < clusterId; c++) {
            double sum1 = 0, sum2 = 0, sum3 = 0;
            int count = 0;
            
            for (int i = 0; i < blockN; i++) {
                if (labels[i] == c) {
                    sum1 += allPoints[start + i][0];
                    sum2 += allPoints[start + i][1];
                    sum3 += allPoints[start + i][2];
                    count++;
                }
            }
            
            if (count > 0) {
                representatives.add(new ColorPoint(sum1 / count, sum2 / count, sum3 / count));
            }
        }
        
        // Add noise points (they represent unique/rare colors)
        for (int i = 0; i < blockN; i++) {
            if (labels[i] == -1) {
                representatives.add(new ColorPoint(
                    allPoints[start + i][0],
                    allPoints[start + i][1],
                    allPoints[start + i][2]
                ));
            }
        }
        
        return representatives;
    }
    
    private float calculateAdaptiveEps(List<ColorPoint> points) {
        int n = points.size();
        if (n <= minPts) {
            return 15.0f;
        }
        
        int k = minPts - 1;
        if (k < 1) k = 1;
        if (k >= n) k = n - 1;
        
        // Sample a subset of blocks for eps estimation
        int numBlocks = (n + blockSize - 1) / blockSize;
        int sampleBlocks = Math.min(10, numBlocks);
        
        Random random = new Random(seed);
        double totalEps = 0;
        
        for (int s = 0; s < sampleBlocks; s++) {
            int blockIdx = random.nextInt(numBlocks);
            int start = blockIdx * blockSize;
            int end = Math.min(start + blockSize, n);
            int blockN = end - start;
            
            if (blockN <= k) {
                totalEps += 15.0f;
                continue;
            }
            
            // Sample points from this block and find k-distances
            int sampleSize = Math.min(20, blockN);
            double[] kDistances = new double[sampleSize];
            
            for (int i = 0; i < sampleSize; i++) {
                int idx = start + random.nextInt(blockN);
                ColorPoint p = points.get(idx);
                
                double[] distances = new double[blockN];
                for (int j = 0; j < blockN; j++) {
                    distances[j] = p.distanceTo(points.get(start + j));
                }
                
                Arrays.sort(distances);
                kDistances[i] = distances[k];
            }
            
            // Use MEDIAN (50th percentile) for conservative clustering
            Arrays.sort(kDistances);
            int medianIdx = sampleSize / 2;
            totalEps += kDistances[medianIdx];
        }
        
        float avgEps = (float)(totalEps / sampleBlocks);
        
        if (avgEps < 8.0f) avgEps = 8.0f;
        if (avgEps > 30.0f) avgEps = 30.0f;
        
        return avgEps;
    }
    
    private List<ColorPoint> kmeansCluster(List<ColorPoint> points, int k) {
        int n = points.size();
        if (n <= k) {
            return new ArrayList<>(points);
        }
        
        // Convert to array for efficiency
        double[][] pointArray = new double[n][3];
        for (int i = 0; i < n; i++) {
            ColorPoint p = points.get(i);
            pointArray[i][0] = p.c1();
            pointArray[i][1] = p.c2();
            pointArray[i][2] = p.c3();
        }
        
        // K-Means++ initialization
        double[][] centroids = initPlusPlus(pointArray, k);
        int[] assignments = new int[n];
        
        // Fewer iterations for large k (diminishing returns)
        int maxIter = k > 100 ? 20 : (k > 32 ? 30 : KMEANS_MAX_ITERATIONS);
        
        // Main K-Means loop
        for (int iter = 0; iter < maxIter; iter++) {
            // Assign points to nearest centroids
            int changed = assignPoints(pointArray, centroids, assignments);
            
            // Update centroids
            double maxMovement = updateCentroids(pointArray, centroids, assignments, k);
            
            // Check convergence
            if (maxMovement < KMEANS_THRESHOLD || changed == 0) {
                break;
            }
        }
        
        // Convert centroids to ColorPoints
        List<ColorPoint> result = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            result.add(new ColorPoint(centroids[c][0], centroids[c][1], centroids[c][2]));
        }
        
        return result;
    }
    
    private double[][] initPlusPlus(double[][] points, int k) {
        int n = points.length;
        double[][] centroids = new double[k][3];
        Random random = new Random(seed);
        
        if (k > 64) {
            int step = Math.max(1, n / k);
            for (int c = 0; c < k; c++) {
                int idx = (c * step + random.nextInt(step)) % n;
                centroids[c] = points[idx].clone();
            }
            return centroids;
        }
        
        // Standard K-Means++ for small k
        double[] distances = new double[n];
        
        // First centroid: random point
        int first = random.nextInt(n);
        centroids[0] = points[first].clone();
        
        // Remaining centroids: D² weighting
        for (int c = 1; c < k; c++) {
            double totalDist = 0;
            
            for (int i = 0; i < n; i++) {
                double minDist = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    double d = distanceSq(points[i], centroids[j]);
                    if (d < minDist) minDist = d;
                }
                distances[i] = minDist;
                totalDist += minDist;
            }
            
            // Weighted random selection
            double threshold = random.nextDouble() * totalDist;
            double cumulative = 0;
            int selected = n - 1;
            
            for (int i = 0; i < n; i++) {
                cumulative += distances[i];
                if (cumulative >= threshold) {
                    selected = i;
                    break;
                }
            }
            
            centroids[c] = points[selected].clone();
        }
        
        return centroids;
    }
    
    int assignPoints(double[][] points, double[][] centroids, int[] assignments) {
        int n = points.length;
        int k = centroids.length;
        AtomicInteger changed = new AtomicInteger(0);
        
        if (n > PARALLEL_THRESHOLD) {
            IntStream.range(0, n).parallel().forEach(i -> {
                int closest = 0;
                double minDist = distanceSq(points[i], centroids[0]);
                
                for (int c = 1; c < k; c++) {
                    double d = distanceSq(points[i], centroids[c]);
                    if (d < minDist) {
                        minDist = d;
                        closest = c;
                    }
                }
                
                if (assignments[i] != closest) {
                    assignments[i] = closest;
                    changed.incrementAndGet();
                }
            });
        } else {
            for (int i = 0; i < n; i++) {
                int closest = 0;
                double minDist = distanceSq(points[i], centroids[0]);
                
                for (int c = 1; c < k; c++) {
                    double d = distanceSq(points[i], centroids[c]);
                    if (d < minDist) {
                        minDist = d;
                        closest = c;
                    }
                }
                
                if (assignments[i] != closest) {
                    assignments[i] = closest;
                    changed.incrementAndGet();
                }
            }
        }
        
        return changed.get();
    }
    
    private double updateCentroids(double[][] points, double[][] centroids, int[] assignments, int k) {
        int n = points.length;
        
        double[] sums = new double[k * 3];
        int[] counts = new int[k];
        
        for (int i = 0; i < n; i++) {
            int c = assignments[i];
            sums[c * 3] += points[i][0];
            sums[c * 3 + 1] += points[i][1];
            sums[c * 3 + 2] += points[i][2];
            counts[c]++;
        }
        
        double maxMovement = 0;
        Random random = new Random(seed);
        
        for (int c = 0; c < k; c++) {
            double[] newCentroid = new double[3];
            
            if (counts[c] > 0) {
                double inv = 1.0 / counts[c];
                newCentroid[0] = sums[c * 3] * inv;
                newCentroid[1] = sums[c * 3 + 1] * inv;
                newCentroid[2] = sums[c * 3 + 2] * inv;
            } else {
                // Empty cluster: reinitialize
                int idx = random.nextInt(n);
                newCentroid = points[idx].clone();
            }
            
            double movement = distanceSq(centroids[c], newCentroid);
            if (movement > maxMovement) {
                maxMovement = movement;
            }
            
            centroids[c] = newCentroid;
        }
        
        return Math.sqrt(maxMovement);
    }
    
    private static double distanceSq(double[] a, double[] b) {
        double d0 = a[0] - b[0];
        double d1 = a[1] - b[1];
        double d2 = a[2] - b[2];
        return d0*d0 + d1*d1 + d2*d2;
    }
    
    @Override
    public String getName() {
        return "Hybrid DBSCAN+K-Means" + (nativeAccelerator.isAvailable() ? " (Native)" : "");
    }
}
