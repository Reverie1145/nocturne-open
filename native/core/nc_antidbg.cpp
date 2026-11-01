// Runtime environment probes. Called on a native-owned timer thread and from
// the integrity path; any hit is a kill signal.
//
// CRT-free: only Win32 calls, intrinsics and header-only integer math.
#include <windows.h>
#include <intrin.h>
#include <cstdint>
#include "nc_obf.h"

namespace nc {

namespace {

typedef LONG(NTAPI* NtQIP_t)(HANDLE, ULONG, PVOID, ULONG, PULONG);

// NtQueryInformationProcess is resolved by hashed-name lookup instead of a
// plaintext import so the import table does not advertise the probe.
NtQIP_t ntQip() {
    static NtQIP_t cached = nullptr;
    if (cached) return cached;
    char want[32];
    NC_FILL("NtQueryInformationProcess", want);
    unsigned wantHash = 0x811C9DC5u;
    for (int i = 0; want[i]; i++) wantHash = (wantHash ^ (unsigned char)want[i]) * 0x01000193u;
    char dll[16];
    NC_FILL("ntdll.dll", dll);
    HMODULE h = GetModuleHandleA(dll);
    if (!h) return nullptr;
    auto dos = (PIMAGE_DOS_HEADER)h;
    auto nt = (PIMAGE_NT_HEADERS)((BYTE*)h + dos->e_lfanew);
    DWORD rva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT].VirtualAddress;
    if (!rva) return nullptr;
    auto exp = (PIMAGE_EXPORT_DIRECTORY)((BYTE*)h + rva);
    auto names = (DWORD*)((BYTE*)h + exp->AddressOfNames);
    auto ords = (WORD*)((BYTE*)h + exp->AddressOfNameOrdinals);
    auto funcs = (DWORD*)((BYTE*)h + exp->AddressOfFunctions);
    for (DWORD i = 0; i < exp->NumberOfNames; i++) {
        const char* n = (const char*)h + names[i];
        unsigned hv = 0x811C9DC5u;
        for (int j = 0; n[j]; j++) hv = (hv ^ (unsigned char)n[j]) * 0x01000193u;
        if (hv == wantHash) {
            cached = (NtQIP_t)((BYTE*)h + funcs[ords[i]]);
            return cached;
        }
    }
    return nullptr;
}

// PEB.BeingDebugged / NtGlobalFlag — read straight out of the TEB.
bool pebFlags() {
    BYTE* peb = (BYTE*)__readgsqword(0x60);
    if (!peb) return false;
    if (peb[0x02] != 0) return true;                    // BeingDebugged
    DWORD globalFlag = *(DWORD*)(peb + 0xBC);           // NtGlobalFlag (x64)
    // FLG_HEAP_ENABLE_TAIL_CHECK | FLG_HEAP_ENABLE_FREE_CHECK | FLG_HEAP_VALIDATE_PARAMETERS
    if ((globalFlag & 0x70) != 0) return true;
    return false;
}

// ProcessDebugPort / Flags / ObjectHandle through ntdll.
bool ntProbes() {
    NtQIP_t f = ntQip();
    if (!f) return false;
    HANDLE self = GetCurrentProcess();
    ULONG_PTR port = 0;
    if (f(self, 7, &port, sizeof(port), nullptr) == 0 && port != 0) return true;
    ULONG flags = 0;
    if (f(self, 0x1f, &flags, sizeof(flags), nullptr) == 0 && flags == 0) return true;
    ULONG obj = 0;
    if (f(self, 0x1e, &obj, sizeof(obj), nullptr) == 0 && obj != 0) return true;
    return false;
}

// Hardware breakpoints live in DR0..DR3 on the running thread.
bool hwBreakpoints() {
    CONTEXT ctx{};
    ctx.ContextFlags = CONTEXT_DEBUG_REGISTERS;
    if (!GetThreadContext(GetCurrentThread(), &ctx)) return false;
    return ctx.Dr0 != 0 || ctx.Dr1 != 0 || ctx.Dr2 != 0 || ctx.Dr3 != 0;
}

bool remoteDebugger() {
    BOOL present = FALSE;
    if (CheckRemoteDebuggerPresent(GetCurrentProcess(), &present) && present) return true;
    return IsDebuggerPresent() != FALSE;
}

// Heap force-flags: a debugger that sets them leaves the low bits dirty.
bool heapFlags() {
    BYTE* peb = (BYTE*)__readgsqword(0x60);
    if (!peb) return false;
    BYTE* heap = *(BYTE**)(peb + 0x30);                 // PEB.ProcessHeap
    if (!heap) return false;
    DWORD flags = *(DWORD*)(heap + 0x70);               // HEAP.Flags
    DWORD force = *(DWORD*)(heap + 0x74);               // HEAP.ForceFlags
    return (flags & ~2u) != 0 || force != 0;
}

// rdtsc delta over a fixed workload. A single-stepping or instrumented run
// stretches the delta far beyond a wide physical budget.
bool timingAnomaly() {
    unsigned aux = 0;
    uint64_t t0 = __rdtscp(&aux);
    volatile uint32_t s = 0;
    for (uint32_t i = 1; i <= 4096u; i++) {
        s = s + (i * 2654435761u) ^ (i >> 3);
        if (!opq_even(s)) s ^= 0u;                      // opaque, never taken
    }
    uint32_t keep = s;
    uint64_t t1 = __rdtscp(&aux);
    if (keep == 0xDEADBEEFu) return true;               // keeps `s` observable
    // 4096 dependent ops stay well under this on any real machine.
    return (t1 - t0) > 20000000ull;
}

// A breakpoint planted onto one of our own function prologues leaves 0xCC
// where the real instruction bytes belong. Checking a fixed set of known
// entry points is precise: unlike scanning a whole code section it cannot be
// fooled — or triggered — by a legitimate 0xCC byte inside an immediate.
bool breakpointPlanted() {
    const void* probes[] = {
        (const void*)&breakpointPlanted,
        (const void*)&ntProbes,
        (const void*)&hwBreakpoints,
        (const void*)&remoteDebugger,
        (const void*)&pebFlags,
    };
    for (unsigned i = 0; i < sizeof(probes) / sizeof(probes[0]); i++) {
        const uint8_t* p = (const uint8_t*)probes[i];
        if (p == nullptr) return true;
        // a prologue that is entirely int3 / all-zero means someone patched it
        uint8_t cc = 0, zero = 0;
        for (int k = 0; k < 8; k++) {
            if (p[k] == 0xCC) cc++;
            if (p[k] == 0x00) zero++;
        }
        if (cc > 2 || zero == 8) return true;
    }
    return false;
}

// Weak signals: real debugger correlates, but a busy machine or an unusual
// Windows build can trip them on its own. They are reported, never fatal —
// a false positive here would kill a legitimate client.
void reportWeakSignals() {
    if (heapFlags() || timingAnomaly()) {
        OutputDebugStringA("NC: weak environment signal flagged (non-fatal)\n");
    }
}

} // namespace

// Cheap probe set — runs on every JNI entry, so it must stay allocation-free
// and bounded (no memory scans).
bool environmentCompromisedFast() {
    NC_NOISE(environmentCompromisedFast);
    bool hit = false;
    hit |= pebFlags();
    hit |= remoteDebugger();
    hit |= hwBreakpoints();
    hit |= ntProbes();
    NC_NOISE(hit);
    return hit;
}

// Full probe set — adds the breakpoint-prologue check and the weak-signal
// report. Driven by the native watchdog thread and the explicit nWatchdog
// query, never by a per-call JNI entry.
bool environmentCompromised() {
    NC_NOISE(environmentCompromised);
    bool hit = environmentCompromisedFast();
    hit |= breakpointPlanted();
    reportWeakSignals();
    NC_NOISE(hit);
    return hit;
}

} // namespace nc