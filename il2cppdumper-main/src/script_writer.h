#ifndef IL2CPP_SCRIPT_WRITER_H
#define IL2CPP_SCRIPT_WRITER_H

/**
 * @file script_writer.h
 * @brief Structured output writer for reverse‑engineering scripts.
 *
 * This module generates script files for IDA Pro, Ghidra, radare2 / rizin,
 * and Binary Ninja based on IL2CPP metadata extracted during the dump.
 * The writer receives method and field information and appends it to
 * the appropriate script files in the given output directory.
 */

#include <stddef.h>
#include <stdint.h>

/** Opaque handle for the script writer context. */
typedef struct ScriptWriter ScriptWriter;

/**
 * @brief Open the script writer and create output files.
 *
 * Creates four files in the specified directory:
 *   - `il2cpp_ida.py`     – IDA Pro Python script
 *   - `il2cpp_ghidra.py`  – Ghidra Jython script
 *   - `il2cpp_r2.r2`      – radare2 / rizin script
 *   - `il2cpp_binja.py`   – Binary Ninja Python script
 *
 * Each file is opened with a header comment.  If any file cannot be
 * opened, that file is skipped; the writer still operates on the others.
 * If all four files fail, the function returns NULL.
 *
 * @param output_dir  Directory where script files will be written.
 * @return Pointer to ScriptWriter, or NULL on failure.
 */
ScriptWriter * sw_open(const char * output_dir);

/**
 * @brief Write a method entry to all open script files.
 *
 * The method's metadata (name, return type, parameters, flags, RVA, VA)
 * is formatted appropriately for each target tool.  Deduplication is
 * performed per assembly/class to avoid name clashes.
 *
 * @param sw      ScriptWriter handle.
 * @param dll     Assembly name.
 * @param ns      Namespace.
 * @param cls     Class name.
 * @param method  Method name.
 * @param ret     Return type as a string.
 * @param params  Parameter list as a single string (e.g. "int a, string b").
 * @param flags   Method attribute flags (from IL2CPP metadata).
 * @param rva     Relative virtual address of the method.
 * @param va      Absolute virtual address (base + RVA).
 */
void sw_write_method(ScriptWriter * sw,
  const char * dll,
    const char * ns,
      const char * cls,
        const char * method,
          const char * ret,
            const char * params,
              uint32_t flags,
              uintptr_t rva,
              uintptr_t va);

/**
 * @brief Write a field entry to the script files.
 *
 * Currently only used as a comment in the generated scripts.
 *
 * @param sw      ScriptWriter handle.
 * @param dll     Assembly name.
 * @param ns      Namespace.
 * @param cls     Class name.
 * @param field   Field name.
 * @param type    Field type as a string.
 * @param attrs   Field attribute flags (from IL2CPP metadata).
 * @param offset  Memory offset of the field.
 */
void sw_write_field(ScriptWriter * sw,
  const char * dll,
    const char * ns,
      const char * cls,
        const char * field,
          const char * type,
            uint32_t attrs,
            size_t offset);

/**
 * @brief Close the script writer and finalise all files.
 *
 * Appends any trailing footer (e.g., execution logic) to each script file,
 * flushes and closes all file descriptors, and frees the ScriptWriter
 * structure.
 *
 * @param sw  ScriptWriter handle (may be NULL, in which case nothing happens).
 */
void sw_close(ScriptWriter * sw);

#endif