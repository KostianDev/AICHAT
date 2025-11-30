#ifndef AICHAT_DISTANCE_H
#define AICHAT_DISTANCE_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b);

int find_nearest_centroid(
    const ColorPoint3f* point,
    const ColorPoint3f* centroids,
    int k
);

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
