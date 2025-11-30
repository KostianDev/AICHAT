#define CL_TARGET_OPENCL_VERSION 120
#include "../include/opencl_accel.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __APPLE__
#include <OpenCL/opencl.h>
#else
#include <CL/cl.h>
#endif

#ifdef _OPENMP
#include <omp.h>
#endif

static const char* KERNEL_SOURCE = 
"inline int clamp_int(int v, int lo, int hi) {\n"
"    return v < lo ? lo : (v > hi ? hi : v);\n"
"}\n"
"\n"
"inline float perceptual_distance_sq(float3 a, float3 b) {\n"
"    float3 d = a - b;\n"
"    float avg_r = (a.x + b.x) * 0.5f;\n"
"    float wr = avg_r < 128.0f ? 2.0f : 3.0f;\n"
"    float wg = 4.0f;\n"
"    float wb = avg_r < 128.0f ? 3.0f : 2.0f;\n"
"    return wr * d.x * d.x + wg * d.y * d.y + wb * d.z * d.z;\n"
"}\n"
"\n"
"inline int find_nearest(float3 point, __global const float* palette, int palette_size) {\n"
"    int nearest = 0;\n"
"    float min_dist = 1e38f;\n"
"    for (int i = 0; i < palette_size; i++) {\n"
"        float3 color = (float3)(palette[i * 3], palette[i * 3 + 1], palette[i * 3 + 2]);\n"
"        float dist = perceptual_distance_sq(point, color);\n"
"        if (dist < min_dist) { min_dist = dist; nearest = i; }\n"
"    }\n"
"    return nearest;\n"
"}\n"
"\n"
"__kernel void build_lut_kernel(\n"
"    __global const float* palette, int palette_size,\n"
"    __global ushort* lut, int lut_dim, float lut_scale) {\n"
"    int gid = get_global_id(0);\n"
"    int lut_size = lut_dim * lut_dim * lut_dim;\n"
"    if (gid >= lut_size) return;\n"
"    int bi = gid % lut_dim;\n"
"    int gi = (gid / lut_dim) % lut_dim;\n"
"    int ri = gid / (lut_dim * lut_dim);\n"
"    float3 point = (float3)(ri * lut_scale, gi * lut_scale, bi * lut_scale);\n"
"    lut[gid] = (ushort)find_nearest(point, palette, palette_size);\n"
"}\n"
"\n"
"__kernel void resynthesize_lut_kernel(\n"
"    __global const uint* input_pixels, __global uint* output_pixels,\n"
"    __global const ushort* lut,\n"
"    __global const float* target_palette, __global const float* source_palette,\n"
"    int width, int height, int lut_bits, int shift) {\n"
"    int gid = get_global_id(0);\n"
"    int n = width * height;\n"
"    if (gid >= n) return;\n"
"    uint pixel = input_pixels[gid];\n"
"    int pr = (pixel >> 16) & 0xFF;\n"
"    int pg = (pixel >> 8) & 0xFF;\n"
"    int pb = pixel & 0xFF;\n"
"    int lut_idx = ((pr >> shift) << (lut_bits * 2)) | ((pg >> shift) << lut_bits) | (pb >> shift);\n"
"    int palette_idx = lut[lut_idx];\n"
"    float3 tc = (float3)(target_palette[palette_idx*3], target_palette[palette_idx*3+1], target_palette[palette_idx*3+2]);\n"
"    float3 sc = (float3)(source_palette[palette_idx*3], source_palette[palette_idx*3+1], source_palette[palette_idx*3+2]);\n"
"    int r = (int)(sc.x + (float)(pr) - tc.x + 0.5f);\n"
"    int g = (int)(sc.y + (float)(pg) - tc.y + 0.5f);\n"
"    int b = (int)(sc.z + (float)(pb) - tc.z + 0.5f);\n"
"    r = clamp_int(r, 0, 255); g = clamp_int(g, 0, 255); b = clamp_int(b, 0, 255);\n"
"    output_pixels[gid] = (uint)((r << 16) | (g << 8) | b);\n"
"}\n"
"\n"
"__kernel void resynthesize_direct_kernel(\n"
"    __global const uint* input_pixels, __global uint* output_pixels,\n"
"    __global const float* target_palette, __global const float* source_palette,\n"
"    int palette_size, int width, int height) {\n"
"    int gid = get_global_id(0);\n"
"    int n = width * height;\n"
"    if (gid >= n) return;\n"
"    uint pixel = input_pixels[gid];\n"
"    float3 point = (float3)((float)((pixel >> 16) & 0xFF), (float)((pixel >> 8) & 0xFF), (float)(pixel & 0xFF));\n"
"    int palette_idx = find_nearest(point, target_palette, palette_size);\n"
"    float3 tc = (float3)(target_palette[palette_idx*3], target_palette[palette_idx*3+1], target_palette[palette_idx*3+2]);\n"
"    float3 sc = (float3)(source_palette[palette_idx*3], source_palette[palette_idx*3+1], source_palette[palette_idx*3+2]);\n"
"    int r = (int)(sc.x + point.x - tc.x + 0.5f);\n"
"    int g = (int)(sc.y + point.y - tc.y + 0.5f);\n"
"    int b = (int)(sc.z + point.z - tc.z + 0.5f);\n"
"    r = clamp_int(r, 0, 255); g = clamp_int(g, 0, 255); b = clamp_int(b, 0, 255);\n"
"    output_pixels[gid] = (uint)((r << 16) | (g << 8) | b);\n"
"}\n";

typedef struct {
    cl_platform_id platform;
    cl_device_id device;
    cl_context context;
    cl_command_queue queue;
    cl_program program;
    
    cl_kernel build_lut_kernel;
    cl_kernel resynthesize_lut_kernel;
    cl_kernel resynthesize_direct_kernel;
    
    cl_mem lut_buffer;
    cl_mem target_palette_buffer;
    cl_mem source_palette_buffer;
    int current_palette_size;
    
    char device_name[256];
    char platform_name[256];
    size_t max_work_group_size;
    cl_ulong global_mem_size;
    cl_ulong max_alloc_size;
    
    int initialized;
} OpenCLState;

static OpenCLState g_cl = {0};

static void cleanup_opencl_resources(void);

#define LUT_BITS 7
#define LUT_DIM (1 << LUT_BITS)
#define LUT_SIZE (LUT_DIM * LUT_DIM * LUT_DIM)
#define LUT_SCALE (255.0f / (float)(LUT_DIM - 1))
#define SHIFT (8 - LUT_BITS)

AICHAT_EXPORT int opencl_available(void) {
    cl_uint num_platforms;
    cl_int err = clGetPlatformIDs(0, NULL, &num_platforms);
    return (err == CL_SUCCESS && num_platforms > 0) ? 1 : 0;
}

static int select_best_device(void) {
    cl_uint num_platforms;
    cl_int err = clGetPlatformIDs(0, NULL, &num_platforms);
    if (err != CL_SUCCESS || num_platforms == 0) {
        return -1;
    }
    
    cl_platform_id* platforms = malloc(sizeof(cl_platform_id) * num_platforms);
    clGetPlatformIDs(num_platforms, platforms, NULL);
    
    cl_device_id best_device = NULL;
    cl_platform_id best_platform = NULL;
    cl_ulong best_score = 0;
    
    for (cl_uint i = 0; i < num_platforms; i++) {
        char version_str[256];
        clGetPlatformInfo(platforms[i], CL_PLATFORM_VERSION, sizeof(version_str), version_str, NULL);
        int cl_version = 10; // Default 1.0
        if (strstr(version_str, "OpenCL 3.")) cl_version = 30;
        else if (strstr(version_str, "OpenCL 2.")) cl_version = 20;
        else if (strstr(version_str, "OpenCL 1.2")) cl_version = 12;
        else if (strstr(version_str, "OpenCL 1.1")) cl_version = 11;
        
        cl_uint num_devices;
        err = clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU, 0, NULL, &num_devices);
        if (err != CL_SUCCESS || num_devices == 0) continue;
        
        cl_device_id* devices = malloc(sizeof(cl_device_id) * num_devices);
        clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU, num_devices, devices, NULL);
        
        for (cl_uint j = 0; j < num_devices; j++) {
            cl_ulong global_mem;
            cl_uint compute_units;
            cl_uint clock_freq;
            
            clGetDeviceInfo(devices[j], CL_DEVICE_GLOBAL_MEM_SIZE, sizeof(global_mem), &global_mem, NULL);
            clGetDeviceInfo(devices[j], CL_DEVICE_MAX_COMPUTE_UNITS, sizeof(compute_units), &compute_units, NULL);
            clGetDeviceInfo(devices[j], CL_DEVICE_MAX_CLOCK_FREQUENCY, sizeof(clock_freq), &clock_freq, NULL);
            
            cl_ulong score = (cl_ulong)cl_version * 1000000ULL + 
                             (cl_ulong)compute_units * clock_freq * (global_mem / (1024*1024*1024));
            
            if (score > best_score) {
                best_score = score;
                best_device = devices[j];
                best_platform = platforms[i];
            }
        }
        
        free(devices);
    }
    
    free(platforms);
    
    if (best_device == NULL) {
        return -1;
    }
    
    g_cl.platform = best_platform;
    g_cl.device = best_device;
    
    clGetDeviceInfo(best_device, CL_DEVICE_NAME, sizeof(g_cl.device_name), g_cl.device_name, NULL);
    clGetPlatformInfo(best_platform, CL_PLATFORM_NAME, sizeof(g_cl.platform_name), g_cl.platform_name, NULL);
    clGetDeviceInfo(best_device, CL_DEVICE_MAX_WORK_GROUP_SIZE, sizeof(g_cl.max_work_group_size), &g_cl.max_work_group_size, NULL);
    clGetDeviceInfo(best_device, CL_DEVICE_GLOBAL_MEM_SIZE, sizeof(g_cl.global_mem_size), &g_cl.global_mem_size, NULL);
    clGetDeviceInfo(best_device, CL_DEVICE_MAX_MEM_ALLOC_SIZE, sizeof(g_cl.max_alloc_size), &g_cl.max_alloc_size, NULL);
    
    return 0;
}

AICHAT_EXPORT int opencl_init(void) {
    if (g_cl.initialized) {
        return 0;
    }
    
    if (select_best_device() != 0) {
        fprintf(stderr, "OpenCL: No suitable GPU device found\n");
        return -1;
    }
    
    cl_int err;
    
    g_cl.context = clCreateContext(NULL, 1, &g_cl.device, NULL, NULL, &err);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: Failed to create context (error %d)\n", err);
        return -1;
    }
    
    g_cl.queue = clCreateCommandQueue(g_cl.context, g_cl.device, 0, &err);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: Failed to create command queue (error %d)\n", err);
        clReleaseContext(g_cl.context);
        return -1;
    }
    
    const char* src = KERNEL_SOURCE;
    size_t src_len = strlen(src);
    g_cl.program = clCreateProgramWithSource(g_cl.context, 1, &src, &src_len, &err);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: Failed to create program (error %d)\n", err);
        clReleaseCommandQueue(g_cl.queue);
        clReleaseContext(g_cl.context);
        return -1;
    }
    
    err = clBuildProgram(g_cl.program, 1, &g_cl.device, "-cl-fast-relaxed-math -cl-mad-enable", NULL, NULL);
    if (err != CL_SUCCESS) {
        size_t log_size;
        clGetProgramBuildInfo(g_cl.program, g_cl.device, CL_PROGRAM_BUILD_LOG, 0, NULL, &log_size);
        char* log = malloc(log_size);
        clGetProgramBuildInfo(g_cl.program, g_cl.device, CL_PROGRAM_BUILD_LOG, log_size, log, NULL);
        fprintf(stderr, "OpenCL build error:\n%s\n", log);
        free(log);
        clReleaseProgram(g_cl.program);
        clReleaseCommandQueue(g_cl.queue);
        clReleaseContext(g_cl.context);
        return -1;
    }
    
    g_cl.build_lut_kernel = clCreateKernel(g_cl.program, "build_lut_kernel", &err);
    g_cl.resynthesize_lut_kernel = clCreateKernel(g_cl.program, "resynthesize_lut_kernel", &err);
    g_cl.resynthesize_direct_kernel = clCreateKernel(g_cl.program, "resynthesize_direct_kernel", &err);
    
    if (!g_cl.build_lut_kernel || !g_cl.resynthesize_lut_kernel || !g_cl.resynthesize_direct_kernel) {
        fprintf(stderr, "OpenCL: Failed to create kernels\n");
        cleanup_opencl_resources();
        return -1;
    }
    
    g_cl.lut_buffer = clCreateBuffer(g_cl.context, CL_MEM_READ_WRITE, 
                                      LUT_SIZE * sizeof(uint16_t), NULL, &err);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: Failed to allocate LUT buffer\n");
        cleanup_opencl_resources();
        return -1;
    }
    
    g_cl.initialized = 1;
    
    printf("OpenCL initialized: %s on %s\n", g_cl.device_name, g_cl.platform_name);
    printf("  Global memory: %.1f GB, Max alloc: %.1f GB\n",
           g_cl.global_mem_size / (1024.0*1024.0*1024.0),
           g_cl.max_alloc_size / (1024.0*1024.0*1024.0));
    
    return 0;
}

AICHAT_EXPORT void opencl_cleanup(void) {
    cleanup_opencl_resources();
}

static void cleanup_opencl_resources(void) {
    if (g_cl.lut_buffer) clReleaseMemObject(g_cl.lut_buffer);
    if (g_cl.target_palette_buffer) clReleaseMemObject(g_cl.target_palette_buffer);
    if (g_cl.source_palette_buffer) clReleaseMemObject(g_cl.source_palette_buffer);
    if (g_cl.build_lut_kernel) clReleaseKernel(g_cl.build_lut_kernel);
    if (g_cl.resynthesize_lut_kernel) clReleaseKernel(g_cl.resynthesize_lut_kernel);
    if (g_cl.resynthesize_direct_kernel) clReleaseKernel(g_cl.resynthesize_direct_kernel);
    if (g_cl.program) clReleaseProgram(g_cl.program);
    if (g_cl.queue) clReleaseCommandQueue(g_cl.queue);
    if (g_cl.context) clReleaseContext(g_cl.context);
    memset(&g_cl, 0, sizeof(g_cl));
}

AICHAT_EXPORT const char* opencl_get_device_name(void) {
    return g_cl.initialized ? g_cl.device_name : "Not initialized";
}

AICHAT_EXPORT const char* opencl_get_platform_name(void) {
    return g_cl.initialized ? g_cl.platform_name : "Not initialized";
}

AICHAT_EXPORT size_t opencl_get_max_work_group_size(void) {
    return g_cl.max_work_group_size;
}

AICHAT_EXPORT size_t opencl_get_global_mem_size(void) {
    return (size_t)g_cl.global_mem_size;
}

// Build LUT on GPU
static int build_lut_gpu(const float* palette, int palette_size) {
    if (!g_cl.initialized) return -1;
    
    cl_int err;
    
    // Update palette buffers if needed
    if (g_cl.current_palette_size != palette_size) {
        if (g_cl.target_palette_buffer) clReleaseMemObject(g_cl.target_palette_buffer);
        if (g_cl.source_palette_buffer) clReleaseMemObject(g_cl.source_palette_buffer);
        
        size_t palette_bytes = palette_size * 3 * sizeof(float);
        g_cl.target_palette_buffer = clCreateBuffer(g_cl.context, CL_MEM_READ_ONLY, palette_bytes, NULL, &err);
        g_cl.source_palette_buffer = clCreateBuffer(g_cl.context, CL_MEM_READ_ONLY, palette_bytes, NULL, &err);
        g_cl.current_palette_size = palette_size;
    }
    
    size_t palette_bytes = palette_size * 3 * sizeof(float);
    err = clEnqueueWriteBuffer(g_cl.queue, g_cl.target_palette_buffer, CL_FALSE, 0,
                                palette_bytes, palette, 0, NULL, NULL);
    if (err != CL_SUCCESS) return -1;
    
    int lut_dim = LUT_DIM;
    float lut_scale = LUT_SCALE;
    
    clSetKernelArg(g_cl.build_lut_kernel, 0, sizeof(cl_mem), &g_cl.target_palette_buffer);
    clSetKernelArg(g_cl.build_lut_kernel, 1, sizeof(int), &palette_size);
    clSetKernelArg(g_cl.build_lut_kernel, 2, sizeof(cl_mem), &g_cl.lut_buffer);
    clSetKernelArg(g_cl.build_lut_kernel, 3, sizeof(int), &lut_dim);
    clSetKernelArg(g_cl.build_lut_kernel, 4, sizeof(float), &lut_scale);
    
    size_t global_size = LUT_SIZE;
    size_t local_size = 256;
    global_size = ((global_size + local_size - 1) / local_size) * local_size;
    
    err = clEnqueueNDRangeKernel(g_cl.queue, g_cl.build_lut_kernel, 1, NULL,
                                  &global_size, &local_size, 0, NULL, NULL);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: build_lut_kernel failed (error %d)\n", err);
        return -1;
    }
    
    return 0;
}

AICHAT_EXPORT int opencl_resynthesize_image(
    const uint32_t* image_pixels,
    int width,
    int height,
    const float* target_palette,
    const float* source_palette,
    int palette_size,
    uint32_t* output_pixels
) {
    if (!g_cl.initialized) {
        if (opencl_init() != 0) return -1;
    }
    
    cl_int err;
    int n = width * height;
    size_t image_bytes = n * sizeof(uint32_t);
    size_t palette_bytes = palette_size * 3 * sizeof(float);
    
    if (image_bytes * 2 + palette_bytes * 2 + LUT_SIZE * 2 > g_cl.max_alloc_size) {
        return opencl_resynthesize_streaming(image_pixels, width, height,
                                              target_palette, source_palette, 
                                              palette_size, output_pixels, 0);
    }
    
    if (build_lut_gpu(target_palette, palette_size) != 0) {
        return -1;
    }
    
    if (g_cl.source_palette_buffer) {
        err = clEnqueueWriteBuffer(g_cl.queue, g_cl.source_palette_buffer, CL_FALSE, 0,
                                    palette_bytes, source_palette, 0, NULL, NULL);
        if (err != CL_SUCCESS) return -1;
    }
    
    cl_mem input_buffer = clCreateBuffer(g_cl.context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                                          image_bytes, (void*)image_pixels, &err);
    if (err != CL_SUCCESS) return -1;
    
    cl_mem output_buffer = clCreateBuffer(g_cl.context, CL_MEM_WRITE_ONLY,
                                           image_bytes, NULL, &err);
    if (err != CL_SUCCESS) {
        clReleaseMemObject(input_buffer);
        return -1;
    }
    
    int lut_bits = LUT_BITS;
    int shift = SHIFT;
    
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 0, sizeof(cl_mem), &input_buffer);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 1, sizeof(cl_mem), &output_buffer);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 2, sizeof(cl_mem), &g_cl.lut_buffer);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 3, sizeof(cl_mem), &g_cl.target_palette_buffer);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 4, sizeof(cl_mem), &g_cl.source_palette_buffer);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 5, sizeof(int), &width);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 6, sizeof(int), &height);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 7, sizeof(int), &lut_bits);
    clSetKernelArg(g_cl.resynthesize_lut_kernel, 8, sizeof(int), &shift);
    
    size_t global_size = n;
    size_t local_size = 256;
    global_size = ((global_size + local_size - 1) / local_size) * local_size;
    
    err = clEnqueueNDRangeKernel(g_cl.queue, g_cl.resynthesize_lut_kernel, 1, NULL,
                                  &global_size, &local_size, 0, NULL, NULL);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "OpenCL: resynthesize_lut_kernel failed (error %d)\n", err);
        clReleaseMemObject(input_buffer);
        clReleaseMemObject(output_buffer);
        return -1;
    }
    
    err = clEnqueueReadBuffer(g_cl.queue, output_buffer, CL_TRUE, 0,
                               image_bytes, output_pixels, 0, NULL, NULL);
    
    clReleaseMemObject(input_buffer);
    clReleaseMemObject(output_buffer);
    
    return (err == CL_SUCCESS) ? 0 : -1;
}

AICHAT_EXPORT int opencl_resynthesize_streaming(
    const uint32_t* image_pixels,
    int width,
    int height,
    const float* target_palette,
    const float* source_palette,
    int palette_size,
    uint32_t* output_pixels,
    int tile_height
) {
    if (!g_cl.initialized) {
        if (opencl_init() != 0) return -1;
    }
    
    if (tile_height <= 0) {
        size_t target_tile_bytes = 256 * 1024 * 1024;
        size_t bytes_per_row = width * sizeof(uint32_t);
        tile_height = (int)(target_tile_bytes / bytes_per_row / 2);
        if (tile_height < 64) tile_height = 64;
        if (tile_height > height) tile_height = height;
        tile_height = (tile_height / 64) * 64;
        if (tile_height == 0) tile_height = height;
    }
    
    cl_int err;
    size_t palette_bytes = palette_size * 3 * sizeof(float);
    
    if (build_lut_gpu(target_palette, palette_size) != 0) {
        return -1;
    }
    
    err = clEnqueueWriteBuffer(g_cl.queue, g_cl.source_palette_buffer, CL_TRUE, 0,
                                palette_bytes, source_palette, 0, NULL, NULL);
    if (err != CL_SUCCESS) return -1;
    
    size_t max_tile_pixels = (size_t)width * tile_height;
    size_t tile_bytes = max_tile_pixels * sizeof(uint32_t);
    
    cl_mem input_buffers[2], output_buffers[2];
    for (int i = 0; i < 2; i++) {
        input_buffers[i] = clCreateBuffer(g_cl.context, CL_MEM_READ_ONLY, tile_bytes, NULL, &err);
        output_buffers[i] = clCreateBuffer(g_cl.context, CL_MEM_WRITE_ONLY, tile_bytes, NULL, &err);
        if (err != CL_SUCCESS) {
            for (int j = 0; j < i; j++) {
                clReleaseMemObject(input_buffers[j]);
                clReleaseMemObject(output_buffers[j]);
            }
            return -1;
        }
    }
    
    int lut_bits = LUT_BITS;
    int shift = SHIFT;
    
    int num_tiles = (height + tile_height - 1) / tile_height;
    int buffer_idx = 0;
    
    cl_event write_event = NULL, kernel_event = NULL, read_event = NULL;
    
    for (int tile = 0; tile < num_tiles; tile++) {
        int y_start = tile * tile_height;
        int current_tile_height = (y_start + tile_height > height) ? (height - y_start) : tile_height;
        int tile_pixels = width * current_tile_height;
        size_t current_tile_bytes = tile_pixels * sizeof(uint32_t);
        
        const uint32_t* tile_input = image_pixels + y_start * width;
        err = clEnqueueWriteBuffer(g_cl.queue, input_buffers[buffer_idx], CL_FALSE, 0,
                                    current_tile_bytes, tile_input, 0, NULL, &write_event);
        if (err != CL_SUCCESS) goto cleanup;
        
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 0, sizeof(cl_mem), &input_buffers[buffer_idx]);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 1, sizeof(cl_mem), &output_buffers[buffer_idx]);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 2, sizeof(cl_mem), &g_cl.lut_buffer);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 3, sizeof(cl_mem), &g_cl.target_palette_buffer);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 4, sizeof(cl_mem), &g_cl.source_palette_buffer);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 5, sizeof(int), &width);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 6, sizeof(int), &current_tile_height);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 7, sizeof(int), &lut_bits);
        clSetKernelArg(g_cl.resynthesize_lut_kernel, 8, sizeof(int), &shift);
        
        size_t global_size = tile_pixels;
        size_t local_size = 256;
        global_size = ((global_size + local_size - 1) / local_size) * local_size;
        
        err = clEnqueueNDRangeKernel(g_cl.queue, g_cl.resynthesize_lut_kernel, 1, NULL,
                                      &global_size, &local_size, 1, &write_event, &kernel_event);
        clReleaseEvent(write_event);
        if (err != CL_SUCCESS) goto cleanup;
        
        uint32_t* tile_output = output_pixels + y_start * width;
        err = clEnqueueReadBuffer(g_cl.queue, output_buffers[buffer_idx], CL_FALSE, 0,
                                   current_tile_bytes, tile_output, 1, &kernel_event, &read_event);
        clReleaseEvent(kernel_event);
        if (err != CL_SUCCESS) goto cleanup;
        
        if (tile == num_tiles - 1) {
            clWaitForEvents(1, &read_event);
        }
        clReleaseEvent(read_event);
        
        buffer_idx = 1 - buffer_idx;
    }
    
    for (int i = 0; i < 2; i++) {
        clReleaseMemObject(input_buffers[i]);
        clReleaseMemObject(output_buffers[i]);
    }
    return 0;
    
cleanup:
    for (int i = 0; i < 2; i++) {
        if (input_buffers[i]) clReleaseMemObject(input_buffers[i]);
        if (output_buffers[i]) clReleaseMemObject(output_buffers[i]);
    }
    return -1;
}

AICHAT_EXPORT int opencl_build_lut(
    const float* palette,
    int palette_size,
    uint16_t* lut,
    int lut_dim
) {
    if (!g_cl.initialized) {
        if (opencl_init() != 0) return -1;
    }
    
    if (lut_dim != LUT_DIM) {
        fprintf(stderr, "OpenCL: LUT dimension must be %d\n", LUT_DIM);
        return -1;
    }
    
    if (build_lut_gpu(palette, palette_size) != 0) {
        return -1;
    }
    
    // Read back LUT
    cl_int err = clEnqueueReadBuffer(g_cl.queue, g_cl.lut_buffer, CL_TRUE, 0,
                                      LUT_SIZE * sizeof(uint16_t), lut, 0, NULL, NULL);
    
    return (err == CL_SUCCESS) ? 0 : -1;
}
