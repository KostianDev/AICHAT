/**
 * AICHAT Native Library - Common Functions
 */

#include "../include/common.h"

AICHAT_EXPORT const char* aichat_native_version(void) {
    return "2.0.0-panama";
}

AICHAT_EXPORT int aichat_has_simd(void) {
    return HAS_SSE || HAS_AVX;
}
