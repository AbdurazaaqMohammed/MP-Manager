/**
 * @file script_writer.c
 * @brief Structured output writer for reverse‑engineering scripts.
 *
 * This module implements the generation of script files for IDA Pro,
 * Ghidra, radare2 / rizin, and Binary Ninja.  It receives method and
 * field metadata from the IL2CPP dump and appends formatted entries to
 * the appropriate script files in the output directory.
 *
 * Each script file is a self‑contained program that applies names,
 * comments, and parameter renaming to the target binary, enabling
 * easier reverse engineering of IL2CPP‑based applications.
 *
 * @par Script types generated
 * - **IDA Pro** (`il2cpp_ida.py`): Python script that renames functions,
 *   adds comments, and optionally applies parameter names.
 * - **Ghidra** (`il2cpp_ghidra.py`): Jython script using Ghidra's API
 *   to rename functions, set comments, and apply parameter types.
 * - **radare2 / rizin** (`il2cpp_r2.r2`): Simple r2 commands to set
 *   flags and comments at each method address.
 * - **Binary Ninja** (`il2cpp_binja.py`): Python script that renames
 *   functions, sets comments, and renames parameters.
 *
 * @note All script files are created in the same directory as specified
 *       in @ref sw_open.  If any file cannot be opened, it is skipped.
 */

#include "script_writer.h"
#include "il2cpp_tabledefs.h"
#include "log.h"
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <sys/stat.h>

/**
 * @brief Internal context for the script writer.
 *
 * Holds file descriptors for the four output scripts, counters for
 * methods and fields, and a deduplication buffer to avoid name clashes
 * within the same class.
 */
struct ScriptWriter {
  int fd_ida;
  int fd_ghidra;
  int fd_r2;
  int fd_binja;
  size_t method_count;
  size_t field_count;
  char dedup_key[768];
  char dedup_names[256][80];
  uint8_t dedup_cnt[256];
  int dedup_n;
};

#define SW_BUF 4096

/* -------------------------------------------------------------------------
 * Internal buffering helpers (writev‑like for small buffers)
 * ------------------------------------------------------------------------- */

/**
 * @brief Write a fixed‑size buffer to a file descriptor, retrying on short writes.
 *
 * @param fd   Output file descriptor.
 * @param buf  Data to write.
 * @param len  Number of bytes to write.
 */
static void sw_flush(int fd,
  const char * buf, size_t len) {
  if (fd < 0 || !len) return;
  size_t done = 0;
  while (done < len) {
    ssize_t r = write(fd, buf + done, len - done);
    if (r <= 0) break;
    done += (size_t) r;
  }
}

/**
 * @brief Write a NUL‑terminated string to a file descriptor.
 *
 * @param fd Output file descriptor.
 * @param s  String to write (may be NULL).
 */
static void sw_puts(int fd,
  const char * s) {
  if (fd >= 0 && s) sw_flush(fd, s, strlen(s));
}

/** Copy raw bytes `src` of length `n` into buffer `b` at position `p`, advancing `p`. */
#define SW_RAW(b, p, s, n) do { \
  size_t _n = (n); \
  if ((p) + _n < SW_BUF) { \
    memcpy((b) + (p), (s), _n); \
    (p) += _n; \
  } \
} while (0)

/** Copy NUL‑terminated string `s` into buffer. */
#define SW_STR(b, p, s) do { \
  const char * _s = (s); \
  if (_s) SW_RAW(b, p, _s, strlen(_s)); \
} while (0)

/** Write a single character `c` into buffer. */
#define SW_CHR(b, p, c) do { \
  if ((p) + 1 < SW_BUF)(b)[(p) ++] = (c); \
} while (0)

/* -------------------------------------------------------------------------
 * Formatting helpers for script output
 * ------------------------------------------------------------------------- */

/**
 * @brief Write a quoted Python string literal.
 *
 * Escapes double quotes and backslashes; drops control characters.
 *
 * @param b   Buffer.
 * @param p   Current position (in/out).
 * @param s   String to format (may be NULL).
 */
static void sw_pystr(char * b, size_t * p,
  const char * s) {
  SW_CHR(b, * p, '"');
  if (s)
    for (;* s; ++s) {
      unsigned char c = (unsigned char) * s;
      if (c == '"') {
        SW_RAW(b, * p, "\\\"", 2);
      } else if (c == '\\') {
        SW_RAW(b, * p, "\\\\", 2);
      } else if (c < 0x20) {
        /* drop */ } else {
        SW_CHR(b, * p, (char) c);
      }
    }
  SW_CHR(b, * p, '"');
}

/**
 * @brief Append an unsigned decimal number.
 */
static void sw_ulong(char * b, size_t * p, unsigned long v) {
  char tmp[24];
  int n = snprintf(tmp, sizeof(tmp), "%lu", v);
  if (n > 0) SW_RAW(b, * p, tmp, (size_t) n);
}

/**
 * @brief Append a hexadecimal number (`0x…`).
 */
static void sw_hex(char * b, size_t * p, unsigned long v) {
  char tmp[24];
  int n = snprintf(tmp, sizeof(tmp), "0x%lx", v);
  if (n > 0) SW_RAW(b, * p, tmp, (size_t) n);
}

/**
 * @brief Write a comment string for radare2 (strip newlines).
 */
static void sw_r2cmt(char * b, size_t * p,
  const char * s) {
  if (!s) return;
  for (;* s; ++s) {
    if ( * s == '\n' || * s == '\r') continue;
    SW_CHR(b, * p, * s);
  }
}

/**
 * @brief Build a sanitised label from namespace, class, and member names.
 *
 * Replaces non‑alphanumeric characters with underscores.
 *
 * @param out    Output buffer.
 * @param sz     Size of output buffer.
 * @param ns     Namespace.
 * @param cls    Class name.
 * @param member Member name (method or field).
 */
static void build_label(char * out, size_t sz,
  const char * ns,
    const char * cls,
      const char * member) {
  size_t p = 0;
  #define AP(src) do { \
    const char * _q = (src); \
    while (_q && * _q && p + 1 < sz) { \
      char _c = * _q++; \
      out[p++] = ((_c >= 'A' && _c <= 'Z') || (_c >= 'a' && _c <= 'z') || \
        (_c >= '0' && _c <= '9') || _c == '_') ? _c : '_'; \
    } \
  } while (0)
  if (ns && ns[0]) {
    AP(ns);
    if (p + 1 < sz) out[p++] = '_';
  }
  AP(cls);
  if (member && member[0]) {
    if (p + 2 < sz) {
      out[p++] = '_';
      out[p++] = '_';
    }
    AP(member);
  }
  out[p] = '\0';
  #undef AP
}

/* -------------------------------------------------------------------------
 * Flag conversion helpers
 * ------------------------------------------------------------------------- */

/**
 * @brief Convert method accessibility flags to a string.
 *
 * @param f  Method attributes.
 * @return Static string (e.g. "public", "private").
 */
static
const char * method_access(uint32_t f) {
  switch (f & METHOD_ATTRIBUTE_MEMBER_ACCESS_MASK) {
  case METHOD_ATTRIBUTE_PRIVATE:
    return "private";
  case METHOD_ATTRIBUTE_PUBLIC:
    return "public";
  case METHOD_ATTRIBUTE_FAMILY:
    return "protected";
  case METHOD_ATTRIBUTE_ASSEM:
  case METHOD_ATTRIBUTE_FAM_AND_ASSEM:
    return "internal";
  case METHOD_ATTRIBUTE_FAM_OR_ASSEM:
    return "protected internal";
  default:
    return "private";
  }
}

/**
 * @brief Convert method flags (static, abstract, etc.) to a space‑separated string.
 *
 * @param f   Method attributes.
 * @param out Output buffer.
 * @param sz  Size of output buffer.
 */
static void method_flags_str(uint32_t f, char * out, size_t sz) {
  static
  const struct {
    uint32_t m;
    const char * n;
  }
  kv[] = {
    {
      METHOD_ATTRIBUTE_STATIC,
      "static"
    },
    {
      METHOD_ATTRIBUTE_ABSTRACT,
      "abstract"
    },
    {
      METHOD_ATTRIBUTE_VIRTUAL,
      "virtual"
    },
    {
      METHOD_ATTRIBUTE_FINAL,
      "final"
    },
    {
      METHOD_ATTRIBUTE_PINVOKE_IMPL,
      "extern"
    },
    {
      0,
      NULL
    }
  };
  size_t p = 0;
  out[0] = '\0';
  int i;
  for (i = 0; kv[i].n; ++i) {
    if (!(f & kv[i].m)) continue;
    if (p > 0 && p + 1 < sz) out[p++] = ' ';
    size_t nl = strlen(kv[i].n);
    if (p + nl + 1 < sz) {
      memcpy(out + p, kv[i].n, nl);
      p += nl;
    }
  }
  out[p] = '\0';
}

/**
 * @brief Convert field accessibility flags to a string.
 */
static
const char * field_access(uint32_t a) {
  switch (a & FIELD_ATTRIBUTE_FIELD_ACCESS_MASK) {
  case FIELD_ATTRIBUTE_PRIVATE:
    return "private";
  case FIELD_ATTRIBUTE_PUBLIC:
    return "public";
  case FIELD_ATTRIBUTE_FAMILY:
    return "protected";
  case FIELD_ATTRIBUTE_ASSEMBLY:
  case FIELD_ATTRIBUTE_FAM_AND_ASSEM:
    return "internal";
  case FIELD_ATTRIBUTE_FAM_OR_ASSEM:
    return "protected internal";
  default:
    return "private";
  }
}

/* -------------------------------------------------------------------------
 * Script file headers and footers
 * ------------------------------------------------------------------------- */

static const char g_ida_hdr[] =
    "# -*- coding: utf-8 -*-\n"
    "# il2cpp_ida.py -- IDA Pro 7.x / 8.x\n"
    "# Auto-generated by il2cppdumper by muhammadrizwan87\n"
    "# https://github.com/muhammadrizwan87/il2cppdumper\n"
    "#\n"
    "# Usage  : File -> Script file -> select this file  (Alt+F7)\n"
    "# Compat : IDA Pro 7.x and 8.x (Python 3)\n"
    "# Note   : All il2cpp data is embedded; no extra files needed.\n"
    "import idaapi, idc, ida_funcs\n"
    "ENTRIES = []\n\n";

static const char g_ghidra_hdr[] =
    "# @runtime Jython\n"
    "# -*- coding: utf-8 -*-\n"
    "# @author MuhammadRizwan\n"
    "# @category Il2CppAnalysis\n"
    "# @menupath Tools.Il2CppDumper\n"
    "# @keybinding\n"
    "# @toolbar\n"
    "# il2cpp_ghidra.py -- Ghidra 10.x / 11.x\n"
    "# Auto-generated by il2cppdumper by muhammadrizwan87\n"
    "# https://github.com/muhammadrizwan87/il2cppdumper\n"
    "#\n"
    "# Usage  : Script Manager -> Run\n"
    "#          Reads dump.cs from the same directory automatically.\n"
    "# Compat : Ghidra 10.x / 11.x (Jython runtime)\n"
    "# Note   : No data embedded; works for games of any size.\n"
    "# Error  : If dump.cs reading fails, the script prints the attempted\n"
    "#          file path to the console. Check the path and ensure\n"
    "#          dump.cs is in the correct location, then re-run the script.\n"
    "# Alert  : Non-primitive types (custom classes/structs/enums) are mapped\n"
    "#          to a generic pointer. This is technically incorrect but\n"
    "#          harmless — the decompiler still shows the parameter slot.\n"
    "#          For precise typing, manually adjust the parameter type\n"
    "#          after import.\n\n";

static const char g_r2_hdr[] =
    "# il2cpp_r2.r2 -- radare2 / rizin\n"
    "# Auto-generated by il2cppdumper by muhammadrizwan87\n"
    "# https://github.com/muhammadrizwan87/il2cppdumper\n"
    "#\n"
    "# Usage  : r2 -i il2cpp_r2.r2 libil2cpp.so\n"
    "# Compat : radare2 / rizin (all versions)\n"
    "# Note   : No analysis (aaa) required.\n"
    "#\n"
    "e emu.str=true\n\n";

static const char g_binja_hdr[] =
    "# -*- coding: utf-8 -*-\n"
    "# il2cpp_binja.py -- Binary Ninja 3.x\n"
    "# Auto-generated by il2cppdumper by muhammadrizwan87\n"
    "# https://github.com/muhammadrizwan87/il2cppdumper\n"
    "#\n"
    "# Usage  : Plugins -> Run Script  (or Snippets -> Run)\n"
    "#          Headless: python3 il2cpp_binja.py /path/to/libil2cpp.so\n"
    "# Compat : Binary Ninja 3.x (Python 3)\n"
    "# Note   : All il2cpp data is embedded; no extra files needed.\n"
    "import sys, re\n"
    "_INV = re.compile(r'[^A-Za-z0-9_]')\n"
    "def _san(s): return _INV.sub('_', s or '')\n"
    "ENTRIES = []\n\n";

static const char g_ida_ftr[] =
    "\ndef _parse_params(s):\n"
    "    r=[]\n"
    "    for p in s.split(','):\n"
    "        p=p.strip()\n"
    "        if not p: continue\n"
    "        w=[x for x in p.split() if x not in('ref','out','in','[In]','[Out]')]\n"
    "        r.append(w[-1] if len(w)>=2 and w[-1]!='param' else None)\n"
    "    return r\n"
    "\ndef _apply_params(ea,names,is_static):\n"
    "    if not ida_funcs.get_func(ea): return False\n"
    "    off=0 if is_static else 1\n"
    "    if hasattr(idc,'set_arg_name'):\n"
    "        ok=False\n"
    "        for i,n in enumerate(names):\n"
    "            if not n: continue\n"
    "            try:\n"
    "                if idc.set_arg_name(ea,i+off,n): ok=True\n"
    "            except Exception: pass\n"
    "        return ok\n"
    "    try:\n"
    "        import ida_typeinf\n"
    "        tif=ida_typeinf.tinfo_t()\n"
    "        if not idaapi.get_tinfo(tif,ea):\n"
    "            if not ida_typeinf.guess_tinfo(tif,ea): return False\n"
    "        fi=ida_typeinf.func_type_data_t()\n"
    "        if not tif.get_func_details(fi): return False\n"
    "        ok=False\n"
    "        for i,n in enumerate(names):\n"
    "            if not n: continue\n"
    "            idx=i+off\n"
    "            if idx<len(fi) and fi[idx].name!=n:\n"
    "                fi[idx].name=n; ok=True\n"
    "        if not ok: return False\n"
    "        tif2=ida_typeinf.tinfo_t(); tif2.create_func(fi)\n"
    "        idaapi.apply_tinfo(ea,tif2,idaapi.TINFO_GUESSED)\n"
    "        return True\n"
    "    except Exception: return False\n"
    "\ndef _make_func(ea):\n"
    "    if not ida_funcs.get_func(ea):\n"
    "        nf = idc.get_next_func(ea)\n"
    "        ida_funcs.add_func(ea, nf if nf and nf != idaapi.BADADDR else idaapi.BADADDR)\n"
    "\ndef _run():\n"
    "    if not hasattr(__import__('idaapi'),'get_imagebase'):\n"
    "        print('[il2cpp] Run inside IDA Pro'); return\n"
    "    base=idaapi.get_imagebase()\n"
    "    renamed=skipped=failed=typed=0\n"
    "    idaapi.show_wait_box('Il2Cpp: renaming %d...'%len(ENTRIES))\n"
    "    try:\n"
    "        for idx,(label,rva,acc,fl,ret,meth,params,dll,ns,cls) in enumerate(ENTRIES):\n"
    "            if rva==0: skipped+=1; continue\n"
    "            if idx%500==0:\n"
    "                idaapi.replace_wait_box('Il2Cpp %d/%d renamed=%d'%(idx,len(ENTRIES),renamed))\n"
    "                if idaapi.user_cancelled(): break\n"
    "            ea=base+rva\n"
    "            _make_func(ea)\n"
    "            if not idc.set_name(ea,label,idc.SN_NOWARN|idc.SN_NOCHECK):\n"
    "                failed+=1; continue\n"
    "            sp=[p for p in[acc,fl]if p]\n"
    "            idc.set_cmt(ea,'[il2cpp] %s | %s.%s | %s %s %s(%s)'%(\n"
    "                dll,ns,cls,' '.join(sp),ret,meth,params),1)\n"
    "            if params.strip():\n"
    "                pn=_parse_params(params)\n"
    "                if any(pn):\n"
    "                    try:\n"
    "                        if _apply_params(ea,pn,'static' in fl): typed+=1\n"
    "                    except Exception: pass\n"
    "            renamed+=1\n"
    "    finally: idaapi.hide_wait_box()\n"
    "    msg='[il2cpp] renamed=%d typed=%d skipped=%d failed=%d'%(renamed,typed,skipped,failed)\n"
    "    print(msg); idaapi.info(msg)\n"
    "\n_run()\n";

static const char g_ghidra_ftr[] =
    "\n"
    "import os as _os, re as _re\n"
    "from ghidra.program.model.symbol import SourceType\n"
    "from ghidra.app.cmd.function import CreateFunctionCmd\n"
    "from ghidra.program.model.listing import ParameterImpl, Function\n"
    "from ghidra.program.model.listing import VariableStorage\n"
    "try:\n"
    "    import io as _io\n"
    "    def _open(p): return _io.open(p,\'r\',encoding=\'utf-8\',errors=\'replace\')\n"
    "except Exception:\n"
    "    def _open(p): return open(p,\'r\')\n"
    "\n"
    "def _san(s):\n"
    "    if not s: return \'\'\n"
    "    return _re.sub(r\'[^A-Za-z0-9_]\',\'_\',s)\n"
    "\n"
    "def _label(ns,cls,meth):\n"
    "    if ns: return _san(ns)+\'_\'+_san(cls)+\'__\'+_san(meth)\n"
    "    return _san(cls)+\'__\'+_san(meth)\n"
    "\n"
    "def _parse_dump(path):\n"
    "    entries=[]\n"
    "    _dedup_key=[None]\n"
    "    _dedup_cnt={}\n"
    "    def _unique_label(dll,ns,cls,meth):\n"
    "        key=(dll,ns,cls)\n"
    "        if _dedup_key[0]!=key:\n"
    "            _dedup_key[0]=key\n"
    "            _dedup_cnt.clear()\n"
    "        n=_dedup_cnt.get(meth,0)+1\n"
    "        _dedup_cnt[meth]=n\n"
    "        base=_label(ns,cls,meth)\n"
    "        return base if n==1 else (\'%s_%d\'%(base,n))\n"
    "    try:\n"
    "        f=_open(path); ls=f.readlines(); f.close()\n"
    "    except Exception as e:\n"
    "        print(\'[il2cpp] Cannot read dump.cs: %s\'%e); return entries\n"
    "    RVA=_re.compile(r\'RVA:\\s*(0x[0-9a-fA-F]+)\')\n"
    "    CLS=_re.compile(r\'\\s*(?:\\S+\\s+)*(?:class|struct|interface|enum)\\s+(\\w+)\')\n"
    "    MTH=_re.compile(r\'\\s*(public|private|protected|internal|protected\\s+internal)?\\s*((?:(?:static|abstract|virtual|override|sealed|extern|new)\\s+)*)([\\S]+)\\s+(\\w+)\\s*\\(([^)]*)\\)\')\n"
    "    dll=\'unknown\';ns=\'\';cls=\'\';i=0;N=len(ls)\n"
    "    while i<N:\n"
    "        s=ls[i].strip()\n"
    "        if s.startswith(\'// Dll:\'): dll=s[7:].strip();i+=1;continue\n"
    "        if s.startswith(\'// Namespace:\'): ns=s[13:].strip();i+=1;continue\n"
    "        if s.startswith(\'// RVA:\'):\n"
    "            m=RVA.search(s);rva=int(m.group(1),16) if m else 0\n"
    "            i+=1\n"
    "            if i<N and cls:\n"
    "                m2=MTH.match(ls[i].strip())\n"
    "                if m2:\n"
    "                    acc=(m2.group(1) or \'private\').strip()\n"
    "                    flags=(m2.group(2) or \'\').strip()\n"
    "                    ret=(m2.group(3) or \'\').strip()\n"
    "                    mn=(m2.group(4) or \'\').strip()\n"
    "                    params=(m2.group(5) or \'\').strip()\n"
    "                    entries.append((_unique_label(dll,ns,cls,mn),rva,acc,flags,ret,mn,params,dll,ns,cls))\n"
    "            i+=1;continue\n"
    "        if not s.startswith(\'//\'):\n"
    "            m=CLS.match(s)\n"
    "            if m: cls=m.group(1) or cls\n"
    "        i+=1\n"
    "    return entries\n"
    "\n"
    "def _parse_params_typed(s):\n"
    "    r=[]\n"
    "    for p in s.split(\',\'):\n"
    "        p=p.strip()\n"
    "        if not p: continue\n"
    "        w=[x for x in p.split() if x not in(\'ref\',\'out\',\'in\',\'[In]\',\'[Out]\')]\n"
    "        if len(w)>=2:\n"
    "            tname=w[-2]\n"
    "            pname=w[-1] if w[-1]!=\'param\' else None\n"
    "            r.append((tname,pname))\n"
    "        else:\n"
    "            r.append((None,None))\n"
    "    return r\n"
    "\n"
    "_GHIDRA_TYPE_MAP_CACHE={}\n"
    "def _ghidra_type(type_name):\n"
    "    if type_name in _GHIDRA_TYPE_MAP_CACHE:\n"
    "        return _GHIDRA_TYPE_MAP_CACHE[type_name]\n"
    "    dtm=currentProgram.getDataTypeManager()\n"
    "    builtin=ghidra.program.model.data\n"
    "    table={\n"
    "        \'Void\':    builtin.VoidDataType.dataType,\n"
    "        \'Boolean\': builtin.BooleanDataType.dataType,\n"
    "        \'Byte\':    builtin.ByteDataType.dataType,\n"
    "        \'SByte\':   builtin.SignedByteDataType.dataType,\n"
    "        \'Char\':    builtin.CharDataType.dataType,\n"
    "        \'Int16\':   builtin.ShortDataType.dataType,\n"
    "        \'UInt16\':  builtin.UnsignedShortDataType.dataType,\n"
    "        \'Int32\':   builtin.IntegerDataType.dataType,\n"
    "        \'UInt32\':  builtin.UnsignedIntegerDataType.dataType,\n"
    "        \'Int64\':   builtin.LongLongDataType.dataType,\n"
    "        \'UInt64\':  builtin.UnsignedLongLongDataType.dataType,\n"
    "        \'Single\':  builtin.FloatDataType.dataType,\n"
    "        \'Double\':  builtin.DoubleDataType.dataType,\n"
    "        \'IntPtr\':  builtin.PointerDataType.dataType,\n"
    "        \'UIntPtr\': builtin.PointerDataType.dataType,\n"
    "        \'String\':  builtin.PointerDataType.dataType,\n"
    "    }\n"
    "    dt=table.get(type_name)\n"
    "    if dt is None:\n"
    "        base=type_name.rstrip(\'[]\') if type_name else \'\'\n"
    "        dt=table.get(base, builtin.PointerDataType.dataType)\n"
    "    _GHIDRA_TYPE_MAP_CACHE[type_name]=dt\n"
    "    return dt\n"
    "\n"
    "def _apply_params(func,typed_params,is_static):\n"
    "    def _mk(name, dt):\n"
    "        attempts = []\n"
    "        try:\n"
    "            return ParameterImpl(name, dt, currentProgram)\n"
    "        except Exception as e:\n"
    "            attempts.append(\'(name,dt,program): %s\' % e)\n"
    "        try:\n"
    "            return ParameterImpl(name, dt, VariableStorage.UNASSIGNED_STORAGE, currentProgram)\n"
    "        except Exception as e:\n"
    "            attempts.append(\'(name,dt,UNASSIGNED_STORAGE,program): %s\' % e)\n"
    "        print(\'[il2cpp][ghidra-param-debug] _mk(%s) failed all attempts: %s\' % (name, \' | \'.join(attempts)))\n"
    "        return None\n"
    "\n"
    "    try:\n"
    "        new_params = []\n"
    "        if not is_static:\n"
    "            p = _mk(\'this\', ghidra.program.model.data.PointerDataType.dataType)\n"
    "            if p: new_params.append(p)\n"
    "        seen_names = set([\'this\'])\n"
    "        for i, (tname, pname) in enumerate(typed_params):\n"
    "            dt = _ghidra_type(tname) if tname else \\\n"
    "                 ghidra.program.model.data.Undefined4DataType.dataType\n"
    "            nm = _san(pname) if pname else (\'param_%d\' % (i + 1))\n"
    "            if nm in seen_names:\n"
    "                nm = \'%s_%d\' % (nm, i + 1)\n"
    "            seen_names.add(nm)\n"
    "            p = _mk(nm, dt)\n"
    "            if p: new_params.append(p)\n"
    "        if not new_params:\n"
    "            print(\'[il2cpp][ghidra-param-debug] %s: no params could be built\' % func.getName())\n"
    "            return False\n"
    "        func.replaceParameters(\n"
    "            new_params,\n"
    "            Function.FunctionUpdateType.DYNAMIC_STORAGE_FORMAL_PARAMS,\n"
    "            True, SourceType.USER_DEFINED)\n"
    "        return True\n"
    "    except Exception as e:\n"
    "        print(\'[il2cpp][ghidra-param-debug] %s: replaceParameters failed: %s\' % (func.getName(), e))\n"
    "        return False\n"
    "\n"
    "def _run(entries):\n"
    "    base=currentProgram.getImageBase()\n"
    "    fm=currentProgram.getFunctionManager()\n"
    "    bm=currentProgram.getBookmarkManager()\n"
    "    renamed=skipped=failed=created=typed=0\n"
    "    monitor.setMaximum(len(entries))\n"
    "    for idx,(label,rva,acc,flags,ret,meth,params,dll,ns,cls) in enumerate(entries):\n"
    "        monitor.setProgress(idx)\n"
    "        if monitor.isCancelled(): break\n"
    "        if rva==0: skipped+=1;continue\n"
    "        try:\n"
    "            ea=base.add(rva);func=fm.getFunctionAt(ea)\n"
    "            if func is None:\n"
    "                CreateFunctionCmd(ea).applyTo(currentProgram)\n"
    "                func=fm.getFunctionAt(ea)\n"
    "                if func: created+=1\n"
    "            if func is None: failed+=1;continue\n"
    "            try: func.setName(label,SourceType.USER_DEFINED)\n"
    "            except Exception: failed+=1;continue\n"
    "            sp=[p for p in[acc,flags]if p]\n"
    "            func.setComment(\'[il2cpp] %s | %s.%s | %s %s %s(%s)\'%(\n"
    "                dll,ns,cls,\' \'.join(sp),ret,meth,params))\n"
    "            bm.setBookmark(ea,\'il2cpp\',dll,\'%s.%s\'%(ns,cls))\n"
    "            if params.strip():\n"
    "                tp=_parse_params_typed(params)\n"
    "                if any(t or n for t,n in tp):\n"
    "                    try:\n"
    "                        if _apply_params(func,tp,\'static\' in flags): typed+=1\n"
    "                    except Exception: pass\n"
    "            renamed+=1\n"
    "        except Exception: failed+=1\n"
    "    print(\'[il2cpp] Done: renamed=%d typed=%d created=%d skipped=%d failed=%d total=%d\'%(\n"
    "        renamed,typed,created,skipped,failed,len(entries)))\n"
    "\n"
    "try: _dir=str(getScriptFile().parentFile.absolutePath)\n"
    "except Exception: _dir=_os.path.dirname(_os.path.abspath(\'.\'))\n"
    "_dump=_os.path.join(_dir,\'dump.cs\')\n"
    "print(\'[il2cpp] Reading: %s\'%_dump)\n"
    "_entries=_parse_dump(_dump)\n"
    "print(\'[il2cpp] Parsed %d methods\'%len(_entries))\n"
    "_run(_entries)\n";

static const char g_binja_ftr[] =
    "\ndef _parse_params(s):\n"
    "    r=[]\n"
    "    for p in s.split(','):\n"
    "        p=p.strip()\n"
    "        if not p: continue\n"
    "        w=[x for x in p.split() if x not in('ref','out','in','[In]','[Out]')]\n"
    "        r.append(w[-1] if len(w)>=2 and w[-1]!='param' else None)\n"
    "    return r\n"
    "\ndef _apply_params(func,names,is_static):\n"
    "    pvars=list(func.parameter_vars)\n"
    "    if not pvars: return False\n"
    "    off=0 if is_static else 1\n"
    "    ok=False\n"
    "    for i,n in enumerate(names):\n"
    "        if not n: continue\n"
    "        idx=i+off\n"
    "        if idx<len(pvars):\n"
    "            try: pvars[idx].set_name_async(n); ok=True\n"
    "            except Exception: pass\n"
    "    return ok\n"
    "\ndef _apply(bv):\n"
    "    import binaryninja as bn\n"
    "    tt=bv.get_tag_type('il2cpp') or bv.create_tag_type('il2cpp','\\U0001f3ae')\n"
    "    base=bv.start\n"
    "    renamed=skipped=failed=created=typed=0\n"
    "    needs_upd=False\n"
    "    for idx,(label,rva,acc,fl,ret,meth,params,dll,ns,cls) in enumerate(ENTRIES):\n"
    "        if rva==0: skipped+=1; continue\n"
    "        va=base+rva\n"
    "        funcs=bv.get_functions_at(va)\n"
    "        func=funcs[0] if funcs else None\n"
    "        if func is None:\n"
    "            bv.create_user_function(va); bv.update_analysis_and_wait()\n"
    "            funcs=bv.get_functions_at(va)\n"
    "            func=funcs[0] if funcs else None\n"
    "            if func: created+=1\n"
    "        if func is None: failed+=1; continue\n"
    "        try: func.name=label\n"
    "        except Exception: failed+=1; continue\n"
    "        sp=[p for p in[acc,fl]if p]\n"
    "        bv.set_comment_at(va,'[il2cpp] %s | %s.%s | %s %s %s(%s)'%(\n"
    "            dll,ns,cls,' '.join(sp),ret,meth,params))\n"
    "        try: func.add_tag(tt,'%s.%s'%(ns,cls))\n"
    "        except Exception: pass\n"
    "        if params.strip():\n"
    "            pn=_parse_params(params)\n"
    "            if any(pn):\n"
    "                try:\n"
    "                    if _apply_params(func,pn,'static' in fl):\n"
    "                        typed+=1; needs_upd=True\n"
    "                except Exception: pass\n"
    "        renamed+=1\n"
    "        if idx%500==0:\n"
    "            print('[il2cpp] %d/%d renamed=%d'%(idx,len(ENTRIES),renamed))\n"
    "    if needs_upd: bv.update_analysis_and_wait()\n"
    "    print('[il2cpp] renamed=%d typed=%d created=%d skipped=%d failed=%d'%(\n"
    "        renamed,typed,created,skipped,failed))\n"
    "\nif __name__=='__main__':\n"
    "    import binaryninja as bn\n"
    "    if len(sys.argv)>=2:\n"
    "        with bn.open_view(sys.argv[1]) as bv:\n"
    "            bv.update_analysis_and_wait(); _apply(bv)\n"
    "            bv.create_database(sys.argv[1]+'.bndb')\n"
    "    else:\n"
    "        try: _apply(current_view)\n"
    "        except Exception:\n"
    "            from binaryninjaui import UIContext\n"
    "            _apply(UIContext.activeContext().contentActionHandler().actionContext().binaryView)\n"
    "else:\n"
    "    try: _apply(current_view)\n"
    "    except Exception:\n"
    "        from binaryninjaui import UIContext\n"
    "        _apply(UIContext.activeContext().contentActionHandler().actionContext().binaryView)\n";

/* -------------------------------------------------------------------------
 * File opening helper
 * ------------------------------------------------------------------------- */

/**
 * @brief Open a script file and write the header.
 *
 * Constructs the full path, creates the file, and writes the provided
 * header string.  On failure, logs a warning and returns -1.
 *
 * @param dir   Output directory.
 * @param name  Filename (e.g. "il2cpp_ida.py").
 * @param hdr   Header string to write at the start of the file.
 * @return File descriptor, or -1 on error.
 */
static int open_fd(const char * dir,
  const char * name,
    const char * hdr) {
  char path[4096];
  snprintf(path, sizeof(path), "%s/%s", dir, name);
  int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
  if (fd < 0) {
    LOGW("script_writer: cannot open %s errno=%d (%s)",
      path, errno, strerror(errno));
    return -1;
  }
  sw_puts(fd, hdr);
  LOGI("script_writer: opened %s", path);
  return fd;
}

/* -------------------------------------------------------------------------
 * Public API implementation
 * ------------------------------------------------------------------------- */

/**
 * @brief Open the script writer and create output files.
 *
 * Creates four files in the specified directory with their respective
 * headers.  If any file cannot be opened, it is skipped.  If all four
 * fail, the function returns NULL.
 *
 * @param output_dir  Directory where script files will be written.
 * @return Pointer to ScriptWriter, or NULL on failure.
 */
ScriptWriter * sw_open(const char * output_dir) {
  if (!output_dir || !output_dir[0]) return NULL;

  ScriptWriter * sw = (ScriptWriter * ) calloc(1, sizeof(ScriptWriter));
  if (!sw) return NULL;

  sw -> fd_ida = open_fd(output_dir, "il2cpp_ida.py", g_ida_hdr);
  sw -> fd_ghidra = open_fd(output_dir, "il2cpp_ghidra.py", g_ghidra_hdr);
  sw -> fd_r2 = open_fd(output_dir, "il2cpp_r2.r2", g_r2_hdr);
  sw -> fd_binja = open_fd(output_dir, "il2cpp_binja.py", g_binja_hdr);

  int n = (sw -> fd_ida >= 0) + (sw -> fd_ghidra >= 0) + (sw -> fd_r2 >= 0) + (sw -> fd_binja >= 0);
  if (n == 0) {
    LOGE("script_writer: all files failed");
    free(sw);
    return NULL;
  }
  LOGI("script_writer: %d/4 files opened in %s", n, output_dir);
  return sw;
}

/**
 * @brief Write a method entry to all open script files.
 *
 * Formats the method metadata and appends it to each open script file.
 * Deduplication is performed per assembly/class to avoid name clashes.
 *
 * @param sw      ScriptWriter handle.
 * @param dll     Assembly name.
 * @param ns      Namespace.
 * @param cls     Class name.
 * @param method  Method name.
 * @param ret     Return type as a string.
 * @param params  Parameter list as a single string.
 * @param flags   Method attribute flags.
 * @param rva     Relative virtual address.
 * @param va      Absolute virtual address.
 */
void sw_write_method(ScriptWriter * sw,
  const char * dll,
    const char * ns,
      const char * cls,
        const char * method,
          const char * ret,
            const char * params,
              uint32_t flags, uintptr_t rva, uintptr_t va) {
  if (!sw) return;

  char label[1024], fstr[128];
  build_label(label, sizeof(label), ns, cls, method);
  method_flags_str(flags, fstr, sizeof(fstr));
  const char * acc = method_access(flags);

  // Deduplication: if multiple methods have the same name within a class,
  // append a numeric suffix to the label.
  {
    char key[768];
    int kn = snprintf(key, sizeof(key), "%s|%s|%s",
      dll ? dll : "", ns ? ns : "", cls ? cls : "");
    if (kn < 0) kn = 0;
    if ((size_t) kn >= sizeof(key)) kn = (int) sizeof(key) - 1;

    if (strcmp(sw -> dedup_key, key) != 0) {
      strncpy(sw -> dedup_key, key, sizeof(sw -> dedup_key) - 1);
      sw -> dedup_key[sizeof(sw -> dedup_key) - 1] = '\0';
      sw -> dedup_n = 0;
    }

    int found = -1, i;
    for (i = 0; i < sw -> dedup_n; ++i) {
      if (strncmp(sw -> dedup_names[i], method ? method : "",
          sizeof(sw -> dedup_names[0])) == 0) {
        found = i;
        break;
      }
    }
    if (found < 0 && sw -> dedup_n < 256) {
      found = sw -> dedup_n++;
      strncpy(sw -> dedup_names[found], method ? method : "",
        sizeof(sw -> dedup_names[0]) - 1);
      sw -> dedup_names[found][sizeof(sw -> dedup_names[0]) - 1] = '\0';
      sw -> dedup_cnt[found] = 0;
    }
    if (found >= 0) {
      sw -> dedup_cnt[found]++;
      if (sw -> dedup_cnt[found] > 1) {
        size_t ll = strlen(label);
        if (ll + 12 < sizeof(label))
          snprintf(label + ll, sizeof(label) - ll, "_%u",
            (unsigned) sw -> dedup_cnt[found]);
      }
    }
  }

  if (!dll) dll = "";
  if (!ns) ns = "";
  if (!cls) cls = "";
  if (!method) method = "";
  if (!ret) ret = "";
  if (!params) params = "";

  // Write to IDA and Binary Ninja (Python scripts)
  #define WR_PY(fd) do { \
    if ((fd) >= 0) { \
      char _b[SW_BUF]; \
      size_t _p = 0; \
      SW_STR(_b, _p, "ENTRIES.append(("); \
      sw_pystr(_b, & _p, label); \
      SW_STR(_b, _p, ", "); \
      sw_ulong(_b, & _p, (unsigned long) rva); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, acc); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, fstr); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, ret); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, method); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, params); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, dll); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, ns); \
      SW_STR(_b, _p, ", "); \
      sw_pystr(_b, & _p, cls); \
      SW_STR(_b, _p, "))\n"); \
      sw_flush((fd), _b, _p); \
    } \
  } while (0)

  WR_PY(sw -> fd_ida);
  WR_PY(sw -> fd_binja);
  #undef WR_PY

  // Write to radare2 (r2 commands)
  if (sw -> fd_r2 >= 0 && rva > 0) {
    char r2cls[512], r2lib[512];
    build_label(r2cls, sizeof(r2cls), ns, cls, NULL);
    build_label(r2lib, sizeof(r2lib), dll, NULL, NULL);

    char buf[SW_BUF];
    size_t pos = 0;

    SW_STR(buf, pos, "'@");
    sw_hex(buf, & pos, (unsigned long) rva);
    SW_STR(buf, pos, "'f lib.");
    SW_STR(buf, pos, r2lib);
    SW_CHR(buf, pos, '\n');
    SW_STR(buf, pos, "'@");
    sw_hex(buf, & pos, (unsigned long) rva);
    SW_STR(buf, pos, "'f class.");
    SW_STR(buf, pos, r2cls);
    SW_CHR(buf, pos, '\n');
    SW_STR(buf, pos, "'@");
    sw_hex(buf, & pos, (unsigned long) rva);
    SW_STR(buf, pos, "'f method.");
    SW_STR(buf, pos, label);
    SW_CHR(buf, pos, '\n');
    SW_STR(buf, pos, "'@");
    sw_hex(buf, & pos, (unsigned long) rva);
    SW_STR(buf, pos, "'CC [il2cpp] ");
    sw_r2cmt(buf, & pos, dll);
    SW_STR(buf, pos, " | ");
    sw_r2cmt(buf, & pos, ns);
    if (ns[0]) SW_CHR(buf, pos, '.');
    sw_r2cmt(buf, & pos, cls);
    SW_STR(buf, pos, " | ");
    SW_STR(buf, pos, acc);
    if (fstr[0]) {
      SW_CHR(buf, pos, ' ');
      SW_STR(buf, pos, fstr);
    }
    SW_CHR(buf, pos, ' ');
    SW_STR(buf, pos, ret);
    SW_CHR(buf, pos, ' ');
    SW_STR(buf, pos, method);
    SW_CHR(buf, pos, '(');
    sw_r2cmt(buf, & pos, params);
    SW_STR(buf, pos, ")\n");
    if (method[0]) {
      SW_STR(buf, pos, "'@");
      sw_hex(buf, & pos, (unsigned long) rva);
      SW_STR(buf, pos, "'ic+");
      SW_STR(buf, pos, r2cls);
      SW_CHR(buf, pos, '.');
      SW_STR(buf, pos, method);
      SW_CHR(buf, pos, '\n');
    }
    SW_CHR(buf, pos, '\n');
    sw_flush(sw -> fd_r2, buf, pos);
  }

  sw -> method_count++;
}

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
 * @param attrs   Field attribute flags.
 * @param offset  Memory offset of the field.
 */
void sw_write_field(ScriptWriter * sw,
  const char * dll,
    const char * ns,
      const char * cls,
        const char * field,
          const char * type,
            uint32_t attrs, size_t offset) {
  if (!sw) return;
  (void) dll;

  const char * acc = field_access(attrs);
  const char * s = (attrs & FIELD_ATTRIBUTE_STATIC) ? "static " : "";
  const char * c = (attrs & FIELD_ATTRIBUTE_LITERAL) ? "const " : "";
  const char * ro = (attrs & FIELD_ATTRIBUTE_INIT_ONLY) ? "readonly " : "";

  if (!ns) ns = "";
  if (!cls) cls = "";
  if (!field) field = "";
  if (!type) type = "";

  char hex[24];
  snprintf(hex, sizeof(hex), "%zx", offset);

  #define WR_FIELD(fd) do { \
    if ((fd) >= 0) { \
      char _b[SW_BUF]; \
      size_t _p = 0; \
      SW_STR(_b, _p, "# FIELD offset=0x"); \
      SW_STR(_b, _p, hex); \
      SW_STR(_b, _p, " | "); \
      SW_STR(_b, _p, acc); \
      SW_CHR(_b, _p, ' '); \
      SW_STR(_b, _p, c); \
      SW_STR(_b, _p, s); \
      SW_STR(_b, _p, ro); \
      SW_STR(_b, _p, type); \
      SW_CHR(_b, _p, ' '); \
      if (ns[0]) { \
        SW_STR(_b, _p, ns); \
        SW_CHR(_b, _p, '.'); \
      } \
      SW_STR(_b, _p, cls); \
      SW_CHR(_b, _p, '.'); \
      SW_STR(_b, _p, field); \
      SW_CHR(_b, _p, '\n'); \
      sw_flush((fd), _b, _p); \
    } \
  } while (0)

  WR_FIELD(sw -> fd_ida);
  WR_FIELD(sw -> fd_ghidra);
  WR_FIELD(sw -> fd_binja);
  #undef WR_FIELD

  sw -> field_count++;
}

/**
 * @brief Close the script writer and finalise all files.
 *
 * Appends the footer to each script file, closes all file descriptors,
 * and frees the ScriptWriter structure.
 *
 * @param sw  ScriptWriter handle (may be NULL).
 */
void sw_close(ScriptWriter * sw) {
  if (!sw) return;

  if (sw -> fd_ida >= 0) {
    sw_puts(sw -> fd_ida, g_ida_ftr);
    close(sw -> fd_ida);
  }
  if (sw -> fd_ghidra >= 0) {
    sw_puts(sw -> fd_ghidra, g_ghidra_ftr);
    close(sw -> fd_ghidra);
  }
  if (sw -> fd_binja >= 0) {
    sw_puts(sw -> fd_binja, g_binja_ftr);
    close(sw -> fd_binja);
  }
  if (sw -> fd_r2 >= 0) {
    char buf[128];
    size_t p = 0;
    SW_STR(buf, p, "\n# methods: ");
    {
      char t[24];
      snprintf(t, sizeof(t), "%zu", sw -> method_count);
      SW_STR(buf, p, t);
    }
    SW_STR(buf, p, "  fields: ");
    {
      char t[24];
      snprintf(t, sizeof(t), "%zu", sw -> field_count);
      SW_STR(buf, p, t);
    }
    SW_STR(buf, p, "\n# Use 'f method.' to list all flags.\n");
    sw_flush(sw -> fd_r2, buf, p);
    close(sw -> fd_r2);
  }

  LOGI("script_writer: done methods=%zu fields=%zu",
    sw -> method_count, sw -> field_count);
  free(sw);
}