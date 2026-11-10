package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.SidebarAdapter;
import io.github.abdurazaaqmohammed.adapters.RemoteFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpProfile;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileManager;
import io.github.abdurazaaqmohammed.data.prefs.SidebarPins;
import com.lilincpp.github.libezftp.EZFtpFile;
import io.github.abdurazaaqmohammed.features.files.network.*;
import okhttp3.mockwebserver.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.abdurazaaqmohammed.features.files.network.NetworkProfile;
import io.github.abdurazaaqmohammed.features.files.network.NetworkProfileStore;
import static org.junit.Assert.*;

/** Exercises real Android Keystore, not JVM substitutes. Use the isolated test build. */
public class NetworkRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private MainActivity main;

    @Before public void prepare() throws Exception {
        context = instrumentation.getTargetContext();
        assertTrue("Never modify an installed release", context.getPackageName().endsWith(".editorregression"));
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("working_mode", "non_root").putString("su_command", "/system/bin/false")
                .putBoolean("startup_request_root", false).putBoolean("root_file_ops", false)
                .putBoolean("filemenu_two_column", true).putStringSet("filemenu_hidden", Collections.emptySet())
                .putBoolean("checkForUpdates", false).putString("home1", context.getFilesDir().getPath())
                .putString("home2", context.getFilesDir().getPath()).commit();
        for (String permission : new String[]{"READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE"}) {
            try (InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation()
                    .executeShellCommand("pm grant " + context.getPackageName() + " android.permission." + permission))) {
                byte[] buffer = new byte[1024];
                while (input.read(buffer) != -1) { /* wait for the targeted permission grant */ }
            }
        }
    }

    private void launchMain() {
        main = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
    }

    @After public void cleanup() {
        if (main != null) {
            instrumentation.runOnMainSync(() -> main.finish());
            instrumentation.waitForIdleSync();
        }
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = find(node.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo findContaining(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.getText() != null && node.getText().toString().contains(text)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findContaining(node.getChild(i), text);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo awaitText(String text) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo match = find(instrumentation.getUiAutomation().getRootInActiveWindow(), text);
            if (match != null) return match;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing visible UI text: " + text);
    }

    private void click(String text) {
        AccessibilityNodeInfo node = awaitText(text);
        while (node != null && !node.isClickable()) node = node.getParent();
        assertNotNull("No clickable ancestor for " + text, node);
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        instrumentation.waitForIdleSync();
    }

    @Test public void mergedMenuOpensEveryProtocolAndDiscovery() {
        launchMain();
        instrumentation.runOnMainSync(() -> {
            SidebarAdapter sidebar = new SidebarAdapter(main, null);
            sidebar.setToolOrder(Arrays.asList("ftp_client", "webdav_client", "smb_client"));
            sidebar.rebuild();
            assertEquals(1, Collections.frequency(sidebar.getToolOrder(), "network_client"));
            assertFalse(sidebar.getToolOrder().contains("ftp_client"));
            assertFalse(sidebar.getToolOrder().contains("webdav_client"));
            assertFalse(sidebar.getToolOrder().contains("smb_client"));
        });
        instrumentation.runOnMainSync(() -> main.getNetwork().showConnections());
        awaitText(context.getString(R.string.network_connections));
        AlertDialog first = connectionDialog();
        instrumentation.runOnMainSync(() -> assertEquals(0, ((TabLayout) first.findViewById(R.id.networkTabs)).getSelectedTabPosition()));
        for (int tab : new int[]{R.string.webdav_client, R.string.network_smb_tab}) {
            click(context.getString(tab));
            assertSame("Protocol tabs must not open another dialog", first, connectionDialog());
        }
        awaitText(context.getString(R.string.network_discover));
        awaitText(context.getString(R.string.network_scan_smb));
        click(context.getString(android.R.string.cancel));
    }

    private AlertDialog connectionDialog() {
        try {
            java.lang.reflect.Field field = NetworkController.class.getDeclaredField("connections");
            field.setAccessible(true);
            Object connections = field.get(main.getNetwork());
            field = connections.getClass().getDeclaredField("dialog");
            field.setAccessible(true);
            return (AlertDialog) field.get(connections);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private View visibleForm() {
        ViewGroup forms = connectionDialog().findViewById(R.id.networkForms);
        for (int i = 0; i < forms.getChildCount(); i++) if (forms.getChildAt(i).getVisibility() == View.VISIBLE)
            return forms.getChildAt(i);
        throw new AssertionError("No visible connection form");
    }

    private EditText field(View view, int hint) {
        if (view instanceof TextInputLayout box && context.getString(hint).contentEquals(box.getHint() == null ? "" : box.getHint()))
            return box.getEditText();
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText edit = field(group.getChildAt(i), hint);
            if (edit != null) return edit;
        }
        return null;
    }

    private TextView text(View view, int label) {
        if (view instanceof TextView text && context.getString(label).contentEquals(text.getText())) return text;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = text(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    @Test public void tabsRetainDraftsAndPinningRequiresSaving() {
        launchMain();
        instrumentation.runOnMainSync(() -> {
            main.getNetwork().showConnectionDialog("webdav");
            AlertDialog dialog = connectionDialog();
            TabLayout tabs = dialog.findViewById(R.id.networkTabs);
            View dav = visibleForm();
            field(dav, R.string.network_connection_name).setText("WebDAV draft");
            field(dav, R.string.network_webdav_address).setText("https://example.invalid/dav/");
            field(dav, R.string.network_password).setText("draft-secret");
            tabs.selectTab(tabs.getTabAt(2));
            View smb = visibleForm();
            field(smb, R.string.network_connection_name).setText("SMB draft");
            CheckBox pin = (CheckBox) text(smb, R.string.pin_to_sidebar);
            CheckBox remember = (CheckBox) text(smb, R.string.network_remember);
            pin.setChecked(true);
            assertTrue(remember.isChecked());
            assertFalse(remember.isEnabled());
            tabs.selectTab(tabs.getTabAt(0));
            field(visibleForm(), R.string.ip_address_hint).setText("ftp-draft.invalid");
            tabs.selectTab(tabs.getTabAt(1));
            assertEquals("WebDAV draft", field(visibleForm(), R.string.network_connection_name).getText().toString());
            assertEquals("draft-secret", field(visibleForm(), R.string.network_password).getText().toString());
            tabs.selectTab(tabs.getTabAt(2));
            assertEquals("SMB draft", field(visibleForm(), R.string.network_connection_name).getText().toString());
            assertTrue(((CheckBox) text(visibleForm(), R.string.pin_to_sidebar)).isChecked());
            dialog.dismiss();
        });
    }

    private static final class DavFixture implements Closeable {
        final MockWebServer server = new MockWebServer();
        final Map<String, byte[]> files = new LinkedHashMap<>();
        DavFixture() throws IOException {
            files.put("", null);
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    String path = java.net.URI.create(request.getRequestUrl().toString()).getPath().substring(5);
                    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
                    synchronized (files) {
                        switch (request.getMethod()) {
                            case "PROPFIND": {
                                if (!files.containsKey(path)) return new MockResponse().setResponseCode(404);
                                StringBuilder xml = new StringBuilder("<d:multistatus xmlns:d='DAV:'>").append(properties(path));
                                if ("1".equals(request.getHeader("Depth"))) for (String child : files.keySet())
                                    if (!child.equals(path) && RemotePath.parent(child).equals(path)) xml.append(properties(child));
                                return new MockResponse().setResponseCode(207).setBody(xml.append("</d:multistatus>").toString());
                            }
                            case "PUT":
                                if (files.containsKey(path)) return new MockResponse().setResponseCode(412);
                                files.put(path, request.getBody().readByteArray()); return new MockResponse().setResponseCode(201);
                            case "GET":
                                return new MockResponse().setBody(new okio.Buffer().write(files.get(path)));
                            case "MKCOL":
                                files.put(path, null); return new MockResponse().setResponseCode(201);
                            case "MOVE":
                                String target = java.net.URI.create(request.getHeader("Destination")).getPath().substring(5);
                                files.put(target, files.remove(path)); return new MockResponse().setResponseCode(201);
                            case "DELETE":
                                final String removed = path;
                                files.keySet().removeIf(p -> p.equals(removed) || p.startsWith(removed + "/"));
                                return new MockResponse().setResponseCode(204);
                            default: return new MockResponse().setResponseCode(405);
                        }
                    }
                }
            });
            server.start(java.net.InetAddress.getByName("0.0.0.0"), 0);
        }
        private String properties(String path) {
            byte[] bytes = files.get(path);
            String href = server.url("/dav/" + path).encodedPath() + (bytes == null && !path.isEmpty() ? "/" : "");
            return "<d:response><d:href>" + href + "</d:href><d:propstat><d:prop><d:resourcetype>"
                    + (bytes == null ? "<d:collection/>" : "") + "</d:resourcetype><d:getcontentlength>"
                    + (bytes == null ? 0 : bytes.length) + "</d:getcontentlength></d:prop>"
                    + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>";
        }
        String address() { return "http://127.0.0.1:" + server.getPort() + "/dav/"; }
        @Override public void close() throws IOException { server.shutdown(); }
    }

    @Test public void webdavStreamsAndPaneNavigationWorkOnAndroid() throws Exception {
        launchMain();
        try (DavFixture fixture = new DavFixture(); WebDavClient client = new WebDavClient(fixture.address(), "", "")) {
            client.mkdir("folder");
            byte[] bytes = {0, 1, 2, 3, 4};
            client.upload("folder/data.bin", new ByteArrayInputStream(bytes), bytes.length);
            client.upload("folder/empty", new ByteArrayInputStream(new byte[0]), 0);
            assertEquals(2, client.list("folder").size());
            ByteArrayOutputStream downloaded = new ByteArrayOutputStream();
            client.download("folder/data.bin", downloaded);
            assertArrayEquals(bytes, downloaded.toByteArray());
            client.rename("folder/data.bin", "folder/renamed.bin");
            assertNull(client.stat("folder/data.bin"));
            RemoteFile root = RemoteFile.root(new RemoteFile.Connection(client, fixture.address()));
            instrumentation.runOnMainSync(() -> main.loadFolderInPane(root, true, true));
            awaitText("folder");
            click("folder");
            awaitText("renamed.bin");
            awaitText("empty");
            instrumentation.runOnMainSync(() -> main.loadFolderInPane(root, false, true));
            // Both panes must retain the real connection identity, not local synthetic paths.
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (!(main.pane2Folder instanceof RemoteFile) && android.os.SystemClock.uptimeMillis() < deadline)
                android.os.SystemClock.sleep(50);
            assertTrue(main.pane1Folder instanceof RemoteFile);
            assertTrue(main.pane2Folder instanceof RemoteFile);
            client.delete("folder", true);
            assertTrue(client.list("").isEmpty());
            // Leave no network listing queued against the client being closed.
            instrumentation.runOnMainSync(() -> {
                main.loadFolderInPane(context.getFilesDir(), true, false);
                main.loadFolderInPane(context.getFilesDir(), false, false);
            });
        }
    }

    @Test public void advertisedWebdavServiceIsDetectedOnAndroid() throws Exception {
        launchMain();
        android.net.nsd.NsdManager nsd = (android.net.nsd.NsdManager) context.getSystemService(Context.NSD_SERVICE);
        CountDownLatch registered = new CountDownLatch(1);
        CountDownLatch detected = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<NetworkProfile> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> error = new java.util.concurrent.atomic.AtomicReference<>();
        String name = "mp-dav-" + UUID.randomUUID().toString().substring(0, 8);
        android.net.nsd.NsdManager.RegistrationListener registration = new android.net.nsd.NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(android.net.nsd.NsdServiceInfo service) { registered.countDown(); }
            @Override public void onRegistrationFailed(android.net.nsd.NsdServiceInfo service, int code) { error.set("Registration error " + code); registered.countDown(); }
            @Override public void onServiceUnregistered(android.net.nsd.NsdServiceInfo service) {}
            @Override public void onUnregistrationFailed(android.net.nsd.NsdServiceInfo service, int code) {}
        };
        try (DavFixture fixture = new DavFixture(); NetworkDiscovery discovery = new NetworkDiscovery(context, new NetworkDiscovery.Listener() {
            @Override public void found(NetworkProfile profile) { if (profile.name.startsWith(name)) { result.set(profile); detected.countDown(); } }
            @Override public void status(String message) { /* UI status does not replace discovery results. */ }
        })) {
            android.net.nsd.NsdServiceInfo service = new android.net.nsd.NsdServiceInfo();
            service.setServiceName(name);
            service.setServiceType("_webdav._tcp.");
            service.setPort(fixture.server.getPort());
            service.setAttribute("path", "/dav/");
            nsd.registerService(service, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, registration);
            try {
                assertTrue("mDNS registration timed out", registered.await(10, TimeUnit.SECONDS));
                assertNull(error.get());
                instrumentation.runOnMainSync(discovery::start);
                assertTrue("Advertised WebDAV service was not discovered", detected.await(20, TimeUnit.SECONDS));
                assertEquals("webdav", result.get().protocol);
                assertTrue(result.get().address.endsWith("/dav/"));
                discovery.close();
                instrumentation.runOnMainSync(() -> main.getNetwork().showDiscovery());
                String label = "WebDAV • " + result.get().name + "\n" + result.get().address;
                // Check the visible list as well as the backend callback: a dialog
                // message must not accidentally hide its server-result list.
                click(label);
                awaitText(context.getString(R.string.webdav_client));
                instrumentation.runOnMainSync(() -> {
                    assertEquals(1, ((TabLayout) connectionDialog().findViewById(R.id.networkTabs)).getSelectedTabPosition());
                    assertEquals(result.get().name, field(visibleForm(), R.string.network_connection_name).getText().toString());
                    assertEquals(result.get().address, field(visibleForm(), R.string.network_webdav_address).getText().toString());
                    assertFalse(((CheckBox) text(visibleForm(), R.string.network_remember)).isChecked());
                });
                click(context.getString(android.R.string.cancel));
            } finally { nsd.unregisterService(registration); }
        }
    }

    @Test public void smbRoundTripOnAndroid() throws Exception {
        android.os.Bundle arguments = InstrumentationRegistry.getArguments();
        String port = arguments.getString("networkSmbPort");
        org.junit.Assume.assumeTrue("Supply an adb-reversed disposable SMB fixture", port != null);
        String folder = "android-network-test-" + UUID.randomUUID();
        try (SmbClient client = new SmbClient("127.0.0.1", Integer.parseInt(port), "test", "tester", "test-only", "")) {
            client.mkdir(folder);
            try {
                byte[] bytes = {0, 1, 2, 3};
                client.upload(folder + "/測試.bin", new ByteArrayInputStream(bytes), bytes.length);
                assertEquals(1, client.list(folder).size());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                client.download(folder + "/測試.bin", output);
                assertArrayEquals(bytes, output.toByteArray());
                client.rename(folder + "/測試.bin", folder + "/renamed.bin");
                assertNull(client.stat(folder + "/測試.bin"));
                client.upload(folder + "/empty", new ByteArrayInputStream(new byte[0]), 0);
                output.reset();
                client.download(folder + "/empty", output);
                assertEquals(0, output.size());
            } finally { client.delete(folder, true); }
        }
    }
    @Test public void savedConnectionsAreEncryptedAndSurviveStoreRecreation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Never change a user's release profiles", context.getPackageName().endsWith(".editorregression"));
        NetworkProfile profile = new NetworkProfile();
        profile.name = "regression-" + java.util.UUID.randomUUID();
        profile.address = "https://example.invalid/dav/";
        profile.username = "network-regression-user";
        profile.password = "network-regression-secret";
        NetworkProfileStore store = new NetworkProfileStore(context);
        try {
            store.put(profile);
            String stored = context.getSharedPreferences("network_profiles", Context.MODE_PRIVATE).getString("encrypted", "");
            assertFalse(stored.contains(profile.password));
            assertFalse(stored.contains(profile.username));
            NetworkProfile restored = new NetworkProfileStore(context).load().stream()
                    .filter(p -> p.name.equals(profile.name)).findFirst().orElseThrow(AssertionError::new);
            assertEquals(profile.address, restored.address);
            assertEquals(profile.password, restored.password);
        } finally { store.remove(profile); }
        assertFalse(new NetworkProfileStore(context).load().stream().anyMatch(p -> p.name.equals(profile.name)));
    }

    @Test public void webdavAndSmbConfigurationsCanBeSavedAndPinnedWithoutConnecting() throws Exception {
        launchMain();
        NetworkProfileStore store = new NetworkProfileStore(context);
        List<NetworkProfile> created = new ArrayList<>();
        try {
            for (String protocol : new String[]{"webdav", "smb"}) {
                NetworkProfile profile = new NetworkProfile();
                profile.protocol = protocol;
                profile.name = "offline-pin-" + protocol + "-" + UUID.randomUUID();
                profile.address = protocol.equals("smb") ? "smb://192.0.2.1/Documents" : "https://example.invalid/dav/";
                created.add(profile);
                instrumentation.runOnMainSync(() -> {
                    main.getNetwork().showConnectionDialog(protocol);
                    View form = visibleForm();
                    field(form, R.string.network_connection_name).setText(profile.name);
                    field(form, protocol.equals("smb") ? R.string.network_smb_address : R.string.network_webdav_address).setText(profile.address);
                    ((CheckBox) text(form, R.string.pin_to_sidebar)).setChecked(true);
                });
                instrumentation.waitForIdleSync(); // AlertDialog's onShow callback installs its button listeners asynchronously.
                instrumentation.runOnMainSync(() -> connectionDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick());
                long deadline = android.os.SystemClock.uptimeMillis() + 5000;
                boolean saved;
                do {
                    saved = store.load().stream().anyMatch(p -> p.name.equals(profile.name) && p.protocol.equals(protocol));
                    if (saved && SidebarPins.isPinned(context, profile.pinId())) break;
                    android.os.SystemClock.sleep(50);
                } while (android.os.SystemClock.uptimeMillis() < deadline);
                assertTrue("Configuration must save without trying to reach the server", saved);
                assertTrue(SidebarPins.isPinned(context, profile.pinId()));
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> assertTrue(connectionDialog().isShowing()));
            }
        } finally {
            for (NetworkProfile profile : created) store.remove(profile);
            instrumentation.runOnMainSync(() -> connectionDialog().dismiss());
        }
    }

    @Test public void profileRenameAndDeleteKeepProtocolQualifiedPinsInOrder() throws Exception {
        String name = "pin-order-" + UUID.randomUUID();
        NetworkProfile dav = new NetworkProfile(); dav.name = name; dav.address = "https://example.invalid/dav/";
        NetworkProfile smb = new NetworkProfile(); smb.protocol = "smb"; smb.name = name; smb.address = "smb://192.0.2.1/share";
        NetworkProfileStore store = new NetworkProfileStore(context);
        String ftpPin = SidebarPins.FTP_PREFIX + name;
        try {
            store.put(dav); store.put(smb);
            SidebarPins.add(context, dav.pinId()); SidebarPins.add(context, smb.pinId()); SidebarPins.add(context, ftpPin);
            int position = SidebarPins.get(context).indexOf(dav.pinId());
            java.lang.reflect.Field previous = NetworkProfile.class.getDeclaredField("previousName");
            previous.setAccessible(true); previous.set(dav, name);
            dav.name = name + "-renamed";
            store.put(dav);
            assertEquals(Arrays.asList(dav.pinId(), smb.pinId(), ftpPin), SidebarPins.get(context).subList(position, position + 3));
            store.remove(dav);
            assertFalse(SidebarPins.isPinned(context, dav.pinId()));
            assertTrue(SidebarPins.isPinned(context, smb.pinId()));
        } finally { store.remove(dav); store.remove(smb); SidebarPins.remove(context, ftpPin); }

        ProfileManager ftpStore = new ProfileManager(context);
        FtpProfile ftp = new FtpProfile(name, "192.0.2.1", 21, "", "", false);
        ftpStore.addProfile(ftp);
        int index = ftpStore.getProfiles().indexOf(ftp);
        SidebarPins.add(context, ftpPin);
        FtpProfile renamed = new FtpProfile(name + "-renamed", ftp.getIp(), 21, "", "", false);
        try {
            ftpStore.updateProfile(index, renamed);
            assertFalse(SidebarPins.isPinned(context, ftpPin));
            assertTrue(SidebarPins.isPinned(context, SidebarPins.FTP_PREFIX + renamed.getName()));
        } finally { ftpStore.deleteProfile(index); }
        assertFalse(SidebarPins.isPinned(context, SidebarPins.FTP_PREFIX + renamed.getName()));
    }

    @Test public void ftpConfigurationCanBeSavedAndPinnedWithoutConnecting() {
        launchMain();
        String name = "offline-ftp-" + UUID.randomUUID();
        try {
            instrumentation.runOnMainSync(() -> {
                main.getNetwork().showConnectionDialog("ftp");
                com.google.android.material.textfield.MaterialAutoCompleteTextView saved =
                        (com.google.android.material.textfield.MaterialAutoCompleteTextView) field(visibleForm(), R.string.network_saved_connections);
                saved.getOnItemClickListener().onItemClick(null, null, 0, 0);
                field(visibleForm(), R.string.network_connection_name).setText(name);
                field(visibleForm(), R.string.ip_address_hint).setText("192.0.2.1");
                field(visibleForm(), R.string.port).setText("21");
                ((CheckBox) text(visibleForm(), R.string.pin_to_sidebar)).setChecked(true);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> connectionDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick());
            assertTrue(new ProfileManager(context).getProfiles().stream().anyMatch(p -> !p.isServerProfile() && p.getName().equals(name)));
            assertTrue(SidebarPins.isPinned(context, SidebarPins.FTP_PREFIX + name));
        } finally { instrumentation.runOnMainSync(() -> { main.getFtp().deleteClientProfile(name); connectionDialog().dismiss(); }); }
    }

    private static final class FtpFixture implements AutoCloseable {
        final File directory;
        final File share;
        final int port;
        final org.apache.ftpserver.FtpServer server;

        FtpFixture(Context context) throws Exception {
            directory = new File(context.getCacheDir(), "ftp-fixture-" + UUID.randomUUID());
            share = new File(directory, "share");
            if (!share.mkdirs()) throw new IOException("Cannot create FTP fixture");
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
            org.apache.ftpserver.FtpServerFactory factory = new org.apache.ftpserver.FtpServerFactory();
            org.apache.ftpserver.listener.ListenerFactory listener = new org.apache.ftpserver.listener.ListenerFactory();
            listener.setServerAddress("127.0.0.1"); listener.setPort(port);
            org.apache.ftpserver.DataConnectionConfigurationFactory data = new org.apache.ftpserver.DataConnectionConfigurationFactory();
            data.setPassiveAddress("127.0.0.1");
            listener.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
            factory.addListener("default", listener.createListener());
            org.apache.ftpserver.usermanager.PropertiesUserManagerFactory users = new org.apache.ftpserver.usermanager.PropertiesUserManagerFactory();
            File usersFile = new File(directory, "users.properties");
            if (!usersFile.createNewFile()) throw new IOException("Cannot create fixture users file");
            users.setFile(usersFile);
            factory.setUserManager(users.createUserManager());
            org.apache.ftpserver.usermanager.impl.BaseUser user = new org.apache.ftpserver.usermanager.impl.BaseUser();
            user.setName("tester"); user.setPassword("test-only"); user.setHomeDirectory(share.getPath());
            user.setAuthorities(Collections.singletonList(new org.apache.ftpserver.usermanager.impl.WritePermission()));
            factory.getUserManager().save(user);
            server = factory.createServer(); server.start();
        }

        @Override public void close() { server.stop(); FileUtils.deleteTree(directory, null); }
    }

    private void longPressFile(String name) {
        instrumentation.runOnMainSync(() -> {
            RecyclerView pane = main.findViewById(R.id.listViewPane1);
            RecyclerView.Adapter<?> adapter = pane.getAdapter();
            assertTrue(adapter instanceof FtpFilesArrayAdapter);
            FtpFilesArrayAdapter ftp = (FtpFilesArrayAdapter) adapter;
            for (int i = 1; i < ftp.getItemCount(); i++) if (ftp.getItem(i).getName().equals(name)) {
                RecyclerView.ViewHolder holder = pane.findViewHolderForAdapterPosition(i);
                assertNotNull(holder);
                assertTrue(holder.itemView.performLongClick());
                return;
            }
            throw new AssertionError("Missing FTP row " + name);
        });
        instrumentation.waitForIdleSync();
    }

    private void awaitFile(File file, long length) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while ((!file.isFile() || file.length() != length) && android.os.SystemClock.uptimeMillis() < deadline)
            android.os.SystemClock.sleep(50);
        assertTrue("Download did not complete: " + file.getName(), file.isFile());
        assertEquals(length, file.length());
        instrumentation.waitForIdleSync();
    }

    private void awaitFtpFolder(String path) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            instrumentation.runOnMainSync(() -> loaded.set(main.pane1Folder instanceof FTPFileWrapper folder && folder.remotePath().equals(path)));
            if (loaded.get()) return;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("FTP folder did not load: " + path);
    }

    @Test public void ftpPaneNavigatesCopiesAndMovesOnlyAfterSuccessfulDownload() throws Exception {
        launchMain();
        File destination = new File(context.getCacheDir(), "ftp-destination-" + UUID.randomUUID());
        assertTrue(destination.mkdir());
        try (FtpFixture fixture = new FtpFixture(context)) {
            byte[] binary = {0, 10, 13, (byte) 255};
            File source = new File(fixture.share, "binary.bin");
            File moving = new File(fixture.share, "move.bin");
            File folder = new File(fixture.share, "folder"); assertTrue(folder.mkdir());
            for (File file : new File[]{source, moving}) try (FileOutputStream out = new FileOutputStream(file)) { out.write(binary); }
            instrumentation.runOnMainSync(() -> {
                main.loadFolderInPane(destination, false, false);
                main.getFtp().connect(new FtpProfile("fixture", "127.0.0.1", fixture.port, "tester", "test-only", false), true);
            });
            awaitText("binary.bin");
            click("folder");
            awaitFtpFolder("/folder");
            instrumentation.runOnMainSync(() -> main.getFtp().ftpParent(true));
            awaitText("binary.bin");
            longPressFile("binary.bin");
            click(context.getString(android.R.string.copy) + " ->");
            File copied = new File(destination, "binary.bin");
            awaitFile(copied, binary.length);
            assertArrayEquals(binary, java.nio.file.Files.readAllBytes(copied.toPath()));
            assertTrue(source.exists());
            awaitText("move.bin");
            longPressFile("move.bin");
            click(context.getString(R.string.move) + " ->");
            File moved = new File(destination, "move.bin");
            awaitFile(moved, binary.length);
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (moving.exists() && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(50);
            assertFalse("A move deletes its source only after the complete binary download", moving.exists());
            assertArrayEquals(binary, java.nio.file.Files.readAllBytes(moved.toPath()));
            instrumentation.runOnMainSync(() -> {
                main.loadFolderInPane(context.getFilesDir(), true, false);
                main.loadFolderInPane(context.getFilesDir(), false, false);
            });
        } finally { FileUtils.deleteTree(destination, null); }
    }

    @Test public void networkPinsUseVolumeRowsWithoutFakeCapacity() {
        launchMain();
        instrumentation.runOnMainSync(() -> {
            SidebarAdapter adapter = new SidebarAdapter(main, null);
            StorageUtil.StorageInfo info = new StorageUtil.StorageInfo();
            info.name = "Network test"; info.totalBytes = 100; info.usedBytes = 60; info.freeBytes = 40;
            SidebarAdapter.SidebarEntry entry = new SidebarAdapter.SidebarEntry(SidebarAdapter.EntryType.PIN,
                    "storage", "smb:test", info.name, R.drawable.cloud_download_24px, null, info);
            adapter.setPinned(Collections.singletonList(entry));
            int position = -1;
            for (int i = 0; i < adapter.getCount(); i++) if ("smb:test".equals(adapter.getEntry(i).id())) position = i;
            assertEquals(1, adapter.getItemViewType(position));
            View row = adapter.getView(position, null, new android.widget.LinearLayout(main));
            assertEquals(60, ((ProgressBar) row.findViewById(R.id.pbUsed)).getProgress());
            info.totalBytes = info.usedBytes = -1;
            info.freeBytes = 25;
            row = adapter.getView(position, row, new android.widget.LinearLayout(main));
            assertEquals(View.GONE, ((View) row.findViewById(R.id.pbUsed).getParent()).getVisibility());
            assertEquals(context.getString(R.string.network_storage_free, FileSize.getHumanReadableFileSize(25)),
                    ((TextView) row.findViewById(R.id.tvUsedFree)).getText().toString());
        });
    }

    private View awaitRow() {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        java.util.concurrent.atomic.AtomicReference<View> result = new java.util.concurrent.atomic.AtomicReference<>();
        do {
            instrumentation.runOnMainSync(() -> {
                RecyclerView pane = main.findViewById(R.id.listViewPane1);
                RecyclerView.ViewHolder holder = pane.findViewHolderForAdapterPosition(1);
                if (holder != null) result.set(holder.itemView);
            });
            if (result.get() != null) return result.get();
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("File row never bound");
    }

    @Test public void ftpWebdavAndSmbRowsShareLocalMetricsAndIcons() throws Exception {
        launchMain();
        File folder = new File(context.getCacheDir(), "network-row-" + UUID.randomUUID());
        assertTrue(folder.mkdir());
        File file = new File(folder, "row.txt");
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(new byte[]{1, 2, 3}); }
        try {
            instrumentation.runOnMainSync(() -> main.loadFolderInPane(folder, true, false));
            instrumentation.waitForIdleSync();
            View normal = awaitRow();
            TextView localName = normal.findViewById(R.id.fileName), localDate = normal.findViewById(R.id.fileDate);
            android.widget.ImageView localIcon = normal.findViewById(R.id.fileIcon);
            for (String protocol : new String[]{"webdav", "smb", "ftp"}) {
                instrumentation.runOnMainSync(() -> {
                    RecyclerView pane = main.findViewById(R.id.listViewPane1);
                    if (protocol.equals("ftp")) {
                        FTPFileWrapper up = new FTPFileWrapper("/", new EZFtpFile("..", "/", EZFtpFile.TYPE_DIRECTORY, 0, null));
                        FTPFileWrapper child = new FTPFileWrapper("/", new EZFtpFile(file.getName(), "/", EZFtpFile.TYPE_FILE, file.length(), new Date(file.lastModified())));
                        pane.setAdapter(new FtpFilesArrayAdapter(main, new FTPFileWrapper[]{up, child}, true, null));
                    } else {
                        RemoteFile.Connection connection = new RemoteFile.Connection(null, protocol.equals("smb") ? "smb://test/share/" : "https://test/dav/");
                        RemoteFile root = RemoteFile.root(connection);
                        RemoteFile child = new RemoteFile(connection, new RemoteEntry(file.getName(), false, file.length(), file.lastModified()));
                        pane.setAdapter(new RemoteFilesArrayAdapter(main, root, new RemoteFile[]{child}, true));
                    }
                });
                instrumentation.waitForIdleSync();
                View row = awaitRow();
                instrumentation.runOnMainSync(() -> {
                    TextView nameView = row.findViewById(R.id.fileName), dateView = row.findViewById(R.id.fileDate);
                    android.widget.ImageView icon = row.findViewById(R.id.fileIcon);
                    assertEquals(localName.getTextSize(), nameView.getTextSize(), 0.01f);
                    assertEquals(localName.getMaxLines(), nameView.getMaxLines());
                    assertEquals(localDate.getTextSize(), dateView.getTextSize(), 0.01f);
                    assertEquals(localDate.getText().toString(), dateView.getText().toString());
                    assertEquals(localIcon.getLayoutParams().width, icon.getLayoutParams().width);
                    assertSame(localIcon.getDrawable(), icon.getDrawable());
                    assertEquals(normal.getPaddingTop(), row.getPaddingTop());
                    assertEquals(View.GONE, row.findViewById(R.id.appBadge).getVisibility());
                });
            }
        } finally {
            instrumentation.runOnMainSync(() -> main.loadFolderInPane(context.getFilesDir(), true, false));
            FileUtils.deleteTree(folder, null);
        }
    }
}
