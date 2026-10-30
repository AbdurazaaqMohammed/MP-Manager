#ifndef IL2CPP_DUMPER_API_H
#define IL2CPP_DUMPER_API_H

/**
 * @file il2cpp_api.h
 * @brief Function pointer declarations for the IL2CPP runtime API.
 *
 * This header declares a set of global function pointers that are
 * resolved at runtime via `dlsym` or ELF symbol search (see
 * @ref il2cpp_api_init_ex).  Once populated, these pointers provide
 * access to the internal IL2CPP metadata APIs without linking against
 * the Unity engine library.
 *
 * Every pointer is initially NULL.  After a successful call to
 * `il2cpp_api_init_ex`, the required pointers (those marked "required"
 * in the implementation) are guaranteed to be non‑NULL; optional
 * pointers may remain NULL and callers must guard against that.
 */

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include "il2cpp_types.h"

/* -------------------------------------------------------------------------
 * Domain & Assembly access
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the current IL2CPP application domain.
 * @return Domain pointer, or NULL if not yet initialised.
 */
extern Il2CppDomain*          (*il2cpp_domain_get)           (void);

/**
 * @brief Retrieve the array of loaded assemblies in the given domain.
 * @param domain  Domain to query.
 * @param size    [out] Receives the number of assemblies.
 * @return Pointer to an array of @c Il2CppAssembly* pointers.
 */
extern const Il2CppAssembly** (*il2cpp_domain_get_assemblies)(const Il2CppDomain*, size_t*);

/**
 * @brief Obtain the image (PE/ELF module) containing an assembly.
 * @param asm  Assembly handle.
 * @return Image descriptor, or NULL.
 */
extern const Il2CppImage*     (*il2cpp_assembly_get_image)   (const Il2CppAssembly*);

/* -------------------------------------------------------------------------
 * Image (module) metadata
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the human‑readable name of an image (e.g. "Assembly-CSharp.dll").
 * @param img  Image handle.
 * @return Name string (owned by runtime), or NULL.
 */
extern const char*        (*il2cpp_image_get_name)       (const Il2CppImage*);

/**
 * @brief Number of classes defined in an image.
 * @param img  Image handle.
 * @return Class count.
 */
extern size_t             (*il2cpp_image_get_class_count)(const Il2CppImage*);

/**
 * @brief Get the class at a specific index within an image.
 * @param img   Image handle.
 * @param index Zero‑based index (must be < count).
 * @return Class descriptor, or NULL.
 */
extern const Il2CppClass* (*il2cpp_image_get_class)      (const Il2CppImage*, size_t);

/* -------------------------------------------------------------------------
 * Class inspection
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the short name of a class (without namespace).
 * @param klass  Class handle.
 * @return Name string, or NULL.
 */
extern const char*          (*il2cpp_class_get_name)      (Il2CppClass*);

/**
 * @brief Get the namespace of a class.
 * @param klass  Class handle.
 * @return Namespace string (empty string for global namespace), or NULL.
 */
extern const char*          (*il2cpp_class_get_namespace) (Il2CppClass*);

/**
 * @brief Retrieve the raw type attributes flags (TypeAttributes).
 * @param klass  Class handle.
 * @return Bitmask of TYPE_ATTRIBUTE_* flags.
 */
extern int                  (*il2cpp_class_get_flags)     (const Il2CppClass*);

/**
 * @brief Test whether a class is a value type (struct or primitive).
 * @param klass  Class handle.
 * @return true if it is a value type.
 */
extern bool                 (*il2cpp_class_is_valuetype)  (const Il2CppClass*);

/**
 * @brief Test whether a class is an enumeration.
 * @param klass  Class handle.
 * @return true if it is an enum.
 */
extern bool                 (*il2cpp_class_is_enum)       (const Il2CppClass*);

/**
 * @brief Get the base (parent) class.
 * @param klass  Class handle.
 * @return Parent class, or NULL for interfaces / System.Object.
 */
extern Il2CppClass*         (*il2cpp_class_get_parent)    (Il2CppClass*);

/**
 * @brief Get the type descriptor associated with a class.
 * @param klass  Class handle.
 * @return Il2CppType pointer, or NULL.
 */
extern const Il2CppType*    (*il2cpp_class_get_type)      (Il2CppClass*);

/**
 * @brief Resolve an Il2CppType back to its Il2CppClass.
 * @param type  Type descriptor.
 * @return Class handle, or NULL.
 */
extern Il2CppClass*         (*il2cpp_class_from_type)     (const Il2CppType*);

/**
 * @brief Iterate over the interfaces implemented by a class.
 * @param klass  Class handle.
 * @param iter   [in/out] Iterator state.  Must be initialised to NULL
 *               before the first call.
 * @return Next interface, or NULL when finished.
 */
extern Il2CppClass*         (*il2cpp_class_get_interfaces)(Il2CppClass*, void**);

/**
 * @brief Iterate over the fields of a class.
 * @param klass  Class handle.
 * @param iter   [in/out] Iterator state (start with NULL).
 * @return Next field, or NULL.
 */
extern FieldInfo*           (*il2cpp_class_get_fields)    (Il2CppClass*, void**);

/**
 * @brief Iterate over the properties of a class.
 * @param klass  Class handle.
 * @param iter   [in/out] Iterator state (start with NULL).
 * @return Next property, or NULL.
 */
extern const PropertyInfo*  (*il2cpp_class_get_properties)(Il2CppClass*, void**);

/**
 * @brief Iterate over the methods of a class.
 * @param klass  Class handle.
 * @param iter   [in/out] Iterator state (start with NULL).
 * @return Next method, or NULL.
 */
extern const MethodInfo*    (*il2cpp_class_get_methods)   (Il2CppClass*, void**);

/**
 * @brief Get the image that defines this class.
 * @param klass  Class handle.
 * @return Image pointer, or NULL.
 */
extern const Il2CppImage*   (*il2cpp_class_get_image)     (Il2CppClass*);

/**
 * @brief Enumerate every class known to the runtime (global callback).
 * @param callback  Function called for each class.
 * @param user_data Opaque pointer passed to the callback.
 */
extern void                 (*il2cpp_class_for_each)      (void (*)(Il2CppClass*, void*), void*);

/* -------------------------------------------------------------------------
 * Reflection helpers (optional, used by fallback strategy)
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the corlib image (System.Private.CoreLib / mscorlib).
 * @return Image handle for the core library.
 */
extern const Il2CppImage*  (*il2cpp_get_corlib)               (void);

/**
 * @brief Look up a class by namespace and name within an image.
 * @param image     Image to search.
 * @param namespace_ Namespace (may be empty string).
 * @param name      Class short name.
 * @return Class handle, or NULL.
 */
extern Il2CppClass*        (*il2cpp_class_from_name)           (const Il2CppImage*, const char*, const char*);

/**
 * @brief Get a method by name from a class (with specified argument count).
 * @param klass    Class handle.
 * @param name     Method name.
 * @param args     Number of arguments (for overload resolution).
 * @return Method descriptor, or NULL.
 */
extern const MethodInfo*   (*il2cpp_class_get_method_from_name)(Il2CppClass*, const char*, int);

/**
 * @brief Create an IL2CPP managed string from a UTF‑8 C string.
 * @param str  Source C string.
 * @return Managed string handle.
 */
extern Il2CppString*       (*il2cpp_string_new)                (const char*);

/**
 * @brief Convert a reflected System.Type back to an Il2CppClass.
 * @param type  Reflection type wrapper.
 * @return Class handle, or NULL.
 */
extern Il2CppClass*        (*il2cpp_class_from_system_type)    (Il2CppReflectionType*);

/* -------------------------------------------------------------------------
 * Field inspection
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the raw field attributes (FieldAttributes).
 * @param field  Field handle.
 * @return Bitmask of FIELD_ATTRIBUTE_* flags.
 */
extern int               (*il2cpp_field_get_flags)        (FieldInfo*);

/**
 * @brief Get the name of a field.
 * @param field  Field handle.
 * @return Name string, or NULL.
 */
extern const char*       (*il2cpp_field_get_name)         (FieldInfo*);

/**
 * @brief Get the type descriptor of a field.
 * @param field  Field handle.
 * @return Il2CppType pointer, or NULL.
 */
extern const Il2CppType* (*il2cpp_field_get_type)         (FieldInfo*);

/**
 * @brief Get the memory offset of a field within its object.
 * @param field  Field handle.
 * @return Byte offset from the start of the object / class.
 */
extern size_t            (*il2cpp_field_get_offset)       (FieldInfo*);

/**
 * @brief Read the value of a static field.
 * @param field  Field handle.
 * @param value  [out] Buffer large enough to hold the field's value.
 */
extern void              (*il2cpp_field_static_get_value) (FieldInfo*, void*);

/* -------------------------------------------------------------------------
 * Property inspection
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the getter method of a property.
 * @param prop  Property handle.
 * @return Method descriptor, or NULL.
 */
extern const MethodInfo* (*il2cpp_property_get_get_method)(PropertyInfo*);

/**
 * @brief Get the setter method of a property.
 * @param prop  Property handle.
 * @return Method descriptor, or NULL.
 */
extern const MethodInfo* (*il2cpp_property_get_set_method)(PropertyInfo*);

/**
 * @brief Get the name of a property.
 * @param prop  Property handle.
 * @return Name string, or NULL.
 */
extern const char*       (*il2cpp_property_get_name)      (PropertyInfo*);

/* -------------------------------------------------------------------------
 * Method inspection
 * ------------------------------------------------------------------------- */

/**
 * @brief Get the raw method attributes and implementation flags.
 * @param method   Method handle.
 * @param iflags   [out] Receives the implementation flags.
 * @return Bitmask of METHOD_ATTRIBUTE_* flags.
 */
extern uint32_t          (*il2cpp_method_get_flags)      (const MethodInfo*, uint32_t*);

/**
 * @brief Get the name of a method.
 * @param method  Method handle.
 * @return Name string, or NULL.
 */
extern const char*       (*il2cpp_method_get_name)       (const MethodInfo*);

/**
 * @brief Get the return type of a method.
 * @param method  Method handle.
 * @return Il2CppType pointer, or NULL.
 */
extern const Il2CppType* (*il2cpp_method_get_return_type)(const MethodInfo*);

/**
 * @brief Get the number of parameters of a method.
 * @param method  Method handle.
 * @return Parameter count.
 */
extern uint32_t          (*il2cpp_method_get_param_count)(const MethodInfo*);

/**
 * @brief Get the type descriptor for a method parameter.
 * @param method  Method handle.
 * @param index   Zero‑based parameter index.
 * @return Il2CppType pointer, or NULL.
 */
extern const Il2CppType* (*il2cpp_method_get_param)      (const MethodInfo*, uint32_t);

/**
 * @brief Get the name of a method parameter.
 * @param method  Method handle.
 * @param index   Zero‑based parameter index.
 * @return Parameter name, or NULL.
 */
extern const char*       (*il2cpp_method_get_param_name) (const MethodInfo*, uint32_t);

/* -------------------------------------------------------------------------
 * Type utilities
 * ------------------------------------------------------------------------- */

/**
 * @brief Determine whether an Il2CppType represents a by‑reference type.
 * @param type  Type descriptor.
 * @return true if it is a by‑ref type.
 */
extern bool (*il2cpp_type_is_byref)(const Il2CppType*);

/* -------------------------------------------------------------------------
 * Thread management (optional, for safety)
 * ------------------------------------------------------------------------- */

/**
 * @brief Attach the current native thread to the IL2CPP domain.
 *        Required before certain API calls can be made from a non‑managed
 *        thread.
 * @param domain  Target domain.
 * @return Thread handle, or NULL on failure.
 */
extern Il2CppThread* (*il2cpp_thread_attach)(Il2CppDomain*);

/**
 * @brief Detach the current native thread from the IL2CPP domain.
 * @param thread  Thread handle returned by @ref il2cpp_thread_attach.
 */
extern void          (*il2cpp_thread_detach)(Il2CppThread*);

/**
 * @brief Check whether the given thread is the VM's own internal thread.
 * @param thread  Thread handle.
 * @return true if it is the VM thread.
 */
extern bool          (*il2cpp_is_vm_thread) (Il2CppThread*);

/* -------------------------------------------------------------------------
 * API initialisation & base address
 * ------------------------------------------------------------------------- */

/**
 * @brief Resolve all function pointers declared in this header.
 *
 * Attempts to locate each symbol using (in order):
 *   1. @p handle  (a dlopen handle to libil2cpp.so).
 *   2. RTLD_DEFAULT (global symbol table).
 *   3. Manual ELF symbol search starting from @p base.
 *
 * Required symbols that cannot be found cause the function to fail.
 *
 * @param handle  A dlopen handle to libil2cpp.so, or RTLD_DEFAULT.
 * @param base    Base address of libil2cpp.so in memory (used for ELF
 *                symbol fallback).
 * @return 1 if all required symbols were resolved, 0 otherwise.
 */
int       il2cpp_api_init_ex(void *handle, uintptr_t base);

/**
 * @brief Return the cached base address of libil2cpp.so.
 *
 * The base address is determined during @ref il2cpp_api_init_ex.  It is
 * used to calculate RVAs (relative virtual addresses) for method pointers
 * in the dump output.
 *
 * @return Base address, or 0 if unknown.
 */
uintptr_t il2cpp_get_base(void);

#endif