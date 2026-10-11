#include <jni.h>
#include <zlib.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdlib>
#include <android/log.h>

#define LOG_TAG "MP-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct TarEntry {
    std::string name;
    long size;
    long mtime;
    bool isDir;
};

// Baca header TAR (512 bytes)
bool readTarHeader(gzFile file, TarEntry& entry) {
    char header[512];
    int read = gzread(file, header, 512);
    if (read != 512) return false;
    
    // Cek empty block (akhir arsip)
    bool allZero = true;
    for (int i = 0; i < 512; i++) {
        if (header[i] != 0) { allZero = false; break; }
    }
    if (allZero) return false;
    
    // Parse name (offset 0-99)
    size_t nameLen = strnlen(header, 100);
    entry.name = std::string(header, nameLen);
    
    // Parse size (offset 124-135, octal)
    char sizeStr[13];
    memcpy(sizeStr, header + 124, 12);
    sizeStr[12] = '\0';
    entry.size = strtol(sizeStr, nullptr, 8);
    
    // Parse mtime (offset 136-147, octal)
    char mtimeStr[13];
    memcpy(mtimeStr, header + 136, 12);
    mtimeStr[12] = '\0';
    entry.mtime = strtol(mtimeStr, nullptr, 8);
    
    // Cek direktori (offset 156, typeflag '5' = directory)
    entry.isDir = (header[156] == '5');
    
    return true;
}

// JNI: list .tar.gz entries — return String[] format: "path|size|mtime|isDir"
extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_abdurazaaqmohammed_utils_NativeArchiveReader_listTarGzNative(
    JNIEnv* env, jclass, jstring jpath) {
    
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("listTarGzNative: %s", path);
    
    gzFile file = gzopen(path, "rb");
    if (!file) {
        LOGE("Failed to open: %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return nullptr;
    }
    
    // Buffer gede biar cepet
    gzbuffer(file, 128 * 1024);
    
    std::vector<TarEntry> entries;
    while (true) {
        TarEntry entry;
        if (!readTarHeader(file, entry)) break;
        if (!entry.name.empty()) entries.push_back(entry);
        
        // Skip data + padding (512-byte blocks)
        long dataSize = ((entry.size + 511) / 512) * 512;
        if (dataSize > 0) {
            gzseek(file, dataSize, SEEK_CUR);
        }
    }
    
    gzclose(file);
    env->ReleaseStringUTFChars(jpath, path);
    
    LOGI("Found %zu entries", entries.size());
    
    // Bikin String[] hasil
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(entries.size(), stringClass, nullptr);
    
    for (size_t i = 0; i < entries.size(); i++) {
        const TarEntry& e = entries[i];
        // Format: "name|size|mtime|isDir"
        std::string s = e.name + "|" +
                       std::to_string(e.size) + "|" +
                       std::to_string(e.mtime) + "|" +
                       (e.isDir ? "1" : "0");
        jstring js = env->NewStringUTF(s.c_str());
        env->SetObjectArrayElement(result, i, js);
        env->DeleteLocalRef(js);
    }
    
    return result;
}

// JNI: cek native available
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_abdurazaaqmohammed_utils_NativeArchiveReader_isNativeAvailable(
    JNIEnv* env, jclass) {
    return JNI_TRUE;
}
