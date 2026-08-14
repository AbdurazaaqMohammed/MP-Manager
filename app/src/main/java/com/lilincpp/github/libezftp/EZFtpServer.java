package com.lilincpp.github.libezftp;

import com.lilincpp.github.libezftp.user.EZFtpUser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class EZFtpServer implements IEZFtpServer {

    private IEZFtpServer ftpServerImpl;

    private EZFtpServer(List<EZFtpUser> users, int port, File keystoreFile, String keystorePassword, boolean implicitSsl) {
        ftpServerImpl = new EZFtpServerImpl(users, port, keystoreFile, keystorePassword, implicitSsl);
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
        private File keystoreFile;
        private String keystorePassword;
        private boolean implicitSsl;

        public Builder addUser(EZFtpUser user) {
            users.add(user);
            return this;
        }

        public Builder setListenPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Enable FTPS on this server.
         *
         * @param keystoreFile     JKS keystore file containing a key entry, or null to disable FTPS
         * @param keystorePassword keystore and key password
         * @param implicitSsl      whether to use implicit TLS (FTPS direct) instead of explicit
         */
        public Builder setFtps(File keystoreFile, String keystorePassword, boolean implicitSsl) {
            this.keystoreFile = keystoreFile;
            this.keystorePassword = keystorePassword;
            this.implicitSsl = implicitSsl;
            return this;
        }

        public EZFtpServer create() {
            return new EZFtpServer(users, port, keystoreFile, keystorePassword, implicitSsl);
        }
    }
}
