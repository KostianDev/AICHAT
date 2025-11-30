#ifndef AICHAT_OPENCL_ACCEL_H
#define AICHAT_OPENCL_ACCEL_H

#include "common.h"
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

AICHAT_EXPORT int opencl_available(void);
AICHAT_EXPORT int opencl_init(void);
AICHAT_EXPORT size_t opencl_get_global_mem_size(void);

AICHAT_EXPORT int opencl_resynthesize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const float* target_palette,
    const float* source_palette,
    int palette_size,
    uint32_t* output_pixels
);

AICHAT_EXPORT int opencl_resynthesize_streaming(
    const uint32_t* image_pixels,
    int width,
    int height,
    const float* target_palette,
    const float* source_palette,
    int palette_size,
    uint32_t* output_pixels,
    int tile_height
);

AICHAT_EXPORT int opencl_build_lut(
    const float* palette,
    int palette_size,
    uint16_t* lut,
    int lut_dim
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_OPENCL_ACCEL_H
