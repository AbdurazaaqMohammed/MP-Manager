/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.utils.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class LinkedIterator<T> implements Iterator<T> {
    
    private final org.apache.commons.collections4.Transformer<? super T, T> transformer;
    private T mNext;
    private boolean mComputed;
    
    public LinkedIterator(boolean includeSelf, T item, org.apache.commons.collections4.Transformer<? super T, T> transformer) {
        this.mNext = item;
        this.transformer = transformer;
        this.mComputed = includeSelf;
    }
    
    @Override
    public boolean hasNext() {
        return computeNext() != null;
    }
    @Override
    public T next() {
        T item = computeNext();
        if (item == null) {
            throw new NoSuchElementException();
        }
        mComputed = false;
        return item;
    }
    private T computeNext() {
        T next = this.mNext;
        if (next == null || mComputed) {
            return next;
        }
        next = transformer.transform(next);
        this.mNext = next;
        mComputed = next != null;
        return next;
    }

    public static<T1> Iterator<T1> of(boolean includeSelf, T1 item, org.apache.commons.collections4.Transformer<? super T1, T1> transformer) {
        if (item == null) {
            return EmptyIterator.of();
        }
        return new LinkedIterator<>(includeSelf, item, transformer);
    }
    public static<T1> Iterator<T1> of(T1 item, org.apache.commons.collections4.Transformer<? super T1, T1> transformer) {
        return of(false, item, transformer);
    }
}
