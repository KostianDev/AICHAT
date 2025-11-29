/**
 * AICHAT Native Library - Image Processing
 * 
 * Pixel extraction, sampling, and resynthesis.
 */

#ifndef AICHAT_IMAGE_H
#define AICHAT_IMAGE_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// Pixel Operations
// ============================================================================

/**
 * Extracts pixel data from packed RGB image.
 * Converts uint32_t (0xRRGGBB) to ColorPoint3f.
 * 
 * @param image_pixels Packed RGB pixels
 * @param n            Number of pixels
 * @param output       Output color points
 */
AICHAT_EXPORT void extract_pixels(
    const uint32_t* image_pixels,
    int n,
    ColorPoint3f* output
);

/**
 * Samples pixels using reservoir sampling for large images.
 * Maintains statistical properties of the original distribution.
 * 
 * @param input       Full pixel array
 * @param input_size  Total number of input pixels
 * @param output      Sampled output array
 * @param sample_size Maximum sample size
 * @param seed        Random seed
 * @return Actual number of samples (min of input_size and sample_size)
 */
AICHAT_EXPORT int sample_pixels(
    const ColorPoint3f* input,
    int input_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
);

// ============================================================================
// Image Resynthesis
// ============================================================================

/**
 * Performs image resynthesis.
 * Maps each pixel to closest target palette color,
 * then replaces with corresponding source palette color.
 * 
 * @param image_pixels   Input image as flat array of RGB values
 * @param width          Image width
 * @param height         Image height
 * @param target_palette Palette extracted from target image
 * @param source_palette Palette to apply (from source image)
 * @param palette_size   Size of both palettes (must be equal)
 * @param output_pixels  Output image buffer (must be preallocated)
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

#ifdef __cplusplus
}
#endif

#endif // AICHAT_IMAGE_H
