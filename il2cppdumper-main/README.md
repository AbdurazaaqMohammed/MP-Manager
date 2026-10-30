# Il2Cpp Self‑Dumper

## 📖 Overview

**A **Zygisk-free, root-free** il2cpp runtime dumper that runs **inside the target
process itself**. load it via `system.loadlibrary()` and it automatically finds
`libil2cpp.so`, resolves all Unity il2cpp APIs, and writes a `dump.cs` to the
app's own files directory.**

*Extracts a C#‑like pseudo‑source of every managed type (classes, structs, enums, interfaces) along with field offsets and method addresses, directly from the live IL2CPP runtime memory.*

---

## 📋 Table of Contents

1. [Special Advantages](#-1-special-advantages)  
   1.1 [Encrypted Metadata Support](#-encrypted-metadata-support)  
   1.2 [Container / Virtual Machine Native Support](#-container--vm-native-support)  
   1.3 [Crash-Safe Operation](#-crash-safe-operation)  
   1.4 [Zero Impact on App Behaviour](#-zero-impact-on-app-behaviour)  
   1.5 [Multi-Strategy Enumeration](#-multi-strategy-enumeration)  
   1.6 [Script Generation for Reverse Engineering Tools](#-script-generation-for-reverse-engineering-tools)
2. [Limitations](#-2-limitations)
3. [Security Perspective](#%EF%B8%8F-3-security-perspective)  
   3.1 [What This Library Can Access](#-what-this-library-can-access)  
   3.2 [What This Library Does NOT Do](#-what-this-library-does-not-do)  
   3.3 [Policy Compliance](#%EF%B8%8F-policy-compliance)
4. [Output Locations & Fallback Order](#-4-output-locations--fallback-order)
5. [Customising the Output Path](#-5-customising-the-output-path)
6. [Usage Guide](#-6-usage-guide)  
   6.1 [Prerequisites](#-prerequisites)  
   6.2 [Build Instructions](#%EF%B8%8F-build-instructions)  
   6.3 [Implementation Instructions](#-implementation-instructions)
7. [Acknowledgments](#-7-acknowledgments)

---

## 🚀 1. Special Advantages

### ✅ Encrypted Metadata Support

The library **does not decrypt anything**. It waits until IL2CPP itself has loaded the decrypted metadata into memory, then dumps the live state. Works with any encryption scheme — no key extraction, no file parsing.

### ✅ Container / VM Native Support

>🔥 **Special Feature**
>Works inside **any container or virtual machine**. Dumps official apps/games without tampering their integrity.

>- Implement the library inside the container
>- Clone and run the target app inside the container
>- The `dump.cs` is written directly to a shared folder, accessible from the host

### ✅ Crash-Safe Operation

All IL2CPP calls are wrapped with a signal handler (`SIGSEGV`/`SIGBUS`) on an alternate stack. If a function pointer is wrong, the crash is caught, logged, and the dump thread exits cleanly — the host app never crashes.

### ✅ Zero Impact on App Behaviour

- ❎ No method hooking
- ❎ No bytecode modification
- 🧵 The dump thread runs detached at default `SCHED_OTHER` priority
- ❄️ All polling loops use `sleep(1)` — no CPU busy‑wait, no battery drain
- 😑 The library loads as a passive observer, the game’s FPS, network, and UI are unaffected

### ✅ Multi-Strategy Enumeration

The dumper automatically tries three enumeration methods, failing gracefully to the next if one is unavailable:

1. **Image‑based** – `il2cpp_image_get_class` (fast, well‑structured)
2. **Callback‑based** – `il2cpp_class_for_each` (newer IL2CPP builds)
3. **Reflection‑based** – `Assembly.Load` + `GetTypes()` (slow but guaranteed to work even on stripped/obfuscated builds)

This guarantees a successful dump on Unity versions from 2017.4 to the latest 6000.x releases.

### ✅ Script Generation for Reverse Engineering Tools

- **IDA, Ghidra, Binary Ninja, radare2 scripts** – Generated alongside `dump.cs`. Each script renames methods to C# names, adds full metadata comments (dll, namespace, class, return type, parameters), and **renames parameters**. Ghidra additionally maps primitive types and replaces prototypes.
- **Zero manual mapping** – No need to cross‑reference RVAs or write custom importers. Just load your binary and run the script.
- **Usage** – Each script’s header explains its usage. Follow the workflow from there.

---

## ⛔ 2. Limitations

- **Generic type argument display**: Generic classes appear as `List\`1` not `List<int>`. Full instantiation details require `Il2CppGenericInst` parsing, which is planned but not yet implemented.
- **XOR/custom‑encrypted runtime symbols**: If `methodPointer` values are obfuscated in memory (not just the metadata file), the RVA/VA comments will show incorrect addresses.
- **Unity versions before 2017.1**: Very old IL2CPP ABIs may have different struct layouts; in such cases the library produces a best‑effort partial dump.
- **Deobfuscation**: The dumper does **not** rename symbols, decrypt strings, or restore control flow. It outputs raw metadata exactly as the engine sees it.

---

## 🛡️ 3. Security Perspective

### 🔑 What This Library Can Access

The library operates **entirely within the existing security boundary** of the target process. It:
- ✅ Reads `/proc/self/maps` and `/proc/self/cmdline` — accessible to every process by design
- ✅ Reads the process’s own mapped memory — it is inside the process
- ✅ Writes to the app’s own files directory — storage the process already owns
- ✅ Makes JNI calls using the app’s own `JavaVM` — standard Android API

**No system call is made that the app itself could not make.**

### ❎ What This Library Does NOT Do

- ❌ Does not escalate privileges
- ❌ Does not communicate over the network
- ❌ Does not access other apps’ data
- ❌ Does not modify **any** memory (no hooks, no patches, no code injection)
- ❌ Does not persist after the process exits
- ❌ Does not request additional Android permissions

### 🏛️ Policy Compliance

Because the library loads as part of the app’s own process under the app’s UID, it operates entirely within Android’s standard application sandbox. No security policy is violated. The library is functionally equivalent to an app examining its own runtime state — a normal and permitted operation.

---

## 📁 4. Output Locations & Fallback Order

The library tries the following directories **in order**. If one fails (e.g., permission denied, mount namespace issue), it moves to the next. The final file is always `dump.cs`.

| Step | Strategy                                   | Example path                                                   |
|------|--------------------------------------------|----------------------------------------------------------------|
| 1    | JNI `Context.getFilesDir()`                | `/data/data/<pkg>/files/il2cpp_dump/`                          |
| 2    | Hardcoded `/data/data/<pkg>/files/`        | `/data/data/<pkg>/files/il2cpp_dump/`                          |
| 3    | Scoped external dir (own `Android/data`)   | `/storage/emulated/0/Android/data/<pkg>/files/il2cpp_dump/`    |
| 4    | `/sdcard/il2cpp_dump/`                     | `/sdcard/il2cpp_dump/`                                         |

Steps 3‑4 only run when the app's private directory cannot be written. The
private directory is the intended home: MP-Manager's injected
`DocumentsProvider` exposes it as the `data` root, so the dump is readable
from a file manager without root.

---

## 📁 5. Customising the Output Path

You can hardcode a custom directory by editing `jni_entry.c`.

For example, to always write to:
`/storage/emulated/0/Android/data/com.example.vm/rootfs/storage/emulated/0/il2cpp_dump/<package>/dump.cs`

Replace the `output_dir` construction block in the `dump_thread()` function with:

```c
char output_dir[PATH_MAX] = {0};

if (pkg[0] && strcmp(pkg, "unknown") != 0) {
    snprintf(output_dir, sizeof(output_dir),
             "/storage/emulated/0/Android/data/com.example.vm/rootfs"
             "/storage/emulated/0/il2cpp_dump/%s", pkg);
    mkdir_p(output_dir);
    LOGI("Output dir (custom): %s", output_dir);
} else {
    snprintf(output_dir, sizeof(output_dir),
             "/storage/emulated/0/Android/data/com.example.vm/rootfs"
             "/storage/emulated/0/il2cpp_dump/unknown");
    mkdir_p(output_dir);
    LOGW("Output dir (custom, unknown pkg): %s", output_dir);
}
```

Rebuild the library and the dump will appear exactly at that hardcoded path.

---

## 💡 6. Usage Guide

### 📝 Prerequisites

- Android NDK (for native compilation)
- Termux app (for on-device building)

> 💡 Tip:
> If you encounter issues with NDK builds or Termux setup, you can use the GitHub Actions workflow to auto-compile the source — no manual setup needed, handles dependencies, and builds for all architectures.

### 🛠️ Build Instructions

```bash
# Clone the repository
git clone https://github.com/muhammadrizwan87/il2cppdumper.git
cd il2cppdumper

# Set NDK path
export NDK_HOME=/path/to/your/ndk
# export NDK_HOME=/data/data/com.termux/files/home/android-sdk/ndk/24.0.8215888

# Build for all architectures
$NDK_HOME/ndk-build NDK_PROJECT_PATH=. NDK_APPLICATION_MK=./jni/Application.mk

# Output will be in libs/
ls libs/
# armeabi-v7a/ arm64-v8a/ x86/ x86_64/
```

### 🔀 Implementation Instructions

The native library must be loaded **as early as possible** — ideally in the app’s `Application` class static initialiser or its Smali equivalent.

#### Option A: Patch an existing APK (Smali)

**1. Smali code to load the library:**
```smali
.method static constructor <clinit>()V
    .registers 1

    const-string v0, "il2cppdumper"

    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    return-void
.end method
```

**2. Add the native libraries:**
```
lib/armeabi-v7a/libil2cppdumper.so
lib/arm64-v8a/libil2cppdumper.so
lib/x86/libil2cppdumper.so
lib/x86_64/libil2cppdumper.so
```

#### Option B: Integrate into your own Android project

**1. Add to your Application class:**
```java
public class MyApp extends Application {
    static {
        System.loadLibrary("il2cppdumper");   // note: correct spelling from Android.mk
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Dump starts automatically when library loads
    }
}
```

**2. Update `build.gradle`:**
```gradle
android {
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
}
```

---

## 🤝 7. Acknowledgments

*Built on the foundation of [Zygisk-Il2CppDumper](https://github.com/Perfare/Zygisk-Il2CppDumper) by Perfare.*