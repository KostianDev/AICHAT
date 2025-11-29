/**
 * AICHAT Native Library - Random Number Generator
 * 
 * Fast xorshift64 PRNG for deterministic random sampling.
 */

#ifndef AICHAT_RANDOM_H
#define AICHAT_RANDOM_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// XorShift64 Random Number Generator
// ============================================================================

typedef struct {
    uint64_t state;
} XorShift64;

/**
 * Initialize PRNG with seed.
 */
static inline void xorshift64_init(XorShift64* rng, uint64_t seed) {
    rng->state = seed ? seed : 42;
}

/**
 * Generate next 64-bit random value.
 */
static inline uint64_t xorshift64_next(XorShift64* rng) {
    uint64_t x = rng->state;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    rng->state = x;
    return x;
}

/**
 * Generate random double in [0, 1).
 */
static inline double xorshift64_double(XorShift64* rng) {
    return (double)(xorshift64_next(rng) >> 11) / (double)(1ULL << 53);
}

/**
 * Generate random integer in [0, max).
 */
static inline int xorshift64_int(XorShift64* rng, int max) {
    return (int)(xorshift64_next(rng) % (uint64_t)max);
}

#ifdef __cplusplus
}
#endif

#endif // AICHAT_RANDOM_H
