/**
 * AICHAT Native Library - Distance Functions
 * 
 * SIMD-optimized distance calculations for color clustering.
 */

#ifndef AICHAT_DISTANCE_H
#define AICHAT_DISTANCE_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// Distance Functions
// ============================================================================

/**
 * Calculates squared Euclidean distance between two color points.
 * Faster than regular distance (no sqrt).
 */
AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b);

/**
 * Calculates Euclidean distance between two color points.
 */
AICHAT_EXPORT float distance(const ColorPoint3f* a, const ColorPoint3f* b);

/**
 * Finds the index of the nearest centroid for a single point.
 */
int find_nearest_centroid(
    const ColorPoint3f* point,
    const ColorPoint3f* centroids,
    int k
);

/**
 * Batch assignment: finds closest centroid for each point.
 * Uses SIMD when available (SSE for k >= 4).
 * 
 * @param points      Input points
 * @param n           Number of points
 * @param centroids   Array of centroids
 * @param k           Number of centroids
 * @param assignments In/Out: cluster assignments
 * @return Number of points that changed assignment
 */
AICHAT_EXPORT int assign_points_batch(
    const ColorPoint3f* points,
    int n,
    const ColorPoint3f* centroids,
    int k,
    int* assignments
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_DISTANCE_H
