package com.lilincpp.github.libezftp;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Date;

/**
 * ftp file info
 */
public final class EZFtpFile implements Parcelable {

    public static final int TYPE_FILE = 0;
    public static final int TYPE_DIRECTORY = 1;
    public static final int TYPE_LINK = 2;

    private String name;
    private String remotePath;
    private int type;
    private long size;
    private Date modifiedDate = null;


    public EZFtpFile(String name, String remotePath, int type, long size, Date modifiedDate) {
        this.name = name;
        this.remotePath = remotePath;
        this.type = type;
        this.size = size;
        this.modifiedDate = modifiedDate;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public int getType() {
        return type;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(remotePath);
        dest.writeInt(type);
        dest.writeLong(size);
        dest.writeSerializable(modifiedDate);
    }

    private static final Parcelable.Creator<EZFtpFile> CREATOR = new Creator<EZFtpFile>() {
        @Override
        public EZFtpFile createFromParcel(Parcel source) {
            return new EZFtpFile(source);
        }

        @Override
        public EZFtpFile[] newArray(int size) {
            return new EZFtpFile[size];
        }
    };

    private EZFtpFile(Parcel in) {
        this.name = in.readString();
        this.remotePath = in.readString();
        this.type = in.readInt();
        this.size = in.readLong();
        this.modifiedDate = (Date) in.readSerializable();
    }

    @Override
    public String toString() {
        return "EZFtpFile{" +
                "name='" + name + '\'' +
                ", remotePath='" + remotePath + '\'' +
                ", type=" + type +
                ", size=" + size +
                ", modifiedDate=" + modifiedDate +
                '}';
    }
}
