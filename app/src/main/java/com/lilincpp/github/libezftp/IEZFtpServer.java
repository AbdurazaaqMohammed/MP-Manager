package com.lilincpp.github.libezftp;

/**
 * FTP server interface
 */
public interface IEZFtpServer {
    /**
     * start ftp server
     */
    void start();

    /**
     * stop ftp server
     */
    void stop();

    /**
     * whether has been stopped the ftp server
     * @return true is stopped,false is not
     */
    boolean isStopped();
}