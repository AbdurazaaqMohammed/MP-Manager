package io.github.abdurazaaqmohammed.data.remote;

import java.util.EnumMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.data.remote.ftp.EzfFtpRemoteFileSystem;
import io.github.abdurazaaqmohammed.data.remote.s3.S3RemoteFileSystem;
import io.github.abdurazaaqmohammed.data.remote.webdav.WebDavRemoteFileSystem;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystemFactory;

/**
 * Which backend serves which {@link RemoteCredentials.Kind}.
 *
 * <p>Deliberately explicit: a kind with no entry here reports "unsupported"
 * rather than being guessed at, so the connection dialog can hide options that
 * cannot work instead of offering one that throws on connect.
 *
 * <p>Kinds without an entry (SFTP, SMB) are not implemented yet. They stay in
 * the enum so stored profiles survive, and the UI greys them out.
 */
public final class RemoteRegistry {

    private static final Map<RemoteCredentials.Kind, RemoteFileSystemFactory> FACTORIES =
            new EnumMap<>(RemoteCredentials.Kind.class);

    static {
        register(RemoteCredentials.Kind.FTP, factory(RemoteCredentials.Kind.FTP,
                ctx -> new EzfFtpRemoteFileSystem(ctx)));
        register(RemoteCredentials.Kind.FTPS_EXPLICIT, factory(RemoteCredentials.Kind.FTPS_EXPLICIT,
                ctx -> new EzfFtpRemoteFileSystem(ctx)));
        register(RemoteCredentials.Kind.FTPS_IMPLICIT, factory(RemoteCredentials.Kind.FTPS_IMPLICIT,
                ctx -> new EzfFtpRemoteFileSystem(ctx)));
        register(RemoteCredentials.Kind.WEBDAV, factory(RemoteCredentials.Kind.WEBDAV,
                ctx -> new WebDavRemoteFileSystem()));
        register(RemoteCredentials.Kind.S3, factory(RemoteCredentials.Kind.S3,
                ctx -> new S3RemoteFileSystem()));
    }

    private RemoteRegistry() {
    }

    private interface Backend {
        RemoteFileSystem create(android.content.Context appContext);
    }

    private static RemoteFileSystemFactory factory(RemoteCredentials.Kind kind, Backend backend) {
        return new RemoteFileSystemFactory() {
            @Override
            public RemoteCredentials.Kind kind() {
                return kind;
            }

            @Override
            public RemoteFileSystem create(android.content.Context appContext) {
                return backend.create(appContext);
            }
        };
    }

    private static void register(RemoteCredentials.Kind kind, RemoteFileSystemFactory f) {
        FACTORIES.put(kind, f);
    }

    public static RemoteFileSystemFactory factoryFor(RemoteCredentials.Kind kind) {
        return kind == null ? null : FACTORIES.get(kind);
    }

    public static boolean isSupported(RemoteCredentials.Kind kind) {
        return factoryFor(kind) != null;
    }
}