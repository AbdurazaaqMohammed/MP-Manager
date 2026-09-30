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

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
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
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * FTP server/client dialogs + pane loading extracted from MainActivity.
 */
public class FtpController {

    private final MainActivity activity;
    private IEZFtpClient ftpClient;
    private MaterialAutoCompleteTextView profileSpinner;
    private ImageButton profileManageButton;

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
            ((android.text.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
            Extensions.showMessage(activity, activity.rss.getString(R.string.copied));
            return false;
        });
        view.findViewById(R.id.copy).setOnClickListener(v -> {
            ((android.text.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE)).setText(ipString);
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
                            Extensions.showMessage(activity, activity.rss.getString(R.string.failed_to_start_ftp_server, e.getMessage()));
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
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ftp_client, null);
        EditText ipInput = view.findViewById(R.id.ipInput);
        EditText portInput = view.findViewById(R.id.portInput);
        EditText userInput = view.findViewById(R.id.userInput);
        EditText passInput = view.findViewById(R.id.passInput);
        MaterialAutoCompleteTextView securityInput = view.findViewById(R.id.securityInput);
        String[] securityOptions = activity.rss.getStringArray(R.array.ftp_security_options);
        securityInput.setSimpleItems(securityOptions);
        securityInput.setText(securityOptions[0], false);
        FrameLayout container = view.findViewById(R.id.container);
        View header = LayoutInflater.from(activity).inflate(R.layout.dialog_ftp_client_header, container, false);
        container.addView(header, 0);

        profileSpinner = header.findViewById(R.id.profile_spinner);
        profileManageButton = header.findViewById(R.id.manage_profiles);

        new ProfileHelper(activity, ipInput, portInput, userInput, passInput, securityInput, profileSpinner, profileManageButton).setupProfileSpinner(false);
        activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.rss.getString(R.string.ftp_client))
                .setView(view)
                .setPositiveButton(activity.rss.getString(R.string.connect), (dialog, which) -> {

                    String ip = ipInput.getText().toString().trim();
                    int port = Integer.parseInt(portInput.getText().toString());
                    String user = userInput.getText().toString();
                    String pass = passInput.getText().toString();
                    int securityType = getSecurityTypeIndex(securityInput.getText().toString());

                    ftpClient = new EZFtpClient();
                    ftpClient.connect(ip, port, user, pass, securityType, new OnEZFtpCallBack<>() {
                        @Override
                        public void onSuccess(Void response) {
                            activity.runOnUiThread(() -> {
                                Extensions.showMessage(activity, activity.rss.getString(R.string.connected_to_ftp));
                                fetchFtpDirAndLoad(File.separator, activity.lastPaneSelected == 1);
                            });
                        }

                        @Override
                        public void onFail(int code, String msg) {
                            activity.runOnUiThread(() -> Extensions.showMessage(activity, activity.rss.getString(R.string.ftp_connect_failed, msg)));
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        if (ftpClient == null || !ftpClient.isConnected()) return;

        ftpClient.changeDirectory(path, new OnEZFtpCallBack<>() {
            @Override
            public void onSuccess(String newPath) {
                ftpClient.getCurDirFileList(new OnEZFtpCallBack<>() {
                    @Override
                    public void onSuccess(List<EZFtpFile> response) {
                        activity.runOnUiThread(() -> {
                            List<FTPFileWrapper> files = new ArrayList<>();
                            files.add(new FTPFileWrapper(newPath, new EZFtpFile("..", "", 0, 0, new Date())));
                            int foldersCount = 0;
                            //int filesCount = 0;
                            if (response != null) {
                                for (EZFtpFile f : response) {
                                    if (f.getType() == EZFtpFile.TYPE_DIRECTORY) foldersCount++;
                                    //else filesCount++;
                                    files.add(new FTPFileWrapper(newPath, f));
                                }
                            }
                            RecyclerView pane = activity.findViewById(pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                            pane.setAdapter(new FtpFilesArrayAdapter(activity, files.toArray(new FTPFileWrapper[0]), pane1, ftpClient));
                            TextView currentFolderPath = activity.findViewById(R.id.currentFolderPath);
                            currentFolderPath.setText(activity.rss.getString(R.string.ftp, path));
                            activity.uiHelper.scrollTextView(currentFolderPath);

                            activity.<TextView>findViewById(R.id.folderCount).setText(new StringBuilder("Folders: ").append(foldersCount).append(" Files: ").append(response.size() - foldersCount));
                        });
                    }

                    @Override
                    public void onFail(int code, String msg) {
                    }
                });
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }

    /** Go to the parent of the current FTP directory (back-press / up flows). */
    public void ftpParent(boolean pane1) {
        if (ftpClient == null) return;
        ftpClient.getCurDirPath(new OnEZFtpCallBack<>() {
            @Override
            public void onSuccess(String response) {
                int startIndex = response.indexOf(File.separator);
                int endIndex = response.lastIndexOf(File.separator);
                fetchFtpDirAndLoad((startIndex == endIndex) ? File.separator : response.substring(0, endIndex), pane1);
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }

    public void loadFtpFolderInPane(FTPFileWrapper folder, boolean pane1) {
        if (folder.getName().equals("..")) {
            if (ftpClient != null) {
                ftpParent(activity.lastPaneSelected == 1);
            }
        } else if (folder.isDirectory()) {
            fetchFtpDirAndLoad(folder.getFtpFile().getName(), pane1);
        } else {
            Extensions.showMessage(activity, "FTP File Download coming soon");
        }
    }
}
