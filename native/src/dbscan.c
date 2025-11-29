/**
 * AICHAT Native Library - DBSCAN Clustering
 * 
 * Optimized implementation with grid-based spatial index.
 * Based on "DBSCAN Revisited, Revisited" (Schubert et al., 2017).
 */

#include "../include/dbscan.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

// ============================================================================
// Grid-based Spatial Index
// ============================================================================

typedef struct {
    int* indices;
    int count;
    int capacity;
} GridCell;

typedef struct {
    GridCell* cells;
    int grid_size;
    float cell_size;
    float min_c1, min_c2, min_c3;
    int total_cells;
} SpatialGrid;

static inline int grid_coord(float val, float min_val, float cell_size, int grid_size) {
    int coord = (int)((val - min_val) / cell_size);
    if (coord < 0) coord = 0;
    if (coord >= grid_size) coord = grid_size - 1;
    return coord;
}

static inline int grid_index(int x, int y, int z, int grid_size) {
    return x * grid_size * grid_size + y * grid_size + z;
}

static SpatialGrid* grid_create(const ColorPoint3f* points, int n, float eps) {
    if (n == 0) return NULL;
    
    SpatialGrid* grid = (SpatialGrid*)malloc(sizeof(SpatialGrid));
    
    // Find bounding box
    float min_c1 = FLT_MAX, max_c1 = -FLT_MAX;
    float min_c2 = FLT_MAX, max_c2 = -FLT_MAX;
    float min_c3 = FLT_MAX, max_c3 = -FLT_MAX;
    
    for (int i = 0; i < n; i++) {
        if (points[i].c1 < min_c1) min_c1 = points[i].c1;
        if (points[i].c1 > max_c1) max_c1 = points[i].c1;
        if (points[i].c2 < min_c2) min_c2 = points[i].c2;
        if (points[i].c2 > max_c2) max_c2 = points[i].c2;
        if (points[i].c3 < min_c3) min_c3 = points[i].c3;
        if (points[i].c3 > max_c3) max_c3 = points[i].c3;
    }
    
    grid->cell_size = eps;
    grid->min_c1 = min_c1 - eps;
    grid->min_c2 = min_c2 - eps;
    grid->min_c3 = min_c3 - eps;
    
    float range_c1 = (max_c1 - min_c1) + 2 * eps;
    float range_c2 = (max_c2 - min_c2) + 2 * eps;
    float range_c3 = (max_c3 - min_c3) + 2 * eps;
    float max_range = fmaxf(fmaxf(range_c1, range_c2), range_c3);
    
    grid->grid_size = (int)ceilf(max_range / eps);
    if (grid->grid_size < 1) grid->grid_size = 1;
    if (grid->grid_size > 256) grid->grid_size = 256;
    
    grid->total_cells = grid->grid_size * grid->grid_size * grid->grid_size;
    grid->cells = (GridCell*)calloc(grid->total_cells, sizeof(GridCell));
    
    // Count points per cell
    int* counts = (int*)calloc(grid->total_cells, sizeof(int));
    for (int i = 0; i < n; i++) {
        int x = grid_coord(points[i].c1, grid->min_c1, grid->cell_size, grid->grid_size);
        int y = grid_coord(points[i].c2, grid->min_c2, grid->cell_size, grid->grid_size);
        int z = grid_coord(points[i].c3, grid->min_c3, grid->cell_size, grid->grid_size);
        counts[grid_index(x, y, z, grid->grid_size)]++;
    }
    
    // Allocate cell arrays
    for (int i = 0; i < grid->total_cells; i++) {
        if (counts[i] > 0) {
            grid->cells[i].indices = (int*)malloc(counts[i] * sizeof(int));
            grid->cells[i].capacity = counts[i];
            grid->cells[i].count = 0;
        }
    }
    
    // Insert points
    for (int i = 0; i < n; i++) {
        int x = grid_coord(points[i].c1, grid->min_c1, grid->cell_size, grid->grid_size);
        int y = grid_coord(points[i].c2, grid->min_c2, grid->cell_size, grid->grid_size);
        int z = grid_coord(points[i].c3, grid->min_c3, grid->cell_size, grid->grid_size);
        int idx = grid_index(x, y, z, grid->grid_size);
        grid->cells[idx].indices[grid->cells[idx].count++] = i;
    }
    
    free(counts);
    return grid;
}

static void grid_destroy(SpatialGrid* grid) {
    if (!grid) return;
    for (int i = 0; i < grid->total_cells; i++) {
        if (grid->cells[i].indices) {
            free(grid->cells[i].indices);
        }
    }
    free(grid->cells);
    free(grid);
}

static int grid_range_query(
    const SpatialGrid* grid,
    const ColorPoint3f* points,
    int point_idx,
    float eps_sq,
    int* neighbors,
    int max_neighbors
) {
    const ColorPoint3f* p = &points[point_idx];
    
    int cx = grid_coord(p->c1, grid->min_c1, grid->cell_size, grid->grid_size);
    int cy = grid_coord(p->c2, grid->min_c2, grid->cell_size, grid->grid_size);
    int cz = grid_coord(p->c3, grid->min_c3, grid->cell_size, grid->grid_size);
    
    int count = 0;
    
    // Check 3x3x3 neighborhood
    for (int dx = -1; dx <= 1 && count < max_neighbors; dx++) {
        int nx = cx + dx;
        if (nx < 0 || nx >= grid->grid_size) continue;
        
        for (int dy = -1; dy <= 1 && count < max_neighbors; dy++) {
            int ny = cy + dy;
            if (ny < 0 || ny >= grid->grid_size) continue;
            
            for (int dz = -1; dz <= 1 && count < max_neighbors; dz++) {
                int nz = cz + dz;
                if (nz < 0 || nz >= grid->grid_size) continue;
                
                int cell_idx = grid_index(nx, ny, nz, grid->grid_size);
                GridCell* cell = &grid->cells[cell_idx];
                
                for (int i = 0; i < cell->count && count < max_neighbors; i++) {
                    int j = cell->indices[i];
                    float d1 = p->c1 - points[j].c1;
                    float d2 = p->c2 - points[j].c2;
                    float d3 = p->c3 - points[j].c3;
                    float dist_sq = d1*d1 + d2*d2 + d3*d3;
                    
                    if (dist_sq <= eps_sq) {
                        neighbors[count++] = j;
                    }
                }
            }
        }
    }
    
    return count;
}

// ============================================================================
// Epsilon Calculation
// ============================================================================

AICHAT_EXPORT float dbscan_calculate_eps(
    const ColorPoint3f* points,
    int n,
    int min_pts,
    int sample_size,
    uint64_t seed
) {
    if (n <= min_pts) {
        return 15.0f;
    }
    
    int k = min_pts > 1 ? min_pts - 1 : 1;
    if (k > n - 1) k = n - 1;
    
    int actual_samples = sample_size < n ? sample_size : n;
    
    float* k_distances = (float*)malloc(actual_samples * sizeof(float));
    float* point_distances = (float*)malloc(n * sizeof(float));
    
    // LCG for sampling
    uint64_t rng_state = seed ? seed : 12345;
    
    for (int s = 0; s < actual_samples; s++) {
        rng_state = rng_state * 6364136223846793005ULL + 1442695040888963407ULL;
        int idx = (int)((rng_state >> 33) % (uint64_t)n);
        
        const ColorPoint3f* p = &points[idx];
        
        // Compute distances to all other points
        for (int j = 0; j < n; j++) {
            float d1 = p->c1 - points[j].c1;
            float d2 = p->c2 - points[j].c2;
            float d3 = p->c3 - points[j].c3;
            point_distances[j] = sqrtf(d1*d1 + d2*d2 + d3*d3);
        }
        
        // Partial sort to find k-th smallest
        for (int i = 0; i <= k; i++) {
            int min_idx = i;
            for (int j = i + 1; j < n; j++) {
                if (point_distances[j] < point_distances[min_idx]) {
                    min_idx = j;
                }
            }
            if (min_idx != i) {
                float tmp = point_distances[i];
                point_distances[i] = point_distances[min_idx];
                point_distances[min_idx] = tmp;
            }
        }
        
        k_distances[s] = point_distances[k];
    }
    
    // Sort k-distances
    for (int i = 0; i < actual_samples - 1; i++) {
        for (int j = i + 1; j < actual_samples; j++) {
            if (k_distances[j] < k_distances[i]) {
                float tmp = k_distances[i];
                k_distances[i] = k_distances[j];
                k_distances[j] = tmp;
            }
        }
    }
    
    // Elbow at ~85th percentile
    int elbow_idx = (int)(actual_samples * 0.85);
    float eps = k_distances[elbow_idx];
    
    free(k_distances);
    free(point_distances);
    
    // Clamp to reasonable range
    if (eps < 5.0f) eps = 5.0f;
    if (eps > 100.0f) eps = 100.0f;
    
    return eps;
}

// ============================================================================
// DBSCAN Clustering
// ============================================================================

AICHAT_EXPORT int dbscan_cluster(
    const ColorPoint3f* points,
    int n,
    float eps,
    int min_pts,
    int* labels
) {
    if (n == 0) return 0;
    
    float eps_sq = eps * eps;
    
    // Initialize labels
    for (int i = 0; i < n; i++) {
        labels[i] = DBSCAN_UNCLASSIFIED;
    }
    
    // Build spatial grid
    SpatialGrid* grid = grid_create(points, n, eps);
    if (!grid) return 0;
    
    // Allocate buffers
    int* neighbors = (int*)malloc(n * sizeof(int));
    int* seed_queue = (int*)malloc(n * sizeof(int));
    int* in_queue = (int*)calloc(n, sizeof(int));
    
    int cluster_id = 0;
    
    for (int i = 0; i < n; i++) {
        if (labels[i] != DBSCAN_UNCLASSIFIED) continue;
        
        int neighbor_count = grid_range_query(grid, points, i, eps_sq, neighbors, n);
        
        if (neighbor_count < min_pts) {
            labels[i] = DBSCAN_NOISE;
            continue;
        }
        
        // Start new cluster
        labels[i] = cluster_id;
        
        int queue_start = 0;
        int queue_end = 0;
        
        for (int j = 0; j < neighbor_count; j++) {
            int neighbor = neighbors[j];
            if (neighbor != i && !in_queue[neighbor]) {
                seed_queue[queue_end++] = neighbor;
                in_queue[neighbor] = 1;
            }
        }
        
        // Expand cluster
        while (queue_start < queue_end) {
            int q = seed_queue[queue_start++];
            
            if (labels[q] == DBSCAN_NOISE) {
                labels[q] = cluster_id;
            }
            
            if (labels[q] != DBSCAN_UNCLASSIFIED) continue;
            
            labels[q] = cluster_id;
            
            int q_neighbor_count = grid_range_query(grid, points, q, eps_sq, neighbors, n);
            
            if (q_neighbor_count >= min_pts) {
                for (int j = 0; j < q_neighbor_count; j++) {
                    int neighbor = neighbors[j];
                    if (!in_queue[neighbor] && 
                        (labels[neighbor] == DBSCAN_UNCLASSIFIED || labels[neighbor] == DBSCAN_NOISE)) {
                        seed_queue[queue_end++] = neighbor;
                        in_queue[neighbor] = 1;
                    }
                }
            }
        }
        
        // Clear in_queue
        for (int j = 0; j < queue_end; j++) {
            in_queue[seed_queue[j]] = 0;
        }
        
        cluster_id++;
    }
    
    free(neighbors);
    free(seed_queue);
    free(in_queue);
    grid_destroy(grid);
    
    return cluster_id;
}

// ============================================================================
// Centroid Calculation
// ============================================================================

AICHAT_EXPORT void dbscan_calculate_centroids(
    const ColorPoint3f* points,
    int n,
    const int* labels,
    int num_clusters,
    ColorPoint3f* centroids
) {
    if (num_clusters == 0) return;
    
    double* sums = (double*)calloc(num_clusters * 3, sizeof(double));
    int* counts = (int*)calloc(num_clusters, sizeof(int));
    
    for (int i = 0; i < n; i++) {
        int cluster = labels[i];
        if (cluster >= 0 && cluster < num_clusters) {
            sums[cluster * 3 + 0] += points[i].c1;
            sums[cluster * 3 + 1] += points[i].c2;
            sums[cluster * 3 + 2] += points[i].c3;
            counts[cluster]++;
        }
    }
    
    for (int c = 0; c < num_clusters; c++) {
        if (counts[c] > 0) {
            double inv = 1.0 / (double)counts[c];
            centroids[c].c1 = (float)(sums[c * 3 + 0] * inv);
            centroids[c].c2 = (float)(sums[c * 3 + 1] * inv);
            centroids[c].c3 = (float)(sums[c * 3 + 2] * inv);
        } else {
            centroids[c].c1 = 127.5f;
            centroids[c].c2 = 127.5f;
            centroids[c].c3 = 127.5f;
        }
    }
    
    free(sums);
    free(counts);
}
