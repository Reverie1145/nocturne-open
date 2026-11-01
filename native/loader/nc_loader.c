// nc_loader — minimal bootstrap DLL, loaded via System.load from a decrypted
// temp file. Its only job: manually map the core image from memory (sections,
// relocations, imports, TLS callbacks, exception table, entry point), hand over
// to nc_on_load, then erase every trace of both images.
//
// Manual mapping means the core never appears in the PEB module list and its
// PE headers are gone after init — module-walking dumpers and LoadLibrary-based
// tools cannot see or name it.
//
// Exposes one name-bound JNI method: Java_cn_reverie_nocturne_NBootstrap_bind
// (NBootstrap is kept unrenamed by the obfuscation config).
#include <jni.h>
#include <windows.h>
#include <stdlib.h>
#include <string.h>

static PIMAGE_SECTION_HEADER sectionOf(PIMAGE_NT_HEADERS nt, WORD i) {
    return (PIMAGE_SECTION_HEADER)((BYTE *)&nt->OptionalHeader
        + nt->FileHeader.SizeOfOptionalHeader + i * sizeof(IMAGE_SECTION_HEADER));
}

static HMODULE selfBase(void *addr) {
    HMODULE h = NULL;
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
        | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, (LPCWSTR)addr, &h);
    return h;
}

static void eraseHeaders(BYTE *base) {
    DWORD old = 0;
    if (VirtualProtect(base, 0x1000, PAGE_READWRITE, &old)) {
        SecureZeroMemory(base, 0x1000);
        VirtualProtect(base, 0x1000, PAGE_NOACCESS, &old);
    }
}

// Remove the loader itself from the PEB module list so even the bootstrap is
// invisible to module-enumeration. Raw x64 offsets (LDR_DATA_TABLE_ENTRY).
static void unlinkSelf(void *addr) {
    HMODULE h = selfBase(addr);
    if (!h) return;
    BYTE *peb = (BYTE *)__readgsqword(0x60);
    BYTE *ldr = *(BYTE **)(peb + 0x18);
    if (!ldr) return;
    LIST_ENTRY *head = (LIST_ENTRY *)(ldr + 0x20); /* InMemoryOrderModuleList */
    LIST_ENTRY *cur = head->Flink;
    while (cur != head) {
        LIST_ENTRY *next = cur->Flink;
        HMODULE dllBase = *(HMODULE *)((BYTE *)cur + 0x20);
        if (dllBase == h) {
            cur->Blink->Flink = cur->Flink;
            cur->Flink->Blink = cur->Blink;
        }
        cur = next;
    }
}

static void *findExport(BYTE *base, const char *name) {
    PIMAGE_DOS_HEADER dos = (PIMAGE_DOS_HEADER)base;
    if (dos->e_magic != IMAGE_DOS_SIGNATURE) return NULL;
    PIMAGE_NT_HEADERS nt = (PIMAGE_NT_HEADERS)(base + dos->e_lfanew);
    DWORD rva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT].VirtualAddress;
    if (!rva) return NULL;
    PIMAGE_EXPORT_DIRECTORY exp = (PIMAGE_EXPORT_DIRECTORY)(base + rva);
    DWORD *names = (DWORD *)(base + exp->AddressOfNames);
    WORD *ords = (WORD *)(base + exp->AddressOfNameOrdinals);
    DWORD *funcs = (DWORD *)(base + exp->AddressOfFunctions);
    for (DWORD i = 0; i < exp->NumberOfNames; i++) {
        if (strcmp((const char *)(base + names[i]), name) == 0) {
            return base + funcs[ords[i]];
        }
    }
    return NULL;
}

// Windows 8.1+ is required: the mapper relies on the modern loader layout.
static BOOL osTooOld(void) {
    typedef LONG (WINAPI *RtlGetVersion_t)(PRTL_OSVERSIONINFOW);
    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    if (!ntdll) return FALSE;
    RtlGetVersion_t fn = (RtlGetVersion_t)(void *)GetProcAddress(ntdll, "RtlGetVersion");
    if (!fn) return FALSE;
    RTL_OSVERSIONINFOW vi = {0};
    vi.dwOSVersionInfoSize = sizeof(vi);
    if (fn(&vi) != 0) return FALSE;
    if (vi.dwMajorVersion < 6) return TRUE;
    if (vi.dwMajorVersion == 6 && vi.dwMinorVersion < 3) return TRUE;
    return FALSE;
}

static jboolean mapImage(BYTE *image, SIZE_T size, BYTE **outBase) {
    PIMAGE_DOS_HEADER dos = (PIMAGE_DOS_HEADER)image;
    if (dos->e_magic != IMAGE_DOS_SIGNATURE) return JNI_FALSE;
    PIMAGE_NT_HEADERS nt = (PIMAGE_NT_HEADERS)(image + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE) return JNI_FALSE;
    if (nt->FileHeader.Machine != IMAGE_FILE_MACHINE_AMD64) return JNI_FALSE;
    if (nt->OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR64_MAGIC) return JNI_FALSE;
    // the payload must actually contain the whole image
    if (nt->OptionalHeader.SizeOfImage == 0
        || nt->OptionalHeader.SizeOfHeaders > size) return JNI_FALSE;

    BYTE *base = (BYTE *)VirtualAlloc(NULL, nt->OptionalHeader.SizeOfImage,
                                      MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!base) return JNI_FALSE;
    memcpy(base, image, nt->OptionalHeader.SizeOfHeaders);

    for (WORD i = 0; i < nt->FileHeader.NumberOfSections; i++) {
        PIMAGE_SECTION_HEADER s = sectionOf(nt, i);
        if (s->PointerToRawData && s->SizeOfRawData) {
            SIZE_T off = (SIZE_T)s->PointerToRawData + s->SizeOfRawData;
            if (off > size) { VirtualFree(base, 0, MEM_RELEASE); return JNI_FALSE; }
            memcpy(base + s->VirtualAddress, image + s->PointerToRawData, s->SizeOfRawData);
        }
    }

    // ---- relocations (x64: DIR64 only) ----
    DWORD relocRva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_BASERELOC].VirtualAddress;
    DWORD relocSize = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_BASERELOC].Size;
    if (relocRva && (ULONG_PTR)base != nt->OptionalHeader.ImageBase) {
        ULONG_PTR delta = (ULONG_PTR)base - nt->OptionalHeader.ImageBase;
        DWORD offset = 0;
        while (offset < relocSize) {
            PIMAGE_BASE_RELOCATION r = (PIMAGE_BASE_RELOCATION)(base + relocRva + offset);
            if (!r->SizeOfBlock) break;
            if (offset + r->SizeOfBlock > relocSize) break;
            WORD *entries = (WORD *)((BYTE *)r + sizeof(IMAGE_BASE_RELOCATION));
            DWORD count = (r->SizeOfBlock - sizeof(IMAGE_BASE_RELOCATION)) / sizeof(WORD);
            for (DWORD i = 0; i < count; i++) {
                WORD type = entries[i] >> 12, off = entries[i] & 0x0FFF;
                if (type == IMAGE_REL_BASED_DIR64) {
                    *(ULONG_PTR *)(base + r->VirtualAddress + off) += delta;
                } else if (type == IMAGE_REL_BASED_HIGHLOW) {
                    *(DWORD *)(base + r->VirtualAddress + off) += (DWORD)delta;
                }
            }
            offset += r->SizeOfBlock;
        }
    }

    // ---- imports ----
    DWORD impRva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;
    if (impRva) {
        for (PIMAGE_IMPORT_DESCRIPTOR imp = (PIMAGE_IMPORT_DESCRIPTOR)(base + impRva);
             imp->Name; imp++) {
            HMODULE dll = LoadLibraryA((const char *)(base + imp->Name));
            if (!dll) { VirtualFree(base, 0, MEM_RELEASE); return JNI_FALSE; }
            PIMAGE_THUNK_DATA oft = imp->OriginalFirstThunk
                ? (PIMAGE_THUNK_DATA)(base + imp->OriginalFirstThunk)
                : (PIMAGE_THUNK_DATA)(base + imp->FirstThunk);
            PIMAGE_THUNK_DATA ft = (PIMAGE_THUNK_DATA)(base + imp->FirstThunk);
            for (; oft->u1.AddressOfData; oft++, ft++) {
                void *fn = IMAGE_SNAP_BY_ORDINAL(oft->u1.Ordinal)
                    ? (void *)GetProcAddress(dll, (LPCSTR)IMAGE_ORDINAL(oft->u1.Ordinal))
                    : (void *)GetProcAddress(dll,
                        (LPCSTR)((PIMAGE_IMPORT_BY_NAME)(base + oft->u1.AddressOfData))->Name);
                if (!fn) { VirtualFree(base, 0, MEM_RELEASE); return JNI_FALSE; }
                ft->u1.Function = (ULONGLONG)fn;
            }
        }
    }

    // ---- section protections ----
    for (WORD i = 0; i < nt->FileHeader.NumberOfSections; i++) {
        PIMAGE_SECTION_HEADER s = sectionOf(nt, i);
        DWORD protect = PAGE_READONLY;
        DWORD c = s->Characteristics;
        if (c & IMAGE_SCN_MEM_EXECUTE) {
            protect = (c & IMAGE_SCN_MEM_WRITE) ? PAGE_EXECUTE_READWRITE : PAGE_EXECUTE_READ;
        } else if (c & IMAGE_SCN_MEM_WRITE) {
            protect = PAGE_READWRITE;
        }
        DWORD old;
        VirtualProtect(base + s->VirtualAddress, s->Misc.VirtualSize, protect, &old);
    }

    // ---- exception table (x64 table-based SEH for the mapped image) ----
    DWORD pdataRva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXCEPTION].VirtualAddress;
    DWORD pdataSize = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXCEPTION].Size;
    if (pdataRva && pdataSize) {
        typedef BOOLEAN (WINAPI *AddFn_t)(PRUNTIME_FUNCTION, DWORD, ULONGLONG);
        AddFn_t addFn = (AddFn_t)(void *)GetProcAddress(GetModuleHandleW(L"kernel32.dll"),
                                                       "RtlAddFunctionTable");
        if (addFn) {
            addFn((PRUNTIME_FUNCTION)(base + pdataRva), pdataSize / sizeof(RUNTIME_FUNCTION),
                  (ULONGLONG)base);
        }
    }

    // ---- TLS callbacks (present only if the core ever gains thread statics;
    // running them is required for correctness when they exist) ----
    DWORD tlsRva = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_TLS].VirtualAddress;
    if (tlsRva) {
        PIMAGE_TLS_DIRECTORY tls = (PIMAGE_TLS_DIRECTORY)(base + tlsRva);
        if (tls->AddressOfCallBacks) {
            PIMAGE_TLS_CALLBACK *cb = (PIMAGE_TLS_CALLBACK *)tls->AddressOfCallBacks;
            for (; *cb; ++cb) (*cb)((PVOID)base, DLL_PROCESS_ATTACH, NULL);
        }
    }

    // ---- entry point ----
    if (nt->OptionalHeader.AddressOfEntryPoint) {
        BOOL (WINAPI *dllMain)(HMODULE, DWORD, LPVOID) =
            (BOOL (WINAPI *)(HMODULE, DWORD, LPVOID))(base + nt->OptionalHeader.AddressOfEntryPoint);
        if (!dllMain((HMODULE)base, DLL_PROCESS_ATTACH, NULL)) {
            VirtualFree(base, 0, MEM_RELEASE);
            return JNI_FALSE;
        }
    }

    *outBase = base;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_cn_reverie_nocturne_NBootstrap_bind(
        JNIEnv *env, jobject self, jbyteArray core, jclass guard) {
    if (!env || !core || !guard) return JNI_FALSE;
    if (osTooOld()) return JNI_FALSE;
    jsize len = (*env)->GetArrayLength(env, core);
    if (len <= 0) return JNI_FALSE;
    BYTE *image = (BYTE *)malloc((size_t)len);
    if (!image) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, core, 0, len, (jbyte *)image);

    BYTE *base = NULL;
    jboolean ok = mapImage(image, (SIZE_T)len, &base);
    if (image) memset(image, 0, (size_t)len);
    free(image);
    if (!ok) return JNI_FALSE;

    // hand over: the core registers its natives and starts its own watchdog
    char sym[16];
    sym[0] = 'n'; sym[1] = 'c'; sym[2] = '_'; sym[3] = 'o'; sym[4] = 'n';
    sym[5] = '_'; sym[6] = 'l'; sym[7] = 'o'; sym[8] = 'a'; sym[9] = 'd'; sym[10] = 0;
    int (WINAPI *onLoad)(JNIEnv *, jclass) =
        (int (WINAPI *)(JNIEnv *, jclass))findExport(base, sym);
    if (!onLoad || !onLoad(env, guard)) {
        VirtualFree(base, 0, MEM_RELEASE);
        return JNI_FALSE;
    }

    // vanish: kill the mapped image's headers and the loader's own traces
    eraseHeaders(base);
    void *selfAddr = (void *)&Java_cn_reverie_nocturne_NBootstrap_bind;
    unlinkSelf(selfAddr);
    eraseHeaders((BYTE *)selfBase(selfAddr));
    return JNI_TRUE;
}