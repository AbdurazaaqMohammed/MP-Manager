#ifndef ELF_SYM_FIND_H
#define ELF_SYM_FIND_H

/**
 * @file elf_sym_find.h
 * @brief Low‑level ELF symbol lookup interface.
 *
 * Provides a single function that searches the dynamic symbol table of
 * an ELF image loaded in memory for a given symbol name.  This is used as
 * a fallback when standard `dlsym` resolution fails (e.g. on stripped or
 * partially‑exported libraries).
 */

#include <stdint.h>

/**
 * @brief Search for a symbol by name in the ELF image at the given base.
 *
 * Supports 32‑bit and 64‑bit ELF files.  The function parses the program
 * headers to locate the dynamic section, then walks the dynamic symbol
 * table (either SYSV hash or GNU hash) looking for a matching name.
 * Only global and weak symbols of type FUNC, OBJECT, or NOTYPE are
 * considered.
 *
 * @param base     Base address where the ELF image is mapped in memory.
 * @param sym_name Name of the symbol to find (case‑sensitive C string).
 * @return Runtime address of the symbol, or 0 if not found.
 */
uintptr_t elf_sym_find(uintptr_t base, const char *sym_name);

#endif