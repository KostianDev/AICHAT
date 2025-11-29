#include "../include/color.h"
#include <math.h>

static inline float srgb_to_linear(float c) {
    c = c / 255.0f;
    return (c > 0.04045f) ? powf((c + 0.055f) / 1.055f, 2.4f) : c / 12.92f;
}

static inline float linear_to_srgb(float c) {
    c = (c > 0.0031308f) ? (1.055f * powf(c, 1.0f / 2.4f) - 0.055f) : 12.92f * c;
    return c * 255.0f;
}

static inline float lab_f(float t) {
    return (t > LAB_EPSILON) ? cbrtf(t) : (LAB_KAPPA * t + 16.0f) / 116.0f;
}

static inline float lab_f_inv(float t) {
    return (t > LAB_DELTA) ? t * t * t : (116.0f * t - 16.0f) / LAB_KAPPA;
}

static void rgb_to_lab_single(const ColorPoint3f* rgb, ColorPoint3f* lab) {
    // RGB to linear
    float r = srgb_to_linear(rgb->c1);
    float g = srgb_to_linear(rgb->c2);
    float b = srgb_to_linear(rgb->c3);
    
    // Linear RGB to XYZ
    float x = (r * 0.4124564f + g * 0.3575761f + b * 0.1804375f) * 100.0f;
    float y = (r * 0.2126729f + g * 0.7151522f + b * 0.0721750f) * 100.0f;
    float z = (r * 0.0193339f + g * 0.1191920f + b * 0.9503041f) * 100.0f;
    
    // XYZ to Lab
    float fx = lab_f(x / REF_X);
    float fy = lab_f(y / REF_Y);
    float fz = lab_f(z / REF_Z);
    
    lab->c1 = 116.0f * fy - 16.0f;  // L
    lab->c2 = 500.0f * (fx - fy);   // a
    lab->c3 = 200.0f * (fy - fz);   // b
}

static void lab_to_rgb_single(const ColorPoint3f* lab, ColorPoint3f* rgb) {
    // Lab to XYZ
    float fy = (lab->c1 + 16.0f) / 116.0f;
    float fx = lab->c2 / 500.0f + fy;
    float fz = fy - lab->c3 / 200.0f;
    
    float x = lab_f_inv(fx) * REF_X;
    float y = lab_f_inv(fy) * REF_Y;
    float z = lab_f_inv(fz) * REF_Z;
    
    // XYZ to linear RGB
    x /= 100.0f;
    y /= 100.0f;
    z /= 100.0f;
    
    float r = x *  3.2404542f + y * -1.5371385f + z * -0.4985314f;
    float g = x * -0.9692660f + y *  1.8760108f + z *  0.0415560f;
    float b = x *  0.0556434f + y * -0.2040259f + z *  1.0572252f;
    
    // Linear to sRGB with clamping
    rgb->c1 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(r)));
    rgb->c2 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(g)));
    rgb->c3 = fminf(255.0f, fmaxf(0.0f, linear_to_srgb(b)));
}

AICHAT_EXPORT void rgb_to_lab_batch(
    const ColorPoint3f* rgb,
    ColorPoint3f* lab,
    int n
) {
    #pragma omp parallel for if(n > 1000)
    for (int i = 0; i < n; i++) {
        rgb_to_lab_single(&rgb[i], &lab[i]);
    }
}

AICHAT_EXPORT void lab_to_rgb_batch(
    const ColorPoint3f* lab,
    ColorPoint3f* rgb,
    int n
) {
    #pragma omp parallel for if(n > 1000)
    for (int i = 0; i < n; i++) {
        lab_to_rgb_single(&lab[i], &rgb[i]);
    }
}
