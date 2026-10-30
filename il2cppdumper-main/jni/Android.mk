# Android.mk - Build script for libil2cppdumper.so
# Place this file inside the jni/ folder of your project.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Module name – the shared library will be libil2cppdumper.so
LOCAL_MODULE := il2cppdumper

# Source files to compile
LOCAL_SRC_FILES := \
	../src/jni_entry.c \
	../src/il2cpp_dump.c \
	../src/il2cpp_api.c \
	../src/elf_sym_find.c \
	../src/script_writer.c

# Compiler flags

# -fvisibility=hidden prevents symbol clashes with the host app
# -O2 gives reasonable speed without excessive code size
LOCAL_CFLAGS := -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden -O2

# Uncomment the next line to enable debug logging (LOGD macro)
# LOCAL_CFLAGS += -DIL2CPP_DEBUG

# Linker flags – optional build‑id for debugging
LOCAL_LDFLAGS := -Wl,--build-id=sha1

# Libraries needed at runtime (logcat and dynamic linker)
LOCAL_LDLIBS := -llog -ldl

# Build as a shared library
include $(BUILD_SHARED_LIBRARY)