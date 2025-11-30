#include "../include/hybrid.h"
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

typedef struct {
    ColorPoint3f* representatives;
    int count;
} BlockResult;

static inline float point_distance_sq(const ColorPoint3f* a, const ColorPoint3f* b) {
    float d1 = a->c1 - b->c1;
    float d2 = a->c2 - b->c2;
    float d3 = a->c3 - b->c3;
    return d1*d1 + d2*d2 + d3*d3;
}

static inline int count_neighbors(const ColorPoint3f* points, int n, int idx, float eps_sq) {
    const ColorPoint3f* p = &points[idx];
    int count = 0;
    for (int j = 0; j < n; j++) {
        float d1 = p->c1 - points[j].c1;
        float d2 = p->c2 - points[j].c2;
        float d3 = p->c3 - points[j].c3;
        if (d1*d1 + d2*d2 + d3*d3 <= eps_sq) count++;
    }
    return count;
}

static BlockResult block_dbscan(
    const ColorPoint3f* points,
    int n,
    float eps,
    int min_pts
) {
    BlockResult result = {NULL, 0};
    
    if (n == 0) return result;
    
    float eps_sq = eps * eps;
    
    int* labels = (int*)malloc(n * sizeof(int));
    int* queue = (int*)malloc(n * sizeof(int));
    
    for (int i = 0; i < n; i++) labels[i] = -2;
    
    int cluster_id = 0;
    
    for (int i = 0; i < n; i++) {
        if (labels[i] != -2) continue;
        
        int neighbor_count = count_neighbors(points, n, i, eps_sq);
        
        if (neighbor_count < min_pts) {
            labels[i] = -1;
            continue;
        }
        
        labels[i] = cluster_id;
        int queue_start = 0, queue_end = 0;
        
        const ColorPoint3f* pi = &points[i];
        for (int j = 0; j < n; j++) {
            if (j != i && labels[j] == -2) {
                float d1 = pi->c1 - points[j].c1;
                float d2 = pi->c2 - points[j].c2;
                float d3 = pi->c3 - points[j].c3;
                if (d1*d1 + d2*d2 + d3*d3 <= eps_sq) {
                    queue[queue_end++] = j;
                    labels[j] = -3;
                }
            }
        }
        
        while (queue_start < queue_end) {
            int q = queue[queue_start++];
            
            if (labels[q] == -1) {
                labels[q] = cluster_id;
                continue;
            }
            
            labels[q] = cluster_id;
            
            if (count_neighbors(points, n, q, eps_sq) >= min_pts) {
                const ColorPoint3f* pq = &points[q];
                for (int j = 0; j < n; j++) {
                    if (labels[j] == -2) {
                        float d1 = pq->c1 - points[j].c1;
                        float d2 = pq->c2 - points[j].c2;
                        float d3 = pq->c3 - points[j].c3;
                        if (d1*d1 + d2*d2 + d3*d3 <= eps_sq) {
                            queue[queue_end++] = j;
                            labels[j] = -3;
                        }
                    }
                }
            }
        }
        
        cluster_id++;
    }
    
    free(queue);
    
    int noise_count = 0;
    for (int i = 0; i < n; i++) {
        if (labels[i] == -1) noise_count++;
    }
    
    int max_representatives = cluster_id + noise_count;
    if (max_representatives == 0) {
        free(labels);
        return result;
    }
    
    result.representatives = (ColorPoint3f*)malloc((size_t)max_representatives * sizeof(ColorPoint3f));
    result.count = 0;
    
    for (int c = 0; c < cluster_id; c++) {
        double sum_c1 = 0, sum_c2 = 0, sum_c3 = 0;
        int count = 0;
        
        for (int i = 0; i < n; i++) {
            if (labels[i] == c) {
                sum_c1 += points[i].c1;
                sum_c2 += points[i].c2;
                sum_c3 += points[i].c3;
                count++;
            }
        }
        
        if (count > 0) {
            result.representatives[result.count].c1 = (float)(sum_c1 / count);
            result.representatives[result.count].c2 = (float)(sum_c2 / count);
            result.representatives[result.count].c3 = (float)(sum_c3 / count);
            result.count++;
        }
    }
    
    for (int i = 0; i < n; i++) {
        if (labels[i] == -1) {
            result.representatives[result.count++] = points[i];
        }
    }
    
    free(labels);
    return result;
}

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
) {
    if (n == 0 || k <= 0) return 0;
    if (k > n) k = n;
    
    int actual_max_iter = kmeans_max_iter;
    if (k > 100) actual_max_iter = 20;
    else if (k > 32) actual_max_iter = 30;
    
    if (n <= block_size * 2) {
        int* assignments = (int*)malloc(n * sizeof(int));
        int iterations = kmeans_cluster(points, n, k, actual_max_iter, 
                                         kmeans_threshold, centroids, assignments, seed);
        free(assignments);
        return iterations;
    }
    
    int num_blocks = (n + block_size - 1) / block_size;
    
    ColorPoint3f* all_representatives = (ColorPoint3f*)malloc(n * sizeof(ColorPoint3f));
    int total_representatives = 0;
    
    #ifdef _OPENMP
    BlockResult* block_results = (BlockResult*)malloc(num_blocks * sizeof(BlockResult));
    
    #pragma omp parallel for if(num_blocks > 4)
    for (int b = 0; b < num_blocks; b++) {
        int start = b * block_size;
        int end = start + block_size;
        if (end > n) end = n;
        int block_n = end - start;
        
        block_results[b] = block_dbscan(&points[start], block_n, dbscan_eps, dbscan_min_pts);
    }
    
    for (int b = 0; b < num_blocks; b++) {
        for (int i = 0; i < block_results[b].count; i++) {
            all_representatives[total_representatives++] = block_results[b].representatives[i];
        }
        free(block_results[b].representatives);
    }
    free(block_results);
    
    #else
    for (int b = 0; b < num_blocks; b++) {
        int start = b * block_size;
        int end = start + block_size;
        if (end > n) end = n;
        int block_n = end - start;
        
        BlockResult br = block_dbscan(&points[start], block_n, dbscan_eps, dbscan_min_pts);
        
        for (int i = 0; i < br.count; i++) {
            all_representatives[total_representatives++] = br.representatives[i];
        }
        free(br.representatives);
    }
    #endif
    
    if (total_representatives < k) {
        XorShift64 rng;
        xorshift64_init(&rng, seed);
        
        while (total_representatives < k) {
            int idx = xorshift64_int(&rng, n);
            all_representatives[total_representatives++] = points[idx];
        }
    }
    
    int* assignments = (int*)malloc(total_representatives * sizeof(int));
    int iterations = kmeans_cluster(all_representatives, total_representatives, k,
                                     actual_max_iter, kmeans_threshold, 
                                     centroids, assignments, seed);
    
    free(assignments);
    free(all_representatives);
    
    return iterations;
}

AICHAT_EXPORT float hybrid_calculate_dbscan_eps(
    const ColorPoint3f* points,
    int n,
    int block_size,
    int min_pts,
    uint64_t seed
) {
    if (n <= block_size) {
        return 15.0f;
    }
    
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    int num_blocks = (n + block_size - 1) / block_size;
    int sample_blocks = num_blocks < 10 ? num_blocks : 10;
    
    float total_eps = 0.0f;
    
    for (int s = 0; s < sample_blocks; s++) {
        int block_idx = xorshift64_int(&rng, num_blocks);
        int start = block_idx * block_size;
        int end = start + block_size;
        if (end > n) end = n;
        int block_n = end - start;
        
        if (block_n <= min_pts) {
            total_eps += 15.0f;
            continue;
        }
        
        int k = min_pts > 1 ? min_pts - 1 : 1;
        if (k >= block_n) k = block_n - 1;
        
        int sample_size = block_n < 20 ? block_n : 20;
        float* k_distances = (float*)malloc(sample_size * sizeof(float));
        
        for (int i = 0; i < sample_size; i++) {
            int idx = start + xorshift64_int(&rng, block_n);
            const ColorPoint3f* p = &points[idx];
            
            float* distances = (float*)malloc(block_n * sizeof(float));
            for (int j = 0; j < block_n; j++) {
                distances[j] = sqrtf(point_distance_sq(p, &points[start + j]));
            }
            
            for (int m = 0; m <= k; m++) {
                int min_idx = m;
                for (int j = m + 1; j < block_n; j++) {
                    if (distances[j] < distances[min_idx]) min_idx = j;
                }
                if (min_idx != m) {
                    float tmp = distances[m];
                    distances[m] = distances[min_idx];
                    distances[min_idx] = tmp;
                }
            }
            
            k_distances[i] = distances[k];
            free(distances);
        }
        
        for (int i = 0; i < sample_size - 1; i++) {
            for (int j = i + 1; j < sample_size; j++) {
                if (k_distances[j] < k_distances[i]) {
                    float tmp = k_distances[i];
                    k_distances[i] = k_distances[j];
                    k_distances[j] = tmp;
                }
            }
        }
        
        int median_idx = sample_size / 2;
        total_eps += k_distances[median_idx];
        free(k_distances);
    }
    
    float avg_eps = total_eps / sample_blocks;
    
    if (avg_eps < 8.0f) avg_eps = 8.0f;
    if (avg_eps > 30.0f) avg_eps = 30.0f;
    
    return avg_eps;
}
