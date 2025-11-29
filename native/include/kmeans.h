#ifndef AICHAT_KMEANS_H
#define AICHAT_KMEANS_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    ColorPoint3f* centroids;
    int* assignments;
    int iterations;
    int converged;
} KMeansResult;

AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

AICHAT_EXPORT float kmeans_update_centroids(
    const ColorPoint3f* points,
    int n,
    const int* assignments,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

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
