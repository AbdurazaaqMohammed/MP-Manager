#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "MP-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_abdurazaaqmohammed_utils_NativeTest_helloFromC(
    JNIEnv* env, jclass) {
    LOGI("Native: helloFromC called");
    return env->NewStringUTF("Hello from C native!");
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_abdurazaaqmohammed_utils_NativeTest_getNativeInfo(
    JNIEnv* env, jclass) {
    std::string info = "MP Native v1.0 | ";
    info += __VERSION__;
    return env->NewStringUTF(info.c_str());
}
