#ifndef IL2CPP_DUMPER_DUMP_H
#define IL2CPP_DUMPER_DUMP_H

/**
 * @file il2cpp_dump.h
 * @brief Public interface for the IL2CPP metadata dump.
 *
 * Declares the entry point that triggers the full enumeration of
 * IL2CPP types and writes a C#‑like listing to the specified file.
 * This version adds an optional output directory for script generation.
 */

/**
 * @brief Execute the managed type dump with optional script output.
 *
 * This function will:
 *   1. Open (or create) the output file at @p output_path, retrying on
 *      failure and falling back to alternative directories if necessary.
 *   2. Determine the base address of `libil2cpp.so`.
 *   3. Iterate all loaded managed assemblies and write a pseudo‑C#
 *      representation of every class, including fields, properties, and
 *      methods, into the file.
 *   4. If @p output_dir is non‑NULL and non‑empty, generate script files
 *      (`il2cpp_ida.py`, `il2cpp_ghidra.py`, `il2cpp_r2.r2`,
 *      `il2cpp_binja.py`) in that directory.
 *
 * The dump is performed on the calling thread.  The caller is responsible
 * for ensuring that the IL2CPP runtime is fully initialised and that the
 * API function pointers have been resolved (e.g. via
 * @ref il2cpp_api_init_ex ) before invoking this function.
 *
 * @param output_path  Absolute path for the output `dump.cs` file
 *                     (e.g. `/data/data/com.example/files/il2cpp_dump/dump.cs`).
 * @param output_dir   Optional directory for script files; if NULL or empty,
 *                     no scripts are generated.
 * @return 1 on success, 0 on failure (missing APIs, no assemblies, I/O
 *         error, or all dump strategies failed).
 */
int il2cpp_do_dump(const char *output_path, const char *output_dir);

#endif