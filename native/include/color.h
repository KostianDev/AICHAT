/**
 * AICHAT Native Library - Color Space Conversion
 * 
 * RGB <-> CIELAB conversion with batch processing.
 */

#ifndef AICHAT_COLOR_H
#define AICHAT_COLOR_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// Color Space Conversion
// ============================================================================

/**
 * Converts a batch of RGB colors to CIELAB.
 * Uses D65 reference white.
 * 
 * @param rgb Input RGB colors (0-255 range)
 * @param lab Output LAB colors
 * @param n   Number of colors to convert
 */
AICHAT_EXPORT void rgb_to_lab_batch(
    const ColorPoint3f* rgb,
    ColorPoint3f* lab,
    int n
);

/**
 * Converts a batch of CIELAB colors to RGB.
 * Output is clamped to 0-255 range.
 * 
 * @param lab Input LAB colors
 * @param rgb Output RGB colors (0-255 range)
 * @param n   Number of colors to convert
 */
AICHAT_EXPORT void lab_to_rgb_batch(
    const ColorPoint3f* lab,
    ColorPoint3f* rgb,
    int n
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_COLOR_H
