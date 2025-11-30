#include "../include/common.h"

#ifdef HAVE_OPENCL
#include "../include/opencl_accel.h"
#endif

AICHAT_EXPORT const char* aichat_native_version(void) {
#if defined(HAVE_OPENCL) && defined(HAVE_TURBOJPEG)
    return "2.1.0-opencl-turbojpeg";
#elif defined(HAVE_OPENCL)
    return "2.1.0-opencl";
#elif defined(HAVE_TURBOJPEG)
    return "2.1.0-turbojpeg";
#else
    return "2.1.0";
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

AICHAT_EXPORT int aichat_has_turbojpeg(void) {
#ifdef HAVE_TURBOJPEG
    return 1;
#else
    return 0;
#endif
}

#ifndef HAVE_TURBOJPEG
AICHAT_EXPORT int decode_jpeg_file_turbojpeg(const char* path, int* w, int* h, unsigned char** pixels) {
    (void)path; (void)w; (void)h; (void)pixels;
    return -1;
}
AICHAT_EXPORT void turbojpeg_free(unsigned char* pixels) {
    (void)pixels;
}
AICHAT_EXPORT int turbojpeg_encode_to_file(const unsigned char* pixels, int w, int h, int quality, const char* path) {
    (void)pixels; (void)w; (void)h; (void)quality; (void)path;
    return -1;
}
#endif
