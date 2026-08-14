package io.github.abdurazaaqmohammed.MPManager.ftp;

public class FtpProfile {
    private String name;
    private String ip;
    private int port;
    private String username;
    private String password;
    private boolean isServerProfile;
    private int securityType;

    public FtpProfile(String name, String ip, int port, String username, String password, boolean isServerProfile) {
        this(name, ip, port, username, password, isServerProfile, 0);
    }

    public FtpProfile(String name, String ip, int port, String username, String password, boolean isServerProfile, int securityType) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
        this.isServerProfile = isServerProfile;
        this.securityType = securityType;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isServerProfile() { return isServerProfile; }
    public int getSecurityType() { return securityType; }
    public void setSecurityType(int securityType) { this.securityType = securityType; }
}
