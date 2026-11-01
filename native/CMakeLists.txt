cmake_minimum_required(VERSION 3.20)
project(NocturneNative C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# JDK headers (for jni.h) — pass -DJDK_INCLUDE_DIR=<jdk>/include from Gradle
if(NOT JDK_INCLUDE_DIR)
    if(DEFINED ENV{JAVA_HOME})
        set(JDK_INCLUDE_DIR "$ENV{JAVA_HOME}/include")
    else()
        message(FATAL_ERROR "JDK_INCLUDE_DIR not set and JAVA_HOME missing")
    endif()
endif()

# ---- build seed + client credentials, baked into the core image ----
# The app secret comes from the configure process environment. Keep it out of
# CMake's cache and command line; the generated header stays in build output.
if(NOT DEFINED NC_BUILD_SEED)
    set(NC_BUILD_SEED 0)
endif()
if(NOT DEFINED NC_APP_ID)
    set(NC_APP_ID "nc-1")
endif()
if(NOT DEFINED ENV{NOCTURNE_APP_SECRET} OR "$ENV{NOCTURNE_APP_SECRET}" STREQUAL "")
    message(FATAL_ERROR "NOCTURNE_APP_SECRET is required for the native protected build")
endif()
if(NOT "$ENV{NOCTURNE_APP_SECRET}" MATCHES "^[A-Za-z0-9_-]+$")
    message(FATAL_ERROR "NOCTURNE_APP_SECRET must contain only letters, digits, underscores, or hyphens")
endif()
set(NC_APP_SECRET "$ENV{NOCTURNE_APP_SECRET}")
file(GENERATE
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/nc_secret_config.h"
    CONTENT "#pragma once\n#define NC_APP_SECRET ${NC_APP_SECRET}\n"
)

# ---------------- loader: tiny, ordinary DLL loaded via System.load ---------
add_library(nc_loader SHARED
    loader/nc_loader.c
)
target_include_directories(nc_loader PRIVATE ${JDK_INCLUDE_DIR} ${JDK_INCLUDE_DIR}/win32)
# static CRT, no vcruntime dependency; optimized for size, stack probes off
set_target_properties(nc_loader PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded"
)
target_compile_options(nc_loader PRIVATE /GS- /Os /utf-8 /wd4090 /JMC-)
target_link_libraries(nc_loader PRIVATE kernel32)

# ---------------- core: CRT-free, manually mapped, all secrets inside -------
add_library(nocturne_core SHARED
    core/nc_core.cpp
    core/nc_mem.cpp
    core/nc_sha256.cpp
    core/nc_antidbg.cpp
)
target_include_directories(nocturne_core PRIVATE
    ${JDK_INCLUDE_DIR} ${JDK_INCLUDE_DIR}/win32 ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_BINARY_DIR})
target_compile_definitions(nocturne_core PRIVATE
    UNICODE _UNICODE NC_BUILD
    NC_BUILD_SEED=${NC_BUILD_SEED}LL
    "NC_APP_ID=${NC_APP_ID}"
    )
# /NODEFAULTLIB + our own entry point: no C runtime is initialized, so manual
# mapping needs no TLS/CRT hand-holding and the image imports only Win32.
# /GS- removes the stack cookie (and thus __security_cookie / __security_check_cookie)
# /EHs-c- removes the frame handler requirement (nothing throws)
# /Gy keeps functions separable so the linker can drop unused code
target_compile_options(nocturne_core PRIVATE
    /O2 /GS- /EHs-c- /Gy /utf-8 /JMC- /Zc:threadSafeInit- /wd4090 /wd4530 /wd4577)
target_link_options(nocturne_core PRIVATE
    /NODEFAULTLIB /ENTRY:NcEntry /OPT:REF /OPT:ICF)
target_link_libraries(nocturne_core PRIVATE kernel32 user32 advapi32)

set_target_properties(nc_loader PROPERTIES OUTPUT_NAME "nc_loader")
set_target_properties(nocturne_core PROPERTIES OUTPUT_NAME "nocturne_core")
