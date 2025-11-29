/**
 * AICHAT Native Library - K-Means Clustering
 */

#include "../include/kmeans.h"
#include "../include/distance.h"
#include "../include/random.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

// ============================================================================
// K-Means++ Initialization
// ============================================================================

AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
) {
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    // Allocate distances array
    float* distances = (float*)malloc(n * sizeof(float));
    
    // First centroid: random point
    int first = xorshift64_int(&rng, n);
    centroids[0] = points[first];
    
    // Remaining centroids: D² weighting
    for (int c = 1; c < k; c++) {
        float total_dist = 0.0f;
        
        // Compute distance to nearest existing centroid
        for (int i = 0; i < n; i++) {
            float min_dist = FLT_MAX;
            for (int j = 0; j < c; j++) {
                float d = distance_squared(&points[i], &centroids[j]);
                if (d < min_dist) min_dist = d;
            }
            distances[i] = min_dist;
            total_dist += min_dist;
        }
        
        // Weighted random selection
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

// ============================================================================
// Centroid Update
// ============================================================================

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
    
    // Allocate accumulators
    float* sums = (float*)calloc(k * 3, sizeof(float));
    int* counts = (int*)calloc(k, sizeof(int));
    
    // Accumulate points per cluster
    for (int i = 0; i < n; i++) {
        int cluster = assignments[i];
        if (cluster >= 0 && cluster < k) {
            sums[cluster * 3 + 0] += points[i].c1;
            sums[cluster * 3 + 1] += points[i].c2;
            sums[cluster * 3 + 2] += points[i].c3;
            counts[cluster]++;
        }
    }
    
    // Update centroids and track movement
    float max_movement = 0.0f;
    
    for (int c = 0; c < k; c++) {
        ColorPoint3f new_centroid;
        
        if (counts[c] > 0) {
            float inv_count = 1.0f / (float)counts[c];
            new_centroid.c1 = sums[c * 3 + 0] * inv_count;
            new_centroid.c2 = sums[c * 3 + 1] * inv_count;
            new_centroid.c3 = sums[c * 3 + 2] * inv_count;
        } else {
            // Empty cluster: reinitialize with random point
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

// ============================================================================
// Full K-Means Clustering
// ============================================================================

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
    
    // Initialize centroids with K-Means++
    kmeans_init_plusplus(points, n, k, centroids, seed);
    
    // Initialize assignments
    memset(assignments, 0, n * sizeof(int));
    
    int iteration;
    for (iteration = 0; iteration < max_iterations; iteration++) {
        // Assign points to nearest centroids
        int changed = assign_points_batch(points, n, centroids, k, assignments);
        
        // Update centroids
        float movement = kmeans_update_centroids(points, n, assignments, k, centroids, seed + iteration);
        
        // Check convergence
        if (movement < convergence_threshold || changed == 0) {
            iteration++;
            break;
        }
    }
    
    return iteration;
}
