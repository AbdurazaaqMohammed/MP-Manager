package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodHandleReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EnumEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FieldEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodHandleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodTypeEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.TypeEncodedValue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single place that pulls every reference out of a kept artifact: instruction
 * references (method/field/type/string/call-site/method-handle), try/catch types,
 * the annotation graph (nested annotations, .class literals, enum constants,
 * method/field refs, arrays) and field initial values.
 */
public final class ReferenceExtractor {

    public static final class MethodCall {
        public final MethodReference reference;
        public final Opcode opcode;

        public MethodCall(MethodReference reference, Opcode opcode) {
            this.reference = reference;
            this.opcode = opcode;
        }
    }

    public static final class ReferenceBatch {
        public final List<MethodCall> calls = new ArrayList<>();
        public final Set<FieldReference> fields = new LinkedHashSet<>();
        public final Set<String> types = new LinkedHashSet<>();
        public final Set<String> strings = new LinkedHashSet<>();
    }

    private ReferenceExtractor() {
    }

    public static ReferenceBatch extractMethod(Method method) {
        ReferenceBatch batch = new ReferenceBatch();
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            for (Instruction instruction : impl.getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction)) continue;
                addReference(((ReferenceInstruction) instruction).getReference(),
                        instruction.getOpcode(), batch);
            }
            for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
                for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                    String type = handler.getExceptionType();
                    if (type != null) batch.types.add(type);
                }
            }
        }
        addAnnotations(method.getAnnotations(), batch);
        return batch;
    }

    /** Class annotations plus every field's annotations and initial values. */
    public static ReferenceBatch extractClass(ClassDef classDef) {
        ReferenceBatch batch = new ReferenceBatch();
        addAnnotations(classDef.getAnnotations(), batch);
        for (Field field : classDef.getFields()) {
            addAnnotations(field.getAnnotations(), batch);
            EncodedValue initial = field.getInitialValue();
            if (initial != null) extractEncodedValue(initial, batch);
        }
        return batch;
    }

    private static void addAnnotations(Set<? extends Annotation> annotations, ReferenceBatch batch) {
        if (annotations == null) return;
        for (Annotation annotation : annotations) {
            batch.types.add(annotation.getType());
            for (AnnotationElement element : annotation.getElements())
                extractEncodedValue(element.getValue(), batch);
        }
    }

    public static void extractAnnotation(Annotation annotation, ReferenceBatch batch) {
        batch.types.add(annotation.getType());
        for (AnnotationElement element : annotation.getElements())
            extractEncodedValue(element.getValue(), batch);
    }

    /** Recursive walk over an encoded value (annotation/array/type/enum/method/field/handle/proto). */
    public static void extractEncodedValue(EncodedValue value, ReferenceBatch batch) {
        if (value == null) return;
        if (value instanceof AnnotationEncodedValue) {
            AnnotationEncodedValue annotation = (AnnotationEncodedValue) value;
            batch.types.add(annotation.getType());
            for (AnnotationElement element : annotation.getElements())
                extractEncodedValue(element.getValue(), batch);
        } else if (value instanceof ArrayEncodedValue) {
            for (EncodedValue item : ((ArrayEncodedValue) value).getValue())
                extractEncodedValue(item, batch);
        } else if (value instanceof TypeEncodedValue) {
            batch.types.add(((TypeEncodedValue) value).getValue());
        } else if (value instanceof EnumEncodedValue) {
            batch.fields.add(((EnumEncodedValue) value).getValue());
        } else if (value instanceof FieldEncodedValue) {
            batch.fields.add(((FieldEncodedValue) value).getValue());
        } else if (value instanceof MethodEncodedValue) {
            batch.calls.add(new MethodCall(((MethodEncodedValue) value).getValue(), null));
        } else if (value instanceof MethodHandleEncodedValue) {
            addMemberReference(((MethodHandleEncodedValue) value).getValue().getMemberReference(), batch);
        } else if (value instanceof MethodTypeEncodedValue) {
            for (CharSequence param : ((MethodTypeEncodedValue) value).getValue().getParameterTypes())
                batch.types.add(param.toString());
            batch.types.add(((MethodTypeEncodedValue) value).getValue().getReturnType());
        } else if (value instanceof StringEncodedValue) {
            batch.strings.add(((StringEncodedValue) value).getValue());
        }
    }

    private static void addReference(Reference reference, Opcode opcode, ReferenceBatch batch) {
        if (reference instanceof MethodReference) {
            batch.calls.add(new MethodCall((MethodReference) reference, opcode));
        } else if (reference instanceof FieldReference) {
            batch.fields.add((FieldReference) reference);
        } else if (reference instanceof TypeReference) {
            batch.types.add(((TypeReference) reference).getType());
        } else if (reference instanceof CallSiteReference) {
            CallSiteReference callSite = (CallSiteReference) reference;
            addMemberReference(callSite.getMethodHandle().getMemberReference(), batch);
            for (EncodedValue argument : callSite.getExtraArguments())
                extractEncodedValue(argument, batch);
        } else if (reference instanceof MethodHandleReference) {
            addMemberReference(((MethodHandleReference) reference).getMemberReference(), batch);
        } else if (reference instanceof StringReference) {
            batch.strings.add(((StringReference) reference).getString());
        }
    }

    private static void addMemberReference(Reference member, ReferenceBatch batch) {
        if (member instanceof MethodReference) {
            batch.calls.add(new MethodCall((MethodReference) member, null));
        } else if (member instanceof FieldReference) {
            batch.fields.add((FieldReference) member);
        } else if (member instanceof MethodHandleReference) {
            addMemberReference(((MethodHandleReference) member).getMemberReference(), batch);
        }
    }
}