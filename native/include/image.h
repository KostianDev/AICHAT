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

AICHAT_EXPORT void posterize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const ColorPoint3f* target_palette,
    const ColorPoint3f* source_palette,
    int palette_size,
    uint32_t* output_pixels
);

AICHAT_EXPORT int sample_pixels_from_image(
    const uint32_t* image_pixels,
    int total_pixels,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
);

AICHAT_EXPORT int turbojpeg_decode(
    const unsigned char* jpeg_data,
    unsigned long jpeg_size,
    int* width,
    int* height,
    unsigned char** pixels
);

AICHAT_EXPORT int turbojpeg_decode_and_sample(
    const unsigned char* jpeg_data,
    unsigned long jpeg_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed,
    int* out_width,
    int* out_height
);

AICHAT_EXPORT int decode_jpeg_file_turbojpeg(
    const char* path,
    int* out_width,
    int* out_height,
    uint32_t** out_pixels
);

AICHAT_EXPORT int turbojpeg_available(void);
AICHAT_EXPORT void turbojpeg_free(void* ptr);
AICHAT_EXPORT void turbojpeg_cleanup(void);

// Fast JPEG encoding using libturbojpeg
AICHAT_EXPORT int turbojpeg_encode(
    const uint32_t* pixels,
    int width,
    int height,
    int quality,
    unsigned char** jpeg_data,
    unsigned long* jpeg_size
);

AICHAT_EXPORT int turbojpeg_encode_to_file(
    const uint32_t* pixels,
    int width,
    int height,
    int quality,
    const char* path
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_IMAGE_H
