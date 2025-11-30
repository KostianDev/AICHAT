#ifndef HYBRID_H
#define HYBRID_H

#include "common.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

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
