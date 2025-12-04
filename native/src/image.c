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

#ifndef __AVX2__
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
#endif

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

AICHAT_EXPORT int sample_pixels_from_image(
    const uint32_t* image_pixels,
    int total_pixels,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed
) {
    if (total_pixels <= sample_size) {
        extract_pixels(image_pixels, total_pixels, output);
        return total_pixels;
    }
    
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    for (int i = 0; i < sample_size; i++) {
        uint32_t pixel = image_pixels[i];
        output[i].c1 = (float)((pixel >> 16) & 0xFF);
        output[i].c2 = (float)((pixel >> 8) & 0xFF);
        output[i].c3 = (float)(pixel & 0xFF);
    }
    
    for (int i = sample_size; i < total_pixels; i++) {
        int j = xorshift64_int(&rng, i + 1);
        if (j < sample_size) {
            uint32_t pixel = image_pixels[i];
            output[j].c1 = (float)((pixel >> 16) & 0xFF);
            output[j].c2 = (float)((pixel >> 8) & 0xFF);
            output[j].c3 = (float)(pixel & 0xFF);
        }
    }
    
    return sample_size;
}

#ifdef __AVX2__
#include <immintrin.h>

static int find_nearest_perceptual_avx2(const ColorPoint3f* point, const ColorPoint3f* palette, int k) {
    float pr = point->c1, pg = point->c2, pb = point->c3;
    float avg_r = pr;
    
    float wr = avg_r < 128.0f ? 2.0f : 3.0f;
    float wg = 4.0f;
    float wb = avg_r < 128.0f ? 3.0f : 2.0f;
    
    __m256 vpr = _mm256_set1_ps(pr);
    __m256 vpg = _mm256_set1_ps(pg);
    __m256 vpb = _mm256_set1_ps(pb);
    __m256 vwr = _mm256_set1_ps(wr);
    __m256 vwg = _mm256_set1_ps(wg);
    __m256 vwb = _mm256_set1_ps(wb);
    
    int nearest = 0;
    float min_dist = 1e30f;
    
    int i = 0;
    for (; i + 7 < k; i += 8) {
        __m256 pr0 = _mm256_set_ps(
            palette[i+7].c1, palette[i+6].c1, palette[i+5].c1, palette[i+4].c1,
            palette[i+3].c1, palette[i+2].c1, palette[i+1].c1, palette[i+0].c1
        );
        __m256 pg0 = _mm256_set_ps(
            palette[i+7].c2, palette[i+6].c2, palette[i+5].c2, palette[i+4].c2,
            palette[i+3].c2, palette[i+2].c2, palette[i+1].c2, palette[i+0].c2
        );
        __m256 pb0 = _mm256_set_ps(
            palette[i+7].c3, palette[i+6].c3, palette[i+5].c3, palette[i+4].c3,
            palette[i+3].c3, palette[i+2].c3, palette[i+1].c3, palette[i+0].c3
        );
        
        __m256 dr = _mm256_sub_ps(vpr, pr0);
        __m256 dg = _mm256_sub_ps(vpg, pg0);
        __m256 db = _mm256_sub_ps(vpb, pb0);
        
        __m256 dr2 = _mm256_mul_ps(dr, dr);
        __m256 dg2 = _mm256_mul_ps(dg, dg);
        __m256 db2 = _mm256_mul_ps(db, db);
        
        __m256 dist = _mm256_add_ps(
            _mm256_mul_ps(vwr, dr2),
            _mm256_add_ps(_mm256_mul_ps(vwg, dg2), _mm256_mul_ps(vwb, db2))
        );
        
        float dists[8];
        _mm256_storeu_ps(dists, dist);
        
        for (int j = 0; j < 8; j++) {
            if (dists[j] < min_dist) {
                min_dist = dists[j];
                nearest = i + j;
            }
        }
    }
    
    for (; i < k; i++) {
        float dist = perceptual_distance_sq(point, &palette[i]);
        if (dist < min_dist) {
            min_dist = dist;
            nearest = i;
        }
    }
    
    return nearest;
}
#endif

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
    
    const int LUT_BITS = 7;
    const int LUT_DIM = 1 << LUT_BITS;
    const int LUT_SIZE = LUT_DIM * LUT_DIM * LUT_DIM;
    const float LUT_SCALE = 255.0f / (float)(LUT_DIM - 1);
    const int SHIFT = 8 - LUT_BITS;
    
    if (palette_size > 4096) {
        #pragma omp parallel for schedule(static, 32768)
        for (int i = 0; i < n; i++) {
            uint32_t pixel = image_pixels[i];
            ColorPoint3f point = {
                .c1 = (float)((pixel >> 16) & 0xFF),
                .c2 = (float)((pixel >> 8) & 0xFF),
                .c3 = (float)(pixel & 0xFF)
            };
            
#ifdef __AVX2__
            int closest = find_nearest_perceptual_avx2(&point, target_palette, palette_size);
#else
            int closest = find_nearest_perceptual(&point, target_palette, palette_size);
#endif
            const ColorPoint3f* target_center = &target_palette[closest];
            const ColorPoint3f* source_center = &source_palette[closest];
            
            int r = (int)(source_center->c1 + (point.c1 - target_center->c1) + 0.5f);
            int g = (int)(source_center->c2 + (point.c2 - target_center->c2) + 0.5f);
            int b = (int)(source_center->c3 + (point.c3 - target_center->c3) + 0.5f);
            
            r = r < 0 ? 0 : (r > 255 ? 255 : r);
            g = g < 0 ? 0 : (g > 255 ? 255 : g);
            b = b < 0 ? 0 : (b > 255 ? 255 : b);
            
            output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
        }
        return;
    }
    
    uint16_t* lut = (uint16_t*)malloc(LUT_SIZE * sizeof(uint16_t));
    if (!lut) return;
    
    #pragma omp parallel for collapse(3) schedule(static)
    for (int ri = 0; ri < LUT_DIM; ri++) {
        for (int gi = 0; gi < LUT_DIM; gi++) {
            for (int bi = 0; bi < LUT_DIM; bi++) {
                ColorPoint3f p = { 
                    ri * LUT_SCALE, 
                    gi * LUT_SCALE, 
                    bi * LUT_SCALE 
                };
#ifdef __AVX2__
                lut[(ri << (LUT_BITS * 2)) | (gi << LUT_BITS) | bi] = 
                    (uint16_t)find_nearest_perceptual_avx2(&p, target_palette, palette_size);
#else
                lut[(ri << (LUT_BITS * 2)) | (gi << LUT_BITS) | bi] = 
                    (uint16_t)find_nearest_perceptual(&p, target_palette, palette_size);
#endif
            }
        }
    }
    
    // Apply palette mapping using LUT
    #pragma omp parallel for schedule(static, 32768)
    for (int i = 0; i < n; i++) {
        uint32_t pixel = image_pixels[i];
        int pr = (pixel >> 16) & 0xFF;
        int pg = (pixel >> 8) & 0xFF;
        int pb = pixel & 0xFF;
        
        int idx = lut[((pr >> SHIFT) << (LUT_BITS * 2)) | ((pg >> SHIFT) << LUT_BITS) | (pb >> SHIFT)];
        
        const ColorPoint3f* target_center = &target_palette[idx];
        const ColorPoint3f* source_center = &source_palette[idx];
        
        int r = (int)(source_center->c1 + (pr - target_center->c1) + 0.5f);
        int g = (int)(source_center->c2 + (pg - target_center->c2) + 0.5f);
        int b = (int)(source_center->c3 + (pb - target_center->c3) + 0.5f);
        
        r = r < 0 ? 0 : (r > 255 ? 255 : r);
        g = g < 0 ? 0 : (g > 255 ? 255 : g);
        b = b < 0 ? 0 : (b > 255 ? 255 : b);
        
        output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
    }
    
    free(lut);
}

// Posterize: replace each pixel with exact palette color (no offset preservation)
AICHAT_EXPORT void posterize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const ColorPoint3f* target_palette,
    const ColorPoint3f* source_palette,
    int palette_size,
    uint32_t* output_pixels
) {
    int n = width * height;
    
    const int LUT_BITS = 7;
    const int LUT_DIM = 1 << LUT_BITS;
    const int LUT_SIZE = LUT_DIM * LUT_DIM * LUT_DIM;
    const float LUT_SCALE = 255.0f / (float)(LUT_DIM - 1);
    const int SHIFT = 8 - LUT_BITS;
    
    // For very large palettes, skip LUT
    if (palette_size > 4096) {
        #pragma omp parallel for schedule(static, 32768)
        for (int i = 0; i < n; i++) {
            uint32_t pixel = image_pixels[i];
            ColorPoint3f point = {
                .c1 = (float)((pixel >> 16) & 0xFF),
                .c2 = (float)((pixel >> 8) & 0xFF),
                .c3 = (float)(pixel & 0xFF)
            };
            
#ifdef __AVX2__
            int closest = find_nearest_perceptual_avx2(&point, target_palette, palette_size);
#else
            int closest = find_nearest_perceptual(&point, target_palette, palette_size);
#endif
            const ColorPoint3f* source_center = &source_palette[closest];
            
            int r = (int)(source_center->c1 + 0.5f);
            int g = (int)(source_center->c2 + 0.5f);
            int b = (int)(source_center->c3 + 0.5f);
            
            output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
        }
        return;
    }
    
    // Build LUT for palette lookup
    uint16_t* lut = (uint16_t*)malloc(LUT_SIZE * sizeof(uint16_t));
    if (!lut) return;
    
    #pragma omp parallel for collapse(3) schedule(static)
    for (int ri = 0; ri < LUT_DIM; ri++) {
        for (int gi = 0; gi < LUT_DIM; gi++) {
            for (int bi = 0; bi < LUT_DIM; bi++) {
                ColorPoint3f p = { 
                    ri * LUT_SCALE, 
                    gi * LUT_SCALE, 
                    bi * LUT_SCALE 
                };
#ifdef __AVX2__
                lut[(ri << (LUT_BITS * 2)) | (gi << LUT_BITS) | bi] = 
                    (uint16_t)find_nearest_perceptual_avx2(&p, target_palette, palette_size);
#else
                lut[(ri << (LUT_BITS * 2)) | (gi << LUT_BITS) | bi] = 
                    (uint16_t)find_nearest_perceptual(&p, target_palette, palette_size);
#endif
            }
        }
    }
    
    // Apply direct color replacement using LUT
    #pragma omp parallel for schedule(static, 32768)
    for (int i = 0; i < n; i++) {
        uint32_t pixel = image_pixels[i];
        int pr = (pixel >> 16) & 0xFF;
        int pg = (pixel >> 8) & 0xFF;
        int pb = pixel & 0xFF;
        
        int idx = lut[((pr >> SHIFT) << (LUT_BITS * 2)) | ((pg >> SHIFT) << LUT_BITS) | (pb >> SHIFT)];
        
        const ColorPoint3f* source_center = &source_palette[idx];
        
        int r = (int)(source_center->c1 + 0.5f);
        int g = (int)(source_center->c2 + 0.5f);
        int b = (int)(source_center->c3 + 0.5f);
        
        output_pixels[i] = (uint32_t)((r << 16) | (g << 8) | b);
    }
    
    free(lut);
}
