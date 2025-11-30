#include "../include/common.h"

#ifdef HAVE_OPENCL
#include "../include/opencl_accel.h"
#endif

AICHAT_EXPORT const char* aichat_native_version(void) {
#ifdef HAVE_OPENCL
    return "2.1.0-opencl";
#else
    return "2.0.0-panama";
#endif
}

AICHAT_EXPORT int aichat_has_simd(void) {
    return HAS_SSE || HAS_AVX;
}

AICHAT_EXPORT int aichat_has_opencl(void) {
#ifdef HAVE_OPENCL
    return opencl_available();
#else
    return 0;
#endif
}
