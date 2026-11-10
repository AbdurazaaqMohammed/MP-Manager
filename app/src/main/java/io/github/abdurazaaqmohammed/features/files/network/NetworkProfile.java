package io.github.abdurazaaqmohammed.features.files.network;

import java.net.URI;

import okhttp3.HttpUrl;

public final class NetworkProfile {
    public String name = "";
    public String protocol = "webdav";
    public String address = "";
    public String username = "";
    public String password = "";
    public String domain = "";
    transient String previousName;

    public String pinId() { return protocol + ":" + name; }
    public String storageIdentity() { return canonicalAddress() + "\u0000" + username + "\u0000" + domain; }

    public void validate() {
        if (protocol.equals("webdav")) {
            HttpUrl url = HttpUrl.get(address);
            if (!url.username().isEmpty() || !url.password().isEmpty() || url.query() != null || url.fragment() != null)
                throw new IllegalArgumentException("Enter an HTTP(S) collection URL without credentials, query or fragment");
        } else if (protocol.equals("smb")) {
            URI uri = smbUri();
            RemotePath.validateName(share());
            if (!"smb".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() == 0
                    || uri.getPort() > 65535 || uri.getPort() < -1)
                throw new IllegalArgumentException("Enter smb://host/share (optional :port)");
        } else throw new IllegalArgumentException("Unknown network protocol");
        if (name.trim().isEmpty()) name = address;
    }

    private URI smbUri() {
        try { return new URI(address); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid SMB URL", e); }
    }

    public String share() {
        String path = smbUri().getPath();
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("An SMB share is required");
        String share = path.substring(1);
        if (share.endsWith("/")) share = share.substring(0, share.length() - 1);
        return share;
    }

    public RemoteClient connect() throws java.io.IOException {
        validate();
        if (protocol.equals("webdav")) return new WebDavClient(address, username, password);
        URI uri = smbUri();
        String host = uri.getHost();
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        return new SmbClient(host, uri.getPort() == -1 ? 445 : uri.getPort(), share(), username, password, domain);
    }

    public String canonicalAddress() {
        validate();
        if (protocol.equals("webdav")) {
            HttpUrl url = HttpUrl.get(address);
            return url.newBuilder().encodedPath(url.encodedPath().endsWith("/")
                    ? url.encodedPath() : url.encodedPath() + "/").build().toString();
        }
        URI uri = smbUri();
        try {
            return new URI("smb", null, uri.getHost().toLowerCase(java.util.Locale.ROOT),
                    uri.getPort() == 445 ? -1 : uri.getPort(), "/" + share() + "/", null, null).toASCIIString();
        } catch (java.net.URISyntaxException e) { throw new IllegalArgumentException("Invalid SMB URL", e); }
    }
}
