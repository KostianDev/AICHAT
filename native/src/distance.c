#include "../include/distance.h"
#include <math.h>
#include <float.h>
#include <stdlib.h>

#ifdef _OPENMP
#include <omp.h>
#endif

#if HAS_SSE
    #include <emmintrin.h>
#endif

AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b) {
    float d1 = a->c1 - b->c1;
    float d2 = a->c2 - b->c2;
    float d3 = a->c3 - b->c3;
    return d1 * d1 + d2 * d2 + d3 * d3;
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

AICHAT_EXPORT int assign_points_batch(
    const ColorPoint3f* points,
    int n,
    const ColorPoint3f* centroids,
    int k,
    int* assignments
) {
    int changed = 0;
    
    #pragma omp parallel for reduction(+:changed) if(n > 5000)
    for (int i = 0; i < n; i++) {
        int nearest = find_nearest_centroid(&points[i], centroids, k);
        if (assignments[i] != nearest) {
            assignments[i] = nearest;
            changed++;
        }
    }
    
    return changed;
}
