#include "../include/image.h"
#include "../include/distance.h"
#include "../include/random.h"
#include <stdlib.h>
#include <string.h>
#include <math.h>

#ifdef _OPENMP
#include <omp.h>
#endif

static inline float perceptual_distance_sq(const ColorPoint3f* a, const ColorPoint3f* b) {
    float dr = a->c1 - b->c1;
    float dg = a->c2 - b->c2;
    float db = a->c3 - b->c3;
    float avg_r = (a->c1 + b->c1) * 0.5f;
    
    float wr = avg_r < 128.0f ? 2.0f : 3.0f;
    float wg = 4.0f;
    float wb = avg_r < 128.0f ? 3.0f : 2.0f;
    
    return wr * dr * dr + wg * dg * dg + wb * db * db;
}

static int find_nearest_perceptual(const ColorPoint3f* point, const ColorPoint3f* palette, int k) {
    int nearest = 0;
    float min_dist = perceptual_distance_sq(point, &palette[0]);
    
    for (int i = 1; i < k; i++) {
        float dist = perceptual_distance_sq(point, &palette[i]);
        if (dist < min_dist) {
            min_dist = dist;
            nearest = i;
        }
    }
    
    return nearest;
}

AICHAT_EXPORT void extract_pixels(
    const uint32_t* image_pixels,
    int n,
    ColorPoint3f* output
) {
    #pragma omp parallel for if(n > 10000)
    for (int i = 0; i < n; i++) {
        uint32_t pixel = image_pixels[i];
        output[i].c1 = (float)((pixel >> 16) & 0xFF);
        output[i].c2 = (float)((pixel >> 8) & 0xFF);
        output[i].c3 = (float)(pixel & 0xFF);
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
    
    memcpy(output, input, sample_size * sizeof(ColorPoint3f));
    
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
    
    if (palette_size <= 64) {
        uint8_t* lut = (uint8_t*)malloc(32 * 32 * 32);
        
        #pragma omp parallel for collapse(3) if(palette_size > 8)
        for (int r = 0; r < 32; r++) {
            for (int g = 0; g < 32; g++) {
                for (int b = 0; b < 32; b++) {
                    ColorPoint3f p = { r * 8.0f + 4.0f, g * 8.0f + 4.0f, b * 8.0f + 4.0f };
                    lut[(r << 10) | (g << 5) | b] = (uint8_t)find_nearest_perceptual(&p, target_palette, palette_size);
                }
            }
        }
        
        #pragma omp parallel for if(n > 10000)
        for (int i = 0; i < n; i++) {
            uint32_t pixel = image_pixels[i];
            int pr = (pixel >> 16) & 0xFF;
            int pg = (pixel >> 8) & 0xFF;
            int pb = pixel & 0xFF;
            
            int idx = lut[((pr >> 3) << 10) | ((pg >> 3) << 5) | (pb >> 3)];
            
            const ColorPoint3f* target_center = &target_palette[idx];
            const ColorPoint3f* source_center = &source_palette[idx];
            
            int r = (int)fminf(255.0f, fmaxf(0.0f, source_center->c1 + (pr - target_center->c1) + 0.5f));
            int g = (int)fminf(255.0f, fmaxf(0.0f, source_center->c2 + (pg - target_center->c2) + 0.5f));
            int b = (int)fminf(255.0f, fmaxf(0.0f, source_center->c3 + (pb - target_center->c3) + 0.5f));
            
            output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
        }
        
        free(lut);
    } else {
        #pragma omp parallel for if(n > 10000)
        for (int i = 0; i < n; i++) {
            uint32_t pixel = image_pixels[i];
            ColorPoint3f point = {
                .c1 = (float)((pixel >> 16) & 0xFF),
                .c2 = (float)((pixel >> 8) & 0xFF),
                .c3 = (float)(pixel & 0xFF)
            };
            
            int closest = find_nearest_perceptual(&point, target_palette, palette_size);
            const ColorPoint3f* target_center = &target_palette[closest];
            const ColorPoint3f* source_center = &source_palette[closest];
            
            float dc1 = point.c1 - target_center->c1;
            float dc2 = point.c2 - target_center->c2;
            float dc3 = point.c3 - target_center->c3;
            
            int r = (int)fminf(255.0f, fmaxf(0.0f, source_center->c1 + dc1 + 0.5f));
            int g = (int)fminf(255.0f, fmaxf(0.0f, source_center->c2 + dc2 + 0.5f));
            int b = (int)fminf(255.0f, fmaxf(0.0f, source_center->c3 + dc3 + 0.5f));
            
            output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
        }
    }
}
