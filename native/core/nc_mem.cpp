// Minimal memory primitives for the CRT-free core.
//
// The core links with /NODEFAULTLIB, so the compiler-emitted calls to memcpy /
// memset / memmove have no implementation to bind to. These are the only three
// C-runtime symbols the image needs.
//
// They MUST be built on the hardware string intrinsics (rep stosb / rep movsb)
// and not on byte loops: MSVC's loop-idiom recognition rewrites a byte loop
// into a call to memcpy/memset, so a hand-written loop here becomes a call to
// itself — unbounded recursion ending in STATUS_STACK_OVERFLOW.
#include <string.h>
#include <intrin.h>
#pragma function(memcpy, memset, memmove)

extern "C" void* __cdecl memcpy(void* dst, const void* src, size_t n) {
    if (n) __movsb((unsigned char*)dst, (const unsigned char*)src, n);
    return dst;
}

extern "C" void* __cdecl memset(void* dst, int value, size_t n) {
    if (n) __stosb((unsigned char*)dst, (unsigned char)value, n);
    return dst;
}

extern "C" void* __cdecl memmove(void* dst, const void* src, size_t n) {
    if (n == 0 || dst == src) return dst;
    unsigned char* d = (unsigned char*)dst;
    const unsigned char* s = (const unsigned char*)src;
    if (d < s) {
        __movsb(d, s, n);
    } else {
        // descending copy: not a memcpy idiom, so the optimizer leaves it alone
        for (size_t i = n; i > 0; i--) {
            volatile unsigned char v = s[i - 1];
            d[i - 1] = v;
        }
    }
    return dst;
}