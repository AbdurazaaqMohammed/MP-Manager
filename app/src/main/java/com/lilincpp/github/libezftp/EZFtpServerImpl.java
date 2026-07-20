package com.lilincpp.github.libezftp;


import android.util.Log;

import com.lilincpp.github.libezftp.exceptions.EZFtpNoInitException;
import com.lilincpp.github.libezftp.user.EZFtpUser;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * FTP server implement
 */
final class EZFtpServerImpl implements IEZFtpServer {

    private static final String TAG = "EZFtpServerImpl";

    private FtpServer ftpServer;
    private final Object lock = new Object();
    private boolean isInit = false;

    /**
     * create a ftp server
     *
     * @param users support user list(need login)
     * @param port  ftp server listen port
     */
    EZFtpServerImpl(List<EZFtpUser> users, int port) {
        //配置参数
        FtpServerFactory serverFactory = new FtpServerFactory();
        //设置访问用户名和密码还有共享路径
        for (EZFtpUser user : users) {
            BaseUser baseUser = new BaseUser();
            baseUser.setName(user.getName());
            baseUser.setPassword(user.getPassword());
            baseUser.setHomeDirectory(user.getSharedPath());
            List<Authority> authorities = new ArrayList<>();
            authorities.add(user.getPermission().getAuthority());
            baseUser.setAuthorities(authorities);
            try {
                serverFactory.getUserManager().save(baseUser);
            } catch (FtpException e) {
                e.printStackTrace();
            }
        }
        //设置监听端口
        ListenerFactory factory = new ListenerFactory();
        factory.setPort(port);
        serverFactory.addListener("default", factory.createListener());
        //创建FTP服务实例
        ftpServer = serverFactory.createServer();
        isInit = true;
    }

    /**
     * make sure server is init
     */
    private void checkInit() {
        synchronized (lock) {
            if (!isInit) {
                throw new EZFtpNoInitException("EZFtp server is no init or has been release!");
            }
        }
    }

    /**
     * release ftp server
     */
    private void release() {
        synchronized (lock) {
            if (ftpServer != null && !ftpServer.isStopped()) {
                ftpServer.stop();
            }
            isInit = false;
        }
    }

    /**
     * start ftp server
     */
    @Override
    public void start() {
        checkInit();
        try {
            ftpServer.start();
        } catch (FtpException e) {
            e.printStackTrace();
        }
    }

    /**
     * stop ftp server
     */
    @Override
    public void stop() {
        checkInit();
        ftpServer.stop();
    }

    /**
     * whether is stopped the ftp server
     * @return true is stopped,false is not.
     */
    @Override
    public boolean isStopped() {
        return ftpServer.isStopped();
    }
}
