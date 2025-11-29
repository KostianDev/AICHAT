/**
 * AICHAT Native Library - Distance Functions
 * 
 * SIMD-optimized distance calculations.
 */

#include "../include/distance.h"
#include <math.h>
#include <float.h>
#include <stdlib.h>

#if HAS_SSE
    #include <emmintrin.h>
#endif

// ============================================================================
// Scalar Distance Functions
// ============================================================================

AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b) {
    float d1 = a->c1 - b->c1;
    float d2 = a->c2 - b->c2;
    float d3 = a->c3 - b->c3;
    return d1 * d1 + d2 * d2 + d3 * d3;
}

AICHAT_EXPORT float distance(const ColorPoint3f* a, const ColorPoint3f* b) {
    return sqrtf(distance_squared(a, b));
}

int find_nearest_centroid(
    const ColorPoint3f* point,
    const ColorPoint3f* centroids,
    int k
) {
    int nearest = 0;
    float min_dist = FLT_MAX;
    
    for (int i = 0; i < k; i++) {
        float dist = distance_squared(point, &centroids[i]);
        if (dist < min_dist) {
            min_dist = dist;
            nearest = i;
        }
    }
    
    return nearest;
}

// ============================================================================
// SSE-Optimized Distance Functions
// ============================================================================

#if HAS_SSE
/**
 * SSE-optimized distance calculation for 4 centroids at once.
 */
static void find_nearest_sse(
    const ColorPoint3f* RESTRICT points,
    int n,
    const ColorPoint3f* RESTRICT centroids,
    int k,
    int* RESTRICT assignments
) {
    for (int i = 0; i < n; i++) {
        const ColorPoint3f* p = &points[i];
        float min_dist = FLT_MAX;
        int nearest = 0;
        
        // Load point components
        __m128 pc1 = _mm_set1_ps(p->c1);
        __m128 pc2 = _mm_set1_ps(p->c2);
        __m128 pc3 = _mm_set1_ps(p->c3);
        
        // Process 4 centroids at a time
        int j;
        for (j = 0; j + 3 < k; j += 4) {
            // Load 4 centroids
            __m128 c1 = _mm_set_ps(
                centroids[j+3].c1, centroids[j+2].c1,
                centroids[j+1].c1, centroids[j].c1
            );
            __m128 c2 = _mm_set_ps(
                centroids[j+3].c2, centroids[j+2].c2,
                centroids[j+1].c2, centroids[j].c2
            );
            __m128 c3 = _mm_set_ps(
                centroids[j+3].c3, centroids[j+2].c3,
                centroids[j+1].c3, centroids[j].c3
            );
            
            // Compute differences
            __m128 d1 = _mm_sub_ps(pc1, c1);
            __m128 d2 = _mm_sub_ps(pc2, c2);
            __m128 d3 = _mm_sub_ps(pc3, c3);
            
            // Compute squared distances
            __m128 dist = _mm_add_ps(
                _mm_add_ps(_mm_mul_ps(d1, d1), _mm_mul_ps(d2, d2)),
                _mm_mul_ps(d3, d3)
            );
            
            // Extract and compare
            float distances[4];
            _mm_storeu_ps(distances, dist);
            
            for (int di = 0; di < 4; di++) {
                if (distances[di] < min_dist) {
                    min_dist = distances[di];
                    nearest = j + di;
                }
            }
        }
        
        // Handle remaining centroids
        for (; j < k; j++) {
            float dist = distance_squared(p, &centroids[j]);
            if (dist < min_dist) {
                min_dist = dist;
                nearest = j;
            }
        }
        
        assignments[i] = nearest;
    }
}
#endif

// ============================================================================
// Batch Assignment
// ============================================================================

AICHAT_EXPORT int assign_points_batch(
    const ColorPoint3f* points,
    int n,
    const ColorPoint3f* centroids,
    int k,
    int* assignments
) {
    int changed = 0;
    
#if HAS_SSE
    // Use SSE for larger k values
    if (k >= 4) {
        int* new_assignments = (int*)malloc(n * sizeof(int));
        find_nearest_sse(points, n, centroids, k, new_assignments);
        
        for (int i = 0; i < n; i++) {
            if (assignments[i] != new_assignments[i]) {
                assignments[i] = new_assignments[i];
                changed++;
            }
        }
        free(new_assignments);
        return changed;
    }
#endif
    
    // Scalar fallback
    for (int i = 0; i < n; i++) {
        int nearest = find_nearest_centroid(&points[i], centroids, k);
        if (assignments[i] != nearest) {
            assignments[i] = nearest;
            changed++;
        }
    }
    
    return changed;
}
