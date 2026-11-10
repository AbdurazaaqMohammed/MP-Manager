package io.github.abdurazaaqmohammed.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.tools.smali.dexlib2.DebugItemType;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Strips debug information (.line, .param, .source, .prologue, .local) from dexlib2 ClassDef objects
 * to prevent unnecessary dex bloat (typically ~40KB per dex).
 */
public final class DexDebugStripper {

    private DexDebugStripper() {}

    public static ClassDef strip(ClassDef delegate) {
        if (delegate == null) return null;
        return new StrippedClassDef(delegate);
    }

    public static List<ClassDef> stripAll(Iterable<? extends ClassDef> classes) {
        if (classes == null) return Collections.emptyList();
        List<ClassDef> out = new ArrayList<>();
        for (ClassDef c : classes) {
            out.add(strip(c));
        }
        return out;
    }

    private static class StrippedClassDef implements ClassDef {
        private final ClassDef delegate;

        StrippedClassDef(ClassDef delegate) {
            this.delegate = delegate;
        }

        @Override @Nonnull public String getType() { return delegate.getType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override @Nullable public String getSuperclass() { return delegate.getSuperclass(); }
        @Override @Nonnull public List<String> getInterfaces() { return delegate.getInterfaces(); }
        @Override @Nullable public String getSourceFile() { return null; }
        @Override @Nonnull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @Nonnull public Iterable<? extends Field> getStaticFields() { return delegate.getStaticFields(); }
        @Override @Nonnull public Iterable<? extends Field> getInstanceFields() { return delegate.getInstanceFields(); }
        @Override @Nonnull public Iterable<? extends Field> getFields() { return delegate.getFields(); }

        @Override @Nonnull public Iterable<? extends Method> getDirectMethods() {
            return wrapMethods(delegate.getDirectMethods());
        }

        @Override @Nonnull public Iterable<? extends Method> getVirtualMethods() {
            return wrapMethods(delegate.getVirtualMethods());
        }

        @Override @Nonnull public Iterable<? extends Method> getMethods() {
            return wrapMethods(delegate.getMethods());
        }

        @Override public int compareTo(@Nonnull CharSequence o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
        @Override public int length() { return delegate.length(); }
        @Override public char charAt(int index) { return delegate.charAt(index); }
        @NonNull @Override public CharSequence subSequence(int start, int end) { return delegate.subSequence(start, end); }
        @Override @Nonnull public String toString() { return delegate.toString(); }

        private Iterable<? extends Method> wrapMethods(Iterable<? extends Method> methods) {
            List<Method> wrapped = new ArrayList<>();
            for (Method method : methods) {
                wrapped.add(new StrippedMethod(method));
            }
            return wrapped;
        }
    }

    private static class StrippedMethod implements Method {
        private final Method delegate;

        StrippedMethod(Method delegate) {
            this.delegate = delegate;
        }

        @Override @Nonnull public String getDefiningClass() { return delegate.getDefiningClass(); }
        @Override @Nonnull public String getName() { return delegate.getName(); }

        @Override @Nonnull public List<? extends MethodParameter> getParameters() {
            List<MethodParameter> params = new ArrayList<>();
            for (MethodParameter p : delegate.getParameters()) {
                params.add(new ImmutableMethodParameter(p.getType(), null, null));
            }
            return params;
        }

        @Override @Nonnull public List<? extends CharSequence> getParameterTypes() { return delegate.getParameterTypes(); }
        @Override @Nonnull public String getReturnType() { return delegate.getReturnType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override @Nonnull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @Nonnull public Set<HiddenApiRestriction> getHiddenApiRestrictions() { return delegate.getHiddenApiRestrictions(); }

        @Override @Nullable public MethodImplementation getImplementation() {
            MethodImplementation impl = delegate.getImplementation();
            if (impl == null) return null;
            return new StrippedMethodImplementation(impl);
        }

        @Override public int compareTo(@Nonnull MethodReference o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
    }

    private static class StrippedMethodImplementation implements MethodImplementation {
        private final MethodImplementation delegate;

        StrippedMethodImplementation(MethodImplementation delegate) {
            this.delegate = delegate;
        }

        @Override public int getRegisterCount() { return delegate.getRegisterCount(); }
        @NonNull @Override public Iterable<? extends Instruction> getInstructions() { return delegate.getInstructions(); }
        @NonNull @Override public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks() { return delegate.getTryBlocks(); }

        @NonNull
        @Override public Iterable<? extends DebugItem> getDebugItems() {
            return Collections.emptyList();
        }
    }
}
