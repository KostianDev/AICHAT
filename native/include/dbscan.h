/**
 * AICHAT Native Library - DBSCAN Clustering
 * 
 * Optimized DBSCAN using grid-based spatial index.
 * Based on "DBSCAN Revisited, Revisited" (Schubert et al., 2017).
 */

#ifndef AICHAT_DBSCAN_H
#define AICHAT_DBSCAN_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// DBSCAN Functions
// ============================================================================

/**
 * Calculates adaptive epsilon using k-distance graph heuristic.
 * Uses the "elbow" method from the original DBSCAN paper.
 * 
 * @param points      Input color points
 * @param n           Number of points
 * @param min_pts     Minimum points for core point
 * @param sample_size Number of points to sample for eps calculation
 * @param seed        Random seed
 * @return Recommended epsilon value (clamped to 5-100 for color data)
 */
AICHAT_EXPORT float dbscan_calculate_eps(
    const ColorPoint3f* points,
    int n,
    int min_pts,
    int sample_size,
    uint64_t seed
);

/**
 * Performs DBSCAN clustering using grid-based spatial index.
 * 
 * For N points with small eps (typical for color clustering):
 * - Grid construction: O(N)
 * - Range queries: O(1) average (only check 27 cells)
 * - Total: O(N) average case for well-distributed data
 * 
 * @param points   Input color points
 * @param n        Number of points
 * @param eps      Epsilon neighborhood radius (recommended: 10-50 for RGB)
 * @param min_pts  Minimum points for core point (recommended: 4-10 for 3D)
 * @param labels   Output: cluster labels (-1 = noise, 0+ = cluster id)
 * @return Number of clusters found
 */
AICHAT_EXPORT int dbscan_cluster(
    const ColorPoint3f* points,
    int n,
    float eps,
    int min_pts,
    int* labels
);

/**
 * Calculates centroids from DBSCAN labels.
 * Noise points (label == -1) are excluded.
 * 
 * @param points       Input color points
 * @param n            Number of points
 * @param labels       DBSCAN cluster labels
 * @param num_clusters Number of clusters
 * @param centroids    Output: cluster centroids
 */
AICHAT_EXPORT void dbscan_calculate_centroids(
    const ColorPoint3f* points,
    int n,
    const int* labels,
    int num_clusters,
    ColorPoint3f* centroids
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_DBSCAN_H
