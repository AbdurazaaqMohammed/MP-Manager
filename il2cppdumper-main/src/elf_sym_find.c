/**
 * @file elf_sym_find.c
 * @brief Manual ELF symbol table lookup for IL2CPP API resolution.
 *
 * When `dlsym` fails to find an IL2CPP function (common on Android where
 * `libil2cpp.so` may only export a handful of symbols), this module walks
 * the ELF structures directly to locate the target function.
 *
 * It supports both 32‑bit and 64‑bit ELF images, and parses SysV and GNU
 * hash tables to avoid a brute‑force scan of the entire symbol table.
 * The search is limited to @c ELF_SCAN_LIMIT entries for safety.
 *
 * @section workflow Workflow (per architecture)
 *   1. Verify the ELF magic and class.
 *   2. Compute the load bias (difference between file offset and virtual
 *      address) by scanning @c PT_LOAD segments.
 *   3. Locate the @c PT_DYNAMIC segment to access the dynamic section.
 *   4. Parse @c DT_SYMTAB, @c DT_STRTAB, @c DT_HASH / @c DT_GNU_HASH to
 *      find the symbol table, string table, and hash table.
 *   5. Determine the number of symbols from the hash table (or fallback
 *      to the scan limit).
 *   6. Walk the symbol table, comparing names from the string table
 *      against the target symbol name, and return the adjusted address
 *      (base + st_value) of the first matching global/weak function.
 */

#include "elf_sym_find.h"
#include "log.h"

#include <elf.h>
#include <string.h>
#include <stdint.h>

/**
 * @brief Maximum number of symbols to scan if the hash table is missing or
 *        reports an unreasonably large count.
 */
#define ELF_SCAN_LIMIT 65536U

/* -------------------------------------------------------------------------
 * GNU hash table helpers – count symbols
 * ------------------------------------------------------------------------- */

/**
 * @brief Count the number of dynamic symbols in a 64‑bit ELF GNU hash table.
 *
 * The GNU hash format stores:
 *   - nbuckets : number of hash buckets.
 *   - symoffset: index of the first symbol in the hash table.
 *   - bloom_size: number of 64‑bit bloom filter words.
 *   - bloom    : the bloom filter array.
 *   - buckets  : hash bucket array (index into chains).
 *   - chains   : hash chain array (each entry is a hash value with the
 *                lowest bit set on the last entry of a bucket).
 *
 * This function walks the buckets to find the maximum chain index,
 * then walks that chain until the terminator bit is found.  The total
 * number of symbols in the table is the last chain index + 1.
 *
 * @param bias  Load bias (difference between mapped address and vaddr).
 * @param raw   Virtual address of the GNU hash table (from DT_GNU_HASH).
 * @return The number of symbols covered by this hash table.
 */
static uint32_t gnu_hash_count64(uintptr_t bias, uintptr_t raw) {
    /* Hash table layout:
     *   uint32_t nbuckets
     *   uint32_t symoffset
     *   uint32_t bloom_size
     *   uint64_t bloom[bloom_size]
     *   uint32_t buckets[nbuckets]
     *   uint32_t chains[...]
     */
    const uint32_t *h       = (const uint32_t *)(bias + raw);
    uint32_t nbuckets       = h[0];
    uint32_t symoffset      = h[1];
    uint32_t bloom_size     = h[2];
    const uint64_t *bloom   = (const uint64_t *)(h + 4);
    const uint32_t *buckets = (const uint32_t *)(bloom + bloom_size);
    const uint32_t *chains  = buckets + nbuckets;
    if (nbuckets == 0) return symoffset;  /* empty hash table */

    /* Find the highest bucket value (last entry in any chain start). */
    uint32_t last = 0, i;
    for (i = 0; i < nbuckets; ++i)
        if (buckets[i] > last) last = buckets[i];
    if (last < symoffset) return symoffset;

    /* Walk the chain from the last entry until the terminator bit is set. */
    const uint32_t *c = chains + (last - symoffset);
    while (!(*c & 1u)) { ++c; ++last; }
    return last + 1;                      /* count = last index + 1 */
}

/**
 * @brief Count symbols in a 32‑bit ELF GNU hash table.
 *
 * The format is identical to the 64‑bit version except the bloom filter
 * uses 32‑bit words.
 */
static uint32_t gnu_hash_count32(uintptr_t bias, uintptr_t raw) {
    const uint32_t *h       = (const uint32_t *)(bias + raw);
    uint32_t nbuckets       = h[0];
    uint32_t symoffset      = h[1];
    uint32_t bloom_size     = h[2];
    const uint32_t *bloom   = h + 4;
    const uint32_t *buckets = bloom + bloom_size;
    const uint32_t *chains  = buckets + nbuckets;
    if (nbuckets == 0) return symoffset;
    uint32_t last = 0, i;
    for (i = 0; i < nbuckets; ++i)
        if (buckets[i] > last) last = buckets[i];
    if (last < symoffset) return symoffset;
    const uint32_t *c = chains + (last - symoffset);
    while (!(*c & 1u)) { ++c; ++last; }
    return last + 1;
}

/* -------------------------------------------------------------------------
 * 64‑bit ELF symbol lookup
 * ------------------------------------------------------------------------- */

/**
 * @brief Search for a symbol name in a 64‑bit ELF image.
 *
 * Parses the ELF header, program headers, and dynamic section to locate
 * the symbol and string tables.  Then iterates (up to @c ELF_SCAN_LIMIT
 * entries) comparing the symbol names.  Only global/weak symbols of type
 * FUNC, OBJECT, or NOTYPE are considered.
 *
 * @param base  Base address where the ELF image is mapped.
 * @param name  Symbol name to find.
 * @return Runtime address of the symbol (bias + st_value), or 0 if not found.
 */
static uintptr_t elf64_find(uintptr_t base, const char *name) {
    const Elf64_Ehdr *eh = (const Elf64_Ehdr *)base;
    /* Quick sanity check: must be a 64‑bit ELF */
    if (eh->e_ident[EI_CLASS] != ELFCLASS64) return 0;

    /* ---- 1. Compute load bias ---- */
    const Elf64_Phdr *ph = (const Elf64_Phdr *)(base + eh->e_phoff);
    uintptr_t bias = base;
    uint16_t i;
    for (i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type == PT_LOAD) {
            bias = base - (uintptr_t)ph[i].p_vaddr;   /* bias = file_base - vaddr */
            break;
        }
    }

    /* ---- 2. Locate the dynamic section ---- */
    const Elf64_Dyn *dyn = NULL;
    for (i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf64_Dyn *)(bias + (uintptr_t)ph[i].p_vaddr);
            break;
        }
    }
    if (!dyn) return 0;                           /* no dynamic section */

    /* ---- 3. Parse dynamic tags to find symbol/string/hash tables ---- */
    const Elf64_Sym *symtab    = NULL;
    const char      *strtab    = NULL;
    uint32_t         nsyms     = 0;
    uint32_t         syment    = sizeof(Elf64_Sym);
    uintptr_t        gnu_raw   = 0;
    const uint32_t  *sysv_hash = NULL;

    const Elf64_Dyn *d;
    for (d = dyn; d->d_tag != DT_NULL; ++d) {
        switch (d->d_tag) {
        case DT_SYMTAB:  symtab    = (const Elf64_Sym *)(bias + d->d_un.d_ptr); break;
        case DT_STRTAB:  strtab    = (const char *)     (bias + d->d_un.d_ptr); break;
        case DT_SYMENT:  syment    = (uint32_t)d->d_un.d_val;                    break;
        case DT_HASH:    sysv_hash = (const uint32_t *) (bias + d->d_un.d_ptr); break;
        case DT_GNU_HASH:gnu_raw   = (uintptr_t)d->d_un.d_ptr;                  break;
        default: break;
        }
    }
    if (!symtab || !strtab) return 0;    /* cannot proceed without symbol table */

    /* ---- 4. Determine the number of symbols ---- */
    if (sysv_hash)       nsyms = sysv_hash[1];          /* SysV hash: nchain */
    else if (gnu_raw)    nsyms = gnu_hash_count64(bias, gnu_raw);
    uint32_t limit = (nsyms && nsyms < ELF_SCAN_LIMIT) ? nsyms : ELF_SCAN_LIMIT;

    /* ---- 5. Walk the symbol table ---- */
    uint32_t j;
    for (j = 0; j < limit; ++j) {
        const Elf64_Sym *s = (const Elf64_Sym *)
                             ((const uint8_t *)symtab + (size_t)j * syment);
        if (!s->st_name || !s->st_value) continue;    /* skip empty entries */
        unsigned char bind = ELF64_ST_BIND(s->st_info);
        unsigned char type = ELF64_ST_TYPE(s->st_info);
        /* Only consider global/weak functions and objects (or notype). */
        if (type != STT_FUNC && type != STT_OBJECT && type != STT_NOTYPE) continue;
        if (bind != STB_GLOBAL && bind != STB_WEAK) continue;
        /* Compare the name from the string table with the requested name. */
        if (strcmp(strtab + s->st_name, name) == 0)
            return bias + (uintptr_t)s->st_value;     /* success */
    }
    return 0;
}

/* -------------------------------------------------------------------------
 * 32‑bit ELF symbol lookup
 * ------------------------------------------------------------------------- */

/**
 * @brief Search for a symbol name in a 32‑bit ELF image.
 *
 * Identical logic to @ref elf64_find but operating on 32‑bit data structures.
 */
static uintptr_t elf32_find(uintptr_t base, const char *name) {
    const Elf32_Ehdr *eh = (const Elf32_Ehdr *)base;
    if (eh->e_ident[EI_CLASS] != ELFCLASS32) return 0;

    /* ---- 1. Compute load bias ---- */
    const Elf32_Phdr *ph = (const Elf32_Phdr *)(base + eh->e_phoff);
    uintptr_t bias = base;
    uint16_t i;
    for (i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type == PT_LOAD) {
            bias = base - (uintptr_t)ph[i].p_vaddr;
            break;
        }
    }

    /* ---- 2. Locate the dynamic section ---- */
    const Elf32_Dyn *dyn = NULL;
    for (i = 0; i < eh->e_phnum; ++i) {
        if (ph[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf32_Dyn *)(bias + (uintptr_t)ph[i].p_vaddr);
            break;
        }
    }
    if (!dyn) return 0;

    /* ---- 3. Parse dynamic tags ---- */
    const Elf32_Sym *symtab    = NULL;
    const char      *strtab    = NULL;
    uint32_t         nsyms     = 0;
    uint32_t         syment    = sizeof(Elf32_Sym);
    uintptr_t        gnu_raw   = 0;
    const uint32_t  *sysv_hash = NULL;

    const Elf32_Dyn *d;
    for (d = dyn; d->d_tag != (Elf32_Sword)DT_NULL; ++d) {
        switch (d->d_tag) {
        case DT_SYMTAB:  symtab    = (const Elf32_Sym *)(bias + d->d_un.d_ptr); break;
        case DT_STRTAB:  strtab    = (const char *)     (bias + d->d_un.d_ptr); break;
        case DT_SYMENT:  syment    =  d->d_un.d_val;                             break;
        case DT_HASH:    sysv_hash = (const uint32_t *) (bias + d->d_un.d_ptr); break;
        case DT_GNU_HASH:gnu_raw   = (uintptr_t)d->d_un.d_ptr;                  break;
        default: break;
        }
    }
    if (!symtab || !strtab) return 0;

    /* ---- 4. Determine symbol count ---- */
    if (sysv_hash)     nsyms = sysv_hash[1];
    else if (gnu_raw)  nsyms = gnu_hash_count32(bias, gnu_raw);
    uint32_t limit = (nsyms && nsyms < ELF_SCAN_LIMIT) ? nsyms : ELF_SCAN_LIMIT;

    /* ---- 5. Walk the symbol table ---- */
    uint32_t j;
    for (j = 0; j < limit; ++j) {
        const Elf32_Sym *s = (const Elf32_Sym *)
                             ((const uint8_t *)symtab + (size_t)j * syment);
        if (!s->st_name || !s->st_value) continue;
        unsigned char bind = ELF32_ST_BIND(s->st_info);
        unsigned char type = ELF32_ST_TYPE(s->st_info);
        if (type != STT_FUNC && type != STT_OBJECT && type != STT_NOTYPE) continue;
        if (bind != STB_GLOBAL && bind != STB_WEAK) continue;
        if (strcmp(strtab + s->st_name, name) == 0)
            return bias + (uintptr_t)s->st_value;
    }
    return 0;
}

/* -------------------------------------------------------------------------
 * Public interface
 * ------------------------------------------------------------------------- */

/**
 * @brief Resolve a symbol name to an address inside the ELF image at
 *        @p base.
 *
 * Detects the ELF class from the header and dispatches to the
 * appropriate 32‑bit or 64‑bit helper.
 *
 * @param base     Base address of the ELF image in memory.
 * @param sym_name Symbol name (case‑sensitive).
 * @return Runtime address of the symbol, or 0 if not found.
 */
uintptr_t elf_sym_find(uintptr_t base, const char *sym_name) {
    if (!base || !sym_name) return 0;

    /* Verify the ELF magic number: 0x7f E L F */
    const unsigned char *id = (const unsigned char *)base;
    if (id[EI_MAG0] != ELFMAG0 || id[EI_MAG1] != ELFMAG1 ||
        id[EI_MAG2] != ELFMAG2 || id[EI_MAG3] != ELFMAG3) {
        LOGE("elf_sym_find: bad magic at 0x%lx", (unsigned long)base);
        return 0;
    }

    if (id[EI_CLASS] == ELFCLASS64) return elf64_find(base, sym_name);
    if (id[EI_CLASS] == ELFCLASS32) return elf32_find(base, sym_name);

    LOGE("elf_sym_find: unknown ELF class %d", (int)id[EI_CLASS]);
    return 0;
}