#ifndef AICHAT_IMAGE_H
#define AICHAT_IMAGE_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

AICHAT_EXPORT void extract_pixels(
    const uint32_t* image_pixels,
    int n,
    ColorPoint3f* output
);

AICHAT_EXPORT int sample_pixels(
    const ColorPoint3f* input,
    int input_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
);

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
