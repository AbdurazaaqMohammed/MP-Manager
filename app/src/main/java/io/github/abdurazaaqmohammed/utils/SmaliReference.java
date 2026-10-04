package io.github.abdurazaaqmohammed.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Smali/dalvik instruction reference.
 *
 * <p>A static lookup table rather than something scraped: an opcode's meaning is
 * fixed by the Dalvik specification, so there is nothing to refresh. Kept here
 * as plain data so it can be searched offline and read without a decompiler.
 */
public final class SmaliReference {

    /** One row: mnemonic, operand form, and what it does. */
    public static final class Op {
        public final String opcode;
        public final String form;
        public final String description;
        public final String group;

        Op(String opcode, String form, String group, String description) {
            this.opcode = opcode;
            this.form = form;
            this.group = group;
            this.description = description;
        }

        boolean matches(String query) {
            if (query == null || query.isEmpty()) return true;
            String q = query.toLowerCase(Locale.ENGLISH);
            return opcode.toLowerCase(Locale.ENGLISH).contains(q)
                    || description.contains(query)
                    || form.toLowerCase(Locale.ENGLISH).contains(q)
                    || group.contains(query);
        }
    }

    private static final List<Op> ALL;

    static {
        List<Op> l = new ArrayList<>();
        // format is opcode, operand form, group, description
        l.addAll(Arrays.asList(
                new Op("move", "move vA, vB", "数据传送", "寄存器间传送"),
                new Op("move/from16", "move/from16 vAA, vBBBB", "数据传送", "16 位寄存器源"),
                new Op("move/16", "move/16 vAAAA, vBBBB", "数据传送", "16 位传送"),
                new Op("move-wide", "move-wide vA, vB", "数据传送", "宽（64 位）寄存器传送"),
                new Op("move-wide/from16", "move-wide/from16 vAA, vBBBB", "数据传送", "宽传送，16 位源"),
                new Op("move-wide/16", "move-wide/16 vAAAA, vBBBB", "数据传送", "宽传送，16 位"),
                new Op("move-object", "move-object vA, vB", "数据传送", "传送对象引用"),
                new Op("move-object/from16", "move-object/from16 vAA, vBBBB", "数据传送", "对象引用，16 位源"),
                new Op("move-object/16", "move-object/16 vAAAA, vBBBB", "数据传送", "对象引用，16 位"),
                new Op("move-result", "move-result vAA", "数据传送", "取最近一次调用结果"),
                new Op("move-result-wide", "move-result-wide vAA", "数据传送", "取宽结果"),
                new Op("move-result-object", "move-result-object vAA", "数据传送", "取对象结果"),
                new Op("move-exception", "move-exception vAA", "数据传送", "取最近抛出的异常"),
                new Op("return-void", "return-void", "返回", "无返回值返回"),
                new Op("return", "return vAA", "返回", "返回值返回"),
                new Op("return-wide", "return-wide vAA", "返回", "宽值返回"),
                new Op("return-object", "return-object vAA", "返回", "对象返回"),
                new Op("const/4", "const/4 vA, #+B", "常量", "4 位立即数（-8..7）"),
                new Op("const/16", "const/16 vAA, #+BBBB", "常量", "16 位立即数"),
                new Op("const", "const vAA, #+BBBBBBBB", "常量", "32 位立即数"),
                new Op("const/high16", "const/high16 vAA, #+BBBB0000", "常量", "16 位立即数左移 16 位"),
                new Op("const-wide/16", "const-wide/16 vAA, #+BBBB", "常量", "16 位立即数，扩展为宽"),
                new Op("const-wide/32", "const-wide/32 vAA, #+BBBBBBBB", "常量", "32 位立即数，扩展为宽"),
                new Op("const-wide", "const-wide vAA, #+BBBBBBBBBBBBBBBB", "常量", "64 位立即数"),
                new Op("const-wide/high16", "const-wide/high16 vAA, #+BBBB000000000000", "常量", "16 位立即数左移 48 位"),
                new Op("const-string", "const-string vAA, string@BBBB", "常量", "字符串引用"),
                new Op("const-string/jumbo", "const-string/jumbo vAA, string@BBBBBBBB", "常量", "字符串引用（32 位索引）"),
                new Op("const-class", "const-class vAA, type@BBBB", "常量", "类引用（Class 对象）"),
                new Op("monitor-enter", "monitor-enter vAA", "同步", "进入同步块，获取对象锁"),
                new Op("monitor-exit", "monitor-exit vAA", "同步", "退出同步块，释放对象锁"),
                new Op("check-cast", "check-cast vAA, type@BBBB", "类型", "运行时类型检查并转型"),
                new Op("instance-of", "instance-of vA, vB, type@CCCC", "类型", "判断对象是否某类型"),
                new Op("array-length", "array-length vA, vB", "类型", "取数组长度"),
                new Op("new-instance", "new-instance vAA, type@BBBB", "对象", "新建对象实例"),
                new Op("new-array", "new-array vA, vB, type@CCCC", "对象", "新建指定类型的数组"),
                new Op("filled-new-array", "filled-new-array {vC..vG}, type@BBBB", "对象", "创建并填充数组"),
                new Op("filled-new-array/range", "filled-new-array/range {vCCCC..vNNNN}, type@BBBB", "对象", "按范围创建并填充数组"),
                new Op("fill-array-data", "fill-array-data vAA, +BBBBBBBB", "对象", "用数据填充数组"),
                new Op("throw", "throw vAA", "异常", "抛出异常"),
                new Op("goto", "goto +AA", "跳转", "无条件跳转（±8 字节）"),
                new Op("goto/16", "goto/16 +AAAA", "跳转", "无条件跳转（16 位）"),
                new Op("goto/32", "goto/32 +AAAAAAAA", "跳转", "无条件跳转（32 位）"),
                new Op("packed-switch", "packed-switch vAA, +BBBBBBBB", "跳转", "查表跳转（紧凑 switch）"),
                new Op("sparse-switch", "sparse-switch vAA, +BBBBBBBB", "跳转", "查表跳转（稀疏 switch）"),
                new Op("cmpl-float", "cmpl-float", "比较", "比较浮点，相等返回 0"),
                new Op("cmpg-float", "cmpg-float", "比较", "比较浮点，NaN 返回 1"),
                new Op("cmpl-double", "cmpl-double", "比较", "比较双精度浮点"),
                new Op("cmpg-double", "cmpg-double", "比较", "比较双精度浮点，NaN 返回 1"),
                new Op("cmp-long", "cmp-long vAA, vBB, vCC", "比较", "比较长整数"),
                new Op("if-eq", "if-eq vA, vB, +CCCC", "条件跳转", "等于则跳转"),
                new Op("if-ne", "if-ne vA, vB, +CCCC", "条件跳转", "不等于则跳转"),
                new Op("if-lt", "if-lt vA, vB, +CCCC", "条件跳转", "小于则跳转"),
                new Op("if-ge", "if-ge vA, vB, +CCCC", "条件跳转", "大于等于则跳转"),
                new Op("if-gt", "if-gt vA, vB, +CCCC", "条件跳转", "大于则跳转"),
                new Op("if-le", "if-le vA, vB, +CCCC", "条件跳转", "小于等于则跳转"),
                new Op("if-eqz", "if-eqz vAA, +BBBB", "条件跳转", "等于 0 则跳转"),
                new Op("if-nez", "if-nez vAA, +BBBB", "条件跳转", "不等于 0 则跳转"),
                new Op("if-ltz", "if-ltz vAA, +BBBB", "条件跳转", "小于 0 则跳转"),
                new Op("if-gez", "if-gez vAA, +BBBB", "条件跳转", "大于等于 0 则跳转"),
                new Op("if-gtz", "if-gtz vAA, +BBBB", "条件跳转", "大于 0 则跳转"),
                new Op("if-lez", "if-lez vAA, +BBBB", "条件跳转", "小于等于 0 则跳转"),
                new Op("aget", "aget vAA, vBB, vCC", "数组", "取数组元素"),
                new Op("aget-wide", "aget-wide vAA, vBB, vCC", "数组", "取宽数组元素"),
                new Op("aget-object", "aget-object vAA, vBB, vCC", "数组", "取数组中的对象"),
                new Op("aget-boolean", "aget-boolean vAA, vBB, vCC", "数组", "取数组中的布尔值"),
                new Op("aget-byte", "aget-byte vAA, vBB, vCC", "数组", "取数组中的字节"),
                new Op("aget-char", "aget-char vAA, vBB, vCC", "数组", "取数组中的字符"),
                new Op("aget-short", "aget-short vAA, vBB, vCC", "数组", "取数组中的短整数"),
                new Op("aput", "aput vAA, vBB, vCC", "数组", "写数组元素"),
                new Op("aput-wide", "aput-wide vAA, vBB, vCC", "数组", "写宽数组元素"),
                new Op("aput-object", "aput-object vAA, vBB, vCC", "数组", "写数组中的对象"),
                new Op("aput-boolean", "aput-boolean vAA, vBB, vCC", "数组", "写布尔值到数组"),
                new Op("aput-byte", "aput-byte vAA, vBB, vCC", "数组", "写字节到数组"),
                new Op("aput-char", "aput-char vAA, vBB, vCC", "数组", "写字符到数组"),
                new Op("aput-short", "aput-short vAA, vBB, vCC", "数组", "写短整数到数组"),
                new Op("iget", "iget vA, vB, field@CCCC", "实例字段", "读实例字段"),
                new Op("iget-wide", "iget-wide vA, vB, field@CCCC", "实例字段", "读宽实例字段"),
                new Op("iget-object", "iget-object vA, vB, field@CCCC", "实例字段", "读对象类型实例字段"),
                new Op("iget-boolean", "iget-boolean vA, vB, field@CCCC", "实例字段", "读布尔实例字段"),
                new Op("iget-byte", "iget-byte vA, vB, field@CCCC", "实例字段", "读字节实例字段"),
                new Op("iget-char", "iget-char vA, vB, field@CCCC", "实例字段", "读字符实例字段"),
                new Op("iget-short", "iget-short vA, vB, field@CCCC", "实例字段", "读短整型实例字段"),
                new Op("iput", "iput vA, vB, field@CCCC", "实例字段", "写实例字段"),
                new Op("iput-wide", "iput-wide vA, vB, field@CCCC", "实例字段", "写宽实例字段"),
                new Op("iput-object", "iput-object vA, vB, field@CCCC", "实例字段", "写对象类型实例字段"),
                new Op("iput-boolean", "iput-boolean vA, vB, field@CCCC", "实例字段", "写布尔实例字段"),
                new Op("iput-byte", "iput-byte vA, vB, field@CCCC", "实例字段", "写字节实例字段"),
                new Op("iput-char", "iput-char vA, vB, field@CCCC", "实例字段", "写字符实例字段"),
                new Op("iput-short", "iput-short vA, vB, field@CCCC", "实例字段", "写短整型实例字段"),
                new Op("sget", "sget vAA, field@BBBB", "静态字段", "读静态字段"),
                new Op("sget-wide", "sget-wide vAA, field@BBBB", "静态字段", "读宽静态字段"),
                new Op("sget-object", "sget-object vAA, field@BBBB", "静态字段", "读对象类型静态字段"),
                new Op("sget-boolean", "sget-boolean vAA, field@BBBB", "静态字段", "读布尔静态字段"),
                new Op("sget-byte", "sget-byte vAA, field@BBBB", "静态字段", "读字节静态字段"),
                new Op("sget-char", "sget-char vAA, field@BBBB", "静态字段", "读字符静态字段"),
                new Op("sget-short", "sget-short vAA, field@BBBB", "静态字段", "读短整型静态字段"),
                new Op("sput", "sput vAA, field@BBBB", "静态字段", "写静态字段"),
                new Op("sput-wide", "sput-wide vAA, field@BBBB", "静态字段", "写宽静态字段"),
                new Op("sput-object", "sput-object vAA, field@BBBB", "静态字段", "写对象类型静态字段"),
                new Op("sput-boolean", "sput-boolean vAA, field@BBBB", "静态字段", "写布尔静态字段"),
                new Op("sput-byte", "sput-byte vAA, field@BBBB", "静态字段", "写字节静态字段"),
                new Op("sput-char", "sput-char vAA, field@BBBB", "静态字段", "写字符静态字段"),
                new Op("sput-short", "sput-short vAA, field@BBBB", "静态字段", "写短整型静态字段"),
                new Op("invoke-virtual", "invoke-virtual {vC..vG}, method@BBBB", "方法调用", "虚方法调用（可重写）"),
                new Op("invoke-super", "invoke-super {vC..vG}, method@BBBB", "方法调用", "调用父类实现"),
                new Op("invoke-direct", "invoke-direct {vC..vG}, method@BBBB", "方法调用", "直接方法调用（private/构造）"),
                new Op("invoke-static", "invoke-static {vC..vG}, method@BBBB", "方法调用", "静态方法调用"),
                new Op("invoke-interface", "invoke-interface {vC..vG}, method@BBBB", "方法调用", "接口方法调用"),
                new Op("invoke-virtual/range", "invoke-virtual/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "虚方法调用，寄存器范围"),
                new Op("invoke-super/range", "invoke-super/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "父类调用，寄存器范围"),
                new Op("invoke-direct/range", "invoke-direct/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "直接调用，寄存器范围"),
                new Op("invoke-static/range", "invoke-static/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "静态调用，寄存器范围"),
                new Op("invoke-interface/range", "invoke-interface/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "接口调用，寄存器范围"),
                new Op("neg-int", "neg-int vA, vB", "运算", "按位取负"),
                new Op("not-int", "not-int vA, vB", "运算", "按位取反"),
                new Op("neg-long", "neg-long vA, vB", "运算", "长整数取负"),
                new Op("not-long", "not-long vA, vB", "运算", "长整数取反"),
                new Op("neg-float", "neg-float vA, vB", "运算", "浮点取负"),
                new Op("neg-double", "neg-double vA, vB", "运算", "双精度取负"),
                new Op("int-to-long", "int-to-long vA, vB", "类型转换", "int 转 long"),
                new Op("int-to-float", "int-to-float vA, vB", "类型转换", "int 转 float"),
                new Op("int-to-double", "int-to-double vA, vB", "类型转换", "int 转 double"),
                new Op("long-to-int", "long-to-int vA, vB", "类型转换", "long 转 int"),
                new Op("long-to-float", "long-to-float vA, vB", "类型转换", "long 转 float"),
                new Op("long-to-double", "long-to-double vA, vB", "类型转换", "long 转 double"),
                new Op("float-to-int", "float-to-int vA, vB", "类型转换", "float 转 int"),
                new Op("float-to-long", "float-to-long vA, vB", "类型转换", "float 转 long"),
                new Op("float-to-double", "float-to-double vA, vB", "类型转换", "float 转 double"),
                new Op("double-to-int", "double-to-int vA, vB", "类型转换", "double 转 int"),
                new Op("double-to-long", "double-to-long vA, vB", "类型转换", "double 转 long"),
                new Op("double-to-float", "double-to-float vA, vB", "类型转换", "double 转 float"),
                new Op("int-to-byte", "int-to-byte vA, vB", "类型转换", "int 转 byte"),
                new Op("int-to-char", "int-to-char vA, vB", "类型转换", "int 转 char"),
                new Op("int-to-short", "int-to-short vA, vB", "类型转换", "int 转 short"),
                new Op("add-int", "add-int vA, vB, vC", "运算", "整数加法"),
                new Op("sub-int", "sub-int vA, vB, vC", "运算", "整数减法"),
                new Op("mul-int", "mul-int vA, vB, vC", "运算", "整数乘法"),
                new Op("div-int", "div-int vA, vB, vC", "运算", "整数除法"),
                new Op("rem-int", "rem-int vA, vB, vC", "运算", "整数取余"),
                new Op("and-int", "and-int vA, vB, vC", "运算", "按位与"),
                new Op("or-int", "or-int vA, vB, vC", "运算", "按位或"),
                new Op("xor-int", "xor-int vA, vB, vC", "运算", "按位异或"),
                new Op("shl-int", "shl-int vA, vB, vC", "运算", "左移"),
                new Op("shr-int", "shr-int vA, vB, vC", "运算", "算术右移"),
                new Op("ushr-int", "ushr-int vA, vB, vC", "运算", "逻辑右移"),
                new Op("add-long", "add-long vA, vB, vC", "运算", "长整数加法"),
                new Op("sub-long", "sub-long vA, vB, vC", "运算", "长整数减法"),
                new Op("mul-long", "mul-long vA, vB, vC", "运算", "长整数乘法"),
                new Op("div-long", "div-long vA, vB, vC", "运算", "长整数除法"),
                new Op("rem-long", "rem-long vA, vB, vC", "运算", "长整数取余"),
                new Op("and-long", "and-long vA, vB, vC", "运算", "长整数按位与"),
                new Op("or-long", "or-long vA, vB, vC", "运算", "长整数按位或"),
                new Op("xor-long", "xor-long vA, vB, vC", "运算", "长整数按位异或"),
                new Op("shl-long", "shl-long vA, vB, vC", "运算", "长整数左移"),
                new Op("shr-long", "shr-long vA, vB, vC", "运算", "长整数算术右移"),
                new Op("ushr-long", "ushr-long vA, vB, vC", "运算", "长整数逻辑右移"),
                new Op("add-float", "add-float vA, vB, vC", "运算", "浮点加法"),
                new Op("sub-float", "sub-float vA, vB, vC", "运算", "浮点减法"),
                new Op("mul-float", "mul-float vA, vB, vC", "运算", "浮点乘法"),
                new Op("div-float", "div-float vA, vB, vC", "运算", "浮点除法"),
                new Op("rem-float", "rem-float vA, vB, vC", "运算", "浮点取余"),
                new Op("add-double", "add-double vA, vB, vC", "运算", "双精度加法"),
                new Op("sub-double", "sub-double vA, vB, vC", "运算", "双精度减法"),
                new Op("mul-double", "mul-double vA, vB, vC", "运算", "双精度乘法"),
                new Op("div-double", "div-double vA, vB, vC", "运算", "双精度除法"),
                new Op("rem-double", "rem-double vA, vB, vC", "运算", "双精度取余"),
                new Op("add-int/2addr", "add-int/2addr vA, vB", "运算2", "加法并写回 A"),
                new Op("sub-int/2addr", "sub-int/2addr vA, vB", "运算2", "减法并写回 A"),
                new Op("mul-int/2addr", "mul-int/2addr vA, vB", "运算2", "乘法并写回 A"),
                new Op("div-int/2addr", "div-int/2addr vA, vB", "运算2", "除法并写回 A"),
                new Op("rem-int/2addr", "rem-int/2addr vA, vB", "运算2", "取余并写回 A"),
                new Op("and-int/2addr", "and-int/2addr vA, vB", "运算2", "与并写回 A"),
                new Op("or-int/2addr", "or-int/2addr vA, vB", "运算2", "或并写回 A"),
                new Op("xor-int/2addr", "xor-int/2addr vA, vB", "运算2", "异或并写回 A"),
                new Op("shl-int/2addr", "shl-int/2addr vA, vB", "运算2", "左移并写回 A"),
                new Op("shr-int/2addr", "shr-int/2addr vA, vB", "运算2", "算术右移并写回 A"),
                new Op("ushr-int/2addr", "ushr-int/2addr vA, vB", "运算2", "逻辑右移并写回 A"),
                new Op("add-long/2addr", "add-long/2addr vA, vB", "运算2", "长整数加法，写回 A"),
                new Op("sub-long/2addr", "sub-long/2addr vA, vB", "运算2", "长整数减法，写回 A"),
                new Op("mul-long/2addr", "mul-long/2addr vA, vB", "运算2", "长整数乘法，写回 A"),
                new Op("div-long/2addr", "div-long/2addr vA, vB", "运算2", "长整数除法，写回 A"),
                new Op("and-long/2addr", "and-long/2addr vA, vB", "运算2", "长整数与，写回 A"),
                new Op("or-long/2addr", "or-long/2addr vA, vB", "运算2", "长整数或，写回 A"),
                new Op("xor-long/2addr", "xor-long/2addr vA, vB", "运算2", "长整数异或，写回 A"),
                new Op("add-float/2addr", "add-float/2addr vA, vB", "运算2", "浮点加法，写回 A"),
                new Op("sub-float/2addr", "sub-float/2addr vA, vB", "运算2", "浮点减法，写回 A"),
                new Op("mul-float/2addr", "mul-float/2addr vA, vB", "运算2", "浮点乘法，写回 A"),
                new Op("div-float/2addr", "div-float/2addr vA, vB", "运算2", "浮点除法，写回 A"),
                new Op("add-double/2addr", "add-double/2addr vA, vB", "运算2", "双精度加法，写回 A"),
                new Op("sub-double/2addr", "sub-double/2addr vA, vB", "运算2", "双精度减法，写回 A"),
                new Op("mul-double/2addr", "mul-double/2addr vA, vB", "运算2", "双精度乘法，写回 A"),
                new Op("div-double/2addr", "div-double/2addr vA, vB", "运算2", "双精度除法，写回 A"),
                new Op("add-int/lit16", "add-int/lit16 vA, vB, #+CCCC", "运算常量", "加立即数"),
                new Op("rsub-int", "rsub-int vA, vB, #+CCCC", "运算常量", "立即数减去寄存器"),
                new Op("mul-int/lit16", "mul-int/lit16 vA, vB, #+CCCC", "运算常量", "乘立即数"),
                new Op("div-int/lit16", "div-int/lit16 vA, vB, #+CCCC", "运算常量", "除立即数"),
                new Op("rem-int/lit16", "rem-int/lit16 vA, vB, #+CCCC", "运算常量", "对立即数取余"),
                new Op("and-int/lit16", "and-int/lit16 vA, vB, #+CCCC", "运算常量", "与立即数"),
                new Op("or-int/lit16", "or-int/lit16 vA, vB, #+CCCC", "运算常量", "或立即数"),
                new Op("xor-int/lit16", "xor-int/lit16 vA, vB, #+CCCC", "运算常量", "异或立即数"),
                new Op("add-int/lit8", "add-int/lit8 vAA, vBB, #+CC", "运算常量", "加 8 位立即数"),
                new Op("rsub-int/lit8", "rsub-int/lit8 vAA, vBB, #+CC", "运算常量", "8 位立即数减去寄存器"),
                new Op("mul-int/lit8", "mul-int/lit8 vAA, vBB, #+CC", "运算常量", "乘 8 位立即数"),
                new Op("div-int/lit8", "div-int/lit8 vAA, vBB, #+CC", "运算常量", "除 8 位立即数"),
                new Op("rem-int/lit8", "rem-int/lit8 vAA, vBB, #+CC", "运算常量", "对 8 位立即数取余"),
                new Op("and-int/lit8", "and-int/lit8 vAA, vBB, #+CC", "运算常量", "与 8 位立即数"),
                new Op("or-int/lit8", "or-int/lit8 vAA, vBB, #+CC", "运算常量", "或 8 位立即数"),
                new Op("xor-int/lit8", "xor-int/lit8 vAA, vBB, #+CC", "运算常量", "异或 8 位立即数"),
                new Op("shl-int/lit8", "shl-int/lit8 vAA, vBB, #+CC", "运算常量", "按 8 位立即数左移"),
                new Op("shr-int/lit8", "shr-int/lit8 vAA, vBB, #+CC", "运算常量", "按 8 位立即数右移"),
                new Op("ushr-int/lit8", "ushr-int/lit8 vAA, vBB, #+CC", "运算常量", "按 8 位立即数逻辑右移"),
                new Op("move/from16", "move/from16 vAA, vBBBB", "数据传送", "16 位寄存器源（宽/对象见对应前缀）"),
                new Op("move-result", "move-result vAA", "数据传送", "取最近一次调用结果"),
                new Op("nop", "nop", "杂项", "空操作（可能含对齐填充）"),
                new Op("unused", "unused", "杂项", "已废弃指令，遇到说明校验被绕过"),
                new Op("int-to-float", "int-to-float vA, vB", "类型转换", "int 转 float"),
                new Op("invoke-polymorphic", "invoke-polymorphic {vC..vG}, method@BBBB", "方法调用", "多态方法调用"),
                new Op("invoke-polymorphic/range", "invoke-polymorphic/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "多态调用，寄存器范围"),
                new Op("invoke-custom", "invoke-custom {vC..vG}, method@BBBB", "方法调用", "自定义方法调用（call site）"),
                new Op("invoke-custom/range", "invoke-custom/range {vCCCC..vNNNN}, method@BBBB", "方法调用", "自定义调用，寄存器范围"),
                new Op("const-method-handle", "const-method-handle vAA, method@BBBB", "常量", "方法句柄常量"),
                new Op("const-method-type", "const-method-type vAA, prototype@BBBB", "常量", "方法类型常量")
        ));
        // A mnemonic may only appear once: two rows for the same opcode would
        // make the list lie about how many there are.
        java.util.Map<String, Op> unique = new java.util.LinkedHashMap<>();
        for (Op o : l) unique.putIfAbsent(o.opcode, o);
        ALL = Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    private SmaliReference() {
    }

    /** Every opcode, sorted by mnemonic. */
    public static List<Op> all() {
        List<Op> copy = new ArrayList<>(ALL);
        copy.sort(Comparator.comparing(o -> o.opcode));
        return copy;
    }

    /** Opcodes matching {@code query}, or all of them when it is empty. */
    public static List<Op> search(String query) {
        if (query == null || query.trim().isEmpty()) return all();
        String q = query.trim();
        List<Op> out = new ArrayList<>();
        for (Op o : ALL) {
            if (o.matches(q)) out.add(o);
        }
        out.sort(Comparator.comparing(o -> o.opcode));
        return out;
    }

    /** Distinct groups, in the order they first appear. */
    public static Set<String> groups() {
        Set<String> s = new LinkedHashSet<>();
        for (Op o : ALL) s.add(o.group);
        return s;
    }
}