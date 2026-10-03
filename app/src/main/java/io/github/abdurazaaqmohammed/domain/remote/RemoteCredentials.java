package io.github.abdurazaaqmohammed.domain.remote;

/**
 * Connection details for one remote endpoint.
 *
 * <p>A record of plain strings rather than a typed hierarchy: every protocol
 * ends up needing a free-form "extra" anyway (an SFTP private key passphrase, a
 * WebDAV realm, an S3 session token), and a sealed per-protocol subclass would
 * add a migration every time a field is added.
 *
 * <p>Secrets are stored here in plain text like the existing
 * {@code ProfileHelper} FTP profiles do. Moving them to EncryptedSharedPreferences
 * is a separate change; see RemoteCredentialsStore.
 *
 * @param id       stable local id, used as the SharedPreferences key
 * @param kind     which backend this connects to, e.g. {@link Kind#WEBDAV}
 * @param host     hostname or IP
 * @param port     0 means "let the backend pick its default"
 * @param username may be empty for anonymous FTP
 * @param password may be empty
 * @param path     default path to open on connect
 * @param insecure skip certificate validation (FTPES/FTPS self-signed, SMB with
 *                 a self-signed cert, WebDAV with a private CA)
 * @param extra    protocol-specific extras; keys are documented per {@link Kind}
 */
public record RemoteCredentials(
        String id,
        Kind kind,
        String host,
        int port,
        String username,
        String password,
        String path,
        boolean insecure,
        java.util.Map<String, String> extra) {

    public enum Kind {
        FTP,
        FTPS_EXPLICIT,
        FTPS_IMPLICIT,
        SFTP,
        SMB,
        WEBDAV,
        S3
    }

    public static RemoteCredentials of(String id, Kind kind, String host, int port) {
        return new RemoteCredentials(id, kind, host, port, "", "", "/", false, java.util.Map.of());
    }

    public String extra(String key, String fallback) {
        String v = extra == null ? null : extra.get(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public boolean extraBoolean(String key, boolean fallback) {
        String v = extra == null ? null : extra.get(key);
        if (v == null || v.isEmpty()) return fallback;
        return v.equalsIgnoreCase("true") || v.equals("1");
    }

    /** Default port for the protocol, used when {@link #port()} is 0. */
    public int effectivePort() {
        if (port > 0) return port;
        return switch (kind) {
            case FTP -> 21;
            case FTPS_EXPLICIT -> 21;
            case FTPS_IMPLICIT -> 990;
            case SFTP -> 22;
            case SMB -> 445;
            case WEBDAV -> 0;
            case S3 -> 443;
        };
    }

    /** Extra keys understood by this protocol, for the settings dialog. */
    public java.util.List<String> knownExtraKeys() {
        return switch (kind) {
            case FTP, FTPS_EXPLICIT, FTPS_IMPLICIT -> java.util.List.of("passive", "encoding");
            case SFTP -> java.util.List.of("privateKeyPath", "privateKeyPassphrase", "strictHostKey");
            case SMB -> java.util.List.of("domain", "share", "smb2Only", "workgroup");
            case WEBDAV -> java.util.List.of("basePath", "authScheme", "trustSelfSigned");
            case S3 -> java.util.List.of("region", "endpoint", "pathStyle", "sessionToken");
        };
    }
}