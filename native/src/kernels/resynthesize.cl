// OpenCL kernel for GPU-accelerated image resynthesis
// Compatible with NVIDIA, AMD, and Intel GPUs

// Perceptual color distance (weighted RGB)
inline float perceptual_distance_sq(float3 a, float3 b) {
    float3 d = a - b;
    float avg_r = (a.x + b.x) * 0.5f;
    
    float wr = avg_r < 128.0f ? 2.0f : 3.0f;
    float wg = 4.0f;
    float wb = avg_r < 128.0f ? 3.0f : 2.0f;
    
    return wr * d.x * d.x + wg * d.y * d.y + wb * d.z * d.z;
}

// Find nearest color in palette
inline int find_nearest(float3 point, __global const float* palette, int palette_size) {
    int nearest = 0;
    float min_dist = INFINITY;
    
    for (int i = 0; i < palette_size; i++) {
        float3 color = (float3)(
            palette[i * 3],
            palette[i * 3 + 1],
            palette[i * 3 + 2]
        );
        float dist = perceptual_distance_sq(point, color);
        if (dist < min_dist) {
            min_dist = dist;
            nearest = i;
        }
    }
    
    return nearest;
}

// Build LUT kernel - each work item computes one LUT entry
__kernel void build_lut_kernel(
    __global const float* palette,
    int palette_size,
    __global ushort* lut,
    int lut_dim,
    float lut_scale
) {
    int gid = get_global_id(0);
    
    int lut_size = lut_dim * lut_dim * lut_dim;
    if (gid >= lut_size) return;
    
    // Decode 1D index to 3D RGB
    int bi = gid % lut_dim;
    int gi = (gid / lut_dim) % lut_dim;
    int ri = gid / (lut_dim * lut_dim);
    
    float3 point = (float3)(
        ri * lut_scale,
        gi * lut_scale,
        bi * lut_scale
    );
    
    lut[gid] = (ushort)find_nearest(point, palette, palette_size);
}

// Main resynthesis kernel using LUT
__kernel void resynthesize_lut_kernel(
    __global const uint* input_pixels,
    __global uint* output_pixels,
    __global const ushort* lut,
    __global const float* target_palette,
    __global const float* source_palette,
    int width,
    int height,
    int lut_bits,
    int shift
) {
    int gid = get_global_id(0);
    int n = width * height;
    
    if (gid >= n) return;
    
    uint pixel = input_pixels[gid];
    int pr = (pixel >> 16) & 0xFF;
    int pg = (pixel >> 8) & 0xFF;
    int pb = pixel & 0xFF;
    
    // LUT lookup
    int lut_idx = ((pr >> shift) << (lut_bits * 2)) | 
                  ((pg >> shift) << lut_bits) | 
                  (pb >> shift);
    int palette_idx = lut[lut_idx];
    
    // Get palette centers
    float3 target_center = (float3)(
        target_palette[palette_idx * 3],
        target_palette[palette_idx * 3 + 1],
        target_palette[palette_idx * 3 + 2]
    );
    float3 source_center = (float3)(
        source_palette[palette_idx * 3],
        source_palette[palette_idx * 3 + 1],
        source_palette[palette_idx * 3 + 2]
    );
    
    // Apply color transfer with offset preservation
    int r = (int)(source_center.x + (pr - target_center.x) + 0.5f);
    int g = (int)(source_center.y + (pg - target_center.y) + 0.5f);
    int b = (int)(source_center.z + (pb - target_center.z) + 0.5f);
    
    // Clamp
    r = clamp(r, 0, 255);
    g = clamp(g, 0, 255);
    b = clamp(b, 0, 255);
    
    output_pixels[gid] = (uint)((r << 16) | (g << 8) | b);
}

// Direct resynthesis kernel (no LUT, for very large palettes)
__kernel void resynthesize_direct_kernel(
    __global const uint* input_pixels,
    __global uint* output_pixels,
    __global const float* target_palette,
    __global const float* source_palette,
    int palette_size,
    int width,
    int height
) {
    int gid = get_global_id(0);
    int n = width * height;
    
    if (gid >= n) return;
    
    uint pixel = input_pixels[gid];
    float3 point = (float3)(
        (float)((pixel >> 16) & 0xFF),
        (float)((pixel >> 8) & 0xFF),
        (float)(pixel & 0xFF)
    );
    
    int palette_idx = find_nearest(point, target_palette, palette_size);
    
    float3 target_center = (float3)(
        target_palette[palette_idx * 3],
        target_palette[palette_idx * 3 + 1],
        target_palette[palette_idx * 3 + 2]
    );
    float3 source_center = (float3)(
        source_palette[palette_idx * 3],
        source_palette[palette_idx * 3 + 1],
        source_palette[palette_idx * 3 + 2]
    );
    
    int r = (int)(source_center.x + (point.x - target_center.x) + 0.5f);
    int g = (int)(source_center.y + (point.y - target_center.y) + 0.5f);
    int b = (int)(source_center.z + (point.z - target_center.z) + 0.5f);
    
    r = clamp(r, 0, 255);
    g = clamp(g, 0, 255);
    b = clamp(b, 0, 255);
    
    output_pixels[gid] = (uint)((r << 16) | (g << 8) | b);
}
