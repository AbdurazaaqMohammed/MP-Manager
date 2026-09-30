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
package com.reandroid.archive.io;

import com.reandroid.common.FileChannelInputStream;
import com.reandroid.utils.io.FileUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ZipFileInput extends ZipInput {
    private final File file;
    private FileChannel fileChannel;
    private InputStream mCurrentInputStream;
    private final List<WorkerChannel> workerChannels;
    private ThreadLocal<FileChannel> workerLocal;
    public ZipFileInput(File file){
        this.file = file;
        this.workerChannels = new ArrayList<>();
    }

    public File getFile(){
        return file;
    }

    @Override
    public long position() throws IOException {
        FileChannel fileChannel = this.fileChannel;
        if(fileChannel != null){
            return fileChannel.position();
        }
        return 0;
    }
    @Override
    public void position(long pos) throws IOException {
        getFileChannel().position(pos);
    }
    @Override
    public long getLength(){
        return this.file.length();
    }
    @Override
    public InputStream getInputStream(long offset, long length) throws IOException {
        Thread thread = Thread.currentThread();
        if(thread instanceof ZipReadWorker){
            return getWorkerInputStream(thread, offset, length);
        }
        closeCurrentInputStream();
        FileChannel fileChannel = getFileChannel();
        fileChannel.position(offset);
        mCurrentInputStream = new FileChannelInputStream(fileChannel, length);
        return mCurrentInputStream;
    }
    private InputStream getWorkerInputStream(Thread thread, long offset, long length) throws IOException {
        FileChannel channel;
        synchronized (workerChannels){
            if(workerLocal == null){
                workerLocal = new ThreadLocal<>();
            }
            channel = workerLocal.get();
            if(channel == null){
                channel = FileUtil.openReadChannel(file);
                workerChannels.add(new WorkerChannel(thread, channel));
                workerLocal.set(channel);
            }
        }
        channel.position(offset);
        return new FileChannelInputStream(channel, length, 32 * 1024);
    }
    @Override
    public byte[] getFooter(int minLength) throws IOException {
        long position = getLength();
        if(minLength>position){
            minLength = (int) position;
        }
        position = position - minLength;
        FileChannel fileChannel = getFileChannel();
        fileChannel.position(position);
        ByteBuffer buffer = ByteBuffer.allocate(minLength);
        fileChannel.read(buffer);
        return buffer.array();
    }
    public FileChannel getFileChannel() throws IOException {
        FileChannel fileChannel = this.fileChannel;
        if(fileChannel != null){
            return fileChannel;
        }
        synchronized (this){
            fileChannel = FileUtil.openReadChannel(file);
            this.fileChannel = fileChannel;
            return fileChannel;
        }
    }
    @Override
    public void close() throws IOException {
        closeCurrentInputStream();
        closeChannel();
    }
    public void closeWorkers(){
        List<WorkerChannel> workers;
        synchronized (workerChannels){
            workers = new ArrayList<>(workerChannels);
            workerChannels.clear();
            workerLocal = null;
        }
        for(WorkerChannel worker : workers){
            worker.close();
        }
    }
    @Override
    public boolean isOpen(){
        FileChannel fileChannel = this.fileChannel;
        if(fileChannel == null){
            return false;
        }
        synchronized (this){
            return fileChannel.isOpen();
        }
    }
    private void closeChannel() throws IOException {
        FileChannel fileChannel = this.fileChannel;
        if(fileChannel == null){
            return;
        }
        synchronized (this){
            fileChannel.close();
            this.fileChannel = null;
        }
    }
    private void closeCurrentInputStream() throws IOException {
        InputStream current = this.mCurrentInputStream;
        if(current == null){
            return;
        }
        current.close();
        mCurrentInputStream = null;
    }
    @Override
    public String toString(){
        return "File: " + this.file;
    }

    private static final class WorkerChannel {
        private final Thread thread;
        private final FileChannel channel;
        WorkerChannel(Thread thread, FileChannel channel){
            this.thread = thread;
            this.channel = channel;
        }
        void close(){
            try{
                channel.close();
            }catch (IOException ignored){
            }
        }
    }
}
