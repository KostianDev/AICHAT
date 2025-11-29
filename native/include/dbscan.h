#ifndef AICHAT_DBSCAN_H
#define AICHAT_DBSCAN_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

AICHAT_EXPORT float dbscan_calculate_eps(
    const ColorPoint3f* points,
    int n,
    int min_pts,
    int sample_size,
    uint64_t seed
);

AICHAT_EXPORT int dbscan_cluster(
    const ColorPoint3f* points,
    int n,
    float eps,
    int min_pts,
    int* labels
);

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
