/*
 * Copyright 2013, Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.tools.smali.dexlib2.writer;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.MethodHandleType;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.ReferenceType;
import com.android.tools.smali.dexlib2.dexbacked.raw.CallSiteIdItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.ClassDefItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.FieldIdItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.HiddenApiClassDataItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.ItemType;
import com.android.tools.smali.dexlib2.dexbacked.raw.MethodHandleItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.MethodIdItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.ProtoIdItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.StringIdItem;
import com.android.tools.smali.dexlib2.dexbacked.raw.TypeIdItem;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction10t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction10x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction12x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction20bc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction20t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21ih;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21lh;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21s;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22b;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22cs;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22s;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction23x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction30t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31t;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction32x;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35mi;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35ms;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rmi;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rms;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction45cc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction4rcc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction51l;
import com.android.tools.smali.dexlib2.iface.instruction.formats.PackedSwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.SparseSwitchPayload;
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodHandleReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodProtoReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.android.tools.smali.dexlib2.base.BaseAnnotation;
import com.android.tools.smali.dexlib2.base.BaseAnnotationElement;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c;
import com.android.tools.smali.dexlib2.formatter.DexFormatter;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.debug.LineNumber;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.VariableRegisterInstruction;
import com.android.tools.smali.dexlib2.util.InstructionUtil;
import com.android.tools.smali.dexlib2.util.MethodUtil;
import com.android.tools.smali.dexlib2.writer.io.DeferredOutputStream;
import com.android.tools.smali.dexlib2.writer.io.DeferredOutputStreamFactory;
import com.android.tools.smali.dexlib2.writer.io.DexDataStore;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.io.MemoryDeferredOutputStream;
import com.android.tools.smali.dexlib2.writer.util.TryListBuilder;
import com.android.tools.smali.util.ExceptionWithContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Adler32;

public abstract class DexWriter<
        StringKey extends CharSequence, StringRef extends StringReference, TypeKey extends CharSequence,
        TypeRef extends TypeReference, ProtoRefKey extends MethodProtoReference,
        FieldRefKey extends FieldReference, MethodRefKey extends MethodReference,
        ClassKey extends Comparable<? super ClassKey>,
        CallSiteKey extends CallSiteReference,
        MethodHandleKey extends MethodHandleReference,
        AnnotationKey extends Annotation, AnnotationSetKey,
        TypeListKey,
        FieldKey, MethodKey,
        EncodedArrayKey,
        EncodedValue,
        AnnotationElement extends com.android.tools.smali.dexlib2.iface.AnnotationElement,
        StringSectionType extends StringSection<StringKey, StringRef>,
        TypeSectionType extends TypeSection<StringKey, TypeKey, TypeRef>,
        ProtoSectionType extends ProtoSection<StringKey, TypeKey, ProtoRefKey, TypeListKey>,
        FieldSectionType extends FieldSection<StringKey, TypeKey, FieldRefKey, FieldKey>,
        MethodSectionType extends MethodSection<StringKey, TypeKey, ProtoRefKey, MethodRefKey, MethodKey>,
        ClassSectionType extends ClassSection<StringKey, TypeKey, TypeListKey, ClassKey, FieldKey, MethodKey,
                AnnotationSetKey, EncodedArrayKey>,
        CallSiteSectionType extends CallSiteSection<CallSiteKey, EncodedArrayKey>,
        MethodHandleSectionType extends MethodHandleSection<MethodHandleKey, FieldRefKey, MethodRefKey>,
        TypeListSectionType extends TypeListSection<TypeKey, TypeListKey>,
        AnnotationSectionType extends AnnotationSection<StringKey, TypeKey, AnnotationKey, AnnotationElement,
                EncodedValue>,
        AnnotationSetSectionType extends AnnotationSetSection<AnnotationKey, AnnotationSetKey>,
        EncodedArraySectionType extends EncodedArraySection<EncodedArrayKey, EncodedValue>> {
    public static final int NO_INDEX = -1;
    public static final int NO_OFFSET = 0;

    public static final int MAX_POOL_SIZE = (1 << 16);

    protected final Opcodes opcodes;

    protected int stringIndexSectionOffset = NO_OFFSET;
    protected int typeSectionOffset = NO_OFFSET;
    protected int protoSectionOffset = NO_OFFSET;
    protected int fieldSectionOffset = NO_OFFSET;
    protected int methodSectionOffset = NO_OFFSET;
    protected int classIndexSectionOffset = NO_OFFSET;
    protected int callSiteSectionOffset = NO_OFFSET;
    protected int methodHandleSectionOffset = NO_OFFSET;

    protected int stringDataSectionOffset = NO_OFFSET;
    protected int classDataSectionOffset = NO_OFFSET;
    protected int typeListSectionOffset = NO_OFFSET;
    protected int encodedArraySectionOffset = NO_OFFSET;
    protected int annotationSectionOffset = NO_OFFSET;
    protected int annotationSetSectionOffset = NO_OFFSET;
    protected int annotationSetRefSectionOffset = NO_OFFSET;
    protected int annotationDirectorySectionOffset = NO_OFFSET;
    protected int debugSectionOffset = NO_OFFSET;
    protected int codeSectionOffset = NO_OFFSET;
    protected int hiddenApiRestrictionsOffset = NO_OFFSET;
    protected int mapSectionOffset = NO_OFFSET;

    protected boolean hasHiddenApiRestrictions = false;

    protected int numAnnotationSetRefItems = 0;
    protected int numAnnotationDirectoryItems = 0;
    protected int numDebugInfoItems = 0;
    protected int numCodeItemItems = 0;
    protected int numClassDataItems = 0;

    // The sections defined here must be kept in sync with these section arrays:
    // - DexWriter.overflowableSections
    // - DexPool.sections

    public final StringSectionType stringSection;
    public final TypeSectionType typeSection;
    public final ProtoSectionType protoSection;
    public final FieldSectionType fieldSection;
    public final MethodSectionType methodSection;
    public final ClassSectionType classSection;
    public final CallSiteSectionType callSiteSection;
    public final MethodHandleSectionType methodHandleSection;

    public final TypeListSectionType typeListSection;
    public final AnnotationSectionType annotationSection;
    public final AnnotationSetSectionType annotationSetSection;
    public final EncodedArraySectionType encodedArraySection;

    private final IndexSection<?>[] overflowableSections;

    protected DexWriter(Opcodes opcodes) {
        this.opcodes = opcodes;

        SectionProvider sectionProvider = getSectionProvider();
        this.stringSection = sectionProvider.getStringSection();
        this.typeSection = sectionProvider.getTypeSection();
        this.protoSection = sectionProvider.getProtoSection();
        this.fieldSection = sectionProvider.getFieldSection();
        this.methodSection = sectionProvider.getMethodSection();
        this.classSection = sectionProvider.getClassSection();
        this.callSiteSection = sectionProvider.getCallSiteSection();
        this.methodHandleSection = sectionProvider.getMethodHandleSection();
        this.typeListSection = sectionProvider.getTypeListSection();
        this.annotationSection = sectionProvider.getAnnotationSection();
        this.annotationSetSection = sectionProvider.getAnnotationSetSection();
        this.encodedArraySection = sectionProvider.getEncodedArraySection();

        overflowableSections = new IndexSection<?>[] {
                //stringSection,            // supports jumbo indexes
                typeSection,
                protoSection,
                fieldSection,
                methodSection,
                //classSection,             // redundant check: cannot be larger than typeSection
                callSiteSection,
                methodHandleSection,
        };
    }

    @Nonnull protected abstract SectionProvider getSectionProvider();

    protected abstract void writeEncodedValue(@Nonnull InternalEncodedValueWriter writer,
                                              @Nonnull EncodedValue encodedValue) throws IOException;

    private final Comparator<Map.Entry<? extends CallSiteKey, Integer>> callSiteComparator =
            new Comparator<>() {
                @Override
                public int compare(Entry<? extends CallSiteKey, Integer> o1, Entry<? extends CallSiteKey, Integer> o2) {
                    int offset1 = encodedArraySection.getItemOffset(callSiteSection.getEncodedCallSite(o1.getKey()));
                    int offset2 = encodedArraySection.getItemOffset(callSiteSection.getEncodedCallSite(o2.getKey()));
                    return Ints.compare(offset1, offset2);
                }
            };

    /**
     * Orders pool entries by the string form of their key, which is the order the dex format
     * requires for the string and type id sections.
     *
     * <p>The keys are the pool's own reference objects, and their {@code toString()} is the plain
     * string the entry is keyed by - see {@code BuilderTypeReference}/{@code BuilderClassDef},
     * which return the descriptor they already hold instead of rebuilding it through
     * {@code DexFormatter} on every single comparison.
     */
    private static final Comparator<Map.Entry> toStringKeyComparator =
            (o1, o2) -> o1.getKey().toString().compareTo(o2.getKey().toString());

    private static <T extends Comparable<? super T>> Comparator<Map.Entry<? extends T, ?>> comparableKeyComparator() {
        return (o1, o2) -> o1.getKey().compareTo(o2.getKey());
    }

    private static <T extends Comparable<? super T>> Comparator<Entry<?, ? extends T>> comparableValueComparator() {
        return (o1, o2) -> o1.getValue().compareTo(o2.getValue());
    }

    /**
     * Below this many items, splitting a sort across threads costs more time than it saves.
     */
    private static final int PARALLEL_SORT_THRESHOLD = 1 << 13;

    /**
     * {@link Arrays#parallelSort} is only present on API 24+. It is resolved reflectively so this
     * code degrades to a plain sort on older runtimes instead of failing to load.
     */
    @Nullable private static final Method PARALLEL_SORT_METHOD;

    static {
        Method method = null;
        try {
            method = Arrays.class.getMethod("parallelSort", Object[].class, Comparator.class);
        } catch (ReflectiveOperationException ignored) {
        }
        PARALLEL_SORT_METHOD = method;
    }

    /**
     * Sorts a pool's items by key, the same way {@link Collections#sort} does but across all cores
     * for large pools. Sorting is a large slice of the write phase for a multi-megabyte dex (six
     * pools, hundreds of thousands of items in total), and it is the last part of that phase that
     * is still single threaded.
     *
     * <p>The resulting order is identical: the keys interned in every pool are unique, so there are
     * no ties for a differently-stable sort to order differently.
     *
     * @return a new list holding the pool's entries in write order
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    private static <T> List<T> sortPoolItems(@Nonnull Collection<? extends T> items,
                                             @Nonnull Comparator<? super T> comparator) {
        List<T> sorted = new ArrayList<>(items);
        if (sorted.size() < 2) {
            return sorted;
        }
        if (PARALLEL_SORT_METHOD == null || sorted.size() < PARALLEL_SORT_THRESHOLD) {
            Collections.sort(sorted, comparator);
            return sorted;
        }

        final Object[] array = sorted.toArray();
        try {
            PARALLEL_SORT_METHOD.invoke(null, array, comparator);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            Arrays.sort(array, (Comparator) comparator);
        }
        final ListIterator<T> iterator = sorted.listIterator();
        for (Object item: array) {
            iterator.next();
            iterator.set((T) item);
        }
        return sorted;
    }

    /** Index of the string pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_STRINGS = 0;
    /** Index of the type pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_TYPES = 1;
    /** Index of the prototype pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_PROTOS = 2;
    /** Index of the field pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_FIELDS = 3;
    /** Index of the method pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_METHODS = 4;
    /** Index of the class pool's sorted entries in the array returned by {@link #sortPoolEntries()}. */
    private static final int POOL_CLASSES = 5;
    /** The number of constant pools that are written in sorted order. */
    private static final int POOL_COUNT = 6;

    /**
     * Sorts every constant pool whose entries are written in sorted order, at once.
     *
     * <p>Sorting is about a third of the write phase, and the six sorts are independent of each
     * other: each one only reads its own pool's keys. Run one after another, they are a chain of
     * partly idle sorts - {@link Arrays#parallelSort} only splits a pool into as many pieces as its
     * size allows, and the smaller pools do not parallelise at all - so most cores sit idle for
     * most of the phase. Run concurrently, the same comparisons keep every core busy, and the phase
     * costs about as long as its single largest sort instead of the sum of all six.
     *
     * <p>The result is unchanged by construction: every pool is still ordered by exactly the same
     * comparator, and the indices are still assigned afterwards, in the write loops, in the same
     * order as before.
     *
     * @return the sorted entry lists, indexed by the {@code POOL_*} constants
     */
    @Nonnull
    private Object[] sortPoolEntries() throws IOException {
        final Object[] sortedPools = new Object[POOL_COUNT];
        runChunked(POOL_COUNT, pool -> {
            switch (pool) {
                case POOL_STRINGS:
                    sortedPools[POOL_STRINGS] = sortPoolItems(stringSection.getItems(), toStringKeyComparator);
                    break;
                case POOL_TYPES:
                    sortedPools[POOL_TYPES] = sortPoolItems(typeSection.getItems(), toStringKeyComparator);
                    break;
                case POOL_PROTOS:
                    sortedPools[POOL_PROTOS] = sortPoolItems(
                            protoSection.getItems(), DexWriter.<ProtoRefKey>comparableKeyComparator());
                    break;
                case POOL_FIELDS:
                    sortedPools[POOL_FIELDS] = sortPoolItems(
                            fieldSection.getItems(), DexWriter.<FieldRefKey>comparableKeyComparator());
                    break;
                case POOL_METHODS:
                    sortedPools[POOL_METHODS] = sortPoolItems(
                            methodSection.getItems(), DexWriter.<MethodRefKey>comparableKeyComparator());
                    break;
                case POOL_CLASSES:
                    sortedPools[POOL_CLASSES] = sortPoolItems(
                            classSection.getItems(), DexWriter.<ClassKey>comparableKeyComparator());
                    break;
                default:
                    break;
            }
        });
        return sortedPools;
    }

    /** Reads a sorted entry list out of the array built by {@link #sortPoolEntries()}. */
    @SuppressWarnings("unchecked")
    @Nonnull
    private static <T> List<T> sortedPool(@Nonnull Object[] sortedPools, int pool) {
        return (List<T>) sortedPools[pool];
    }

    /** Smallest index range handed to one worker thread. */
    private static final int MIN_INDEX_CHUNK = 1024;

    /**
     * Below this many items a lookup pass stays on the calling thread: handing work to worker
     * threads costs on the order of a millisecond, which a small pool's lookups do not earn back.
     */
    private static final int MIN_PARALLEL_ITEMS = 16 * 1024;

    /** Work over a contiguous range of indices, {@code [from, to)}. */
    private interface IndexedTask {
        void run(int from, int to);
    }

    /**
     * Runs {@code task} over {@code [0, size)}, in parallel when that is worth doing.
     *
     * <p>Looking up what a pool entry writes - the section indices of its defining class, name,
     * prototype and so on - is a walk over a scattered object graph, so it is bound by memory
     * latency rather than by the handful of instructions it takes. The values do not depend on each
     * other and are read-only, so they can be resolved on several cores at once; the caller then
     * writes them out in order, which is all that has to stay sequential.
     */
    private static void forEachIndex(int size, @Nonnull IndexedTask task) throws IOException {
        if (size <= 0) {
            return;
        }
        if (size < MIN_PARALLEL_ITEMS) {
            task.run(0, size);
            return;
        }
        int maxChunks = Math.max(1, Runtime.getRuntime().availableProcessors()) * 2;
        int numChunks = Math.max(1, Math.min((size + MIN_INDEX_CHUNK - 1) / MIN_INDEX_CHUNK, maxChunks));
        int chunkSize = (size + numChunks - 1) / numChunks;
        runChunked(numChunks, chunk ->
                task.run(chunk * chunkSize, Math.min(chunk * chunkSize + chunkSize, size)));
    }

    protected class InternalEncodedValueWriter extends EncodedValueWriter<StringKey, TypeKey, FieldRefKey, MethodRefKey,
            AnnotationElement, ProtoRefKey, MethodHandleKey, EncodedValue> {
        private InternalEncodedValueWriter(@Nonnull DexDataWriter writer) {
            super(writer, stringSection, typeSection, fieldSection, methodSection, protoSection, methodHandleSection,
                    annotationSection);
        }

        @Override protected void writeEncodedValue(@Nonnull EncodedValue encodedValue) throws IOException {
            DexWriter.this.writeEncodedValue(this, encodedValue);
        }
    }

    private int getDataSectionOffset() {
        return HeaderItem.ITEM_SIZE +
                stringSection.getItemCount() * StringIdItem.ITEM_SIZE +
                typeSection.getItemCount() * TypeIdItem.ITEM_SIZE +
                protoSection.getItemCount() * ProtoIdItem.ITEM_SIZE +
                fieldSection.getItemCount() * FieldIdItem.ITEM_SIZE +
                methodSection.getItemCount() * MethodIdItem.ITEM_SIZE +
                classSection.getItemCount() * ClassDefItem.ITEM_SIZE +
                callSiteSection.getItemCount() * CallSiteIdItem.ITEM_SIZE +
                methodHandleSection.getItemCount() * MethodHandleItem.ITEM_SIZE;
    }

    @Nonnull
    public List<String> getMethodReferences() {
        List<String> methodReferences = Lists.newArrayList();
        for (Entry<? extends MethodRefKey, Integer> methodReference: methodSection.getItems()) {
            methodReferences.add(DexFormatter.INSTANCE.getMethodDescriptor(methodReference.getKey()));
        }
        return methodReferences;
    }

    @Nonnull
    public List<String> getFieldReferences() {
        List<String> fieldReferences = Lists.newArrayList();
        for (Entry<? extends FieldRefKey, Integer> fieldReference: fieldSection.getItems()) {
            fieldReferences.add(DexFormatter.INSTANCE.getFieldDescriptor(fieldReference.getKey()));
        }
        return fieldReferences;
    }

    @Nonnull
    public List<String> getTypeReferences() {
        List<String> classReferences = Lists.newArrayList();
        for (Entry<? extends TypeKey, Integer> typeReference: typeSection.getItems()) {
            classReferences.add(typeReference.getKey().toString());
        }
        return classReferences;
    }

    /**
     * Checks whether any of the size-sensitive constant pools have overflowed and have more than 64Ki entries.
     *
     * Note that even if this returns true, it may still be possible to successfully write the dex file, if the
     * overflowed items are not referenced anywhere that uses a 16-bit index.
     *
     * @return true if any of the size-sensitive constant pools have overflowed
     */
    public boolean hasOverflowed() {
        return hasOverflowed(MAX_POOL_SIZE);
    }

    /**
     * Checks whether any of the size-sensitive constant pools have more than the supplied maximum number of entries.
     *
     * @param maxPoolSize the maximum number of entries allowed in any of the size-sensitive constant pools
     * @return true if any of the size-sensitive constant pools have overflowed the supplied size limit
     */
    public boolean hasOverflowed(int maxPoolSize) {
        for (IndexSection section: overflowableSections) {
            if (section.getItemCount() > maxPoolSize) return true;
        }
        return false;
    }

    public void writeTo(@Nonnull DexDataStore dest) throws IOException {
        this.writeTo(dest, MemoryDeferredOutputStream.getFactory());
    }

    public void writeTo(@Nonnull DexDataStore dest,
                        @Nonnull DeferredOutputStreamFactory tempFactory) throws IOException {
        try {
            int dataSectionOffset = getDataSectionOffset();

            // The index sections below are written in sorted order. Sorting the pools is the single
            // largest part of this method and the sorts do not depend on each other, so they are all
            // prepared up front, concurrently - see sortPoolEntries(). Nothing else is reordered:
            // each pool's indices are still assigned in its write loop below, in the same order.
            Object[] sortedPools = sortPoolEntries();

            try (DexDataWriter headerWriter = outputAt(dest, 0);
                 DexDataWriter indexWriter = outputAt(dest, HeaderItem.ITEM_SIZE);
                 DexDataWriter offsetWriter = outputAt(dest, dataSectionOffset)) {
                writeStrings(indexWriter, offsetWriter, sortedPool(sortedPools, POOL_STRINGS));
                writeTypes(indexWriter, sortedPool(sortedPools, POOL_TYPES));
                writeTypeLists(offsetWriter);
                writeProtos(indexWriter, sortedPool(sortedPools, POOL_PROTOS));
                writeFields(indexWriter, sortedPool(sortedPools, POOL_FIELDS));
                writeMethods(indexWriter, sortedPool(sortedPools, POOL_METHODS));

                // encoded arrays depend on method handles..
                try (DexDataWriter methodHandleWriter = outputAt(dest, indexWriter.getPosition() +
                        classSection.getItemCount() * ClassDefItem.ITEM_SIZE +
                        callSiteSection.getItemCount() * CallSiteIdItem.ITEM_SIZE)) {
                    writeMethodHandles(methodHandleWriter);
                }

                // call sites depend on encoded arrays..
                writeEncodedArrays(offsetWriter);

                // class defs depend on method handles and call sites..
                try (DexDataWriter callSiteWriter = outputAt(dest, indexWriter.getPosition() +
                        classSection.getItemCount() * ClassDefItem.ITEM_SIZE)) {
                    writeCallSites(callSiteWriter);
                }

                writeAnnotations(offsetWriter);
                writeAnnotationSets(offsetWriter);
                writeAnnotationSetRefs(offsetWriter);
                writeAnnotationDirectories(offsetWriter);
                writeDebugAndCodeItems(offsetWriter, tempFactory);
                writeClasses(dest, indexWriter, offsetWriter, sortedPool(sortedPools, POOL_CLASSES));

                writeMapItem(offsetWriter);
                writeHeader(headerWriter, dataSectionOffset, offsetWriter.getPosition());
            }
            updateSignatureAndChecksum(dest);
        } finally {
            dest.close();
        }
    }

    /**
     * Computes the SHA-1 signature and Adler32 checksum and writes them into the header.
     *
     * <p>This is the fused form of the old {@code updateSignature()} + {@code updateChecksum()}
     * pair. The old code pulled the whole file through two separate 4&nbsp;KiB
     * {@link DexDataStore#readAt} passes; both digests are streaming, so the values produced
     * here are bit-identical with far less I/O overhead. When the destination is a
     * {@link MemoryDataStore} the backing array is hashed directly with zero copies.
     *
     * <p>Ordering is load-bearing and preserved exactly: the signature covers
     * {@code [SIGNATURE_DATA_START_OFFSET, end)}, is written first, and the checksum then
     * covers {@code [CHECKSUM_DATA_START_OFFSET, end)} <em>including</em> those signature
     * bytes, just like the old sequential pair.
     */
    private void updateSignatureAndChecksum(@Nonnull DexDataStore dataStore) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }

        if (dataStore instanceof MemoryDataStore) {
            updateSignatureAndChecksumMemory((MemoryDataStore) dataStore, md);
            return;
        }

        Adler32 a32 = new Adler32();
        byte[] buffer = new byte[32 * 1024];

        InputStream input = dataStore.readAt(HeaderItem.SIGNATURE_DATA_START_OFFSET);
        int bytesRead = input.read(buffer);
        while (bytesRead >= 0) {
            md.update(buffer, 0, bytesRead);
            bytesRead = input.read(buffer);
        }
        input.close();

        byte[] signature = md.digest();
        if (signature.length != HeaderItem.SIGNATURE_SIZE) {
            throw new RuntimeException("unexpected digest write: " + signature.length + " bytes");
        }

        // write signature
        OutputStream output = dataStore.outputAt(HeaderItem.SIGNATURE_OFFSET);
        output.write(signature);
        output.close();

        input = dataStore.readAt(HeaderItem.CHECKSUM_DATA_START_OFFSET);
        bytesRead = input.read(buffer);
        while (bytesRead >= 0) {
            a32.update(buffer, 0, bytesRead);
            bytesRead = input.read(buffer);
        }
        input.close();

        // write checksum, utilizing logic in DexWriter to write the integer value properly
        output = dataStore.outputAt(HeaderItem.CHECKSUM_OFFSET);
        DexDataWriter.writeInt(output, (int)a32.getValue());
        output.close();
    }

    /**
     * Zero-copy variant of {@link #updateSignatureAndChecksum} for {@link MemoryDataStore}:
     * hashes the backing array directly instead of round-tripping every 4&nbsp;KiB through an
     * {@link InputStream}. Produces identical signature/checksum bytes.
     */
    private void updateSignatureAndChecksumMemory(@Nonnull MemoryDataStore dataStore,
                                                  @Nonnull MessageDigest md) throws IOException {
        byte[] buf = dataStore.getBuffer();
        int size = dataStore.getSize();

        // SHA-1 over [SIGNATURE_DATA_START_OFFSET, end). Guarded for a short buffer;
        // a real dex header is always larger, this just avoids an AIOOBE on corrupt input.
        int sigStart = Math.min(HeaderItem.SIGNATURE_DATA_START_OFFSET, size);
        md.update(buf, sigStart, size - sigStart);

        byte[] signature = md.digest();
        if (signature.length != HeaderItem.SIGNATURE_SIZE) {
            throw new RuntimeException("unexpected digest write: " + signature.length + " bytes");
        }

        // write signature first: the checksum below must cover these bytes.
        OutputStream output = dataStore.outputAt(HeaderItem.SIGNATURE_OFFSET);
        output.write(signature);
        output.close();

        // Adler32 over [CHECKSUM_DATA_START_OFFSET, end), now including the signature.
        Adler32 a32 = new Adler32();
        int cksumStart = Math.min(HeaderItem.CHECKSUM_DATA_START_OFFSET, size);
        a32.update(buf, cksumStart, size - cksumStart);

        // write checksum, utilizing logic in DexWriter to write the integer value properly
        output = dataStore.outputAt(HeaderItem.CHECKSUM_OFFSET);
        DexDataWriter.writeInt(output, (int)a32.getValue());
        output.close();
    }

    private static DexDataWriter outputAt(DexDataStore dataStore, int filePosition) throws IOException {
        return new DexDataWriter(dataStore.outputAt(filePosition), filePosition);
    }

    /**
     * Writes the string index section and the string data it points at.
     *
     * <p>The strings are materialised up front, in parallel - turning a pool key back into its
     * string walks a couple of objects, which is the expensive half of this loop - and then written
     * in order, because each string's offset is simply where the previous one ended. The bytes
     * written are the same either way.
     */
    private void writeStrings(@Nonnull DexDataWriter indexWriter, @Nonnull DexDataWriter offsetWriter,
                              @Nonnull List<Entry<? extends StringKey, Integer>> stringEntries) throws IOException {
        stringIndexSectionOffset = indexWriter.getPosition();
        stringDataSectionOffset = offsetWriter.getPosition();
        final int itemCount = stringEntries.size();

        final String[] strings = new String[itemCount];
        forEachIndex(itemCount, (from, to) -> {
            for (int i = from; i < to; i++) {
                strings[i] = stringEntries.get(i).getKey().toString();
            }
        });

        for (int i = 0; i < itemCount; i++) {
            stringEntries.get(i).setValue(i);
            indexWriter.writeInt(offsetWriter.getPosition());
            String stringValue = strings[i];
            offsetWriter.writeUleb128(stringValue.length());
            offsetWriter.writeString(stringValue);
            offsetWriter.write(0);
        }
    }

    /**
     * Writes the type index section. The string index each type refers to is resolved in parallel,
     * then written in order - see {@link #forEachIndex}.
     */
    private void writeTypes(@Nonnull DexDataWriter writer,
                            @Nonnull List<Map.Entry<? extends TypeKey, Integer>> typeEntries) throws IOException {
        typeSectionOffset = writer.getPosition();
        final int itemCount = typeEntries.size();

        final int[] stringIndices = new int[itemCount];
        forEachIndex(itemCount, (from, to) -> {
            for (int i = from; i < to; i++) {
                TypeKey key = typeEntries.get(i).getKey();
                stringIndices[i] = stringSection.getItemIndex(typeSection.getString(key));
            }
        });

        for (int i = 0; i < itemCount; i++) {
            typeEntries.get(i).setValue(i);
            writer.writeInt(stringIndices[i]);
        }
    }

    private void writeProtos(@Nonnull DexDataWriter writer,
                             @Nonnull List<Map.Entry<? extends ProtoRefKey, Integer>> protoEntries) throws IOException {
        protoSectionOffset = writer.getPosition();
        final int itemCount = protoEntries.size();

        final int[] values = new int[itemCount * 3];
        forEachIndex(itemCount, (from, to) -> {
            for (int i = from; i < to; i++) {
                ProtoRefKey key = protoEntries.get(i).getKey();
                int base = i * 3;
                values[base] = stringSection.getItemIndex(protoSection.getShorty(key));
                values[base + 1] = typeSection.getItemIndex(protoSection.getReturnType(key));
                values[base + 2] = typeListSection.getNullableItemOffset(protoSection.getParameters(key));
            }
        });

        for (int i = 0; i < itemCount; i++) {
            protoEntries.get(i).setValue(i);
            int base = i * 3;
            writer.writeInt(values[base]);
            writer.writeInt(values[base + 1]);
            writer.writeInt(values[base + 2]);
        }
    }

    private void writeFields(@Nonnull DexDataWriter writer,
                             @Nonnull List<Map.Entry<? extends FieldRefKey, Integer>> fieldEntries) throws IOException {
        fieldSectionOffset = writer.getPosition();
        final int itemCount = fieldEntries.size();

        final int[] values = new int[itemCount * 3];
        forEachIndex(itemCount, (from, to) -> {
            for (int i = from; i < to; i++) {
                FieldRefKey key = fieldEntries.get(i).getKey();
                int base = i * 3;
                values[base] = typeSection.getItemIndex(fieldSection.getDefiningClass(key));
                values[base + 1] = typeSection.getItemIndex(fieldSection.getFieldType(key));
                values[base + 2] = stringSection.getItemIndex(fieldSection.getName(key));
            }
        });

        for (int i = 0; i < itemCount; i++) {
            fieldEntries.get(i).setValue(i);
            int base = i * 3;
            writer.writeUshort(values[base]);
            writer.writeUshort(values[base + 1]);
            writer.writeInt(values[base + 2]);
        }
    }

    private void writeMethods(@Nonnull DexDataWriter writer,
                              @Nonnull List<Map.Entry<? extends MethodRefKey, Integer>> methodEntries) throws IOException {
        methodSectionOffset = writer.getPosition();
        final int itemCount = methodEntries.size();

        final int[] values = new int[itemCount * 3];
        forEachIndex(itemCount, (from, to) -> {
            for (int i = from; i < to; i++) {
                MethodRefKey key = methodEntries.get(i).getKey();
                int base = i * 3;
                values[base] = typeSection.getItemIndex(methodSection.getDefiningClass(key));
                values[base + 1] = protoSection.getItemIndex(methodSection.getPrototype(key));
                values[base + 2] = stringSection.getItemIndex(methodSection.getName(key));
            }
        });

        for (int i = 0; i < itemCount; i++) {
            methodEntries.get(i).setValue(i);
            int base = i * 3;
            writer.writeUshort(values[base]);
            writer.writeUshort(values[base + 1]);
            writer.writeInt(values[base + 2]);
        }
    }

    private void writeClasses(@Nonnull DexDataStore dataStore, @Nonnull DexDataWriter indexWriter,
                              @Nonnull DexDataWriter offsetWriter,
                              @Nonnull List<Map.Entry<? extends ClassKey, Integer>> classEntriesKeySorted)
            throws IOException {
        classIndexSectionOffset = indexWriter.getPosition();
        classDataSectionOffset = offsetWriter.getPosition();

        int index = 0;
        for (Map.Entry<? extends ClassKey, Integer> key: classEntriesKeySorted) {
            index = writeClass(indexWriter, offsetWriter, index, key);
        }

        if (!shouldWriteHiddenApiRestrictions()) {
            return;
        }

        offsetWriter.align();
        hiddenApiRestrictionsOffset = offsetWriter.getPosition();

        List<Map.Entry<? extends ClassKey, Integer>> classEntriesValueSorted = Lists.newArrayList(classSection.getItems());
        Collections.sort(classEntriesValueSorted, DexWriter.comparableValueComparator());
        RestrictionsWriter restrictionsWriter = new RestrictionsWriter(dataStore, offsetWriter, classEntriesValueSorted.size());

        try {
            for (Map.Entry<? extends ClassKey, Integer> key : classEntriesValueSorted) {

                for (FieldKey fieldKey : classSection.getSortedStaticFields(key.getKey())) {
                    restrictionsWriter.writeRestriction(classSection.getFieldHiddenApiRestrictions(fieldKey));
                }

                for (FieldKey fieldKey : classSection.getSortedInstanceFields(key.getKey())) {
                    restrictionsWriter.writeRestriction(classSection.getFieldHiddenApiRestrictions(fieldKey));
                }

                for (MethodKey methodKey : classSection.getSortedDirectMethods(key.getKey())) {
                    restrictionsWriter.writeRestriction(classSection.getMethodHiddenApiRestrictions(methodKey));
                }

                for (MethodKey methodKey : classSection.getSortedVirtualMethods(key.getKey())) {
                    restrictionsWriter.writeRestriction(classSection.getMethodHiddenApiRestrictions(methodKey));
                }

                restrictionsWriter.finishClass();
            }
        } finally {
            restrictionsWriter.close();
        }
    }

    private boolean shouldWriteHiddenApiRestrictions() {
        return hasHiddenApiRestrictions && opcodes.api >= 29;
    }

    private static class RestrictionsWriter {
        private final int startOffset;

        private final DexDataStore dataStore;
        private final DexDataWriter offsetsWriter;
        private final DexDataWriter restrictionsWriter;

        private boolean writeRestrictionsForClass = false;
        private int pendingBlankEntries = 0;

        public RestrictionsWriter(DexDataStore dataStore, DexDataWriter offsetWriter, int numClasses)
                throws IOException {
            this.startOffset = offsetWriter.getPosition();
            this.dataStore = dataStore;
            this.restrictionsWriter = offsetWriter;

            int offsetsSize = numClasses * HiddenApiClassDataItem.OFFSET_ITEM_SIZE;

            // We don't know the size yet, so skip over it
            restrictionsWriter.writeInt(0);

            this.offsetsWriter = outputAt(dataStore, restrictionsWriter.getPosition());

            // Skip over the offsets
            for (int i=0; i<offsetsSize; i++) {
                restrictionsWriter.write(0);
            }
            restrictionsWriter.flush();
        }

        public void finishClass() throws IOException {
            if (!writeRestrictionsForClass) {
                // Normally the offset gets written when the first non-blank restriction gets written. If only blank
                // restrictions were added, nothing actually gets written, and we write out a blank offset here.
                offsetsWriter.writeInt(0);
            }

            writeRestrictionsForClass = false;
            pendingBlankEntries = 0;
        }

        private void addBlankEntry() throws IOException {
            if (writeRestrictionsForClass) {
                restrictionsWriter.writeUleb128(HiddenApiRestriction.WHITELIST.getValue());
            } else {
                pendingBlankEntries++;
            }
        }

        public void writeRestriction(@Nonnull Set<HiddenApiRestriction> hiddenApiRestrictions) throws IOException {
            if (hiddenApiRestrictions.isEmpty()) {
                addBlankEntry();
                return;
            }

            if (!writeRestrictionsForClass) {
                writeRestrictionsForClass = true;
                offsetsWriter.writeInt(restrictionsWriter.getPosition() - startOffset);

                for (int i = 0; i < pendingBlankEntries; i++) {
                    restrictionsWriter.writeUleb128(HiddenApiRestriction.WHITELIST.getValue());
                }
                pendingBlankEntries = 0;
            }
            restrictionsWriter.writeUleb128(HiddenApiRestriction.combineFlags(hiddenApiRestrictions));
        }

        public void close() throws IOException {
            offsetsWriter.close();
            try (DexDataWriter writer = outputAt(dataStore, startOffset)) {
                writer.writeInt(restrictionsWriter.getPosition() - startOffset);
            }
        }
    }

    /**
     * Writes out the class_def_item and class_data_item for the given class.
     *
     * This will recursively write out any unwritten superclass/interface before writing the class itself, as per the
     * dex specification.
     *
     * @return the index for the next class to be written
     */
    private int writeClass(@Nonnull DexDataWriter indexWriter, @Nonnull DexDataWriter offsetWriter,
                           int nextIndex, @Nullable Map.Entry<? extends ClassKey, Integer> entry) throws IOException {
        if (entry == null) {
            // class does not exist in this dex file, cannot write it
            return nextIndex;
        }

        if (entry.getValue() != NO_INDEX) {
            // class has already been written, no need to write it
            return nextIndex;
        }

        ClassKey key = entry.getKey();

        // set a bogus index, to make sure we don't recurse and double-write it
        entry.setValue(0);

        // first, try to write the superclass
        Map.Entry<? extends ClassKey, Integer> superEntry =
                classSection.getClassEntryByType(classSection.getSuperclass(key));
        nextIndex = writeClass(indexWriter, offsetWriter, nextIndex, superEntry);

        // then, try to write interfaces
        for (TypeKey interfaceTypeKey: typeListSection.getTypes(classSection.getInterfaces(key))) {
            Map.Entry<? extends ClassKey, Integer> interfaceEntry = classSection.getClassEntryByType(interfaceTypeKey);
            nextIndex = writeClass(indexWriter, offsetWriter, nextIndex, interfaceEntry);
        }

        // now set the index for real
        entry.setValue(nextIndex++);

        // and finally, write the class itself
        // first, the class_def_item
        indexWriter.writeInt(typeSection.getItemIndex(classSection.getType(key)));
        indexWriter.writeInt(classSection.getAccessFlags(key));
        indexWriter.writeInt(typeSection.getNullableItemIndex(classSection.getSuperclass(key)));
        indexWriter.writeInt(typeListSection.getNullableItemOffset(classSection.getInterfaces(key)));
        indexWriter.writeInt(stringSection.getNullableItemIndex(classSection.getSourceFile(key)));
        indexWriter.writeInt(classSection.getAnnotationDirectoryOffset(key));

        Collection<? extends FieldKey> staticFields = classSection.getSortedStaticFields(key);
        Collection<? extends FieldKey> instanceFields = classSection.getSortedInstanceFields(key);
        Collection<? extends MethodKey> directMethods = classSection.getSortedDirectMethods(key);
        Collection<? extends MethodKey> virtualMethods = classSection.getSortedVirtualMethods(key);
        boolean classHasData = staticFields.size() > 0 ||
                instanceFields.size() > 0 ||
                directMethods.size() > 0 ||
                virtualMethods.size() > 0;

        if (classHasData) {
            indexWriter.writeInt(offsetWriter.getPosition());
        } else {
            indexWriter.writeInt(NO_OFFSET);
        }

        EncodedArrayKey staticInitializers = classSection.getStaticInitializers(key);
        if (staticInitializers != null) {
            indexWriter.writeInt(encodedArraySection.getItemOffset(staticInitializers));
        } else {
            indexWriter.writeInt(NO_OFFSET);
        }

        // now write the class_data_item
        if (classHasData) {
            numClassDataItems++;

            offsetWriter.writeUleb128(staticFields.size());
            offsetWriter.writeUleb128(instanceFields.size());
            offsetWriter.writeUleb128(directMethods.size());
            offsetWriter.writeUleb128(virtualMethods.size());

            writeEncodedFields(offsetWriter, staticFields);
            writeEncodedFields(offsetWriter, instanceFields);
            writeEncodedMethods(offsetWriter, directMethods);
            writeEncodedMethods(offsetWriter, virtualMethods);
        }

        return nextIndex;
    }

    private void writeCallSites(DexDataWriter writer) throws IOException {
        callSiteSectionOffset = writer.getPosition();

        List<Map.Entry<? extends CallSiteKey, Integer>> callSiteEntries =
                Lists.newArrayList(callSiteSection.getItems());
        sortPoolItems(callSiteEntries, callSiteComparator);

        int index = 0;

        for (Map.Entry<? extends CallSiteKey, Integer> callSite: callSiteEntries) {
            callSite.setValue(index++);
            writer.writeInt(encodedArraySection.getItemOffset(callSiteSection.getEncodedCallSite(callSite.getKey())));
        }
    }

    private void writeMethodHandles(DexDataWriter writer) throws IOException {
        methodHandleSectionOffset = writer.getPosition();

        int index = 0;

        for (Entry<? extends MethodHandleKey, Integer> entry: methodHandleSection.getItems()) {
            entry.setValue(index++);
            MethodHandleKey methodHandleReference = entry.getKey();
            writer.writeUshort(methodHandleReference.getMethodHandleType());
            writer.writeUshort(0);
            int memberIndex = switch (methodHandleReference.getMethodHandleType()) {
                case MethodHandleType.STATIC_PUT, MethodHandleType.STATIC_GET,
                     MethodHandleType.INSTANCE_PUT, MethodHandleType.INSTANCE_GET ->
                        fieldSection.getItemIndex(
                                methodHandleSection.getFieldReference(methodHandleReference));
                case MethodHandleType.INVOKE_STATIC, MethodHandleType.INVOKE_INSTANCE,
                     MethodHandleType.INVOKE_CONSTRUCTOR, MethodHandleType.INVOKE_DIRECT,
                     MethodHandleType.INVOKE_INTERFACE -> methodSection.getItemIndex(
                        methodHandleSection.getMethodReference(methodHandleReference));
                default -> throw new ExceptionWithContext("Invalid method handle type: %d",
                        methodHandleReference.getMethodHandleType());
            };

            writer.writeUshort(memberIndex);
            writer.writeUshort(0);
        }
    }

    private void writeEncodedFields(@Nonnull DexDataWriter writer, @Nonnull Collection<? extends FieldKey> fields)
            throws IOException {
        int prevIndex = 0;
        for (FieldKey key: fields) {
            int index = fieldSection.getFieldIndex(key);
            if (!classSection.getFieldHiddenApiRestrictions(key).isEmpty()) {
                hasHiddenApiRestrictions = true;
            }
            writer.writeUleb128(index - prevIndex);
            writer.writeUleb128(classSection.getFieldAccessFlags(key));
            prevIndex = index;
        }
    }

    private void writeEncodedMethods(@Nonnull DexDataWriter writer, @Nonnull Collection<? extends MethodKey> methods)
            throws IOException {
        int prevIndex = 0;
        for (MethodKey key: methods) {
            int index = methodSection.getMethodIndex(key);
            if (!classSection.getMethodHiddenApiRestrictions(key).isEmpty()) {
                hasHiddenApiRestrictions = true;
            }
            writer.writeUleb128(index-prevIndex);
            writer.writeUleb128(classSection.getMethodAccessFlags(key));
            writer.writeUleb128(classSection.getCodeItemOffset(key));
            prevIndex = index;
        }
    }

    private void writeTypeLists(@Nonnull DexDataWriter writer) throws IOException {
        writer.align();
        typeListSectionOffset = writer.getPosition();
        for (Map.Entry<? extends TypeListKey, Integer> entry: typeListSection.getItems()) {
            writer.align();
            entry.setValue(writer.getPosition());

            Collection<? extends TypeKey> types = typeListSection.getTypes(entry.getKey());
            writer.writeInt(types.size());
            for (TypeKey typeKey: types) {
                writer.writeUshort(typeSection.getItemIndex(typeKey));
            }
        }
    }

    private void writeEncodedArrays(@Nonnull DexDataWriter writer) throws IOException {
        InternalEncodedValueWriter encodedValueWriter = new InternalEncodedValueWriter(writer);
        encodedArraySectionOffset = writer.getPosition();

        for (Map.Entry<? extends EncodedArrayKey, Integer> entry: encodedArraySection.getItems()) {
            entry.setValue(writer.getPosition());
            List<? extends EncodedValue> encodedArray = encodedArraySection.getEncodedValueList(entry.getKey());
            writer.writeUleb128(encodedArray.size());
            for (EncodedValue value: encodedArray) {
                writeEncodedValue(encodedValueWriter, value);
            }
        }
    }

    private void writeAnnotations(@Nonnull DexDataWriter writer) throws IOException {
        InternalEncodedValueWriter encodedValueWriter = new InternalEncodedValueWriter(writer);

        annotationSectionOffset = writer.getPosition();
        for (Map.Entry<? extends AnnotationKey, Integer> entry: annotationSection.getItems()) {
            entry.setValue(writer.getPosition());

            AnnotationKey key = entry.getKey();

            writer.writeUbyte(annotationSection.getVisibility(key));
            writer.writeUleb128(typeSection.getItemIndex(annotationSection.getType(key)));

            Collection<? extends AnnotationElement> elements = Ordering.from(BaseAnnotationElement.BY_NAME)
                    .immutableSortedCopy(annotationSection.getElements(key));

            writer.writeUleb128(elements.size());

            for (AnnotationElement element: elements) {
                writer.writeUleb128(stringSection.getItemIndex(annotationSection.getElementName(element)));
                writeEncodedValue(encodedValueWriter, annotationSection.getElementValue(element));
            }
        }
    }

    private void writeAnnotationSets(@Nonnull DexDataWriter writer) throws IOException {
        writer.align();
        annotationSetSectionOffset = writer.getPosition();
        if (shouldCreateEmptyAnnotationSet()) {
            writer.writeInt(0);
        }
        for (Map.Entry<? extends AnnotationSetKey, Integer> entry: annotationSetSection.getItems()) {
            Collection<? extends AnnotationKey> annotations = Ordering.from(BaseAnnotation.BY_TYPE)
                    .immutableSortedCopy(annotationSetSection.getAnnotations(entry.getKey()));

            writer.align();
            entry.setValue(writer.getPosition());
            writer.writeInt(annotations.size());
            for (AnnotationKey annotationKey: annotations) {
                writer.writeInt(annotationSection.getItemOffset(annotationKey));
            }
        }
    }

    private void writeAnnotationSetRefs(@Nonnull DexDataWriter writer) throws IOException {
        writer.align();
        annotationSetRefSectionOffset = writer.getPosition();
        HashMap<List<? extends AnnotationSetKey>, Integer> internedItems = Maps.newHashMap();

        for (ClassKey classKey: classSection.getSortedClasses()) {
            for (MethodKey methodKey: classSection.getSortedMethods(classKey)) {
                List<? extends AnnotationSetKey> parameterAnnotations = classSection.getParameterAnnotations(methodKey);
                if (parameterAnnotations != null) {
                    Integer prev = internedItems.get(parameterAnnotations);
                    if (prev != null) {
                        classSection.setAnnotationSetRefListOffset(methodKey, prev);
                    } else {
                        writer.align();
                        int position = writer.getPosition();
                        classSection.setAnnotationSetRefListOffset(methodKey, position);
                        internedItems.put(parameterAnnotations, position);

                        numAnnotationSetRefItems++;

                        writer.writeInt(parameterAnnotations.size());
                        for (AnnotationSetKey annotationSetKey: parameterAnnotations) {
                            if (annotationSetSection.getAnnotations(annotationSetKey).size() > 0) {
                                writer.writeInt(annotationSetSection.getItemOffset(annotationSetKey));
                            } else if (shouldCreateEmptyAnnotationSet()) {
                                writer.writeInt(annotationSetSectionOffset);
                            } else {
                                writer.writeInt(NO_OFFSET);
                            }
                        }
                    }
                }
            }
        }
    }

    private void writeAnnotationDirectories(@Nonnull DexDataWriter writer) throws IOException {
        writer.align();
        annotationDirectorySectionOffset = writer.getPosition();
        HashMap<AnnotationSetKey, Integer> internedItems = Maps.newHashMap();

        ByteBuffer tempBuffer = ByteBuffer.allocate(65536);
        tempBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (ClassKey key: classSection.getSortedClasses()) {
            // first, we write the field/method/parameter items to a temporary buffer, so that we can get a count
            // of each type, and determine if we even need to write an annotation directory for this class

            Collection<? extends FieldKey> fields = classSection.getSortedFields(key);
            Collection<? extends MethodKey> methods = classSection.getSortedMethods(key);

            // this is how much space we'll need if every field and method has annotations.
            int maxSize = fields.size() * 8 + methods.size() * 16;
            if (maxSize > tempBuffer.capacity()) {
                tempBuffer = ByteBuffer.allocate(maxSize);
                tempBuffer.order(ByteOrder.LITTLE_ENDIAN);
            }

            ((Buffer) tempBuffer).clear();

            int fieldAnnotations = 0;
            int methodAnnotations = 0;
            int parameterAnnotations = 0;

            for (FieldKey field: fields) {
                AnnotationSetKey fieldAnnotationsKey = classSection.getFieldAnnotations(field);
                if (fieldAnnotationsKey != null) {
                    fieldAnnotations++;
                    tempBuffer.putInt(fieldSection.getFieldIndex(field));
                    tempBuffer.putInt(annotationSetSection.getItemOffset(fieldAnnotationsKey));
                }
            }

            for (MethodKey method: methods) {
                AnnotationSetKey methodAnnotationsKey = classSection.getMethodAnnotations(method);
                if (methodAnnotationsKey != null) {
                    methodAnnotations++;
                    tempBuffer.putInt(methodSection.getMethodIndex(method));
                    tempBuffer.putInt(annotationSetSection.getItemOffset(methodAnnotationsKey));
                }
            }

            for (MethodKey method: methods) {
                int offset = classSection.getAnnotationSetRefListOffset(method);
                if (offset != DexWriter.NO_OFFSET) {
                    parameterAnnotations++;
                    tempBuffer.putInt(methodSection.getMethodIndex(method));
                    tempBuffer.putInt(offset);
                }
            }

            // now, we finally know how many field/method/parameter annotations were written to the temp buffer

            AnnotationSetKey classAnnotationKey = classSection.getClassAnnotations(key);
            if (fieldAnnotations == 0 && methodAnnotations == 0 && parameterAnnotations == 0) {
                if (classAnnotationKey != null) {
                    // This is an internable directory. Let's see if we've already written one like it
                    Integer directoryOffset = internedItems.get(classAnnotationKey);
                    if (directoryOffset != null) {
                        classSection.setAnnotationDirectoryOffset(key, directoryOffset);
                        continue;
                    } else {
                        internedItems.put(classAnnotationKey, writer.getPosition());
                    }
                } else {
                    continue;
                }
            }

            // yep, we need to write it out
            numAnnotationDirectoryItems++;
            classSection.setAnnotationDirectoryOffset(key, writer.getPosition());

            writer.writeInt(annotationSetSection.getNullableItemOffset(classAnnotationKey));
            writer.writeInt(fieldAnnotations);
            writer.writeInt(methodAnnotations);
            writer.writeInt(parameterAnnotations);
            writer.write(tempBuffer.array(), 0, tempBuffer.position());
        }
    }

    /**
     * Writes the debug and code sections.
     *
     * <p>The debug items are written first, in order, on the calling thread. They are small next to
     * the code items, and their offsets have to be known before any code item can be encoded (a code
     * item embeds the offset of its own debug item).
     *
     * <p>The code items are the bulk of the file, and every one of them is self contained: it only
     * reads pool indices that were assigned by the earlier phases, and its position relative to the
     * start of the code section depends solely on the code items before it. That makes the code
     * items encodable in parallel - see {@link #writeCodeItems}.
     */
    private void writeDebugAndCodeItems(@Nonnull DexDataWriter offsetWriter,
                                        @Nonnull DeferredOutputStreamFactory tempFactory) throws IOException {
        debugSectionOffset = offsetWriter.getPosition();
        DebugWriter<StringKey, TypeKey> debugWriter =
                new DebugWriter<>(stringSection, typeSection, offsetWriter);

        // Flatten the (class -> direct methods -> virtual methods) traversal once, on this thread, in
        // exactly the order the dex requires. Every pass below then only has to read from the pools.
        List<MethodKey> methods = new ArrayList<>();
        for (ClassKey classKey: classSection.getSortedClasses()) {
            methods.addAll(classSection.getSortedDirectMethods(classKey));
            methods.addAll(classSection.getSortedVirtualMethods(classKey));
        }
        final int methodCount = methods.size();

        // The debug items can change the debug items of a method (the jumbo-string path below
        // rewrites the implementation), but that copy always holds the same debug items, so this
        // pass can read them straight from the class section.
        int[] debugItemOffsets = new int[methodCount];
        for (int i = 0; i < methodCount; i++) {
            MethodKey methodKey = methods.get(i);
            debugItemOffsets[i] = writeDebugItem(offsetWriter, debugWriter,
                    classSection.getParameterNames(methodKey), classSection.getDebugItems(methodKey));
        }

        offsetWriter.align();
        codeSectionOffset = offsetWriter.getPosition();

        writeCodeItems(offsetWriter, tempFactory, methods, debugItemOffsets);
    }

    /** Smallest useful size of a parallel code-item batch. */
    private static final int MIN_CODE_ITEM_CHUNK = 256;

    /** Below this many methods the thread hand-off costs more than the encoding it saves. */
    private static final int PARALLEL_CODE_ITEM_THRESHOLD = 1024;

    /**
     * Encodes the code items of every method in {@code methods}, in order, into the code section.
     *
     * <p>Each batch of methods is encoded into its own buffer, batches are encoded in parallel, and
     * the buffers are then appended in order. This is byte-for-byte the same as encoding them one
     * after another: a code item starts on a 4 byte boundary, so a batch whose buffer is padded to a
     * multiple of 4 always leaves the next batch starting on the boundary the next code item would
     * have aligned itself to. Only the final batch is left unpadded, because nothing follows it.
     */
    private void writeCodeItems(@Nonnull DexDataWriter offsetWriter,
                                @Nonnull DeferredOutputStreamFactory tempFactory,
                                @Nonnull List<MethodKey> methods,
                                @Nonnull int[] debugItemOffsets) throws IOException {
        final int methodCount = methods.size();
        if (methodCount == 0) {
            return;
        }

        final int maxParallelChunks = Math.max(1, Runtime.getRuntime().availableProcessors()) * 4;
        final int numChunks = Math.min(
                Math.max(1, methodCount / MIN_CODE_ITEM_CHUNK),
                methodCount < PARALLEL_CODE_ITEM_THRESHOLD ? 1 : maxParallelChunks);
        final int chunkSize = (methodCount + numChunks - 1) / numChunks;

        final DeferredOutputStream[] chunkStreams = new DeferredOutputStream[numChunks];
        final int[] chunkLengths = new int[numChunks];
        final int[] chunkItemCounts = new int[numChunks];
        // Offset of each method's code item within its own chunk, or -1 when the method has no code item.
        final int[] localOffsets = new int[methodCount];
        Arrays.fill(localOffsets, -1);

        runChunked(numChunks, chunk -> {
            int from = chunk * chunkSize;
            int to = Math.min(from + chunkSize, methodCount);

            DeferredOutputStream chunkStream = tempFactory.makeDeferredOutputStream();
            DexDataWriter codeWriter = new DexDataWriter(chunkStream, 0);
            ByteArrayOutputStream ehBuf = new ByteArrayOutputStream();
            InstructionWriter<StringRef, TypeRef, FieldRefKey, MethodRefKey, ProtoRefKey,
                    MethodHandleKey, CallSiteKey> instructionWriter =
                    InstructionWriter.makeInstructionWriter(opcodes, codeWriter, stringSection, typeSection,
                            fieldSection, methodSection, protoSection, methodHandleSection, callSiteSection);

            int itemCount = 0;
            for (int i = from; i < to; i++) {
                MethodKey methodKey = methods.get(i);
                List<? extends TryBlock<? extends ExceptionHandler>> tryBlocks =
                        classSection.getTryBlocks(methodKey);
                Iterable<? extends Instruction> instructions = classSection.getInstructions(methodKey);

                if (instructions != null && stringSection.hasJumboIndexes()) {
                    boolean needsFix = false;
                    for (Instruction instruction: instructions) {
                        if (instruction.getOpcode() == Opcode.CONST_STRING) {
                            if (stringSection.getItemIndex(
                                    (StringRef)((ReferenceInstruction)instruction).getReference()) >= 65536) {
                                needsFix = true;
                                break;
                            }
                        }
                    }

                    if (needsFix) {
                        MutableMethodImplementation mutableMethodImplementation =
                                classSection.makeMutableMethodImplementation(methodKey);
                        fixInstructions(mutableMethodImplementation);

                        instructions = mutableMethodImplementation.getInstructions();
                        tryBlocks = mutableMethodImplementation.getTryBlocks();
                    }
                }

                int codeItemOffset;
                try {
                    codeItemOffset = writeCodeItem(
                            codeWriter, instructionWriter, ehBuf, methodKey, tryBlocks, instructions,
                            debugItemOffsets[i]);
                } catch (RuntimeException ex) {
                    throw new ExceptionWithContext(ex, "Exception occurred while writing code_item for method %s",
                            methodSection.getMethodReference(methodKey));
                }

                if (codeItemOffset != -1) {
                    localOffsets[i] = codeItemOffset;
                    itemCount++;
                }
            }

            // Push everything staged in the writer out to the chunk buffer, so that the position
            // below is the size of the whole chunk.
            codeWriter.flush();

            chunkStreams[chunk] = chunkStream;
            chunkLengths[chunk] = codeWriter.getPosition();
            chunkItemCounts[chunk] = itemCount;
        });

        // Append the batches in order.
        int lastNonEmptyChunk = -1;
        for (int chunk = 0; chunk < numChunks; chunk++) {
            if (chunkLengths[chunk] > 0) {
                lastNonEmptyChunk = chunk;
            }
        }

        int[] chunkBases = new int[numChunks];
        for (int chunk = 0; chunk < numChunks; chunk++) {
            if (chunkStreams[chunk] == null) {
                continue;
            }
            chunkBases[chunk] = offsetWriter.getPosition();
            chunkStreams[chunk].writeTo(offsetWriter);
            chunkStreams[chunk].close();
            chunkStreams[chunk] = null;

            numCodeItemItems += chunkItemCounts[chunk];

            if (chunk < lastNonEmptyChunk) {
                int padding = (-chunkLengths[chunk]) & 3;
                for (int i = 0; i < padding; i++) {
                    offsetWriter.write(0);
                }
            }
        }

        for (int i = 0; i < methodCount; i++) {
            int localOffset = localOffsets[i];
            if (localOffset >= 0) {
                classSection.setCodeItemOffset(methods.get(i), chunkBases[i / chunkSize] + localOffset);
            }
        }
    }

    /** A batch of work identified by its index. */
    private interface ChunkTask {
        void run(int chunk) throws IOException;
    }

    /**
     * Runs {@code task} for every index in {@code [0, numChunks)}, using the calling thread plus up
     * to {@code numChunks - 1} short-lived daemon threads. Indices are handed out dynamically, so an
     * uneven chunk is picked up by whichever thread is free first.
     */
    private static void runChunked(int numChunks, @Nonnull ChunkTask task) throws IOException {
        if (numChunks <= 1) {
            for (int i = 0; i < numChunks; i++) {
                task.run(i);
            }
            return;
        }

        final int numWorkers = Math.min(numChunks, Math.max(1, Runtime.getRuntime().availableProcessors()));
        final AtomicInteger nextChunk = new AtomicInteger(0);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable worker = () -> {
            while (failure.get() == null) {
                int chunk = nextChunk.getAndIncrement();
                if (chunk >= numChunks) {
                    return;
                }
                try {
                    task.run(chunk);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                    return;
                }
            }
        };

        Thread[] threads = new Thread[numWorkers - 1];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(worker, "dex-writer-code-items");
            threads[i].setDaemon(true);
            threads[i].start();
        }

        worker.run();

        for (Thread thread : threads) {
            boolean interrupted = false;
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        Throwable t = failure.get();
        if (t != null) {
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new ExceptionWithContext(t, "Exception occurred while writing the code section");
        }
    }

    private void fixInstructions(@Nonnull MutableMethodImplementation methodImplementation) {
        List<? extends Instruction> instructions = methodImplementation.getInstructions();

        for (int i=0; i<instructions.size(); i++) {
            Instruction instruction = instructions.get(i);

            if (instruction.getOpcode() == Opcode.CONST_STRING) {
                if (stringSection.getItemIndex(
                        (StringRef)((ReferenceInstruction)instruction).getReference()) >= 65536) {
                    methodImplementation.replaceInstruction(i, new BuilderInstruction31c(Opcode.CONST_STRING_JUMBO,
                            ((OneRegisterInstruction)instruction).getRegisterA(),
                            ((ReferenceInstruction)instruction).getReference()));
                }
            }
        }
    }

    private int writeDebugItem(@Nonnull DexDataWriter writer,
                               @Nonnull DebugWriter<StringKey, TypeKey> debugWriter,
                               @Nullable Iterable<? extends StringKey> parameterNames,
                               @Nullable Iterable<? extends DebugItem> debugItems) throws IOException {
        int parameterCount = 0;
        int lastNamedParameterIndex = -1;
        if (parameterNames != null) {
            parameterCount = Iterables.size(parameterNames);
            int index = 0;
            for (StringKey parameterName: parameterNames) {
                if (parameterName != null) {
                    lastNamedParameterIndex = index;
                }
                index++;
            }
        }


        if (lastNamedParameterIndex == -1 && (debugItems == null || Iterables.isEmpty(debugItems))) {
            return NO_OFFSET;
        }

        numDebugInfoItems++;

        int debugItemOffset = writer.getPosition();
        int startingLineNumber = 0;

        if (debugItems != null) {
            for (DebugItem debugItem: debugItems) {
                if (debugItem instanceof LineNumber) {
                    startingLineNumber = ((LineNumber)debugItem).getLineNumber();
                    break;
                }
            }
        }
        writer.writeUleb128(startingLineNumber);

        writer.writeUleb128(parameterCount);
        if (parameterNames != null) {
            int index = 0;
            for (StringKey parameterName: parameterNames) {
                if (index == parameterCount) {
                    break;
                }
                index++;
                writer.writeUleb128(stringSection.getNullableItemIndex(parameterName) + 1);
            }
        }

        if (debugItems != null) {
            debugWriter.reset(startingLineNumber);

            for (DebugItem debugItem: debugItems) {
                classSection.writeDebugItem(debugWriter, debugItem);
            }
        }
        // write an END_SEQUENCE opcode, to end the debug item
        writer.write(0);

        return debugItemOffset;
    }

    private int writeCodeItem(@Nonnull DexDataWriter writer,
                              @Nonnull InstructionWriter<StringRef, TypeRef, FieldRefKey, MethodRefKey,
                                      ProtoRefKey, MethodHandleKey, CallSiteKey> instructionWriter,
                              @Nonnull ByteArrayOutputStream ehBuf,
                              @Nonnull MethodKey methodKey,
                              @Nonnull List<? extends TryBlock<? extends ExceptionHandler>> tryBlocks,
                              @Nullable Iterable<? extends Instruction> instructions,
                              int debugItemOffset) throws IOException {
        if (instructions == null && debugItemOffset == NO_OFFSET) {
            return -1;
        }

        // numCodeItemItems is counted by the caller: this method can run on any number of threads,
        // and the caller knows which methods actually produced a code item.

        writer.align();

        int codeItemOffset = writer.getPosition();

        writer.writeUshort(classSection.getRegisterCount(methodKey));

        boolean isStatic = AccessFlags.STATIC.isSet(classSection.getMethodAccessFlags(methodKey));
        Collection<? extends TypeKey> parameters = typeListSection.getTypes(
                protoSection.getParameters(methodSection.getPrototype(methodKey)));

        writer.writeUshort(MethodUtil.getParameterRegisterCount(parameters, isStatic));

        if (instructions != null) {
            tryBlocks = TryListBuilder.massageTryBlocks(tryBlocks);

            int outParamCount = 0;
            int codeUnitCount = 0;
            for (Instruction instruction: instructions) {
                codeUnitCount += instruction.getCodeUnits();
                if (instruction.getOpcode().referenceType == ReferenceType.METHOD) {
                    ReferenceInstruction refInsn = (ReferenceInstruction)instruction;
                    MethodReference methodRef = (MethodReference)refInsn.getReference();
                    Opcode opcode = instruction.getOpcode();
                    int paramCount;
                    if (InstructionUtil.isInvokePolymorphic(opcode)) {
                        paramCount = ((VariableRegisterInstruction)instruction).getRegisterCount();
                    } else {
                        paramCount = MethodUtil.getParameterRegisterCount(methodRef, InstructionUtil.isInvokeStatic(opcode));
                    }
                    if (paramCount > outParamCount) {
                        outParamCount = paramCount;
                    }
                }
            }

            writer.writeUshort(outParamCount);
            writer.writeUshort(tryBlocks.size());
            writer.writeInt(debugItemOffset);

            writer.writeInt(codeUnitCount);
            int codeOffset = 0;
            for (Instruction instruction: instructions) {
                try {
                    switch (instruction.getOpcode().format) {
                        case Format10t:
                            instructionWriter.write((Instruction10t)instruction);
                            break;
                        case Format10x:
                            instructionWriter.write((Instruction10x)instruction);
                            break;
                        case Format11n:
                            instructionWriter.write((Instruction11n)instruction);
                            break;
                        case Format11x:
                            instructionWriter.write((Instruction11x)instruction);
                            break;
                        case Format12x:
                            instructionWriter.write((Instruction12x)instruction);
                            break;
                        case Format20bc:
                            instructionWriter.write((Instruction20bc)instruction);
                            break;
                        case Format20t:
                            instructionWriter.write((Instruction20t)instruction);
                            break;
                        case Format21c:
                            instructionWriter.write((Instruction21c)instruction);
                            break;
                        case Format21ih:
                            instructionWriter.write((Instruction21ih)instruction);
                            break;
                        case Format21lh:
                            instructionWriter.write((Instruction21lh)instruction);
                            break;
                        case Format21s:
                            instructionWriter.write((Instruction21s)instruction);
                            break;
                        case Format21t:
                            instructionWriter.write((Instruction21t)instruction);
                            break;
                        case Format22b:
                            instructionWriter.write((Instruction22b)instruction);
                            break;
                        case Format22c:
                            instructionWriter.write((Instruction22c)instruction);
                            break;
                        case Format22cs:
                            instructionWriter.write((Instruction22cs)instruction);
                            break;
                        case Format22s:
                            instructionWriter.write((Instruction22s)instruction);
                            break;
                        case Format22t:
                            instructionWriter.write((Instruction22t)instruction);
                            break;
                        case Format22x:
                            instructionWriter.write((Instruction22x)instruction);
                            break;
                        case Format23x:
                            instructionWriter.write((Instruction23x)instruction);
                            break;
                        case Format30t:
                            instructionWriter.write((Instruction30t)instruction);
                            break;
                        case Format31c:
                            instructionWriter.write((Instruction31c)instruction);
                            break;
                        case Format31i:
                            instructionWriter.write((Instruction31i)instruction);
                            break;
                        case Format31t:
                            instructionWriter.write((Instruction31t)instruction);
                            break;
                        case Format32x:
                            instructionWriter.write((Instruction32x)instruction);
                            break;
                        case Format35c:
                            instructionWriter.write((Instruction35c)instruction);
                            break;
                        case Format35mi:
                            instructionWriter.write((Instruction35mi)instruction);
                            break;
                        case Format35ms:
                            instructionWriter.write((Instruction35ms)instruction);
                            break;
                        case Format3rc:
                            instructionWriter.write((Instruction3rc)instruction);
                            break;
                        case Format3rmi:
                            instructionWriter.write((Instruction3rmi)instruction);
                            break;
                        case Format3rms:
                            instructionWriter.write((Instruction3rms)instruction);
                            break;
                        case Format45cc:
                            instructionWriter.write((Instruction45cc)instruction);
                            break;
                        case Format4rcc:
                            instructionWriter.write((Instruction4rcc)instruction);
                            break;
                        case Format51l:
                            instructionWriter.write((Instruction51l)instruction);
                            break;
                        case ArrayPayload:
                            instructionWriter.write((ArrayPayload)instruction);
                            break;
                        case PackedSwitchPayload:
                            instructionWriter.write((PackedSwitchPayload)instruction);
                            break;
                        case SparseSwitchPayload:
                            instructionWriter.write((SparseSwitchPayload)instruction);
                            break;
                        default:
                            throw new ExceptionWithContext("Unsupported instruction format: %s",
                                    instruction.getOpcode().format);
                    }
                } catch (RuntimeException ex) {
                    throw new ExceptionWithContext(ex, "Error while writing instruction at code offset 0x%x", codeOffset);
                }
                codeOffset += instruction.getCodeUnits();
            }

            if (tryBlocks.size() > 0) {
                writer.align();

                // filter out unique lists of exception handlers
                Map<List<? extends ExceptionHandler>, Integer> exceptionHandlerOffsetMap = Maps.newHashMap();
                for (TryBlock<? extends ExceptionHandler> tryBlock: tryBlocks) {
                    exceptionHandlerOffsetMap.put(tryBlock.getExceptionHandlers(), 0);
                }
                DexDataWriter.writeUleb128(ehBuf, exceptionHandlerOffsetMap.size());

                for (TryBlock<? extends ExceptionHandler> tryBlock: tryBlocks) {
                    int startAddress = tryBlock.getStartCodeAddress();
                    int endAddress = startAddress + tryBlock.getCodeUnitCount();

                    int tbCodeUnitCount = endAddress - startAddress;

                    writer.writeInt(startAddress);
                    writer.writeUshort(tbCodeUnitCount);

                    if (tryBlock.getExceptionHandlers().size() == 0) {
                        throw new ExceptionWithContext("No exception handlers for the try block!");
                    }

                    Integer offset = exceptionHandlerOffsetMap.get(tryBlock.getExceptionHandlers());
                    if (offset != 0) {
                        // exception handler has already been written out, just use it
                        writer.writeUshort(offset);
                    } else {
                        // if offset has not been set yet, we are about to write out a new exception handler
                        offset = ehBuf.size();
                        writer.writeUshort(offset);
                        exceptionHandlerOffsetMap.put(tryBlock.getExceptionHandlers(), offset);

                        // check if the last exception handler is a catch-all and adjust the size accordingly
                        int ehSize = tryBlock.getExceptionHandlers().size();
                        ExceptionHandler ehLast = tryBlock.getExceptionHandlers().get(ehSize-1);
                        if (ehLast.getExceptionType() == null) {
                            ehSize = ehSize * (-1) + 1;
                        }

                        // now let's layout the exception handlers, assuming that catch-all is always last
                        DexDataWriter.writeSleb128(ehBuf, ehSize);
                        for (ExceptionHandler eh : tryBlock.getExceptionHandlers()) {
                            TypeKey exceptionTypeKey = classSection.getExceptionType(eh);

                            int codeAddress = eh.getHandlerCodeAddress();

                            if (exceptionTypeKey != null) {
                                //regular exception handling
                                DexDataWriter.writeUleb128(ehBuf, typeSection.getItemIndex(exceptionTypeKey));
                                DexDataWriter.writeUleb128(ehBuf, codeAddress);
                            } else {
                                //catch-all
                                DexDataWriter.writeUleb128(ehBuf, codeAddress);
                            }
                        }
                    }
                }

                if (ehBuf.size() > 0) {
                    ehBuf.writeTo(writer);
                    ehBuf.reset();
                }
            }
        } else {
            // no instructions, all we have is the debug item offset
            writer.writeUshort(0);
            writer.writeUshort(0);
            writer.writeInt(debugItemOffset);
            writer.writeInt(0);
        }

        return codeItemOffset;
    }

    private int calcNumItems() {
        int numItems = 0;

        // header item
        numItems++;

        if (stringSection.getItems().size() > 0) {
            numItems += 2; // index and data
        }
        if (typeSection.getItems().size()  > 0) {
            numItems++;
        }
        if (protoSection.getItems().size() > 0) {
            numItems++;
        }
        if (fieldSection.getItems().size() > 0) {
            numItems++;
        }
        if (methodSection.getItems().size() > 0) {
            numItems++;
        }
        if (callSiteSection.getItems().size() > 0) {
            numItems++;
        }
        if (methodHandleSection.getItems().size() > 0) {
            numItems++;
        }
        if (typeListSection.getItems().size() > 0) {
            numItems++;
        }
        if (encodedArraySection.getItems().size() > 0) {
            numItems++;
        }
        if (annotationSection.getItems().size() > 0) {
            numItems++;
        }
        if (annotationSetSection.getItems().size() > 0 || shouldCreateEmptyAnnotationSet()) {
            numItems++;
        }
        if (numAnnotationSetRefItems > 0) {
            numItems++;
        }
        if (numAnnotationDirectoryItems > 0) {
            numItems++;
        }
        if (numDebugInfoItems > 0) {
            numItems++;
        }
        if (numCodeItemItems > 0) {
            numItems++;
        }
        if (classSection.getItems().size() > 0) {
            numItems++;
        }
        if (numClassDataItems > 0) {
            numItems++;
        }
        if (shouldWriteHiddenApiRestrictions()) {
            numItems++;
        }
        // map item itself
        numItems++;

        return numItems;
    }

    private void writeMapItem(@Nonnull DexDataWriter writer) throws IOException{
        writer.align();
        mapSectionOffset = writer.getPosition();
        int numItems = calcNumItems();

        writer.writeInt(numItems);

        // index section
        writeMapItem(writer, ItemType.HEADER_ITEM, 1, 0);
        writeMapItem(writer, ItemType.STRING_ID_ITEM, stringSection.getItems().size(), stringIndexSectionOffset);
        writeMapItem(writer, ItemType.TYPE_ID_ITEM, typeSection.getItems().size(), typeSectionOffset);
        writeMapItem(writer, ItemType.PROTO_ID_ITEM, protoSection.getItems().size(), protoSectionOffset);
        writeMapItem(writer, ItemType.FIELD_ID_ITEM, fieldSection.getItems().size(), fieldSectionOffset);
        writeMapItem(writer, ItemType.METHOD_ID_ITEM, methodSection.getItems().size(), methodSectionOffset);
        writeMapItem(writer, ItemType.CLASS_DEF_ITEM, classSection.getItems().size(), classIndexSectionOffset);
        writeMapItem(writer, ItemType.CALL_SITE_ID_ITEM, callSiteSection.getItems().size(), callSiteSectionOffset);
        writeMapItem(writer, ItemType.METHOD_HANDLE_ITEM, methodHandleSection.getItems().size(),
                methodHandleSectionOffset);

        // data section
        writeMapItem(writer, ItemType.STRING_DATA_ITEM, stringSection.getItems().size(), stringDataSectionOffset);
        writeMapItem(writer, ItemType.TYPE_LIST, typeListSection.getItems().size(), typeListSectionOffset);
        writeMapItem(writer, ItemType.ENCODED_ARRAY_ITEM, encodedArraySection.getItems().size(),
                encodedArraySectionOffset);
        writeMapItem(writer, ItemType.ANNOTATION_ITEM, annotationSection.getItems().size(), annotationSectionOffset);
        writeMapItem(writer, ItemType.ANNOTATION_SET_ITEM,
                annotationSetSection.getItems().size() + (shouldCreateEmptyAnnotationSet() ? 1 : 0),
                annotationSetSectionOffset);
        writeMapItem(writer, ItemType.ANNOTATION_SET_REF_LIST, numAnnotationSetRefItems, annotationSetRefSectionOffset);
        writeMapItem(writer, ItemType.ANNOTATION_DIRECTORY_ITEM, numAnnotationDirectoryItems,
                annotationDirectorySectionOffset);
        writeMapItem(writer, ItemType.DEBUG_INFO_ITEM, numDebugInfoItems, debugSectionOffset);
        writeMapItem(writer, ItemType.CODE_ITEM, numCodeItemItems, codeSectionOffset);
        writeMapItem(writer, ItemType.CLASS_DATA_ITEM, numClassDataItems, classDataSectionOffset);

        if (shouldWriteHiddenApiRestrictions()) {
            writeMapItem(writer, ItemType.HIDDENAPI_CLASS_DATA_ITEM, 1, hiddenApiRestrictionsOffset);
        }

        writeMapItem(writer, ItemType.MAP_LIST, 1, mapSectionOffset);
    }

    private void writeMapItem(@Nonnull DexDataWriter writer, int type, int size, int offset) throws IOException {
        if (size > 0) {
            writer.writeUshort(type);
            writer.writeUshort(0);
            writer.writeInt(size);
            writer.writeInt(offset);
        }
    }

    private void writeHeader(@Nonnull DexDataWriter writer, int dataOffset, int fileSize) throws IOException {
        // Write the appropriate header.
        writer.write(HeaderItem.getMagicForApi(opcodes.api));

        // checksum placeholder
        writer.writeInt(0);

        // signature placeholder
        writer.write(new byte[20]);

        writer.writeInt(fileSize);
        writer.writeInt(HeaderItem.ITEM_SIZE);
        writer.writeInt(HeaderItem.LITTLE_ENDIAN_TAG);

        // link
        writer.writeInt(0);
        writer.writeInt(0);

        // map
        writer.writeInt(mapSectionOffset);

        // index sections

        writeSectionInfo(writer, stringSection.getItems().size(), stringIndexSectionOffset);
        writeSectionInfo(writer, typeSection.getItems().size(), typeSectionOffset);
        writeSectionInfo(writer, protoSection.getItems().size(), protoSectionOffset);
        writeSectionInfo(writer, fieldSection.getItems().size(), fieldSectionOffset);
        writeSectionInfo(writer, methodSection.getItems().size(), methodSectionOffset);
        writeSectionInfo(writer, classSection.getItems().size(), classIndexSectionOffset);

        // data section
        writer.writeInt(fileSize - dataOffset);
        writer.writeInt(dataOffset);
    }

    private void writeSectionInfo(DexDataWriter writer, int numItems, int offset) throws IOException {
        writer.writeInt(numItems);
        if (numItems > 0) {
            writer.writeInt(offset);
        } else {
            writer.writeInt(0);
        }
    }

    private boolean shouldCreateEmptyAnnotationSet() {
        // Workaround for a crash in Dalvik VM before Jelly Bean MR1 (4.2)
        // which is triggered by NO_OFFSET in parameter annotation list.
        // (https://code.google.com/p/android/issues/detail?id=35304)
        return (opcodes.api < 17);
    }

    public abstract class SectionProvider {
        @Nonnull public abstract StringSectionType getStringSection();
        @Nonnull public abstract TypeSectionType getTypeSection();
        @Nonnull public abstract ProtoSectionType getProtoSection();
        @Nonnull public abstract FieldSectionType getFieldSection();
        @Nonnull public abstract MethodSectionType getMethodSection();
        @Nonnull public abstract ClassSectionType getClassSection();
        @Nonnull public abstract CallSiteSectionType getCallSiteSection();
        @Nonnull public abstract MethodHandleSectionType getMethodHandleSection();
        @Nonnull public abstract TypeListSectionType getTypeListSection();
        @Nonnull public abstract AnnotationSectionType getAnnotationSection();
        @Nonnull public abstract AnnotationSetSectionType getAnnotationSetSection();
        @Nonnull public abstract EncodedArraySectionType getEncodedArraySection();
    }
}
