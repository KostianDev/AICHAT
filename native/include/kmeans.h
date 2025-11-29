/**
 * AICHAT Native Library - K-Means Clustering
 * 
 * K-Means++ initialization and clustering for color palette extraction.
 */

#ifndef AICHAT_KMEANS_H
#define AICHAT_KMEANS_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// K-Means Result Structure
// ============================================================================

typedef struct {
    ColorPoint3f* centroids;  // Array of k centroids
    int* assignments;         // Cluster assignment for each point
    int iterations;           // Number of iterations performed
    int converged;            // 1 if converged, 0 otherwise
} KMeansResult;

// ============================================================================
// K-Means Functions
// ============================================================================

/**
 * Performs K-Means++ initialization.
 * D² weighted sampling for better initial centroid placement.
 * 
 * @param points    Input color points
 * @param n         Number of points
 * @param k         Number of clusters
 * @param centroids Output: k centroids (must be preallocated)
 * @param seed      Random seed for reproducibility
 */
AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

/**
 * Updates centroids based on current assignments.
 * Handles empty clusters by reinitializing with random points.
 * 
 * @param points      Input color points
 * @param n           Number of points
 * @param assignments Current cluster assignments
 * @param k           Number of clusters
 * @param centroids   In/Out: centroids to update
 * @param seed        Random seed for empty cluster handling
 * @return Maximum centroid movement distance
 */
AICHAT_EXPORT float kmeans_update_centroids(
    const ColorPoint3f* points,
    int n,
    const int* assignments,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

/**
 * Performs full K-Means clustering.
 * 
 * @param points                Input color points
 * @param n                     Number of points
 * @param k                     Number of clusters
 * @param max_iterations        Maximum iterations
 * @param convergence_threshold Stop when centroid movement < threshold
 * @param centroids             Output: k centroids (must be preallocated)
 * @param assignments           Output: n assignments (must be preallocated)
 * @param seed                  Random seed
 * @return Number of iterations performed
 */
AICHAT_EXPORT int kmeans_cluster(
    const ColorPoint3f* points,
    int n,
    int k,
    int max_iterations,
    float convergence_threshold,
    ColorPoint3f* centroids,
    int* assignments,
    uint64_t seed
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_KMEANS_H
