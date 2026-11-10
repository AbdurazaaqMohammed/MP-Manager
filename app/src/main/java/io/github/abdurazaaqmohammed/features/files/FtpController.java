package io.github.abdurazaaqmohammed.features.files;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.text.ClipboardManager;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpProfile;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileManager;
import com.lilincpp.github.libezftp.EZFtpClient;
import com.lilincpp.github.libezftp.EZFtpFile;
import com.lilincpp.github.libezftp.EZFtpServer;
import com.lilincpp.github.libezftp.user.EZFtpUser;
import com.lilincpp.github.libezftp.user.EZFtpUserPermission;
import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.IEZFtpServer;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import com.lilincpp.github.libezftp.EZFtpServer.Builder;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.ftp.FTPFileWrapper;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpForegroundService;
import io.github.abdurazaaqmohammed.MPManager.ftp.FtpsCertificateUtil;
import io.github.abdurazaaqmohammed.MPManager.ftp.ProfileHelper;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.data.prefs.SidebarPins;
import io.github.abdurazaaqmohammed.features.files.network.RemoteSpace;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * FTP server/client dialogs + pane loading extracted from MainActivity.
 */
public class FtpController {

    private final MainActivity activity;
    private IEZFtpClient ftpClient;
    private MaterialAutoCompleteTextView profileSpinner;
    private ImageButton profileManageButton;
    private final ConcurrentHashMap<String, RemoteSpace.Report> spaceReports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IEZFtpClient, FtpProfile> sessionProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IEZFtpClient, Long> spaceCheckTimes = new ConcurrentHashMap<>();
    private final Set<IEZFtpClient> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public static IEZFtpServer ftpServer;

    public FtpController(MainActivity activity) {
        this.activity = activity;
    }

    public void showFtpServerDialog() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ftp_server, null);
        FrameLayout container = view.findViewById(R.id.container);
        View header = LayoutInflater.from(activity).inflate(R.layout.dialog_ftp_server_header, container, false);
        container.addView(header, 0);

        WifiManager wifiManager = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        String ipString = Formatter.formatIpAddress(ipAddress);
        TextView ipTv = view.findViewById(R.id.ip);
        ipTv.setText(activity.rss.getString(R.string.ip, ipString));
        ipTv.setOnLongClickListener(v -> {
            ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
            Extensions.showMessage(activity, activity.rss.getString(R.string.copied));
            return false;
        });
        view.findViewById(R.id.copy).setOnClickListener(v -> {
            ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
            Extensions.showMessage(activity, activity.rss.getString(R.string.copied));
        });
        view.findViewById(R.id.share).setOnClickListener(v -> activity.startActivity(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, ipString).setType("text/plain")));
        EditText portInput = view.findViewById(R.id.portInput);
        EditText userInput = view.findViewById(R.id.userInput);
        EditText passInput = view.findViewById(R.id.passInput);
        MaterialAutoCompleteTextView securityInput = view.findViewById(R.id.securityInput);
        String[] securityOptions = activity.rss.getStringArray(R.array.ftp_security_options);
        securityInput.setSimpleItems(securityOptions);
        securityInput.setText(securityOptions[0], false);
        profileSpinner = header.findViewById(R.id.profile_spinner);
        profileManageButton = header.findViewById(R.id.manage_profiles);
        new ProfileHelper(activity, null, portInput, userInput, passInput, securityInput, profileSpinner, profileManageButton).setupProfileSpinner(true);

        boolean serverNotStarted = ftpServer == null;
        portInput.setEnabled(serverNotStarted);
        userInput.setEnabled(serverNotStarted);
        passInput.setEnabled(serverNotStarted);
        View pl = header.findViewById(R.id.profile_layout);
        pl.setEnabled(serverNotStarted);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            Extensions.showMessage(activity, "Please allow notifications to show FTP server running");
            activity.permissionLauncher().launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        // This is very important to have notification so user remember that the FTP server is running and can stop it easily and should be shown always not just if dialog or app closed
        Intent serviceIntent = new Intent(activity, FtpForegroundService.class);
        serviceIntent.putExtra("io.github.abdurazaaqmohammed.MPManager.ip", ipString);

        String start = activity.rss.getString(R.string.start);
        String stop = activity.rss.getString(R.string.stop);
        AlertDialog ad = activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.rss.getString(R.string.ftp_server))
                .setView(view)
                .setOnDismissListener(null)
                .setPositiveButton(serverNotStarted ? start : stop, null)
                .show();
                ad.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    TextView tv = (TextView) v;
                    boolean wasStarted = stop.equals(tv.getText().toString());
                    tv.setText(wasStarted ? start : stop);
                    portInput.setEnabled(wasStarted);
                    userInput.setEnabled(wasStarted);
                    passInput.setEnabled(wasStarted);
                    pl.setEnabled(serverNotStarted);
                    if (wasStarted) {
                        if (ftpServer != null) ftpServer.stop();
                        ftpServer = null;
                        activity.stopService(serviceIntent);
                        Extensions.showMessage(activity, activity.rss.getString(R.string.ftp_server_stopped));
                    } else {
                        int port = Integer.parseInt(portInput.getText().toString());
                        String user = userInput.getText().toString();
                        String pass = passInput.getText().toString();
                        int securityType = getSecurityTypeIndex(securityInput.getText().toString());

                        try {
                            Builder builder = new EZFtpServer.Builder()
                                    .setListenPort(port)
                                    .addUser(new EZFtpUser(user, pass, Environment.getExternalStorageDirectory().getPath(), EZFtpUserPermission.WRITE));
                            if (securityType > 0) {
                                boolean implicit = securityType == 2;
                                File keystoreFile = FtpsCertificateUtil.ensureKeystore(new File(activity.getCacheDir(), "ftps-keystore.jks"));
                                builder.setFtps(keystoreFile, FtpsCertificateUtil.getPasswordString(), implicit);
                            }
                            ftpServer = builder.create();
                            ftpServer.start();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                activity.startForegroundService(serviceIntent);
                            } else {
                                activity.startService(serviceIntent);
                            }
                            Extensions.showMessage(activity, activity.rss.getString(R.string.ftp_server_started, port));
                        } catch (Exception e) {
                            activity.stopService(serviceIntent);
                            portInput.setEnabled(true);
                            userInput.setEnabled(true);
                            passInput.setEnabled(true);
                            pl.setEnabled(true);
                            if (ftpServer != null) ftpServer.stop();
                            tv.setText(activity.rss.getString(R.string.start));
                            e.printStackTrace();
                            Snackbar sb = Snackbar.make(activity.findViewById(R.id.bottomBar), activity.rss.getString(R.string.failed_to_start_ftp_server, e.getMessage()), Snackbar.LENGTH_LONG);
                            sb.setAction(R.string.error, v1 -> new ErrorUtil(activity).showError(e));
                            sb.show();
                        }
                        BroadcastReceiver ftpStopReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                if (ad.isShowing()) {
                                    tv.setText(activity.rss.getString(R.string.start));
                                    portInput.setEnabled(true);
                                    userInput.setEnabled(true);
                                    passInput.setEnabled(true);
                                    header.setEnabled(true);
                                }
                                activity.unregisterReceiver(this);
                            }
                        };
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity.registerReceiver(ftpStopReceiver, new IntentFilter("io.github.abdurazaaqmohammed.FTP_STOPPED"), Context.RECEIVER_NOT_EXPORTED);
                        } else activity.registerReceiver(ftpStopReceiver, new IntentFilter("io.github.abdurazaaqmohammed.FTP_STOPPED"));
                    }
                });
    }

    public void showFtpClientDialog() {
        activity.getNetwork().showConnectionDialog("ftp");
    }

    public List<FtpProfile> clientProfiles() {
        List<FtpProfile> clients = new ArrayList<>();
        for (FtpProfile profile : new ProfileManager(activity).getProfiles())
            if (!profile.isServerProfile()) clients.add(profile);
        return clients;
    }

    public void saveClientProfile(FtpProfile profile, String previousName, boolean pin) {
        ProfileManager store = new ProfileManager(activity);
        List<FtpProfile> profiles = store.getProfiles();
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) {
            FtpProfile p = profiles.get(i);
            if (!p.isServerProfile() && (p.getName().equals(previousName) || p.getName().equals(profile.getName()))) {
                index = i;
                if (p.getName().equals(previousName)) break;
            }
        }
        if (index >= 0) store.updateProfile(index, profile);
        else store.addProfile(profile);
        String id = SidebarPins.FTP_PREFIX + profile.getName();
        spaceReports.remove(profile.getName());
        if (previousName != null) spaceReports.remove(previousName);
        if (pin) SidebarPins.add(activity, id);
        else SidebarPins.remove(activity, id);
        activity.refreshSidebarAsync(activity.getSidebarSectionOrder());
    }

    public void deleteClientProfile(String name) {
        ProfileManager store = new ProfileManager(activity);
        List<FtpProfile> profiles = store.getProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            FtpProfile p = profiles.get(i);
            if (!p.isServerProfile() && p.getName().equals(name)) { store.deleteProfile(i); break; }
        }
        spaceReports.remove(name);
        activity.refreshSidebarAsync(activity.getSidebarSectionOrder());
    }

    private static String storageIdentity(FtpProfile profile) {
        return profile.getSecurityType() + "\u0000" + profile.getIp() + "\u0000" + profile.getPort() + "\u0000" + profile.getUsername();
    }
    public RemoteSpace spaceForProfile(FtpProfile profile) {
        RemoteSpace.Report report = spaceReports.get(profile.getName());
        return report != null && report.identity().equals(storageIdentity(profile)) ? report.space() : null;
    }
    public boolean hasCheckedSpace(FtpProfile profile) {
        RemoteSpace.Report report = spaceReports.get(profile.getName());
        return report != null && report.identity().equals(storageIdentity(profile));
    }

    public void connect(FtpProfile profile, boolean pane1) {
        String address;
        try { address = new java.net.URI("ftp", null, profile.getIp(), profile.getPort(), "/", null, null).toString(); }
        catch (Exception e) { new ErrorUtil(activity).showError(e); return; }
        activity.getNetwork().withPermissionForAddress(address, () -> connectReady(profile, pane1));
    }

    /**
     * Connects a saved client profile by name and opens its root in the
     * current pane - the sidebar pin entry point.
     */
    public void connectProfile(String name, boolean pane1) {
        FtpProfile profile = null;
        try {
            for (FtpProfile p : new ProfileManager(activity).getProfiles()) {
                if (!p.isServerProfile() && name.equals(p.getName())) {
                    profile = p;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        if (profile == null) {
            Extensions.showMessage(activity, activity.rss.getString(R.string.ftp_connect_failed, name));
            return;
        }
        connect(profile, pane1);
    }

    private void connectReady(FtpProfile profile, boolean pane1) {
        Extensions.showMessage(activity, activity.rss.getString(R.string.connecting_to, profile.getName()));
        if (closed) return;
        IEZFtpClient client = new EZFtpClient();
        ftpClient = client;
        clients.add(client);
        sessionProfiles.put(client, profile);
        client.connect(profile.getIp(), profile.getPort(), profile.getUsername(),
                profile.getPassword(), profile.getSecurityType(), new OnEZFtpCallBack<>() {
                    @Override
                    public void onSuccess(Void response) {
                        activity.runOnUiThread(() -> {
                            if (closed || activity.isDestroyed()) { client.release(); return; }
                            Extensions.showMessage(activity, activity.rss.getString(R.string.connected_to_ftp));
                            String scheme = profile.getSecurityType() == 0 ? "ftp" : "ftps";
                            String address;
                            try { address = new java.net.URI(scheme, null, profile.getIp(), profile.getPort(), null, null, null).toString(); }
                            catch (Exception e) { address = scheme + "://" + profile.getIp(); }
                            fetchFtpDirAndLoad(client, File.separator, address, pane1, true);
                            refreshSpace(client);
                        });
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        client.release();
                        clients.remove(client);
                        sessionProfiles.remove(client);
                        activity.runOnUiThread(() -> {
                            if (!closed && !activity.isDestroyed()) Extensions.showMessage(activity, activity.rss.getString(R.string.ftp_connect_failed, msg));
                        });
                    }
                });
    }

    private void refreshSpace(IEZFtpClient client) {
        FtpProfile profile = sessionProfiles.get(client);
        if (closed || profile == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        Long previous = spaceCheckTimes.putIfAbsent(client, now);
        if (previous != null && (now - previous < 60_000 || !spaceCheckTimes.replace(client, previous, now))) return;
        String identity = storageIdentity(profile);
        client.getAvailableSpace(new OnEZFtpCallBack<>() {
            @Override public void onSuccess(Long free) {
                if (closed) return;
                spaceReports.put(profile.getName(), new RemoteSpace.Report(identity,
                        free != null && free >= 0 ? RemoteSpace.available(free) : null));
                if (!activity.isDestroyed()) activity.refreshSidebarAsync(activity.getSidebarSectionOrder());
            }
            @Override public void onFail(int code, String message) { /* Optional server extension. */ }
        });
    }

    private int getSecurityTypeIndex(String text) {
        String[] options = activity.rss.getStringArray(R.array.ftp_security_options);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(text)) {
                return i;
            }
        }
        return 0;
    }

    public void fetchFtpDirAndLoad(String path, boolean pane1) {
        File folder = pane1 ? activity.pane1Folder : activity.pane2Folder;
        FTPFileWrapper ftp = folder instanceof FTPFileWrapper ? (FTPFileWrapper) folder : null;
        fetchFtpDirAndLoad(ftp != null && ftp.client != null ? ftp.client : ftpClient,
                path, ftp != null ? ftp.connectionAddress() : "ftp://", pane1, false);
    }

    private void fetchFtpDirAndLoad(IEZFtpClient client, String path, String address, boolean pane1, boolean history) {
        if (closed || client == null || !client.isConnected()) return;
        long generation = activity.beginNetworkPaneLoad(pane1);
        client.getDirectoryFileList(path, new OnEZFtpCallBack<>() {
            @Override public void onSuccess(List<EZFtpFile> response) {
                activity.runOnUiThread(() -> {
                    if (closed || !activity.isNetworkPaneLoadCurrent(pane1, generation)) return;
                    List<FTPFileWrapper> children = new ArrayList<>();
                    if (response != null) for (EZFtpFile f : response) {
                        if (f.getName().equals(".") || f.getName().equals("..")) continue;
                        FTPFileWrapper file = new FTPFileWrapper(path, f, client, address);
                        if (activity.isNotHidden(file)) children.add(file);
                    }
                    FTPFileWrapper[] files = children.toArray(new FTPFileWrapper[0]);
                    FTPFileWrapper folder = FTPFileWrapper.folder(path, client, address);
                    activity.sortFiles(files, folder.displayPath());
                    activity.bindFtpFolder(folder, files, pane1, history);
                    refreshSpace(client);
                });
            }
            @Override public void onFail(int code, String message) {
                if (!closed && activity.isNetworkPaneLoadCurrent(pane1, generation))
                    Extensions.showMessage(activity, activity.getString(R.string.ftp_connect_failed, message));
            }
        });
    }

    /** Go to the parent of the current FTP directory (back-press / up flows). */
    public void ftpParent(boolean pane1) {
        File current = pane1 ? activity.pane1Folder : activity.pane2Folder;
        if (!(current instanceof FTPFileWrapper folder)) return;
        File parent = folder.getParentFile();
        if (parent instanceof FTPFileWrapper ftp) loadFtpFolderInPane(ftp, pane1);
        else activity.loadFolderInPane(Environment.getExternalStorageDirectory(), pane1, true);
    }

    public void loadFtpFolderInPane(FTPFileWrapper folder, boolean pane1) {
        loadFtpFolderInPane(folder, pane1, true);
    }

    public void loadFtpFolderInPane(FTPFileWrapper folder, boolean pane1, boolean history) {
        if (folder.getName().equals("..")) {
            ftpParent(pane1);
        } else if (folder.isDirectory()) {
            fetchFtpDirAndLoad(folder.client != null ? folder.client : ftpClient, folder.remotePath(), folder.connectionAddress(), pane1, history);
        } else {
            Extensions.showMessage(activity, "FTP File Download coming soon");
        }
    }

    public void close() {
        closed = true;
        for (IEZFtpClient client : clients) client.release();
        clients.clear();
        sessionProfiles.clear();
        spaceCheckTimes.clear();
    }
}
