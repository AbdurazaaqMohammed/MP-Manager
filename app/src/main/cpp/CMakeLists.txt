# Native zstd library (vendored official sources, BSD-licensed) plus the
# JNI bridge used by ZstdBridge.java. Built for all configured ABIs.
cmake_minimum_required(VERSION 3.22.1)
project(mp_zstd C)

set(ZSTD_DIR ${CMAKE_CURRENT_SOURCE_DIR}/zstd)

file(GLOB ZSTD_COMMON   ${ZSTD_DIR}/common/*.c)
file(GLOB ZSTD_COMPRESS ${ZSTD_DIR}/compress/*.c)
file(GLOB ZSTD_DECOMPRESS ${ZSTD_DIR}/decompress/*.c)

add_library(mp_zstd SHARED
        zstd_bridge.c
        ${ZSTD_COMMON}
        ${ZSTD_COMPRESS}
        ${ZSTD_DECOMPRESS})

target_include_directories(mp_zstd PRIVATE
        ${ZSTD_DIR}
        ${ZSTD_DIR}/common
        ${ZSTD_DIR}/compress
        ${ZSTD_DIR}/decompress)

# The upstream Makefiles define these for release builds; keep sizes small.
# ZSTD_DISABLE_ASM: use the portable C Huffman decoder everywhere (the .S
# optimiser is x86-64-only and not vendored; the C path works on all ABIs).
target_compile_definitions(mp_zstd PRIVATE
        NDEBUG=1
        XXH_NAMESPACE=ZSTD_
        ZSTD_DISABLE_ASM=1
        ZSTD_MULTITHREAD=1)

target_compile_options(mp_zstd PRIVATE
        -Oz
        -ffunction-sections
        -fdata-sections
        -fvisibility=hidden)

target_link_options(mp_zstd PRIVATE -Wl,--gc-sections)
target_link_libraries(mp_zstd log)
