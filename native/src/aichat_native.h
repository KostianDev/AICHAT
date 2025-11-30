/**
 * AICHAT Native Library - Header
 * High-performance color clustering and image processing functions.
 * 
 * Designed for use with Java Panama FFI.
 */

#ifndef AICHAT_NATIVE_H
#define AICHAT_NATIVE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Export macros for shared library
#ifdef _WIN32
    #define AICHAT_EXPORT __declspec(dllexport)
#else
    #define AICHAT_EXPORT __attribute__((visibility("default")))
#endif

// Data Structures

/**
 * Color point in 3D color space (RGB or LAB).
 * Using float for SIMD alignment and faster operations.
 */
typedef struct {
    float c1;  // R or L
    float c2;  // G or a
    float c3;  // B or b
} ColorPoint3f;

/**
 * Result structure for K-Means clustering.
 */
typedef struct {
    ColorPoint3f* centroids;  // Array of k centroids
    int* assignments;         // Cluster assignment for each point
    int iterations;           // Number of iterations performed
    int converged;            // 1 if converged, 0 otherwise
} KMeansResult;

// Distance Functions (SIMD optimized)

/**
 * Calculates squared Euclidean distance between two color points.
 * Using squared distance avoids expensive sqrt for comparisons.
 */
AICHAT_EXPORT float distance_squared(const ColorPoint3f* a, const ColorPoint3f* b);

/**
 * Calculates Euclidean distance between two color points.
 */
AICHAT_EXPORT float distance(const ColorPoint3f* a, const ColorPoint3f* b);

/**
 * Batch distance calculation: finds closest centroid for each point.
 * Uses SIMD for parallel distance computation.
 * 
 * @param points Array of input points
 * @param n Number of points
 * @param centroids Array of centroids
 * @param k Number of centroids
 * @param assignments Output array of size n for cluster assignments
 * @return Number of points that changed assignment
 */
AICHAT_EXPORT int assign_points_batch(
    const ColorPoint3f* points,
    int n,
    const ColorPoint3f* centroids,
    int k,
    int* assignments
);

// K-Means Clustering

/**
 * Performs K-Means++ initialization.
 * 
 * @param points Input color points
 * @param n Number of points
 * @param k Number of clusters
 * @param centroids Output array for k centroids (must be preallocated)
 * @param seed Random seed for reproducibility
 */
AICHAT_EXPORT void kmeans_init_plusplus(
    const ColorPoint3f* points,
    int n,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

/**
 * Performs full K-Means clustering.
 * 
 * @param points Input color points
 * @param n Number of points
 * @param k Number of clusters
 * @param max_iterations Maximum iterations
 * @param convergence_threshold Stop when centroid movement < threshold
 * @param centroids Output array for k centroids (must be preallocated)
 * @param assignments Output array for n assignments (must be preallocated)
 * @param seed Random seed
 * @return Number of iterations performed
 */
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

/**
 * Updates centroids based on current assignments.
 * Handles empty clusters by reinitializing with random points.
 * 
 * @param points Input color points
 * @param n Number of points
 * @param assignments Current cluster assignments
 * @param k Number of clusters
 * @param centroids In/Out: centroids to update
 * @param seed Random seed for empty cluster handling
 * @return Maximum centroid movement distance
 */
AICHAT_EXPORT float kmeans_update_centroids(
    const ColorPoint3f* points,
    int n,
    const int* assignments,
    int k,
    ColorPoint3f* centroids,
    uint64_t seed
);

// Color Space Conversion (SIMD batch processing)

/**
 * Converts a batch of RGB colors to CIELAB.
 * 
 * @param rgb Input RGB colors (0-255 range)
 * @param lab Output LAB colors
 * @param n Number of colors to convert
 */
AICHAT_EXPORT void rgb_to_lab_batch(
    const ColorPoint3f* rgb,
    ColorPoint3f* lab,
    int n
);

/**
 * Converts a batch of CIELAB colors to RGB.
 * 
 * @param lab Input LAB colors
 * @param rgb Output RGB colors (0-255 range)
 * @param n Number of colors to convert
 */
AICHAT_EXPORT void lab_to_rgb_batch(
    const ColorPoint3f* lab,
    ColorPoint3f* rgb,
    int n
);

// Image Resynthesis

/**
 * Performs image resynthesis: maps each pixel to closest palette color,
 * then replaces with corresponding source palette color.
 * 
 * @param image_pixels Input image as flat array of RGB values
 * @param width Image width
 * @param height Image height
 * @param target_palette Palette extracted from target image
 * @param source_palette Palette to apply (from source image)
 * @param palette_size Size of both palettes (must be equal)
 * @param output_pixels Output image buffer (must be preallocated)
 */
AICHAT_EXPORT void resynthesize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const ColorPoint3f* target_palette,
    const ColorPoint3f* source_palette,
    int palette_size,
    uint32_t* output_pixels
);

/**
 * Extracts pixel data from image for clustering.
 * Converts packed RGB to ColorPoint3f array.
 * 
 * @param image_pixels Packed RGB pixels
 * @param n Number of pixels
 * @param output Output color points
 */
AICHAT_EXPORT void extract_pixels(
    const uint32_t* image_pixels,
    int n,
    ColorPoint3f* output
);

/**
 * Samples pixels using reservoir sampling for large images.
 * 
 * @param input Full pixel array
 * @param input_size Total number of input pixels
 * @param output Sampled output array
 * @param sample_size Maximum sample size
 * @param seed Random seed
 * @return Actual number of samples (min of input_size and sample_size)
 */
AICHAT_EXPORT int sample_pixels(
    const ColorPoint3f* input,
    int input_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
);

// Utility Functions

/**
 * Returns library version string.
 */
AICHAT_EXPORT const char* aichat_native_version(void);

/**
 * Returns 1 if SIMD (SSE/AVX) is available, 0 otherwise.
 */
AICHAT_EXPORT int aichat_has_simd(void);

// Hybrid DBSCAN + K-Means Clustering

/**
 * Performs hybrid DBSCAN + K-Means clustering for color quantization.
 * 
 * Algorithm:
 * 1. Divide points into blocks of block_size
 * 2. Apply DBSCAN to each block to extract representative colors
 * 3. Apply K-Means on collected representatives to get final k centroids
 * 
 * @param points Input color points
 * @param n Number of points
 * @param k Desired number of output clusters
 * @param block_size Size of each block for DBSCAN (recommended: 256-512)
 * @param dbscan_eps DBSCAN epsilon (neighborhood radius)
 * @param dbscan_min_pts DBSCAN minimum points for core point
 * @param kmeans_max_iter Maximum K-Means iterations
 * @param kmeans_threshold K-Means convergence threshold
 * @param centroids Output array for k centroids (must be preallocated)
 * @param seed Random seed for reproducibility
 * @return Number of K-Means iterations performed
 */
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

/**
 * Calculates recommended DBSCAN epsilon based on data distribution.
 * 
 * @param points Input color points
 * @param n Number of points
 * @param block_size Size of each block
 * @param min_pts DBSCAN minimum points parameter
 * @param seed Random seed
 * @return Recommended epsilon value
 */
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

#endif // AICHAT_NATIVE_H
