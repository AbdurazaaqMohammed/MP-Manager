package com.lilincpp.github.libezftp.user;

/**
 * FTP User entry
 */
public final class EZFtpUser {
    private String name;
    private String password;
    private String sharedPath;
    private EZFtpUserPermission permission;

    public EZFtpUser(String name, String password, String sharedPath, EZFtpUserPermission permission) {
        this.name = name;
        this.password = password;
        this.sharedPath = sharedPath;
        this.permission = permission;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getSharedPath() {
        return sharedPath;
    }

    public EZFtpUserPermission getPermission() {
        return permission;
    }

    @Override
    public String toString() {
        return "EZFtpUser{" +
                "name='" + name + '\'' +
                ", password='" + password + '\'' +
                ", sharedPath='" + sharedPath + '\'' +
                ", permission=" + permission +
                '}';
    }
}
