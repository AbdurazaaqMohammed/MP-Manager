# Application.mk - Global NDK settings

# Target CPU architectures
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

# Minimum Android API level (5.0 Lollipop)
APP_PLATFORM := android-21

# This project is pure C – no C++ runtime needed
APP_STL := none

# Global compiler flags (same as in Android.mk)
APP_CFLAGS := -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden -O2