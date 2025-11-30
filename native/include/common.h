#ifndef AICHAT_COMMON_H
#define AICHAT_COMMON_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifdef _WIN32
    #define AICHAT_EXPORT __declspec(dllexport)
#else
    #define AICHAT_EXPORT __attribute__((visibility("default")))
#endif

#define LIKELY(x)   __builtin_expect(!!(x), 1)
#define UNLIKELY(x) __builtin_expect(!!(x), 0)
#define RESTRICT    __restrict__

#if defined(__SSE__) || defined(__SSE2__)
    #define HAS_SSE 1
#else
    #define HAS_SSE 0
#endif

#if defined(__AVX__)
    #define HAS_AVX 1
#else
    #define HAS_AVX 0
#endif

typedef struct {
    float c1;  // R or L
    float c2;  // G or a
    float c3;
} ColorPoint3f;

// CIELAB D65 reference white
#define REF_X 95.047f
#define REF_Y 100.000f
#define REF_Z 108.883f

// LAB conversion thresholds
#define LAB_EPSILON 0.008856f
#define LAB_KAPPA   903.3f
#define LAB_DELTA   (6.0f / 29.0f)

#define DBSCAN_NOISE        -1
#define DBSCAN_UNCLASSIFIED -2

AICHAT_EXPORT const char* aichat_native_version(void);
AICHAT_EXPORT int aichat_has_simd(void);
AICHAT_EXPORT int aichat_has_opencl(void);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_COMMON_H
