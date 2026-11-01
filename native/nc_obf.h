// Nocturne native — compile-time obfuscation primitives (MSVC / C++17).
//
// The core image is linked CRT-free (/NODEFAULTLIB) and manually mapped, so
// nothing here may touch the C runtime: no <string>, no std::*, no allocators.
// Everything is header-only integer math plus a handful of intrinsics.
#pragma once
#include <cstddef>
#include <cstdint>

namespace nc {

// ---- avalanche mix: one multiply-xor round, no lookup table ----
constexpr unsigned mix(unsigned x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

// ---- encrypted literals -------------------------------------------------
// Enc: N-1 payload bytes (the trailing NUL is not stored) XORed with a key
// stream derived from the call-site counter, so every site differs per build
// and no plaintext byte of the literal exists in .rdata.
template <size_t N>
struct Enc {
    char d[N - 1];
    constexpr Enc(const char (&s)[N], unsigned k) : d{} {
        for (size_t i = 0; i + 1 < N; i++) {
            d[i] = static_cast<char>(
                s[i] ^ static_cast<unsigned char>(mix(k + static_cast<unsigned>(i) * 0x9E37u)));
        }
    }
};

// Decode into a caller-owned buffer of at least `n` bytes (NUL terminated).
inline void unc(const char* e, size_t n, unsigned k, char* out) {
    for (size_t i = 0; i + 1 < n; i++) {
        out[i] = static_cast<char>(
            e[i] ^ static_cast<unsigned char>(mix(k + static_cast<unsigned>(i) * 0x9E37u)));
    }
    out[n - 1] = 0;
}

// ---- opaque predicates --------------------------------------------------
// Each is true for EVERY input, but only after real evaluation or reasoning:
// no compiler folds these, and no pattern-matcher can classify them cheaply.
// They gate the junk blocks emitted by NC_NOISE, so the dead arithmetic must
// still be evaluated to prove it is dead.

// x*(x-1) is always even => low bit clear.
inline bool opq_even(uint32_t x) { return (uint32_t)((x * (x - 1u)) & 1u) == 0u; }
// floor(x/2)*2 never exceeds x.
inline bool opq_half(uint32_t x) { return (uint32_t)(2u * (x / 2u)) <= x; }
// x | ~x is all ones for every x.
inline bool opq_ones(uint32_t x) { return (x | (uint32_t)~x) == 0xFFFFFFFFu; }
// x and x-1 can never be equal, so their xor is non-zero.
inline bool opq_xor(uint32_t x) { return (x ^ (x - 1u)) != 0u; }
// (x >> 1) * 2 <= x for unsigned x.
inline bool opq_shr(uint32_t x) { return (uint32_t)((x >> 1) * 2u) <= x; }

// ---- dead-block emitter -------------------------------------------------
// Evaluates a chain of real arithmetic over the live value `v`, then routes it
// through one opaque predicate per round. A consumer must model the arithmetic
// to prove the block unreachable. Semantically inert: nothing observed changes.
#define NC_NOISE(v)                                                             \
    do {                                                                        \
        volatile uint32_t nc_v_ = (uint32_t)(uintptr_t)&(v) ^                   \
            (uint32_t)__LINE__ * 2654435761u;                                   \
        nc_v_ ^= nc_v_ >> 7;                                                    \
        nc_v_ *= 0x9E3779B1u;                                                   \
        nc_v_ ^= nc_v_ >> 13;                                                   \
        if (!::nc::opq_even(nc_v_) || !::nc::opq_half(nc_v_)) {                 \
            nc_v_ ^= 0x5A5A5A5Au; /* never reached */                           \
        }                                                                       \
        if (!::nc::opq_ones(nc_v_) || !::nc::opq_xor(nc_v_)                     \
            || !::nc::opq_shr(nc_v_)) {                                         \
            nc_v_ += 0x13371337u; /* never reached */                           \
        }                                                                       \
        (void)nc_v_;                                                            \
    } while (0)

// ---- literal decode macro ----------------------------------------------
// NC_FILL("Name", buf) fills `buf` (a char array of at least sizeof lit).
#define NC_FILL(lit, buf)                                                       \
    do {                                                                        \
        constexpr unsigned nc_k_ = 0x51ED27B3u + (unsigned)__COUNTER__ * 0x85EBu; \
        static constexpr ::nc::Enc<sizeof(lit)> nc_e_(lit, nc_k_);             \
        ::nc::unc(nc_e_.d, sizeof(lit), nc_k_, (buf));                          \
    } while (0)

// ---- 64-bit seed concealment -------------------------------------------
// The build seed is stored as (seed - K) so the literal in .rdata is not the
// seed itself; only the arithmetic recovers it.
constexpr uint64_t SEED_K = 0x6A09E667F3BCC909ull;

// ---- mixed boolean arithmetic on a live int ----------------------------
// (x + k) - k split into two opaque halves; identical result, two ops.
inline uint32_t mba_bias(uint32_t x, uint32_t k) {
    uint32_t t = x + k;
    return (t ^ (k & 0u)) - (k ^ (k & 0u));
}

} // namespace nc