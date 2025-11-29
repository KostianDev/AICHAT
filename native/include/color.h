#ifndef AICHAT_COLOR_H
#define AICHAT_COLOR_H

#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

AICHAT_EXPORT void rgb_to_lab_batch(
    const ColorPoint3f* rgb,
    ColorPoint3f* lab,
    int n
);

AICHAT_EXPORT void lab_to_rgb_batch(
    const ColorPoint3f* lab,
    ColorPoint3f* rgb,
    int n
);

#ifdef __cplusplus
}
#endif

#endif // AICHAT_COLOR_H
