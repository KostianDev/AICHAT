#include "../include/image.h"
#include "../include/random.h"
#include <turbojpeg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

static __thread tjhandle tj_handle = NULL;

static tjhandle get_tj_handle(void) {
    if (tj_handle == NULL) {
        tj_handle = tjInitDecompress();
    }
    return tj_handle;
}

AICHAT_EXPORT int turbojpeg_decode(
    const unsigned char* jpeg_data,
    unsigned long jpeg_size,
    int* width,
    int* height,
    unsigned char** pixels
) {
    tjhandle handle = get_tj_handle();
    if (handle == NULL) {
        return -1;
    }
    
    int w, h, subsamp, colorspace;
    if (tjDecompressHeader3(handle, jpeg_data, jpeg_size, &w, &h, &subsamp, &colorspace) != 0) {
        return -1;
    }
    
    *width = w;
    *height = h;
    
    size_t pixel_size = (size_t)w * h * 3;
    *pixels = (unsigned char*)malloc(pixel_size);
    if (*pixels == NULL) {
        return -1;
    }
    
    if (tjDecompress2(handle, jpeg_data, jpeg_size, *pixels, w, 0, h, TJPF_RGB, TJFLAG_FASTDCT) != 0) {
        free(*pixels);
        *pixels = NULL;
        return -1;
    }
    
    return 0;
}

AICHAT_EXPORT int turbojpeg_decode_and_sample(
    const unsigned char* jpeg_data,
    unsigned long jpeg_size,
    ColorPoint3f* output,
    int sample_size,
    uint64_t seed,
    int* out_width,
    int* out_height
) {
    tjhandle handle = get_tj_handle();
    if (handle == NULL) {
        return -1;
    }
    
    // Get image dimensions
    int w, h, subsamp, colorspace;
    if (tjDecompressHeader3(handle, jpeg_data, jpeg_size, &w, &h, &subsamp, &colorspace) != 0) {
        return -1;
    }
    
    if (out_width) *out_width = w;
    if (out_height) *out_height = h;
    
    int total_pixels = w * h;
    
    // For small images, decode fully
    if (total_pixels <= sample_size) {
        unsigned char* pixels = (unsigned char*)malloc((size_t)total_pixels * 3);
        if (!pixels) return -1;
        
        if (tjDecompress2(handle, jpeg_data, jpeg_size, pixels, w, 0, h, TJPF_RGB, TJFLAG_FASTDCT) != 0) {
            free(pixels);
            return -1;
        }
        
        for (int i = 0; i < total_pixels; i++) {
            output[i].c1 = (float)pixels[i * 3];
            output[i].c2 = (float)pixels[i * 3 + 1];
            output[i].c3 = (float)pixels[i * 3 + 2];
        }
        
        free(pixels);
        return total_pixels;
    }
    
    // For large images, decode and sample
    unsigned char* pixels = (unsigned char*)malloc((size_t)total_pixels * 3);
    if (!pixels) return -1;
    
    if (tjDecompress2(handle, jpeg_data, jpeg_size, pixels, w, 0, h, TJPF_RGB, TJFLAG_FASTDCT) != 0) {
        free(pixels);
        return -1;
    }
    
    // Reservoir sampling
    XorShift64 rng;
    xorshift64_init(&rng, seed);
    
    // Fill reservoir
    for (int i = 0; i < sample_size; i++) {
        output[i].c1 = (float)pixels[i * 3];
        output[i].c2 = (float)pixels[i * 3 + 1];
        output[i].c3 = (float)pixels[i * 3 + 2];
    }
    
    // Sample remaining
    for (int i = sample_size; i < total_pixels; i++) {
        int j = xorshift64_int(&rng, i + 1);
        if (j < sample_size) {
            output[j].c1 = (float)pixels[i * 3];
            output[j].c2 = (float)pixels[i * 3 + 1];
            output[j].c3 = (float)pixels[i * 3 + 2];
        }
    }
    
    free(pixels);
    return sample_size;
}

AICHAT_EXPORT int turbojpeg_available(void) {
    return get_tj_handle() != NULL ? 1 : 0;
}

AICHAT_EXPORT void turbojpeg_free(void* ptr) {
    free(ptr);
}

AICHAT_EXPORT int decode_jpeg_file_turbojpeg(
    const char* path,
    int* out_width,
    int* out_height,
    uint32_t** out_pixels
) {
    FILE* f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "TurboJPEG: Cannot open file: %s\n", path);
        return -1;
    }
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    unsigned char* jpeg_data = (unsigned char*)malloc(size);
    if (!jpeg_data) {
        fclose(f);
        return -1;
    }
    
    if (fread(jpeg_data, 1, size, f) != (size_t)size) {
        free(jpeg_data);
        fclose(f);
        return -1;
    }
    fclose(f);
    
    // Decode JPEG
    tjhandle handle = get_tj_handle();
    if (handle == NULL) {
        free(jpeg_data);
        return -1;
    }
    
    int w, h, subsamp, colorspace;
    if (tjDecompressHeader3(handle, jpeg_data, size, &w, &h, &subsamp, &colorspace) != 0) {
        fprintf(stderr, "TurboJPEG: Failed to read header: %s\n", tjGetErrorStr2(handle));
        free(jpeg_data);
        return -1;
    }
    
    *out_width = w;
    *out_height = h;
    
    size_t num_pixels = (size_t)w * h;
    *out_pixels = (uint32_t*)malloc(num_pixels * sizeof(uint32_t));
    if (!*out_pixels) {
        free(jpeg_data);
        return -1;
    }
    
    unsigned char* bgrx = (unsigned char*)malloc(num_pixels * 4);
    if (!bgrx) {
        free(*out_pixels);
        *out_pixels = NULL;
        free(jpeg_data);
        return -1;
    }
    
    if (tjDecompress2(handle, jpeg_data, size, bgrx, w, 0, h, TJPF_BGRX, TJFLAG_FASTDCT) != 0) {
        fprintf(stderr, "TurboJPEG: Decompression failed: %s\n", tjGetErrorStr2(handle));
        free(bgrx);
        free(*out_pixels);
        *out_pixels = NULL;
        free(jpeg_data);
        return -1;
    }
    
    free(jpeg_data);
    
    uint32_t* pixels = *out_pixels;
    for (size_t i = 0; i < num_pixels; i++) {
        unsigned char b = bgrx[i * 4];
        unsigned char g = bgrx[i * 4 + 1];
        unsigned char r = bgrx[i * 4 + 2];
        pixels[i] = 0xFF000000u | ((uint32_t)r << 16) | ((uint32_t)g << 8) | b;
    }
    
    free(bgrx);
    return 0;
}

AICHAT_EXPORT void turbojpeg_cleanup(void) {
    if (tj_handle != NULL) {
        tjDestroy(tj_handle);
        tj_handle = NULL;
    }
}
