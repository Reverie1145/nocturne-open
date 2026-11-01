// SHA-256 (compact implementation, public-domain shape) for HMAC in native.
#pragma once
#include <cstdint>
#include <cstddef>

namespace nc {

struct Sha256 {
    uint32_t h[8];
    uint64_t len;
    uint8_t buf[64];
    size_t bufLen;

    Sha256();
    void update(const uint8_t* data, size_t n);
    void finish(uint8_t out[32]);
};

void hmacSha256(const uint8_t* key, size_t keyLen,
                const uint8_t* msg, size_t msgLen,
                uint8_t out[32]);

} // namespace nc
