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
package com.reandroid.arsc.item;

import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.base.Creator;
import com.reandroid.arsc.base.DirectStreamReader;
import com.reandroid.arsc.io.BlockReader;
import com.reandroid.utils.CompareUtil;
import com.reandroid.utils.HexUtil;
import com.reandroid.utils.ObjectsUtil;

import java.io.IOException;
import java.io.OutputStream;

public abstract class OffsetItem extends BlockItem implements DirectStreamReader,
        Comparable<OffsetItem> {

    public static final int NO_ENTRY = ObjectsUtil.of(0xffffffff);
    public static final int NO_ENTRY16 = ObjectsUtil.of(0xffff);

    public static final Creator<OffsetItem> CREATOR_OFFSET16 = Helper.init16();
    public static final Creator<OffsetItem> CREATOR_OFFSET32 = Helper.init32();
    public static final Creator<OffsetItem> CREATOR_SPARSE = Helper.initSparse();

    protected int mOffset;

    protected OffsetItem() {
        super(0);
    }

    public int getOffset() {
        return mOffset;
    }
    public void setOffset(int offset) {
        if (offset != mOffset) {
            validateOffset(offset);
            mOffset = offset;
        }
    }

    public int getIdx() {
        return getIndex();
    }
    public void setIdx(int idx) {
    }

    protected void validateOffset(int offset) {
    }

    public boolean isNoEntry() {
        return getOffset() == NO_ENTRY;
    }

    public int updateOffset(Block target, int offset) {
        if (target.isNull()) {
            setOffset(NO_ENTRY);
        } else {
            setOffset(offset);
            offset = offset + target.countBytes();
        }
        return offset;
    }
    public void readTarget(BlockReader reader, Block target) throws IOException {
        readTarget(reader, target, false);
    }
    public void readTarget(BlockReader reader, Block target, boolean ignoreOutOfRange) throws IOException {
        boolean noEntry = isNoEntry();
        int offset = getOffset();
        if (!noEntry) {
            int maximumPosition = reader.getPosition() + reader.available();
            if (offset < 0 || offset > maximumPosition) {
                if (!ignoreOutOfRange) {
                    throw new IOException("Offset " + offset + " is out of range " + maximumPosition);
                } else {
                    offset = NO_ENTRY;
                    setOffset(offset);
                    noEntry = true;
                }
            }
        }
        target.setNull(noEntry);
        if (!noEntry) {
            int position = reader.getPosition();
            reader.seek(offset);
            try {
                target.readBytes(reader);
            }catch (Exception ex) {
                throw new IOException("Error at:" + this + ex.getMessage() , ex);
            }
            int current = reader.getPosition();
            if (current < position) {
                reader.seek(position);
            }
        }
    }

    protected void validateValueRange(int value) {
        if (value != NO_ENTRY && (value & 0xffff0000) != 0) {
            throw new NumberFormatException("Value out of range [0 - 0xffff]: " +
                    HexUtil.toHex(value, 1));
        }
    }

    protected abstract int itemBytesLength();
    protected abstract void parseOffset(byte[] bytes);
    protected abstract void writeOffset(byte[] bytes);

    @Override
    public void onReadBytes(BlockReader reader) throws IOException {
        byte[] bytes = new byte[itemBytesLength()];
        reader.readFully(bytes);
        parseOffset(bytes);
    }

    @Override
    public byte[] getBytes() {
        if(isNull()){
            return null;
        }
        byte[] bytes = new byte[itemBytesLength()];
        writeOffset(bytes);
        return bytes;
    }

    @Override
    public int countBytes() {
        if(isNull()){
            return 0;
        }
        return itemBytesLength();
    }

    @Override
    protected int onWriteBytes(OutputStream stream) throws IOException {
        if (isNull()) {
            return 0;
        }
        byte[] bytes = new byte[itemBytesLength()];
        writeOffset(bytes);
        stream.write(bytes);
        return bytes.length;
    }

    public int compareOffset(OffsetItem offsetItem) {
        if (offsetItem == this) {
            return 0;
        }
        return CompareUtil.compare(this.getOffset(), offsetItem.getOffset());
    }
    public int compareIdx(OffsetItem offsetItem) {
        if (offsetItem == this) {
            return 0;
        }
        return CompareUtil.compare(this.getIdx(), offsetItem.getIdx());
    }
    @Override
    public int compareTo(OffsetItem offsetItem) {
        return compareIdx(offsetItem);
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('(');
        builder.append(getIdx());
        builder.append(", ");
        if (isNoEntry()) {
            builder.append("NO_ENTRY");
        } else {
            builder.append(getOffset());
        }
        builder.append(')');
        return builder.toString();
    }

    static class Offset16 extends OffsetItem {

        public Offset16() {
        }

        @Override
        protected void validateOffset(int offset) {
            if (offset != NO_ENTRY) {
                validateValueRange(offset / 4);
            }
        }

        @Override
        protected int itemBytesLength() {
            return 2;
        }

        @Override
        protected void parseOffset(byte[] bytes) {
            int offset = getShortUnsigned(bytes, 0);
            if (offset == NO_ENTRY16) {
                offset = NO_ENTRY;
            } else {
                offset = offset * 4;
            }
            mOffset = offset;
        }

        @Override
        protected void writeOffset(byte[] bytes) {
            int offset = mOffset;
            if (offset == NO_ENTRY) {
                putShort(bytes, 0, NO_ENTRY16);
            } else {
                putShort(bytes, 0, mOffset / 4);
            }
        }

        @Override
        public int compareTo(OffsetItem offsetItem) {
            return compareIdx(offsetItem);
        }

    }

    static class Offset32 extends OffsetItem {

        public Offset32() {
        }

        @Override
        protected int itemBytesLength() {
            return 4;
        }

        @Override
        protected void parseOffset(byte[] bytes) {
            mOffset = getInteger(bytes, 0);
        }

        @Override
        protected void writeOffset(byte[] bytes) {
            putInteger(bytes, 0, mOffset);
        }
        @Override
        public int compareTo(OffsetItem offsetItem) {
            return compareIdx(offsetItem);
        }
    }

    static class Sparse extends OffsetItem {

        private int mIdx;

        public Sparse() {
        }

        @Override
        public int getIdx() {
            return mIdx;
        }

        @Override
        public void setIdx(int idx) {
            if (idx != mIdx) {
                validateValueRange(idx);
                this.mIdx = idx;
            }
        }
        @Override
        public boolean isNoEntry() {
            return false;
        }

        @Override
        protected void validateOffset(int offset) {
            validateValueRange(offset / 4);
        }

        @Override
        protected int itemBytesLength() {
            return 4;
        }

        @Override
        protected void parseOffset(byte[] bytes) {
            mIdx = getShortUnsigned(bytes, 0);
            mOffset = getShortUnsigned(bytes, 2) * 4;
        }

        @Override
        protected void writeOffset(byte[] bytes) {
            putShort(bytes, 0, mIdx);
            putShort(bytes, 2, mOffset / 4);
        }

        @Override
        public int compareTo(OffsetItem offsetItem) {
            return compareOffset(offsetItem);
        }
    }

    static class Helper {
        static Creator<OffsetItem> init16() {
            return Offset16::new;
        }
        static Creator<OffsetItem> init32() {
            return Offset32::new;
        }
        static Creator<OffsetItem> initSparse() {
            return Sparse::new;
        }
    }
}
