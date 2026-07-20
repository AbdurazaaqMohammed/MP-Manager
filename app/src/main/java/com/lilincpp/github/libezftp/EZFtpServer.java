package com.lilincpp.github.libezftp;

import com.lilincpp.github.libezftp.user.EZFtpUser;

import java.util.ArrayList;
import java.util.List;

public final class EZFtpServer implements IEZFtpServer {

    private IEZFtpServer ftpServerImpl;

    private EZFtpServer(List<EZFtpUser> users, int port) {
        ftpServerImpl = new EZFtpServerImpl(users, port);
    }

    @Override
    public void start() {
        ftpServerImpl.start();
    }

    @Override
    public void stop() {
        ftpServerImpl.stop();
    }

    @Override
    public boolean isStopped() {
        return ftpServerImpl.isStopped();
    }

    public static final class Builder {
        private List<EZFtpUser> users = new ArrayList<>();
        private int port;

        public Builder addUser(EZFtpUser user) {
            users.add(user);
            return this;
        }

        public Builder setListenPort(int port) {
            this.port = port;
            return this;
        }

        public EZFtpServer create() {
            return new EZFtpServer(users, port);
        }
    }
}
