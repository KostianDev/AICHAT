/**
 * AICHAT Native Library - High-performance color clustering and image processing.
 * SIMD optimized (SSE/AVX).
 */

#include "aichat_native.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

#if defined(__SSE__) || defined(__SSE2__)
    #include <emmintrin.h>
    #define HAS_SSE 1
#else
    #define HAS_SSE 0
#endif

#if defined(__AVX__)
    #include <immintrin.h>
    #define HAS_AVX 1
#else
    #define HAS_AVX 0
#endif

#define LIKELY(x)   __builtin_expect(!!(x), 1)
#define UNLIKELY(x) __builtin_expect(!!(x), 0)
#define RESTRICT __restrict__

static const float REF_X = 95.047f;
static const float REF_Y = 100.000f;
static const float REF_Z = 108.883f;
static const float EPSILON = 0.008856f;
static const float KAPPA = 903.3f;
static const float DELTA = 6.0f / 29.0f;

AICHAT_EXPORT const char* aichat_native_version(void) {
    return "1.0.0-panama";
}

AICHAT_EXPORT int aichat_has_simd(void) {
    return HAS_SSE || HAS_AVX;
}

// Random Number Generator (xorshift64)

typedef struct {
    uint64_t state;
} xorshift64_state;

static inline uint64_t xorshift64(xorshift64_state* s) {
    uint64_t x = s->state;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    s->state = x;
    return x;
}

static inline double xorshift64_double(xorshift64_state* s) {
    return (double)(xorshift64(s) >> 11) / (double)(1ULL << 53);
}

static inline int xorshift64_range(xorshift64_state* s, int max) {
    return (int)(xorshift64(s) % (uint64_t)max);
}

// Distance Functions

AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b) {
    float d1 = a->c1 - b->c1;
    float d2 = a->c2 - b->c2;
    float d3 = a->c3 - b->c3;
    return d1 * d1 + d2 * d2 + d3 * d3;
}

AICHAT_EXPORT float distance(const ColorPoint3f* a, const ColorPoint3f* b) {
    return sqrtf(distance_squared(a, b));
}

/**
 * Find nearest centroid for a single point.
 */
static inline int find_nearest(
    const ColorPoint3f* RESTRICT point,
    const ColorPoint3f* RESTRICT centroids,
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

#if HAS_SSE
/**
 * SSE-optimized distance calculation for 4 points at once.
 * Processes points in groups of 4 for better vectorization.
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
        int nearest = find_nearest(&points[i], centroids, k);
        if (assignments[i] != nearest) {
            assignments[i] = nearest;
            changed++;
        }
    }
    
    return changed;
}

// K-Means Clustering

AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
) {
    xorshift64_state rng = { .state = seed ? seed : 42 };
    
    // Allocate distances array
    float* distances = (float*)malloc(n * sizeof(float));
    
    // First centroid: random point
    int first = xorshift64_range(&rng, n);
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

AICHAT_EXPORT float kmeans_update_centroids(
    const ColorPoint3f* points,
    int n,
    const int* assignments,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
) {
    xorshift64_state rng = { .state = seed ? seed : 42 };
    
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
            int rand_idx = xorshift64_range(&rng, n);
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

// Color Space Conversion

static inline float srgb_to_linear(float c) {
    c = c / 255.0f;
    return (c > 0.04045f) ? powf((c + 0.055f) / 1.055f, 2.4f) : c / 12.92f;
}

static inline float linear_to_srgb(float c) {
    c = (c > 0.0031308f) ? (1.055f * powf(c, 1.0f / 2.4f) - 0.055f) : 12.92f * c;
    return c * 255.0f;
}

static inline float lab_f(float t) {
    return (t > EPSILON) ? cbrtf(t) : (KAPPA * t + 16.0f) / 116.0f;
}

static inline float lab_f_inv(float t) {
    return (t > DELTA) ? t * t * t : (116.0f * t - 16.0f) / KAPPA;
}

static void rgb_to_lab_single(const ColorPoint3f* rgb, ColorPoint3f* lab) {
    // RGB to linear
    float r = srgb_to_linear(rgb->c1);
    float g = srgb_to_linear(rgb->c2);
    float b = srgb_to_linear(rgb->c3);
    
    // Linear RGB to XYZ
    float x = (r * 0.4124564f + g * 0.3575761f + b * 0.1804375f) * 100.0f;
    float y = (r * 0.2126729f + g * 0.7151522f + b * 0.0721750f) * 100.0f;
    float z = (r * 0.0193339f + g * 0.1191920f + b * 0.9503041f) * 100.0f;
    
    // XYZ to Lab
    float fx = lab_f(x / REF_X);
    float fy = lab_f(y / REF_Y);
    float fz = lab_f(z / REF_Z);
    
    lab->c1 = 116.0f * fy - 16.0f;  // L
    lab->c2 = 500.0f * (fx - fy);    // a
    lab->c3 = 200.0f * (fy - fz);    // b
}

static void lab_to_rgb_single(const ColorPoint3f* lab, ColorPoint3f* rgb) {
    // Lab to XYZ
    float fy = (lab->c1 + 16.0f) / 116.0f;
    float fx = lab->c2 / 500.0f + fy;
    float fz = fy - lab->c3 / 200.0f;
    
    float x = lab_f_inv(fx) * REF_X;
    float y = lab_f_inv(fy) * REF_Y;
    float z = lab_f_inv(fz) * REF_Z;
    
    // XYZ to linear RGB
    x /= 100.0f;
    y /= 100.0f;
    z /= 100.0f;
    
    float r = x *  3.2404542f + y * -1.5371385f + z * -0.4985314f;
    float g = x * -0.9692660f + y *  1.8760108f + z *  0.0415560f;
    float b = x *  0.0556434f + y * -0.2040259f + z *  1.0572252f;
    
    // Linear to sRGB
    rgb->c1 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(r)));
    rgb->c2 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(g)));
    rgb->c3 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(b)));
}

AICHAT_EXPORT void rgb_to_lab_batch(
    const ColorPoint3f* rgb,
    ColorPoint3f* lab,
    int n
) {
    // Process in parallel chunks for cache efficiency
    #pragma omp parallel for if(n > 1000)
    for (int i = 0; i < n; i++) {
        rgb_to_lab_single(&rgb[i], &lab[i]);
    }
}

AICHAT_EXPORT void lab_to_rgb_batch(
    const ColorPoint3f* lab,
    ColorPoint3f* rgb,
    int n
) {
    #pragma omp parallel for if(n > 1000)
    for (int i = 0; i < n; i++) {
        lab_to_rgb_single(&lab[i], &rgb[i]);
    }
}

// Image Resynthesis

AICHAT_EXPORT void extract_pixels(
    const uint32_t* image_pixels,
    int n,
    ColorPoint3f* output
) {
    #pragma omp parallel for if(n > 10000)
    for (int i = 0; i < n; i++) {
        uint32_t pixel = image_pixels[i];
        output[i].c1 = (float)((pixel >> 16) & 0xFF);  // R
        output[i].c2 = (float)((pixel >> 8) & 0xFF);   // G
        output[i].c3 = (float)(pixel & 0xFF);          // B
    }
}

AICHAT_EXPORT int sample_pixels(
    const ColorPoint3f* input,
    int input_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
) {
    if (input_size <= sample_size) {
        memcpy(output, input, input_size * sizeof(ColorPoint3f));
        return input_size;
    }
    
    // Reservoir sampling
    xorshift64_state rng = { .state = seed ? seed : 42 };
    
    // Copy first sample_size elements
    memcpy(output, input, sample_size * sizeof(ColorPoint3f));
    
    // Reservoir replacement
    for (int i = sample_size; i < input_size; i++) {
        int j = xorshift64_range(&rng, i + 1);
        if (j < sample_size) {
            output[j] = input[i];
        }
    }
    
    return sample_size;
}

AICHAT_EXPORT void resynthesize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const ColorPoint3f* target_palette,
    const ColorPoint3f* source_palette,
    int palette_size,
    uint32_t* output_pixels
) {
    int n = width * height;
    
    #pragma omp parallel for if(n > 10000)
    for (int i = 0; i < n; i++) {
        uint32_t pixel = image_pixels[i];
        ColorPoint3f point = {
            .c1 = (float)((pixel >> 16) & 0xFF),
            .c2 = (float)((pixel >> 8) & 0xFF),
            .c3 = (float)(pixel & 0xFF)
        };
        
        int closest = find_nearest(&point, target_palette, palette_size);
        const ColorPoint3f* new_color = &source_palette[closest];
        
        int r = (int)fminf(255.0f, fmaxf(0.0f, new_color->c1 + 0.5f));
        int g = (int)fminf(255.0f, fmaxf(0.0f, new_color->c2 + 0.5f));
        int b = (int)fminf(255.0f, fmaxf(0.0f, new_color->c3 + 0.5f));
        
        output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
    }
}

// ============================================================================
// DBSCAN Clustering - Wrapper to optimized implementation
// ============================================================================
// The optimized DBSCAN implementation is in dbscan_optimized.c
// It uses a grid-based spatial index for O(1) average neighbor lookup.

// Forward declarations for optimized functions (defined in dbscan_optimized.c)
extern float dbscan_calculate_eps_optimized(
    const ColorPoint3f* points, int n, int min_pts, int sample_size, uint64_t seed);
extern int dbscan_cluster_optimized(
    const ColorPoint3f* points, int n, float eps, int min_pts, int* labels);
extern void dbscan_calculate_centroids_optimized(
    const ColorPoint3f* points, int n, const int* labels, int num_clusters, ColorPoint3f* centroids);

AICHAT_EXPORT float dbscan_calculate_eps(
    const ColorPoint3f* points,
    int n,
    int min_pts,
    int sample_size,
    uint64_t seed
) {
    return dbscan_calculate_eps_optimized(points, n, min_pts, sample_size, seed);
}

AICHAT_EXPORT int dbscan_cluster(
    const ColorPoint3f* points,
    int n,
    float eps,
    int min_pts,
    int* labels
) {
    return dbscan_cluster_optimized(points, n, eps, min_pts, labels);
}

AICHAT_EXPORT void dbscan_calculate_centroids(
    const ColorPoint3f* points,
    int n,
    const int* labels,
    int num_clusters,
    ColorPoint3f* centroids
) {
    dbscan_calculate_centroids_optimized(points, n, labels, num_clusters, centroids);
}
