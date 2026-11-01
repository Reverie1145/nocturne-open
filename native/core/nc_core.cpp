// Nocturne native core — the entire protected-key surface.
//
// Everything that must never exist in readable Java bytecode lives here:
//   * the build seed (obfuscation seed); every pool / class-pack key is
//     derived from it, so pool ciphertext and packed class bodies in the jar
//     are undecryptable without this image
//   * session-key storage (split-XOR in a native heap block) + HMAC-SHA256
//     signing of "ts|nonce|body" and "app|ts|nonce|path|body"
//   * integrity hashing + the tamper decision (the Java side only moves bytes)
//   * environment watchdog on an OS thread, with a guard on every JNI entry
//   * anti-dump: PE headers erased, image manually mapped (no PEB module entry)
//
// CRT-free (/NODEFAULTLIB): no std::*, no CRT allocators, no static ctors.
// Loaded via NBootstrap; natives are bound with RegisterNatives against the
// live class object, so the Java guard class may be renamed freely.
#include <jni.h>
#include <windows.h>
#include <intrin.h>
#include <cstdint>
#include <cstddef>
#include "nc_obf.h"
#include "nc_secret_config.h"
#include "core/nc_sha256.h"

namespace nc {
bool environmentCompromised();
bool environmentCompromisedFast();
}

namespace {

// ------------------------------------------------------------------ memory

void* ncAlloc(size_t n) {
    return VirtualAlloc(nullptr, n, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
}
void ncFree(void* p, size_t n) {
    SecureZeroMemory(p, n);
    VirtualFree(p, 0, MEM_RELEASE);
}
void ncCopy(void* dst, const void* src, size_t n) {
    auto d = (uint8_t*)dst;
    auto s = (const uint8_t*)src;
    for (size_t i = 0; i < n; i++) d[i] = s[i];
}
int ncEq(const uint8_t* a, const uint8_t* b, size_t n) {
    uint8_t diff = 0;
    for (size_t i = 0; i < n; i++) diff |= a[i] ^ b[i];
    return diff == 0;
}

// ------------------------------------------------------------------ state

// Session key. Each byte is stored as (b ^ mask[i & 7]) with a per-instance
// mask so a plain heap scan never surfaces the key material.
uint8_t g_key[96];
size_t g_keyLen = 0;
uint8_t g_mask[8] = {0xB7, 0x3D, 0x91, 0x5C, 0xE2, 0x48, 0x0A, 0xF6};
CRITICAL_SECTION g_lock;
bool g_lockInit = false;
volatile LONG g_guardHits = 0;

void storeKey(const uint8_t* k, size_t n) {
    if (n > sizeof(g_key)) n = sizeof(g_key);
    EnterCriticalSection(&g_lock);
    for (size_t i = 0; i < n; i++) g_key[i] = k[i] ^ g_mask[i & 7];
    g_keyLen = n;
    LeaveCriticalSection(&g_lock);
}

void loadKey(uint8_t* out, size_t& n) {
    EnterCriticalSection(&g_lock);
    n = g_keyLen;
    for (size_t i = 0; i < n; i++) out[i] = g_key[i] ^ g_mask[i & 7];
    LeaveCriticalSection(&g_lock);
}

void wipeKey() {
    EnterCriticalSection(&g_lock);
    SecureZeroMemory(g_key, sizeof(g_key));
    g_keyLen = 0;
    LeaveCriticalSection(&g_lock);
}

// ---- build seed. Stored as (seed - NC_SEED_K) so the 64-bit literal sitting
// in .rdata is not the seed; only this arithmetic recovers the real value.
constexpr uint64_t SEED_STORED = (uint64_t)NC_BUILD_SEED - nc::SEED_K;

uint64_t buildSeed() {
    return SEED_STORED + nc::SEED_K;
}

// 16-byte key from a 64-bit LCG state. Little-endian spread so Java and
// native agree byte-for-byte (mirrors PoolGen.nativePoolKey / nativePackKey).
void derive16(uint64_t st, uint8_t out[16]) {
    for (int i = 0; i < 4; i++) {
        st = st * 6364136223846793005ULL + 1442695040888963407ULL;
        uint32_t v = (uint32_t)(st >> 32);
        out[i * 4 + 0] = (uint8_t)(v);
        out[i * 4 + 1] = (uint8_t)(v >> 8);
        out[i * 4 + 2] = (uint8_t)(v >> 16);
        out[i * 4 + 3] = (uint8_t)(v >> 24);
    }
}

// ------------------------------------------------------------------ hmac

size_t ncStrLen(const char* s) {
    size_t n = 0;
    if (!s) return 0;
    while (s[n]) n++;
    return n;
}

// Streaming HMAC so a request body never has to be concatenated into one
// buffer (the canonical string is fed piece by piece).
struct Hmac {
    uint8_t opad[64];
    nc::Sha256 inner;

    void init(const uint8_t* key, size_t keyLen) {
        uint8_t k[64];
        SecureZeroMemory(k, sizeof(k));
        if (keyLen > 64) {
            nc::Sha256 s;
            s.update(key, keyLen);
            s.finish(k);
        } else {
            ncCopy(k, key, keyLen);
        }
        uint8_t ipad[64];
        for (int i = 0; i < 64; i++) {
            ipad[i] = (uint8_t)(k[i] ^ 0x36);
            opad[i] = (uint8_t)(k[i] ^ 0x5C);
        }
        SecureZeroMemory(k, sizeof(k));
        inner = nc::Sha256();
        inner.update(ipad, 64);
        SecureZeroMemory(ipad, sizeof(ipad));
    }
    void put(const uint8_t* p, size_t n) { inner.update(p, n); }
    void putByte(uint8_t b) { inner.update(&b, 1); }
    void putStr(const char* s) { if (s) inner.update((const uint8_t*)s, ncStrLen(s)); }
    void finish(uint8_t out[32]) {
        uint8_t mid[32];
        inner.finish(mid);
        nc::Sha256 outer;
        outer.update(opad, 64);
        outer.update(mid, 32);
        outer.finish(out);
        SecureZeroMemory(mid, sizeof(mid));
        SecureZeroMemory(opad, sizeof(opad));
    }
};

void toHex(const uint8_t* in, size_t n, char* out) {
    static const char HEX[] = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[i * 2] = HEX[in[i] >> 4];
        out[i * 2 + 1] = HEX[in[i] & 0xF];
    }
    out[n * 2] = 0;
}

// ------------------------------------------------------------------ guard

void eraseSelfHeaders() {
    auto addr = (uint8_t*)&eraseSelfHeaders;
    MEMORY_BASIC_INFORMATION mbi{};
    if (VirtualQuery(addr, &mbi, sizeof(mbi)) == 0 || !mbi.AllocationBase) return;
    auto base = (uint8_t*)mbi.AllocationBase;
    DWORD old = 0;
    if (VirtualProtect(base, 0x1000, PAGE_READWRITE, &old)) {
        SecureZeroMemory(base, 0x1000);
        VirtualProtect(base, 0x1000, PAGE_NOACCESS, &old);
    }
}

// Called from every JNI entry: a flagged environment disables the key and
// hard-fails the process before any result can be used. Uses the cheap probe
// set — the watchdog thread runs the expensive scans.
void guardOrDie() {
    if (!nc::environmentCompromisedFast()) return;
    InterlockedIncrement(&g_guardHits);
    wipeKey();
    __fastfail(0x1337C0DEu);
}

// Native watchdog: OS thread, independent of JVM scheduling. Enforcement is
// on — a flagged probe wipes the session key and fast-fails.
DWORD WINAPI watchdogLoop(LPVOID) {
    uint32_t jitter = 0x9E3779B9u;
    for (;;) {
        if (nc::environmentCompromised()) {
            wipeKey();
            __fastfail(0x1337C0DEu);
        }
        // jitter so the tick is not a fixed metronome
        jitter = jitter * 1103515245u + 12345u;
        Sleep(2200u + (jitter >> 24) * 11u);
        NC_NOISE(g_guardHits);
    }
    return 0;
}

// ------------------------------------------------------------------ natives

jstring JNI_nHmac(JNIEnv* env, jobject, jstring data) {
    guardOrDie();
    if (!data) return env->NewStringUTF("");
    const char* utf = env->GetStringUTFChars(data, nullptr);
    if (!utf) return env->NewStringUTF("");
    uint8_t key[96];
    size_t keyLen = 0;
    loadKey(key, keyLen);
    uint8_t mac[32];
    nc::hmacSha256(key, keyLen, (const uint8_t*)utf, ncStrLen(utf), mac);
    SecureZeroMemory(key, sizeof(key));
    env->ReleaseStringUTFChars(data, utf);
    char hex[65];
    toHex(mac, 32, hex);
    SecureZeroMemory(mac, sizeof(mac));
    return env->NewStringUTF(hex);
}

void JNI_nSetKey(JNIEnv* env, jobject, jbyteArray arr) {
    if (!arr) { wipeKey(); return; }
    jsize n = env->GetArrayLength(arr);
    if (n <= 0) { wipeKey(); return; }
    uint8_t buf[256];
    if (n > 256) n = 256;
    env->GetByteArrayRegion(arr, 0, n, (jbyte*)buf);
    storeKey(buf, (size_t)n);
    SecureZeroMemory(buf, sizeof(buf));
}

jboolean JNI_nWatchdog(JNIEnv*, jobject) {
    return nc::environmentCompromised() ? JNI_FALSE : JNI_TRUE;
}

void JNI_nPanic(JNIEnv*, jobject, jint) {
    wipeKey();
    __fastfail(0xDEC0DE00u);
}

// ---- all-in-native key derivation. The seed never leaves this image, so
// pool ciphertext / packed class bodies cannot be decrypted without the DLL.
jbyteArray JNI_nPoolKey(JNIEnv* env, jobject, jint poolId, jint blobId) {
    guardOrDie();
    uint64_t st = buildSeed() ^ 0x4E4B4559ULL
        ^ (uint64_t)(int64_t)poolId * 0x9E3779B97F4A7C15ULL
        ^ ((uint64_t)(uint32_t)blobId << 32);
    uint8_t key[16];
    derive16(st, key);
    jbyteArray arr = env->NewByteArray(16);
    if (arr) env->SetByteArrayRegion(arr, 0, 16, (const jbyte*)key);
    SecureZeroMemory(key, sizeof(key));
    return arr;
}

jbyteArray JNI_nPackKey(JNIEnv* env, jobject, jint classIndex) {
    uint64_t st = buildSeed() ^ 0x504B4543ULL
        ^ (uint64_t)(int64_t)classIndex * 0x9E3779B97F4A7C15ULL;
    uint8_t key[16];
    derive16(st, key);
    jbyteArray arr = env->NewByteArray(16);
    if (arr) env->SetByteArrayRegion(arr, 0, 16, (const jbyte*)key);
    SecureZeroMemory(key, sizeof(key));
    return arr;
}

// ---- request signing: the whole canonical string assembly and the HMAC run
// here, against the key held in native memory. No plaintext key on the heap.
jstring JNI_nSign(JNIEnv* env, jobject, jstring ts, jstring nonce, jstring body) {
    guardOrDie();
    const char* cts = ts ? env->GetStringUTFChars(ts, nullptr) : nullptr;
    const char* cnonce = nonce ? env->GetStringUTFChars(nonce, nullptr) : nullptr;
    const char* cbody = body ? env->GetStringUTFChars(body, nullptr) : nullptr;

    uint8_t key[96];
    size_t keyLen = 0;
    loadKey(key, keyLen);

    Hmac h;
    h.init(key, keyLen);
    SecureZeroMemory(key, sizeof(key));
    h.putStr(cts);
    h.putByte('|');
    h.putStr(cnonce);
    h.putByte('|');
    h.putStr(cbody);

    uint8_t mac[32];
    h.finish(mac);
    if (ts) env->ReleaseStringUTFChars(ts, cts);
    if (nonce) env->ReleaseStringUTFChars(nonce, cnonce);
    if (body) env->ReleaseStringUTFChars(body, cbody);

    char hex[65];
    toHex(mac, 32, hex);
    SecureZeroMemory(mac, sizeof(mac));
    return env->NewStringUTF(hex);
}

// ---- integrity: hashing + comparison + the decision all happen natively.
jboolean JNI_nVerify(JNIEnv* env, jobject, jbyteArray data, jbyteArray expect) {
    if (!data || !expect) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    jsize elen = env->GetArrayLength(expect);
    if (elen != 32 || len < 0) return JNI_FALSE;
    uint8_t act[32];
    if (len > 0) {
        auto buf = (uint8_t*)ncAlloc((size_t)len);
        if (!buf) return JNI_FALSE;
        env->GetByteArrayRegion(data, 0, len, (jbyte*)buf);
        nc::Sha256 s;
        s.update(buf, (size_t)len);
        s.finish(act);
        ncFree(buf, (size_t)len);
    } else {
        nc::Sha256 s;
        s.finish(act);
    }
    uint8_t exp[32];
    env->GetByteArrayRegion(expect, 0, 32, (jbyte*)exp);
    int ok = ncEq(act, exp, 32);
    SecureZeroMemory(act, sizeof(act));
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ---- client attestation: both the app id and the app secret live here, as
// build-time encrypted literals (baked from obf.json via NC_APP_ID /
// NC_APP_SECRET). The canonical form "app|ts|nonce|path|body" is assembled
// and HMAC'd inside this image, so neither the secret nor the canonical shape
// exists in the jar.
#define NC_STRINGIFY_2(x) #x
#define NC_STRINGIFY(x) NC_STRINGIFY_2(x)

jstring JNI_nAppSign(JNIEnv* env, jobject, jstring ts, jstring nonce,
                     jstring path, jstring body) {
    guardOrDie();
    const char* t = ts ? env->GetStringUTFChars(ts, nullptr) : nullptr;
    const char* n = nonce ? env->GetStringUTFChars(nonce, nullptr) : nullptr;
    const char* p = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
    const char* b = body ? env->GetStringUTFChars(body, nullptr) : nullptr;

    char appId[32];
    NC_FILL(NC_STRINGIFY(NC_APP_ID), appId);
    char secret[128];
    NC_FILL(NC_STRINGIFY(NC_APP_SECRET), secret);

    Hmac h;
    h.init((const uint8_t*)secret, ncStrLen(secret));
    SecureZeroMemory(secret, sizeof(secret));
    h.putStr(appId); h.putByte('|');
    h.putStr(t); h.putByte('|');
    h.putStr(n); h.putByte('|');
    h.putStr(p); h.putByte('|');
    h.putStr(b);

    uint8_t mac[32];
    h.finish(mac);
    if (ts) env->ReleaseStringUTFChars(ts, t);
    if (nonce) env->ReleaseStringUTFChars(nonce, n);
    if (path) env->ReleaseStringUTFChars(path, p);
    if (body) env->ReleaseStringUTFChars(body, b);

    char hex[65];
    toHex(mac, 32, hex);
    SecureZeroMemory(mac, sizeof(mac));
    return env->NewStringUTF(hex);
}

JNINativeMethod METHODS[] = {
    {(char*)"nSetKey",  (char*)"([B)V",                       (void*)JNI_nSetKey},
    {(char*)"nHmac",    (char*)"(Ljava/lang/String;)Ljava/lang/String;", (void*)JNI_nHmac},
    {(char*)"nPoolKey", (char*)"(II)[B",                      (void*)JNI_nPoolKey},
    {(char*)"nPackKey", (char*)"(I)[B",                       (void*)JNI_nPackKey},
    {(char*)"nSign",    (char*)"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", (void*)JNI_nSign},
    {(char*)"nAppSign", (char*)"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", (void*)JNI_nAppSign},
    {(char*)"nVerify",  (char*)"([B[B)Z",                     (void*)JNI_nVerify},
    {(char*)"nWatchdog",(char*)"()Z",                         (void*)JNI_nWatchdog},
    {(char*)"nPanic",   (char*)"(I)V",                        (void*)JNI_nPanic},
};

} // namespace

// CRT-free image entry (linked with /ENTRY:NcEntry). With no C runtime there
// is nothing to initialize here beyond our own lock; the loader calls this
// directly as the mapped image's DllMain.
extern "C" BOOL APIENTRY NcEntry(HMODULE hModule, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(hModule);
        if (!g_lockInit) {
            InitializeCriticalSection(&g_lock);
            g_lockInit = true;
        }
    }
    return TRUE;
}

// Called by the loader right after manual mapping: bind natives against the
// live (possibly renamed) guard class, start the watchdog, then vanish.
extern "C" __declspec(dllexport)
int __cdecl nc_on_load(JNIEnv* env, jclass guardClass) {
    if (!env || !guardClass) return 0;
    if (!g_lockInit) {
        InitializeCriticalSection(&g_lock);
        g_lockInit = true;
    }
    if (env->RegisterNatives(guardClass, METHODS,
                             sizeof(METHODS) / sizeof(METHODS[0])) != 0) {
        return 0;
    }
    if (!CreateThread(nullptr, 0, watchdogLoop, nullptr, 0, nullptr)) return 0;
    eraseSelfHeaders();
    NC_NOISE(g_keyLen);
    return 1;
}
