/**
 * @file il2cpp_api.c
 * @brief Runtime resolution of the IL2CPP metadata API.
 *
 * This module implements the function pointer resolution logic declared
 * in il2cpp_api.h.  It supports three look‑up methods, tried in order:
 *
 *   1. **dlsym on a specific handle** – usually a `dlopen` of
 *      `libil2cpp.so` with `RTLD_NOLOAD`.
 *   2. **dlsym on RTLD_DEFAULT** – searches the global symbol table,
 *      useful when Unity has already exported the symbols.
 *   3. **Manual ELF symbol lookup** – as a last resort, scans the ELF
 *      symbol tables of the mapped library to find functions by name.
 *
 * A static table (@c g_api_table) lists every symbol the dumper may use,
 * together with its target function pointer and a “required” flag.  After
 * initialisation, all required pointers are guaranteed non‑NULL; optional
 * ones may still be NULL and callers must guard against that.
 */

#include "il2cpp_api.h"
#include "elf_sym_find.h"
#include "log.h"

#include <dlfcn.h>
#include <string.h>
#include <stdint.h>

/* -------------------------------------------------------------------------
 * Global function pointer definitions (all initially NULL)
 * ------------------------------------------------------------------------- */

Il2CppDomain*          (*il2cpp_domain_get)           (void)                          = NULL;
const Il2CppAssembly** (*il2cpp_domain_get_assemblies)(const Il2CppDomain*, size_t*)  = NULL;
const Il2CppImage*     (*il2cpp_assembly_get_image)   (const Il2CppAssembly*)         = NULL;

const char*        (*il2cpp_image_get_name)       (const Il2CppImage*)               = NULL;
size_t             (*il2cpp_image_get_class_count)(const Il2CppImage*)               = NULL;
const Il2CppClass* (*il2cpp_image_get_class)      (const Il2CppImage*, size_t)       = NULL;

const char*         (*il2cpp_class_get_name)      (Il2CppClass*)                     = NULL;
const char*         (*il2cpp_class_get_namespace) (Il2CppClass*)                     = NULL;
int                 (*il2cpp_class_get_flags)     (const Il2CppClass*)               = NULL;
bool                (*il2cpp_class_is_valuetype)  (const Il2CppClass*)               = NULL;
bool                (*il2cpp_class_is_enum)       (const Il2CppClass*)               = NULL;
Il2CppClass*        (*il2cpp_class_get_parent)    (Il2CppClass*)                     = NULL;
const Il2CppType*   (*il2cpp_class_get_type)      (Il2CppClass*)                     = NULL;
Il2CppClass*        (*il2cpp_class_from_type)     (const Il2CppType*)                = NULL;
Il2CppClass*        (*il2cpp_class_get_interfaces)(Il2CppClass*, void**)             = NULL;
FieldInfo*          (*il2cpp_class_get_fields)    (Il2CppClass*, void**)             = NULL;
const PropertyInfo* (*il2cpp_class_get_properties)(Il2CppClass*, void**)             = NULL;
const MethodInfo*   (*il2cpp_class_get_methods)   (Il2CppClass*, void**)             = NULL;
const Il2CppImage*  (*il2cpp_class_get_image)     (Il2CppClass*)                     = NULL;
void                (*il2cpp_class_for_each)      (void (*)(Il2CppClass*, void*), void*) = NULL;

const Il2CppImage*  (*il2cpp_get_corlib)               (void)                        = NULL;
Il2CppClass*        (*il2cpp_class_from_name)           (const Il2CppImage*, const char*, const char*) = NULL;
const MethodInfo*   (*il2cpp_class_get_method_from_name)(Il2CppClass*, const char*, int)               = NULL;
Il2CppString*       (*il2cpp_string_new)                (const char*)                = NULL;
Il2CppClass*        (*il2cpp_class_from_system_type)    (Il2CppReflectionType*)      = NULL;

int               (*il2cpp_field_get_flags)        (FieldInfo*)                      = NULL;
const char*       (*il2cpp_field_get_name)         (FieldInfo*)                      = NULL;
const Il2CppType* (*il2cpp_field_get_type)         (FieldInfo*)                      = NULL;
size_t            (*il2cpp_field_get_offset)       (FieldInfo*)                      = NULL;
void              (*il2cpp_field_static_get_value) (FieldInfo*, void*)               = NULL;

const MethodInfo* (*il2cpp_property_get_get_method)(PropertyInfo*)                   = NULL;
const MethodInfo* (*il2cpp_property_get_set_method)(PropertyInfo*)                   = NULL;
const char*       (*il2cpp_property_get_name)      (PropertyInfo*)                   = NULL;

uint32_t          (*il2cpp_method_get_flags)      (const MethodInfo*, uint32_t*)     = NULL;
const char*       (*il2cpp_method_get_name)       (const MethodInfo*)                = NULL;
const Il2CppType* (*il2cpp_method_get_return_type)(const MethodInfo*)                = NULL;
uint32_t          (*il2cpp_method_get_param_count)(const MethodInfo*)                = NULL;
const Il2CppType* (*il2cpp_method_get_param)      (const MethodInfo*, uint32_t)      = NULL;
const char*       (*il2cpp_method_get_param_name) (const MethodInfo*, uint32_t)      = NULL;

bool (*il2cpp_type_is_byref)(const Il2CppType*)                                      = NULL;

Il2CppThread* (*il2cpp_thread_attach)(Il2CppDomain*)                                 = NULL;
void          (*il2cpp_thread_detach)(Il2CppThread*)                                 = NULL;
bool          (*il2cpp_is_vm_thread) (Il2CppThread*)                                 = NULL;

/* -------------------------------------------------------------------------
 * cached IL2CPP base address
 * ------------------------------------------------------------------------- */

/**
 * @brief Base load address of libil2cpp.so.
 *
 * Set during @ref il2cpp_api_init_ex and used to compute RVAs.
 */
uintptr_t g_il2cpp_base = 0;

/* -------------------------------------------------------------------------
 * Symbol table entry
 * ------------------------------------------------------------------------- */

/**
 * @brief Describes a single API symbol to be resolved.
 */
typedef struct {
    const char *name;       /**< Symbol name (as exported or in ELF). */
    void      **slot;       /**< Address of the global function pointer to fill. */
    int         required;   /**< 1 = abort on failure, 0 = optional. */
} ApiEntry;

/**
 * @brief Static table of all API symbols the dumper may need.
 *
 * Every entry maps a C‑linkage symbol name to the corresponding function
 * pointer declared in il2cpp_api.h.
 */
static const ApiEntry g_api_table[] = {
    { "il2cpp_domain_get",                (void**)&il2cpp_domain_get,                1 },
    { "il2cpp_domain_get_assemblies",     (void**)&il2cpp_domain_get_assemblies,     1 },
    { "il2cpp_assembly_get_image",        (void**)&il2cpp_assembly_get_image,        1 },
    { "il2cpp_image_get_name",            (void**)&il2cpp_image_get_name,            1 },
    { "il2cpp_image_get_class_count",     (void**)&il2cpp_image_get_class_count,     0 },
    { "il2cpp_image_get_class",           (void**)&il2cpp_image_get_class,           0 },
    { "il2cpp_class_get_name",            (void**)&il2cpp_class_get_name,            1 },
    { "il2cpp_class_get_namespace",       (void**)&il2cpp_class_get_namespace,       1 },
    { "il2cpp_class_get_flags",           (void**)&il2cpp_class_get_flags,           1 },
    { "il2cpp_class_is_valuetype",        (void**)&il2cpp_class_is_valuetype,        1 },
    { "il2cpp_class_is_enum",             (void**)&il2cpp_class_is_enum,             1 },
    { "il2cpp_class_get_parent",          (void**)&il2cpp_class_get_parent,          0 },
    { "il2cpp_class_get_type",            (void**)&il2cpp_class_get_type,            1 },
    { "il2cpp_class_from_type",           (void**)&il2cpp_class_from_type,           1 },
    { "il2cpp_class_get_interfaces",      (void**)&il2cpp_class_get_interfaces,      0 },
    { "il2cpp_class_get_fields",          (void**)&il2cpp_class_get_fields,          1 },
    { "il2cpp_class_get_properties",      (void**)&il2cpp_class_get_properties,      1 },
    { "il2cpp_class_get_methods",         (void**)&il2cpp_class_get_methods,         1 },
    { "il2cpp_class_get_image",           (void**)&il2cpp_class_get_image,           0 },
    { "il2cpp_class_for_each",            (void**)&il2cpp_class_for_each,            0 },
    { "il2cpp_get_corlib",                (void**)&il2cpp_get_corlib,                0 },
    { "il2cpp_class_from_name",           (void**)&il2cpp_class_from_name,           0 },
    { "il2cpp_class_get_method_from_name",(void**)&il2cpp_class_get_method_from_name,0 },
    { "il2cpp_string_new",                (void**)&il2cpp_string_new,                0 },
    { "il2cpp_class_from_system_type",    (void**)&il2cpp_class_from_system_type,    0 },
    { "il2cpp_field_get_flags",           (void**)&il2cpp_field_get_flags,           1 },
    { "il2cpp_field_get_name",            (void**)&il2cpp_field_get_name,            1 },
    { "il2cpp_field_get_type",            (void**)&il2cpp_field_get_type,            1 },
    { "il2cpp_field_get_offset",          (void**)&il2cpp_field_get_offset,          1 },
    { "il2cpp_field_static_get_value",    (void**)&il2cpp_field_static_get_value,    0 },
    { "il2cpp_property_get_get_method",   (void**)&il2cpp_property_get_get_method,   1 },
    { "il2cpp_property_get_set_method",   (void**)&il2cpp_property_get_set_method,   1 },
    { "il2cpp_property_get_name",         (void**)&il2cpp_property_get_name,         1 },
    { "il2cpp_method_get_flags",          (void**)&il2cpp_method_get_flags,          1 },
    { "il2cpp_method_get_name",           (void**)&il2cpp_method_get_name,           1 },
    { "il2cpp_method_get_return_type",    (void**)&il2cpp_method_get_return_type,    1 },
    { "il2cpp_method_get_param_count",    (void**)&il2cpp_method_get_param_count,    1 },
    { "il2cpp_method_get_param",          (void**)&il2cpp_method_get_param,          1 },
    { "il2cpp_method_get_param_name",     (void**)&il2cpp_method_get_param_name,     0 },
    { "il2cpp_type_is_byref",             (void**)&il2cpp_type_is_byref,             0 },
    { "il2cpp_thread_attach",             (void**)&il2cpp_thread_attach,             0 },
    { "il2cpp_thread_detach",             (void**)&il2cpp_thread_detach,             0 },
    { "il2cpp_is_vm_thread",              (void**)&il2cpp_is_vm_thread,              0 },
    { NULL, NULL, 0 }   /**< Sentinel */
};

/* -------------------------------------------------------------------------
 * Helper: copy a resolved function pointer into a slot
 * ------------------------------------------------------------------------- */

/**
 * @brief Store a raw symbol address into the target function pointer slot.
 *
 * Uses memcpy to avoid -Wpedantic warnings about casting between
 * data and function pointers.
 *
 * @param sym  Raw symbol address (from dlsym or ELF lookup).
 * @param slot Pointer to the global function pointer variable.
 */
static void safe_load(void *sym, void **slot) {
    memcpy(slot, &sym, sizeof(void *));
}

/* -------------------------------------------------------------------------
 * Public API initialisation
 * ------------------------------------------------------------------------- */

/**
 * @brief Resolve all API function pointers.
 *
 * For each entry in @c g_api_table the function tries:
 *
 *   -# `dlsym` on the given @p handle (if non‑NULL).
 *   -# `dlsym` on @c RTLD_DEFAULT (global symbol space).
 *   -# Manual ELF symbol scan starting at @p base.
 *
 * If a required symbol cannot be found, this function logs an error.
 * At the end, if any required symbol is missing, it returns 0.
 *
 * On success it also caches the base address of `libil2cpp.so` in
 * @ref g_il2cpp_base, either from @p base or, as a fallback, via
 * `dladdr` on the resolved `il2cpp_domain_get` pointer.
 *
 * @param handle A dlopen handle to libil2cpp.so (or RTLD_DEFAULT).
 * @param base   Base address of libil2cpp.so (0 if unknown).
 * @return 1 on success (all required symbols resolved), 0 on failure.
 */
int il2cpp_api_init_ex(void *handle, uintptr_t base) {
    int failures = 0;
    int i;

    for (i = 0; g_api_table[i].name != NULL; ++i) {
        void *sym = NULL;

        /* 1. Try dlsym on the supplied handle. */
        if (handle && !sym)
            sym = dlsym(handle, g_api_table[i].name);

        /* 2. Try the global symbol table (unless handle is already RTLD_DEFAULT). */
        if (!sym && handle != RTLD_DEFAULT)
            sym = dlsym(RTLD_DEFAULT, g_api_table[i].name);

        /* 3. Manual ELF symbol resolution (last resort). */
        if (!sym && base) {
            uintptr_t addr = elf_sym_find(base, g_api_table[i].name);
            if (addr) {
                void *vp = (void *)addr;
                safe_load(vp, g_api_table[i].slot);
                LOGD("ELF resolved: %s -> 0x%lx",
                     g_api_table[i].name, (unsigned long)addr);
                continue;   /* successfully resolved, move to next symbol */
            }
        }

        if (sym) {
            safe_load(sym, g_api_table[i].slot);
            LOGD("dlsym resolved: %s -> %p", g_api_table[i].name, sym);
        } else {
            if (g_api_table[i].required) {
                LOGE("Required symbol missing: %s", g_api_table[i].name);
                ++failures;
            } else {
                LOGD("Optional symbol missing: %s", g_api_table[i].name);
            }
        }
    }

    if (failures) {
        LOGE("il2cpp_api_init_ex: %d required symbol(s) missing", failures);
        return 0;
    }

    /* Determine base address if not already provided. */
    if (base) {
        g_il2cpp_base = base;
    } else if (il2cpp_domain_get) {
        /* Fallback: use dladdr to get the base of the library containing
           the il2cpp_domain_get function. */
        Dl_info info;
        memset(&info, 0, sizeof(info));
        union { void (*fp)(void); void *vp; } u;
        u.fp = (void (*)(void))il2cpp_domain_get;
        if (dladdr(u.vp, &info) && info.dli_fbase)
            g_il2cpp_base = (uintptr_t)info.dli_fbase;
    }

    LOGI("il2cpp_api_init_ex: ok, base=0x%lx", (unsigned long)g_il2cpp_base);
    return 1;
}

/**
 * @brief Get the cached base address of libil2cpp.so.
 *
 * @return Base address (0 if not yet determined).
 */
uintptr_t il2cpp_get_base(void) { return g_il2cpp_base; }