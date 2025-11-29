package aichat.algorithm;

import aichat.model.ColorPoint;
import java.util.List;

/**
 * Strategy interface for clustering algorithms.
 * Allows interchangeable use of different clustering implementations.
 */
public interface ClusteringStrategy {
    
    /**
     * Clusters the given color points into groups and returns the centroids.
     * 
     * @param points List of color points to cluster
     * @param k Number of clusters (interpretation may vary by algorithm)
     * @return List of centroid points representing each cluster
     */
    List<ColorPoint> cluster(List<ColorPoint> points, int k);
    
    /**
     * Returns the name of this clustering algorithm.
     */
    String getName();
}
