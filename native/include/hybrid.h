/**
 * Hybrid DBSCAN + K-Means Clustering Header
 */

#ifndef HYBRID_H
#define HYBRID_H

#include "common.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Performs hybrid DBSCAN + K-Means clustering for color quantization.
 * 
 * Algorithm:
 * 1. Divide points into blocks of block_size
 * 2. Apply DBSCAN to each block to extract representative colors
 * 3. Apply K-Means on collected representatives to get final k centroids
 * 
 * @param points Input color points
 * @param n Number of points
 * @param k Desired number of output clusters
 * @param block_size Size of each block for DBSCAN (recommended: 256-512)
 * @param dbscan_eps DBSCAN epsilon (neighborhood radius)
 * @param dbscan_min_pts DBSCAN minimum points for core point
 * @param kmeans_max_iter Maximum K-Means iterations
 * @param kmeans_threshold K-Means convergence threshold
 * @param centroids Output array for k centroids (must be preallocated)
 * @param seed Random seed for reproducibility
 * @return Number of K-Means iterations performed
 */
AICHAT_EXPORT int hybrid_cluster(
    const ColorPoint3f* points,
    int n,
    int k,
    int block_size,
    float dbscan_eps,
    int dbscan_min_pts,
    int kmeans_max_iter,
    float kmeans_threshold,
    ColorPoint3f* centroids,
    uint64_t seed
);

/**
 * Calculates recommended DBSCAN epsilon based on data distribution.
 * Samples multiple blocks and computes average k-distance.
 * 
 * @param points Input color points
 * @param n Number of points
 * @param block_size Size of each block
 * @param min_pts DBSCAN minimum points parameter
 * @param seed Random seed
 * @return Recommended epsilon value (clamped to 10-60 for color data)
 */
AICHAT_EXPORT float hybrid_calculate_dbscan_eps(
    const ColorPoint3f* points,
    int n,
    int block_size,
    int min_pts,
    uint64_t seed
);

#ifdef __cplusplus
}
#endif

#endif // HYBRID_H
