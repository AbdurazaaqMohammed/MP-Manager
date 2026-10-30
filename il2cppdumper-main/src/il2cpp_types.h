#ifndef IL2CPP_DUMPER_TYPES_H
#define IL2CPP_DUMPER_TYPES_H

/**
 * @file il2cpp_types.h
 * @brief Core IL2CPP runtime type definitions.
 *
 * These structures mirror the internal representations used by the IL2CPP
 * runtime.  They are necessary for interpreting metadata without linking
 * against the actual engine.  The definitions are based on the public
 * IL2CPP header stubs and may need minor adjustments for different Unity
 * versions.
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

/* -------------------------------------------------------------------------
 * Primitive type aliases
 * ------------------------------------------------------------------------- */

/** Unity's 16‑bit character type (UTF‑16 code unit). */
typedef uint16_t  Il2CppChar;

/** Size type used for managed arrays. */
typedef uintptr_t il2cpp_array_size_t;

/** Index into the type definition table. */
typedef int32_t   TypeDefinitionIndex;

/** Index for a generic parameter. */
typedef int32_t   GenericParameterIndex;

/** Raw pointer to a managed method (IL2CPP compiled). */
typedef void (*Il2CppMethodPointer)(void);

/* -------------------------------------------------------------------------
 * Opaque structure forward declarations
 * ------------------------------------------------------------------------- */

/** Represents a loaded .NET assembly image. */
typedef struct Il2CppImage         Il2CppImage;

/** Represents a loaded .NET assembly. */
typedef struct Il2CppAssembly      Il2CppAssembly;

/** The application domain (isolated execution context). */
typedef struct Il2CppDomain        Il2CppDomain;

/** A managed type (class, struct, enum, interface). */
typedef struct Il2CppClass         Il2CppClass;

/** Bounds for a multi‑dimensional array. */
typedef struct Il2CppArrayBounds   Il2CppArrayBounds;

/** Runtime representation of a closed generic class. */
typedef struct Il2CppGenericClass  Il2CppGenericClass;

/** Wrapper around a System.Type for reflection. */
typedef struct Il2CppReflectionType Il2CppReflectionType;

/** Managed thread handle. */
typedef struct Il2CppThread        Il2CppThread;

/** Managed exception object. */
typedef struct Il2CppException     Il2CppException;

/** Monitor (lock) data attached to every managed object. */
typedef struct MonitorData         MonitorData;

/** Virtual method table (vtable) for a class. */
typedef Il2CppClass Il2CppVTable;

/** Field metadata (redefined in tabledefs.h, but we declare here as well). */
typedef struct FieldInfo    FieldInfo;

/** Property metadata. */
typedef struct PropertyInfo PropertyInfo;

/** Event metadata. */
typedef struct EventInfo    EventInfo;

/* -------------------------------------------------------------------------
 * Type enum
 * ------------------------------------------------------------------------- */

/**
 * @brief IL2CPP type enumeration.
 *
 * Each managed type is represented by one of these values.
 * The numerical codes match the IL2CPP runtime.
 */
typedef enum Il2CppTypeEnum {
    IL2CPP_TYPE_END        = 0x00,   /**< Sentinel / end marker. */
    IL2CPP_TYPE_VOID       = 0x01,   /**< System.Void. */
    IL2CPP_TYPE_BOOLEAN    = 0x02,   /**< System.Boolean. */
    IL2CPP_TYPE_CHAR       = 0x03,   /**< System.Char. */
    IL2CPP_TYPE_I1         = 0x04,   /**< System.SByte. */
    IL2CPP_TYPE_U1         = 0x05,   /**< System.Byte. */
    IL2CPP_TYPE_I2         = 0x06,   /**< System.Int16. */
    IL2CPP_TYPE_U2         = 0x07,   /**< System.UInt16. */
    IL2CPP_TYPE_I4         = 0x08,   /**< System.Int32. */
    IL2CPP_TYPE_U4         = 0x09,   /**< System.UInt32. */
    IL2CPP_TYPE_I8         = 0x0a,   /**< System.Int64. */
    IL2CPP_TYPE_U8         = 0x0b,   /**< System.UInt64. */
    IL2CPP_TYPE_R4         = 0x0c,   /**< System.Single. */
    IL2CPP_TYPE_R8         = 0x0d,   /**< System.Double. */
    IL2CPP_TYPE_STRING     = 0x0e,   /**< System.String. */
    IL2CPP_TYPE_PTR        = 0x0f,   /**< Unmanaged pointer. */
    IL2CPP_TYPE_BYREF      = 0x10,   /**< Managed reference (ref/out). */
    IL2CPP_TYPE_VALUETYPE  = 0x11,   /**< Non‑enum value type. */
    IL2CPP_TYPE_CLASS      = 0x12,   /**< Reference type (class). */
    IL2CPP_TYPE_VAR        = 0x13,   /**< Generic type parameter (method). */
    IL2CPP_TYPE_ARRAY      = 0x14,   /**< Multi‑dimensional array. */
    IL2CPP_TYPE_GENERICINST= 0x15,   /**< Instantiated generic type. */
    IL2CPP_TYPE_TYPEDBYREF = 0x16,   /**< System.TypedReference. */
    IL2CPP_TYPE_I          = 0x18,   /**< System.IntPtr. */
    IL2CPP_TYPE_U          = 0x19,   /**< System.UIntPtr. */
    IL2CPP_TYPE_FNPTR      = 0x1b,   /**< Function pointer. */
    IL2CPP_TYPE_OBJECT     = 0x1c,   /**< System.Object. */
    IL2CPP_TYPE_SZARRAY    = 0x1d,   /**< Single‑dimensional, zero‑based array. */
    IL2CPP_TYPE_MVAR       = 0x1e,   /**< Generic type parameter (class). */
    IL2CPP_TYPE_ENUM       = 0x55    /**< Enumeration. */
} Il2CppTypeEnum;

/* -------------------------------------------------------------------------
 * Core data structures
 * ------------------------------------------------------------------------- */

/**
 * @brief Compact representation of a managed type.
 *
 * The union `data` stores different kinds of type information depending on
 * the value of `type`.  For example, a value type stores the type definition
 * index, while a `BYREF` contains a pointer to the target type.
 */
typedef struct Il2CppType {
    union {
        void                    *dummy;               /**< Unused (alignment). */
        TypeDefinitionIndex      klassIndex;          /**< Index for valuetype / class / enum. */
        const struct Il2CppType *type;                /**< Target type for PTR / BYREF / SZARRAY. */
        Il2CppGenericClass      *generic_class;       /**< Generic instantiation details. */
        GenericParameterIndex    genericParameterIndex; /**< Generic parameter index. */
    } data;

    unsigned int attrs    : 16;   /**< Parameter / field attributes (ParamAttributes). */
    Il2CppTypeEnum type   : 8;    /**< The type category (Il2CppTypeEnum). */
    unsigned int num_mods : 6;    /**< Number of custom modifiers. */
    unsigned int byref    : 1;    /**< True if this type is a by‑reference type. */
    unsigned int pinned   : 1;    /**< True if the local variable is pinned. */
} Il2CppType;

/**
 * @brief Metadata for a single method.
 *
 * The structure is larger in the real IL2CPP runtime, but we only need the
 * method pointer for address extraction and rely on the API functions for
 * everything else.
 */
typedef struct MethodInfo {
    Il2CppMethodPointer methodPointer; /**< Native code pointer for the method. */
    /* Omitted fields: name, flags, return type, parameters, etc. */
} MethodInfo;

/**
 * @brief Base managed object (System.Object).
 *
 * Every managed object starts with a pointer to its class (or vtable) and
 * a monitor pointer for locking.
 */
typedef struct Il2CppObject {
    union {
        Il2CppClass  *klass;   /**< Pointer to the Il2CppClass descriptor. */
        Il2CppVTable *vtable;  /**< Alternative: vtable used during virtual dispatch. */
    };
    MonitorData *monitor;    /**< Synchronisation block (lock). */
} Il2CppObject;

/**
 * @brief Managed string (System.String).
 *
 * The length is stored as an int32, followed by the first 32 chars inline;
 * the rest of the string continues out‑of‑bounds (not shown here).
 */
typedef struct Il2CppString {
    Il2CppObject object;     /**< Base object header. */
    int32_t      length;     /**< Number of characters. */
    Il2CppChar   chars[32];  /**< First 32 characters (in‑line). */
} Il2CppString;

/**
 * @brief Managed array (System.Array).
 *
 * `vector` is a variable‑length field; accessing elements requires
 * reading beyond the declared size.
 */
typedef struct Il2CppArray {
    Il2CppObject        obj;        /**< Base object header. */
    Il2CppArrayBounds  *bounds;     /**< NULL for SZARRAY; bounds for multi‑dim arrays. */
    il2cpp_array_size_t max_length; /**< Total number of elements. */
    void               *vector[1];  /**< First element (variable‑length). */
} Il2CppArray;

#endif