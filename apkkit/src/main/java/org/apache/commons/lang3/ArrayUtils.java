///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.apache.commons.lang3;
//
//import com.custom.Function;
//import com.custom.Supplier;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.lang.reflect.Type;
//import java.util.Arrays;
//
//
///**
// * Operations on arrays, primitive arrays (like {@code int[]}) and
// * primitive wrapper arrays (like {@code Integer[]}).
// * <p>
// * This class tries to handle {@code null} input gracefully.
// * An exception will not be thrown for a {@code null}
// * array input. However, an Object array that contains a {@code null}
// * element may throw an exception. Each method documents its behavior.
// * </p>
// * <p>
// * #ThreadSafe#
// * </p>
// * @since 2.0
// */
//public class ArrayUtils {
//
//    /**
//     * An empty immutable {@code boolean} array.
//     */
//    public static final boolean[] EMPTY_BOOLEAN_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Boolean} array.
//     */
//    public static final Boolean[] EMPTY_BOOLEAN_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@code byte} array.
//     */
//    public static final byte[] EMPTY_BYTE_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Byte} array.
//     */
//    public static final Byte[] EMPTY_BYTE_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@code char} array.
//     */
//    public static final char[] EMPTY_CHAR_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Character} array.
//     */
//    public static final Character[] EMPTY_CHARACTER_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Class} array.
//     */
//    public static final Class<?>[] EMPTY_CLASS_ARRAY = {};
//
//    /**
//     * An empty immutable {@code double} array.
//     */
//    public static final double[] EMPTY_DOUBLE_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Double} array.
//     */
//    public static final Double[] EMPTY_DOUBLE_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Field} array.
//     *
//     * @since 3.10
//     */
//    public static final Field[] EMPTY_FIELD_ARRAY = {};
//
//    /**
//     * An empty immutable {@code float} array.
//     */
//    public static final float[] EMPTY_FLOAT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Float} array.
//     */
//    public static final Float[] EMPTY_FLOAT_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@code int} array.
//     */
//    public static final int[] EMPTY_INT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Integer} array.
//     */
//    public static final Integer[] EMPTY_INTEGER_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@code long} array.
//     */
//    public static final long[] EMPTY_LONG_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Long} array.
//     */
//    public static final Long[] EMPTY_LONG_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Method} array.
//     *
//     * @since 3.10
//     */
//    public static final Method[] EMPTY_METHOD_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Object} array.
//     */
//    public static final Object[] EMPTY_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@code short} array.
//     */
//    public static final short[] EMPTY_SHORT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Short} array.
//     */
//    public static final Short[] EMPTY_SHORT_OBJECT_ARRAY = {};
//
//    /**
//     * An empty immutable {@link String} array.
//     */
//    public static final String[] EMPTY_STRING_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Throwable} array.
//     *
//     * @since 3.10
//     */
//    public static final Throwable[] EMPTY_THROWABLE_ARRAY = {};
//
//    /**
//     * An empty immutable {@link Type} array.
//     *
//     * @since 3.10
//     */
//    public static final Type[] EMPTY_TYPE_ARRAY = {};
//
//    /**
//     * The index value when an element is not found in a list or array: {@code -1}.
//     * This value is returned by methods in this class and can also be used in comparisons with values returned by
//     * various method from {@link java.util.List}.
//     */
//    public static final int INDEX_NOT_FOUND = -1;
//
//
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static boolean[] clone(final boolean[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static byte[] clone(final byte[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static char[] clone(final char[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static double[] clone(final double[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static float[] clone(final float[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static int[] clone(final int[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static long[] clone(final long[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Clones an array or returns {@code null}.
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param array the array to clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static short[] clone(final short[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Shallow clones an array or returns {@code null}.
//     * <p>
//     * The objects in the array are not cloned, thus there is no special handling for multi-dimensional arrays.
//     * </p>
//     * <p>
//     * This method returns {@code null} for a {@code null} input array.
//     * </p>
//     *
//     * @param <T>   the component type of the array
//     * @param array the array to shallow clone, may be {@code null}
//     * @return the cloned array, {@code null} if {@code null} input
//     */
//    public static <T> T[] clone(final T[] array) {
//        return array != null ? array.clone() : null;
//    }
//
//    /**
//     * Produces a new {@code boolean} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(boolean[], int, int)
//     */
//    public static boolean[] subarray(final boolean[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_BOOLEAN_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, boolean[]::new);
//    }
//
//    /**
//     * Produces a new {@code byte} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(byte[], int, int)
//     */
//    public static byte[] subarray(final byte[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_BYTE_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, byte[]::new);
//    }
//
//    /**
//     * Produces a new {@code char} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(char[], int, int)
//     */
//    public static char[] subarray(final char[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_CHAR_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, char[]::new);
//    }
//
//    /**
//     * Produces a new {@code double} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(double[], int, int)
//     */
//    public static double[] subarray(final double[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_DOUBLE_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, double[]::new);
//    }
//
//    /**
//     * Produces a new {@code float} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(float[], int, int)
//     */
//    public static float[] subarray(final float[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_FLOAT_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, float[]::new);
//    }
//
//    /**
//     * Produces a new {@code int} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(int[], int, int)
//     */
//    public static int[] subarray(final int[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_INT_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, int[]::new);
//    }
//
//    /**
//     * Produces a new {@code long} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(long[], int, int)
//     */
//    public static long[] subarray(final long[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_LONG_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, long[]::new);
//    }
//
//    /**
//     * Produces a new {@code short} array containing the elements
//     * between the start and end indices.
//     * <p>
//     * The start index is inclusive, the end index exclusive.
//     * Null array input produces null output.
//     * </p>
//     *
//     * @param array  the array
//     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
//     *      is promoted to 0, overvalue (&gt;array.length) results
//     *      in an empty array.
//     * @param endIndexExclusive  elements up to endIndex-1 are present in the
//     *      returned subarray. Undervalue (&lt; startIndex) produces
//     *      empty array, overvalue (&gt;array.length) is demoted to
//     *      array length.
//     * @return a new array containing the elements between
//     *      the start and end indices.
//     * @since 2.1
//     * @see Arrays#copyOfRange(short[], int, int)
//     */
//    public static short[] subarray(final short[] array, int startIndexInclusive, int endIndexExclusive) {
//        if (array == null) {
//            return null;
//        }
//        startIndexInclusive = max0(startIndexInclusive);
//        endIndexExclusive = Math.min(endIndexExclusive, array.length);
//        final int newSize = endIndexExclusive - startIndexInclusive;
//        if (newSize <= 0) {
//            return EMPTY_SHORT_ARRAY;
//        }
//        return arraycopy(array, startIndexInclusive, 0, newSize, short[]::new);
//    }
//
////    /**
////     * Produces a new array containing the elements between
////     * the start and end indices.
////     * <p>
////     * The start index is inclusive, the end index exclusive.
////     * Null array input produces null output.
////     * </p>
////     * <p>
////     * The component type of the subarray is always the same as
////     * that of the input array. Thus, if the input is an array of type
////     * {@link Date}, the following usage is envisaged:
////     * </p>
////     * <pre>
////     * Date[] someDates = (Date[]) ArrayUtils.subarray(allDates, 2, 5);
////     * </pre>
////     *
////     * @param <T> the component type of the array
////     * @param array  the array
////     * @param startIndexInclusive  the starting index. Undervalue (&lt;0)
////     *      is promoted to 0, overvalue (&gt;array.length) results
////     *      in an empty array.
////     * @param endIndexExclusive  elements up to endIndex-1 are present in the
////     *      returned subarray. Undervalue (&lt; startIndex) produces
////     *      empty array, overvalue (&gt;array.length) is demoted to
////     *      array length.
////     * @return a new array containing the elements between
////     *      the start and end indices.
////     * @since 2.1
////     * @see Arrays#copyOfRange(Object[], int, int)
////     */
////    public static <T> T[] subarray(final T[] array, int startIndexInclusive, int endIndexExclusive) {
////        if (array == null) {
////            return null;
////        }
////        startIndexInclusive = max0(startIndexInclusive);
////        endIndexExclusive = Math.min(endIndexExclusive, array.length);
////        final int newSize = endIndexExclusive - startIndexInclusive;
////        final Class<T> type = getComponentType(array);
////        if (newSize <= 0) {
////            return newInstance(type, 0);
////        }
////        return arraycopy(array, startIndexInclusive, 0, newSize, () -> newInstance(type, newSize));
////    }
//
//    /**
//     * A fluent version of {@link System#arraycopy(Object, int, Object, int, int)} that returns the destination array.
//     *
//     * @param <T>       the type.
//     * @param source    the source array.
//     * @param sourcePos starting position in the source array.
//     * @param destPos   starting position in the destination data.
//     * @param length    the number of array elements to be copied.
//     * @param allocator allocates the array to populate and return.
//     * @return dest
//     * @throws IndexOutOfBoundsException if copying would cause access of data outside array bounds.
//     * @throws ArrayStoreException       if an element in the {@code src} array could not be stored into the {@code dest} array because of a type
//     *                                   mismatch.
//     * @throws NullPointerException      if either {@code src} or {@code dest} is {@code null}.
//     * @since 3.15.0
//     */
//    public static <T> T arraycopy(final T source, final int sourcePos, final int destPos, final int length, final Function<Integer, T> allocator) {
//        return arraycopy(source, sourcePos, allocator.apply(length), destPos, length);
//    }
//
//    /**
//     * A fluent version of {@link System#arraycopy(Object, int, Object, int, int)} that returns the destination array.
//     *
//     * @param <T>       the type.
//     * @param source    the source array.
//     * @param sourcePos starting position in the source array.
//     * @param destPos   starting position in the destination data.
//     * @param length    the number of array elements to be copied.
//     * @param allocator allocates the array to populate and return.
//     * @return dest
//     * @throws IndexOutOfBoundsException if copying would cause access of data outside array bounds.
//     * @throws ArrayStoreException       if an element in the {@code src} array could not be stored into the {@code dest} array because of a type
//     *                                   mismatch.
//     * @throws NullPointerException      if either {@code src} or {@code dest} is {@code null}.
//     * @since 3.15.0
//     */
//    public static <T> T arraycopy(final T source, final int sourcePos, final int destPos, final int length, final Supplier<T> allocator) {
//        return arraycopy(source, sourcePos, allocator.get(), destPos, length);
//    }
//
//    /**
//     * A fluent version of {@link System#arraycopy(Object, int, Object, int, int)} that returns the destination array.
//     *
//     * @param <T>       the type
//     * @param source    the source array.
//     * @param sourcePos starting position in the source array.
//     * @param dest      the destination array.
//     * @param destPos   starting position in the destination data.
//     * @param length    the number of array elements to be copied.
//     * @return dest
//     * @throws IndexOutOfBoundsException if copying would cause access of data outside array bounds.
//     * @throws ArrayStoreException       if an element in the {@code src} array could not be stored into the {@code dest} array because of a type
//     *                                   mismatch.
//     * @throws NullPointerException      if either {@code src} or {@code dest} is {@code null}.
//     * @since 3.15.0
//     */
//    public static <T> T arraycopy(final T source, final int sourcePos, final T dest, final int destPos, final int length) {
//        System.arraycopy(source, sourcePos, dest, destPos, length);
//        return dest;
//    }
//
//    private static int max0(final int other) {
//        return Math.max(0, other);
//    }
//
//}