#include "../include/image.h"
#include "../include/distance.h"
#include "../include/random.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

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
    
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    // Copy first sample_size elements
    memcpy(output, input, sample_size * sizeof(ColorPoint3f));
    
    // Reservoir replacement
    for (int i = sample_size; i < input_size; i++) {
        int j = xorshift64_int(&rng, i + 1);
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
        
        int closest = find_nearest_centroid(&point, target_palette, palette_size);
        const ColorPoint3f* new_color = &source_palette[closest];
        
        int r = (int)fminf(255.0f, fmaxf(0.0f, new_color->c1 + 0.5f));
        int g = (int)fminf(255.0f, fmaxf(0.0f, new_color->c2 + 0.5f));
        int b = (int)fminf(255.0f, fmaxf(0.0f, new_color->c3 + 0.5f));
        
        output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
    }
}
