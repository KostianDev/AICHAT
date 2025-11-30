#include "../include/kmeans.h"
#include "../include/distance.h"
#include "../include/random.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

#ifdef _OPENMP
#include <omp.h>
#endif

AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
) {
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    if (k > 64) {
        int step = n / k;
        if (step < 1) step = 1;
        for (int c = 0; c < k; c++) {
            int idx = (c * step + xorshift64_int(&rng, step)) % n;
            centroids[c] = points[idx];
        }
        return;
    }
    
    float* distances = (float*)malloc(n * sizeof(float));
    
    int first = xorshift64_int(&rng, n);
    centroids[0] = points[first];
    
    for (int c = 1; c < k; c++) {
        float total_dist = 0.0f;
        
        for (int i = 0; i < n; i++) {
            float min_dist = FLT_MAX;
            for (int j = 0; j < c; j++) {
                float d = distance_squared(&points[i], &centroids[j]);
                if (d < min_dist) min_dist = d;
            }
            distances[i] = min_dist;
            total_dist += min_dist;
        }
        
        float threshold = (float)xorshift64_double(&rng) * total_dist;
        float cumulative = 0.0f;
        int selected = n - 1;
        
        for (int i = 0; i < n; i++) {
            cumulative += distances[i];
            if (cumulative >= threshold) {
                selected = i;
                break;
            }
        }
        
        centroids[c] = points[selected];
    }
    
    free(distances);
}

AICHAT_EXPORT float kmeans_update_centroids(
    const ColorPoint3f* points,
    int n,
    const int* assignments,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
) {
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    float* sums = (float*)calloc(k * 3, sizeof(float));
    int* counts = (int*)calloc(k, sizeof(int));
    
    #pragma omp parallel if(n > 10000)
    {
        float* local_sums = (float*)calloc(k * 3, sizeof(float));
        int* local_counts = (int*)calloc(k, sizeof(int));
        
        #pragma omp for nowait
        for (int i = 0; i < n; i++) {
            int cluster = assignments[i];
            if (cluster >= 0 && cluster < k) {
                local_sums[cluster * 3 + 0] += points[i].c1;
                local_sums[cluster * 3 + 1] += points[i].c2;
                local_sums[cluster * 3 + 2] += points[i].c3;
                local_counts[cluster]++;
            }
        }
        
        #pragma omp critical
        {
            for (int c = 0; c < k; c++) {
                sums[c * 3 + 0] += local_sums[c * 3 + 0];
                sums[c * 3 + 1] += local_sums[c * 3 + 1];
                sums[c * 3 + 2] += local_sums[c * 3 + 2];
                counts[c] += local_counts[c];
            }
        }
        
        free(local_sums);
        free(local_counts);
    }
    
    float max_movement = 0.0f;
    
    for (int c = 0; c < k; c++) {
        ColorPoint3f new_centroid;
        
        if (counts[c] > 0) {
            float inv_count = 1.0f / (float)counts[c];
            new_centroid.c1 = sums[c * 3 + 0] * inv_count;
            new_centroid.c2 = sums[c * 3 + 1] * inv_count;
            new_centroid.c3 = sums[c * 3 + 2] * inv_count;
        } else {
            int rand_idx = xorshift64_int(&rng, n);
            new_centroid = points[rand_idx];
        }
        
        float movement = distance_squared(&centroids[c], &new_centroid);
        if (movement > max_movement) {
            max_movement = movement;
        }
        
        centroids[c] = new_centroid;
    }
    
    free(sums);
    free(counts);
    
    return sqrtf(max_movement);
}

AICHAT_EXPORT int kmeans_cluster(
    const ColorPoint3f* points,
    int n,
    int k,
    int max_iterations,
    float convergence_threshold,
    ColorPoint3f* centroids,
    int* assignments,
    uint64_t seed
) {
    if (n == 0 || k <= 0) return 0;
    if (k > n) k = n;
    
    kmeans_init_plusplus(points, n, k, centroids, seed);
    
    memset(assignments, 0, n * sizeof(int));
    
    int iteration;
    for (iteration = 0; iteration < max_iterations; iteration++) {
        int changed = assign_points_batch(points, n, centroids, k, assignments);
        
        float movement = kmeans_update_centroids(points, n, assignments, k, centroids, seed + iteration);
        
        if (movement < convergence_threshold || changed == 0) {
            iteration++;
            break;
        }
    }
    
    return iteration;
}
