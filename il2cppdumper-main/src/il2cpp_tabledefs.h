#ifndef IL2CPP_DUMPER_TABLEDEFS_H
#define IL2CPP_DUMPER_TABLEDEFS_H

/**
 * @file il2cpp_tabledefs.h
 * @brief ECMA‑335 / .NET metadata flag constants for IL2CPP.
 *
 * These macros define the bit‑mask values that IL2CPP uses to describe
 * field, method, type, and parameter attributes.  They correspond directly
 * to the standard .NET metadata tables but are reproduced here so the
 * dumper can decode them without linking against the Unity engine.
 *
 * @see ECMA‑335 Standard, Partition II: Metadata
 */

/* -------------------------------------------------------------------------
 * Field attributes (FieldAttributes / CorFieldAttr)
 * ------------------------------------------------------------------------- */

/** Mask for the accessibility bits of a field. */
#define FIELD_ATTRIBUTE_FIELD_ACCESS_MASK    0x0007
#define FIELD_ATTRIBUTE_COMPILER_CONTROLLED  0x0000   /**< Compiler‑controlled (internal). */
#define FIELD_ATTRIBUTE_PRIVATE              0x0001   /**< private */
#define FIELD_ATTRIBUTE_FAM_AND_ASSEM        0x0002   /**< private protected (family and assembly). */
#define FIELD_ATTRIBUTE_ASSEMBLY             0x0003   /**< internal */
#define FIELD_ATTRIBUTE_FAMILY               0x0004   /**< protected */
#define FIELD_ATTRIBUTE_FAM_OR_ASSEM         0x0005   /**< protected internal (family or assembly). */
#define FIELD_ATTRIBUTE_PUBLIC               0x0006   /**< public */

/** Other field modifiers. */
#define FIELD_ATTRIBUTE_STATIC               0x0010   /**< static */
#define FIELD_ATTRIBUTE_INIT_ONLY            0x0020   /**< readonly (init‑only) */
#define FIELD_ATTRIBUTE_LITERAL              0x0040   /**< const (literal, value embedded in metadata) */
#define FIELD_ATTRIBUTE_NOT_SERIALIZED       0x0080   /**< NonSerialized */
#define FIELD_ATTRIBUTE_SPECIAL_NAME         0x0200   /**< Special‑name (e.g., .value__ for enums) */
#define FIELD_ATTRIBUTE_PINVOKE_IMPL         0x2000   /**< Field has a P/Invoke mapping. */
#define FIELD_ATTRIBUTE_HAS_DEFAULT          0x8000   /**< Field has a default constant value. */
#define FIELD_ATTRIBUTE_HAS_FIELD_RVA        0x0100   /**< Field data is located at a fixed RVA in the PE image. */

/* -------------------------------------------------------------------------
 * Method attributes (MethodAttributes / CorMethodAttr)
 * ------------------------------------------------------------------------- */

/** Mask for the accessibility bits of a method. */
#define METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK  0x0007
#define METHOD_ATTRIBUTE_COMPILER_CONTROLLED 0x0000   /**< Compiler‑controlled. */
#define METHOD_ATTRIBUTE_PRIVATE             0x0001   /**< private */
#define METHOD_ATTRIBUTE_FAM_AND_ASSEM       0x0002   /**< private protected (family and assembly). */
#define METHOD_ATTRIBUTE_ASSEM               0x0003   /**< internal */
#define METHOD_ATTRIBUTE_FAMILY              0x0004   /**< protected */
#define METHOD_ATTRIBUTE_FAM_OR_ASSEM        0x0005   /**< protected internal (family or assembly). */
#define METHOD_ATTRIBUTE_PUBLIC              0x0006   /**< public */

#define METHOD_ATTRIBUTE_STATIC              0x0010   /**< static */
#define METHOD_ATTRIBUTE_FINAL               0x0020   /**< final (cannot be overridden). */
#define METHOD_ATTRIBUTE_VIRTUAL             0x0040   /**< virtual */
#define METHOD_ATTRIBUTE_HIDE_BY_SIG         0x0080   /**< Hide by signature (method‑level). */

/** Vtable layout – distinguishes new slot from reuse. */
#define METHOD_ATTRIBUTE_VTABLE_LAYOUT_MASK  0x0100
#define METHOD_ATTRIBUTE_REUSE_SLOT          0x0000   /**< Override – reuse existing vtable slot. */
#define METHOD_ATTRIBUTE_NEW_SLOT            0x0100   /**< New slot – introduce new virtual method. */

#define METHOD_ATTRIBUTE_ABSTRACT            0x0400   /**< abstract */
#define METHOD_ATTRIBUTE_SPECIAL_NAME        0x0800   /**< Special‑name (e.g., operator overloads). */
#define METHOD_ATTRIBUTE_PINVOKE_IMPL        0x2000   /**< Method has a P/Invoke declaration. */

/* -------------------------------------------------------------------------
 * Type attributes (TypeAttributes / CorTypeAttr)
 * ------------------------------------------------------------------------- */

/** Mask for the visibility bits of a type. */
#define TYPE_ATTRIBUTE_VISIBILITY_MASK       0x00000007
#define TYPE_ATTRIBUTE_NOT_PUBLIC            0x00000000   /**< Not public (internal). */
#define TYPE_ATTRIBUTE_PUBLIC                0x00000001   /**< public */
#define TYPE_ATTRIBUTE_NESTED_PUBLIC         0x00000002   /**< Nested public.*/
#define TYPE_ATTRIBUTE_NESTED_PRIVATE        0x00000003   /**< Nested private.*/
#define TYPE_ATTRIBUTE_NESTED_FAMILY         0x00000004   /**< Nested protected.*/
#define TYPE_ATTRIBUTE_NESTED_ASSEMBLY       0x00000005   /**< Nested internal.*/
#define TYPE_ATTRIBUTE_NESTED_FAM_AND_ASSEM  0x00000006   /**< Nested private protected.*/
#define TYPE_ATTRIBUTE_NESTED_FAM_OR_ASSEM   0x00000007   /**< Nested protected internal.*/

/** Mask to discriminate class vs. interface. */
#define TYPE_ATTRIBUTE_CLASS_SEMANTIC_MASK   0x00000020
#define TYPE_ATTRIBUTE_CLASS                 0x00000000   /**< Class */
#define TYPE_ATTRIBUTE_INTERFACE             0x00000020   /**< Interface */

#define TYPE_ATTRIBUTE_ABSTRACT              0x00000080   /**< abstract */
#define TYPE_ATTRIBUTE_SEALED                0x00000100   /**< sealed */
#define TYPE_ATTRIBUTE_SERIALIZABLE          0x00002000   /**< Serializable */

/* -------------------------------------------------------------------------
 * Parameter attributes (ParamAttributes / CorParamAttr)
 * ------------------------------------------------------------------------- */

#define PARAM_ATTRIBUTE_IN                   0x0001   /**< [In] */
#define PARAM_ATTRIBUTE_OUT                  0x0002   /**< [Out] */
#define PARAM_ATTRIBUTE_OPTIONAL             0x0010   /**< Optional parameter. */
#define PARAM_ATTRIBUTE_HAS_DEFAULT          0x1000   /**< Parameter has a default value. */

#endif