package io.github.abdurazaaqmohammed.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.MediaStore;
import android.service.quicksettings.TileService;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.utils.DnsManager;
import io.github.abdurazaaqmohammed.utils.QrUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootPermissionHelper;
import io.github.abdurazaaqmohammed.utils.WifiPasswordUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class WifiManagerActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout dnsList;
    private TextView dnsCurrent;
    private TextView connText;
    private TextView usageText;
    private ListView passList;
    private final List<WifiPasswordUtil.WifiEntry> passAll = new ArrayList<>();
    private final List<WifiPasswordUtil.WifiEntry> passVisible = new ArrayList<>();
    private BaseAdapter passAdapter;
    private boolean showPass;
    private TextView passCount;

    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(prefs.getInt("theme", dark ? io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark : io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        showPass = !prefs.getBoolean("wifi_hide_pass", false);
        Intent launching = getIntent();
        if (launching != null && DnsManager.APPLY_ACTION.equals(launching.getAction())) {
            String id = launching.getStringExtra(DnsManager.EXTRA_PROFILE_ID);
            DnsManager.DnsProfile target = DnsManager.findProfile(this, id);
            if (target != null) {
                new Thread(() -> {
                    boolean ok = DnsManager.applyProfile(WifiManagerActivity.this, target);
                    handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, ok ? "DNS " + target.name : "DNS switch failed"));
                }).start();
            }
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Wi-Fi Manager");
        toolbar.setSubtitle("Connection DNS passwords usage");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        buildConnection(box);
        buildDns(box);
        buildPasswords(box);
        buildUsage(box);
        refreshConnection();
        refreshDns();
        refreshUsage();
        loadPasswords();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView sectionTitle(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(16), 0, dp(8));
        box.addView(t, p);
        return t;
    }

    private TextView bodyText(LinearLayout box) {
        TextView t = new TextView(this);
        t.setTextSize(14);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.parseColor("#14000000")));
        t.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private MaterialButton button(LinearLayout box, String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        box.addView(b, p);
        return b;
    }

    private void buildConnection(LinearLayout box) {
        sectionTitle(box, "Current Connection");
        connText = bodyText(box);
        connText.setText("Loading");
        MaterialButton refresh = button(box, "Refresh connection");
        refresh.setOnClickListener(v -> refreshConnection());
    }

    private void refreshConnection() {
        new Thread(() -> {
            StringBuilder b = new StringBuilder();
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    try {
                        NetworkInfo active = cm.getActiveNetworkInfo();
                        if (active != null) {
                            b.append("Active ").append(active.getTypeName()).append(" connected=").append(active.isConnected()).append("\n");
                        } else {
                            b.append("Active none\n");
                        }
                    } catch (Exception e) {
                        b.append("Active unknown\n");
                    }
                }
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    b.append("Wi-Fi enabled ").append(wm.isWifiEnabled()).append("\n");
                    try {
                        WifiInfo info = wm.getConnectionInfo();
                        if (info != null) {
                            String ssid = info.getSSID() == null ? "-" : info.getSSID().replace("\"", "");
                            b.append("SSID ").append(ssid).append("\n");
                            b.append("BSSID ").append(info.getBSSID()).append("\n");
                            b.append("Link ").append(info.getLinkSpeed()).append(" Mbps\n");
                            b.append("RSSI ").append(info.getRssi()).append(" dBm\n");
                            b.append("IP ").append(ipStr(info.getIpAddress())).append("\n");
                        }
                        DhcpInfo dhcp = wm.getDhcpInfo();
                        if (dhcp != null) {
                            b.append("Gateway ").append(ipStr(dhcp.gateway)).append("\n");
                            b.append("DNS1 ").append(ipStr(dhcp.dns1)).append("\n");
                            b.append("DNS2 ").append(ipStr(dhcp.dns2)).append("\n");
                        }
                    } catch (Exception e) {
                        b.append("Wi-Fi details unavailable\n");
                    }
                }
            } catch (Exception e) {
                b.append("Unavailable");
            }
            String out = b.toString().trim();
            handler.post(() -> connText.setText(out));
        }).start();
    }

    private String ipStr(int ip) {
        return (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255);
    }

    private void buildDns(LinearLayout box) {
        sectionTitle(box, "Private DNS");
        dnsCurrent = bodyText(box);
        dnsCurrent.setText("Loading");
        dnsList = new LinearLayout(this);
        dnsList.setOrientation(LinearLayout.VERTICAL);
        box.addView(dnsList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton add = new MaterialButton(this);
        add.setText("Add profile");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = dp(4);
        p.setMargins(m, m, m, m);
        row.addView(add, p);
        MaterialButton tile = new MaterialButton(this);
        tile.setText("Quick tile");
        row.addView(tile, p);
        add.setOnClickListener(v -> showAddProfile());
        tile.setOnClickListener(v -> showTileHelp());
    }

    private void refreshDns() {
        new Thread(() -> {
            String mode = DnsManager.currentMode(WifiManagerActivity.this);
            String host = DnsManager.currentHostname(WifiManagerActivity.this);
            DnsManager.DnsProfile match = DnsManager.matchingProfile(WifiManagerActivity.this);
            List<DnsManager.DnsProfile> profiles = DnsManager.getProfiles(WifiManagerActivity.this);
            handler.post(() -> {
                StringBuilder b = new StringBuilder();
                b.append("Mode ").append(mode.isEmpty() ? "-" : mode).append("\n");
                b.append("Host ").append(host.isEmpty() ? "-" : host).append("\n");
                b.append("Profile ").append(match == null ? "-" : match.name);
                dnsCurrent.setText(b);
                dnsList.removeAllViews();
                RadioGroup group = new RadioGroup(this);
                for (DnsManager.DnsProfile profile : profiles) {
                    MaterialCardView card = new MaterialCardView(this);
                    card.setRadius(dp(12));
                    card.setCardElevation(dp(1));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.setMargins(0, dp(4), 0, dp(4));
                    card.setLayoutParams(cp);
                    LinearLayout line = new LinearLayout(this);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setGravity(Gravity.CENTER_VERTICAL);
                    line.setPadding(dp(8), dp(8), dp(8), dp(8));
                    RadioButton radio = new RadioButton(this);
                    radio.setText(profile.name + "  " + profile.describe());
                    radio.setChecked(match != null && match.id.equals(profile.id));
                    radio.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    line.addView(radio);
                    ImageView shortcut = new ImageView(this);
                    shortcut.setImageResource(android.R.drawable.ic_menu_add);
                    shortcut.setPadding(dp(8), dp(8), dp(8), dp(8));
                    shortcut.setOnClickListener(v -> DnsManager.pinShortcut(WifiManagerActivity.this, profile));
                    line.addView(shortcut);
                    if (DnsManager.isCustomId(profile.id)) {
                        ImageView del = new ImageView(this);
                        del.setImageResource(android.R.drawable.ic_menu_delete);
                        del.setPadding(dp(8), dp(8), dp(8), dp(8));
                        del.setOnClickListener(v -> {
                            DnsManager.removeCustom(WifiManagerActivity.this, profile.id);
                            refreshDns();
                        });
                        line.addView(del);
                    }
                    card.addView(line);
                    group.addView(card);
                    radio.setOnClickListener(v -> applyDns(profile));
                }
                dnsList.addView(group);
            });
        }).start();
    }

    private void applyDns(DnsManager.DnsProfile profile) {
        Extensions.showMessage(this, "Applying " + profile.name);
        new Thread(() -> {
            boolean ok = DnsManager.applyProfile(WifiManagerActivity.this, profile);
            handler.post(() -> {
                Extensions.showMessage(WifiManagerActivity.this, ok ? "DNS " + profile.name : "Failed, needs root or WRITE_SECURE_SETTINGS");
                if (!ok) offerRootGrant();
                refreshDns();
            });
        }).start();
    }

    private void offerRootGrant() {
        try {
            boolean rooted = RootManager.getInstance(this).isRootAvailable();
            if (!rooted) return;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Grant WRITE_SECURE_SETTINGS")
                    .setMessage("Root found. Grant this app WRITE_SECURE_SETTINGS so DNS can switch with one tap and no root prompt next time?")
                    .setPositiveButton("Grant", (d, w) -> new Thread(() -> {
                        boolean ok = RootPermissionHelper.grantWriteSecureViaRoot(WifiManagerActivity.this);
                        handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, ok ? "Granted" : "Grant failed"));
                    }).start())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception ignored) {
        }
    }

    private void showAddProfile() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        form.setPadding(p, p, p, p);
        TextInputLayout nameBox = UiFields.box(this, "Name");
        EditText name = UiFields.field(nameBox, InputType.TYPE_CLASS_TEXT);
        form.addView(nameBox);
        TextInputLayout hostBox = UiFields.box(this, "Hostname, e.g. one.one.one.one");
        EditText host = UiFields.field(hostBox, InputType.TYPE_CLASS_TEXT);
        form.addView(hostBox);
        new MaterialAlertDialogBuilder(this)
                .setTitle("New DNS profile")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText() == null ? "" : name.getText().toString().trim();
                    String h = host.getText() == null ? "" : host.getText().toString().trim();
                    if (n.isEmpty() || h.isEmpty()) {
                        Extensions.showMessage(this, "Name and hostname needed");
                        return;
                    }
                    DnsManager.addCustom(this, n, h);
                    refreshDns();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTileHelp() {
        String msg;
        if (Build.VERSION.SDK_INT >= 33) {
            msg = "Add the Private DNS tile to switch profiles from Quick Settings.";
        } else {
            msg = "Pull down Quick Settings, tap edit, and drag the Private DNS tile in. Tapping it cycles your profiles.";
        }
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                ComponentName cn = new ComponentName(this, "io.github.abdurazaaqmohammed.tools.PrivateDnsTileService");
                Method m = TileService.class.getMethod("requestAddTileService",
                        ComponentName.class, CharSequence.class, Icon.class,
                        Executor.class, Consumer.class);
                m.invoke(null, cn, "Private DNS", null, getMainExecutor(), (Consumer<Integer>) result -> {
                });
                return;
            } catch (Exception ignored) {
            }
        }
        new MaterialAlertDialogBuilder(this).setTitle("Private DNS quick tile").setMessage(msg).setPositiveButton(android.R.string.ok, null).show();
    }

    private static class PassHolder {
        TextView ssid;
        TextView pass;
        MaterialButton copyBtn;
        MaterialButton shareBtn;
    }

    private void buildPasswords(LinearLayout box) {
        sectionTitle(box, "Saved Wi-Fi Passwords");
        passCount = new TextView(this);
        passCount.setText("Root needed, loading");
        passCount.setTextSize(13);
        box.addView(passCount);
        TextInputLayout searchBox = UiFields.box(this, "Search networks");
        EditText search = UiFields.field(searchBox, InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        box.addView(searchBox);
        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.HORIZONTAL);
        opts.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox hide = new CheckBox(this);
        hide.setText("Hide passwords");
        hide.setChecked(!showPass);
        hide.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        opts.addView(hide);
        MaterialButton copyAllBtn = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        copyAllBtn.setText("Copy all");
        opts.addView(copyAllBtn);
        MaterialButton exportBtn = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        exportBtn.setText("Export");
        opts.addView(exportBtn);
        box.addView(opts);
        copyAllBtn.setOnClickListener(v -> {
            StringBuilder all = new StringBuilder();
            for (WifiPasswordUtil.WifiEntry e : passVisible) {
                all.append(e.ssid).append(" : ").append(e.password.isEmpty() ? "(Open)" : e.password).append("\n");
            }
            copyPlain("wifi", all.toString().trim());
        });
        exportBtn.setOnClickListener(v -> exportEntries(new ArrayList<>(passVisible)));
        passList = new ListView(this);
        passList.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (passList.getAdapter() != null && passList.getAdapter().getCount() > 0
                    && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        box.addView(passList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        int onVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        float density = getResources().getDisplayMetrics().density;
        passAdapter = new BaseAdapter() {
            public int getCount() {
                return passVisible.size();
            }

            public Object getItem(int position) {
                return passVisible.get(position);
            }

            public long getItemId(int position) {
                return position;
            }

            public View getView(int position, View convertView, ViewGroup parent) {
                WifiPasswordUtil.WifiEntry e = passVisible.get(position);
                PassHolder holder;
                if (convertView == null) {
                    MaterialCardView card = new MaterialCardView(WifiManagerActivity.this);
                    card.setRadius(14 * density);
                    card.setCardElevation(2 * density);
                    LinearLayout inner = new LinearLayout(WifiManagerActivity.this);
                    inner.setOrientation(LinearLayout.VERTICAL);
                    int cp = (int) (12 * density);
                    inner.setPadding(cp, cp, cp, cp);
                    TextView ssid = new TextView(WifiManagerActivity.this);
                    ssid.setTypeface(null, Typeface.BOLD);
                    ssid.setTextColor(onSurface);
                    inner.addView(ssid);
                    TextView pass = new TextView(WifiManagerActivity.this);
                    pass.setTypeface(Typeface.MONOSPACE);
                    pass.setTextColor(onVariant);
                    inner.addView(pass);
                    LinearLayout actionRow = new LinearLayout(WifiManagerActivity.this);
                    actionRow.setOrientation(LinearLayout.HORIZONTAL);
                    actionRow.setGravity(Gravity.END);
                    MaterialButton copyBtn = new MaterialButton(WifiManagerActivity.this, null, com.google.android.material.R.attr.borderlessButtonStyle);
                    copyBtn.setText(android.R.string.copy);
                    actionRow.addView(copyBtn);
                    MaterialButton shareBtn = new MaterialButton(WifiManagerActivity.this, null, com.google.android.material.R.attr.borderlessButtonStyle);
                    shareBtn.setText("Share");
                    actionRow.addView(shareBtn);
                    inner.addView(actionRow);
                    card.addView(inner);
                    holder = new PassHolder();
                    holder.ssid = ssid;
                    holder.pass = pass;
                    holder.copyBtn = copyBtn;
                    holder.shareBtn = shareBtn;
                    card.setTag(holder);
                    convertView = card;
                } else {
                    holder = (PassHolder) convertView.getTag();
                }
                holder.ssid.setText(e.ssid + "  [" + e.security + "]");
                holder.pass.setText(e.password.isEmpty() ? "Open network" : (showPass ? e.password : masked(e.password)));
                holder.copyBtn.setOnClickListener(v -> showCopyMenu(e, v));
                holder.shareBtn.setOnClickListener(v -> showShareMenu(e, v));
                return convertView;
            }
        };
        passList.setAdapter(passAdapter);
        passList.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= passVisible.size()) return;
            copyPasswordOnly(passVisible.get(pos));
        });
        hide.setOnCheckedChangeListener((b, checked) -> {
            showPass = !checked;
            PreferenceManager.getDefaultSharedPreferences(WifiManagerActivity.this).edit().putBoolean("wifi_hide_pass", checked).apply();
            passAdapter.notifyDataSetChanged();
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPass(s.toString());
            }

            public void afterTextChanged(Editable s) {
            }
        });
    }

    private String passOrOpen(WifiPasswordUtil.WifiEntry e) {
        return e.password.isEmpty() ? "(Open)" : e.password;
    }

    private void copyPasswordOnly(WifiPasswordUtil.WifiEntry e) {
        copyPlain("wifi", e.password.isEmpty() ? e.ssid : e.password);
    }

    private void showCopyMenu(WifiPasswordUtil.WifiEntry e, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Name + password");
        menu.getMenu().add("Password only");
        menu.getMenu().add("Name only");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Name + password")) {
                copyPlain("wifi", e.ssid + " : " + passOrOpen(e));
            } else if (title.equals("Password only")) {
                copyPasswordOnly(e);
            } else {
                copyPlain("wifi", e.ssid);
            }
            return true;
        });
        menu.show();
    }

    private void showShareMenu(WifiPasswordUtil.WifiEntry e, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("QR code");
        menu.getMenu().add("Name + password");
        menu.getMenu().add("Password only");
        menu.getMenu().add("Name only");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "QR code" -> showWifiQr(e);
                case "Name + password" -> shareWifiText(e.ssid + " : " + passOrOpen(e));
                case "Password only" -> shareWifiText(e.password.isEmpty() ? e.ssid : e.password);
                default -> shareWifiText(e.ssid);
            }
            return true;
        });
        menu.show();
    }

    private void shareWifiText(String text) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(share, "Share Wi-Fi"));
        } catch (Exception e) {
            Extensions.showMessage(this, "Share failed");
        }
    }

    private void showWifiQr(WifiPasswordUtil.WifiEntry e) {
        try {
            String config = QrUtil.wifiConfig(e.ssid, e.password, e.security);
            Bitmap qr = QrUtil.generate(config, 1024);
            float density = getResources().getDisplayMetrics().density;
            LinearLayout qrBox = new LinearLayout(this);
            qrBox.setOrientation(LinearLayout.VERTICAL);
            qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = (int) (20 * density);
            qrBox.setPadding(pad, pad, pad, pad);
            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(qr);
            int size = (int) (260 * density);
            qrBox.addView(qrView, new LinearLayout.LayoutParams(size, size));
            TextView ssidView = new TextView(this);
            ssidView.setText(e.ssid);
            ssidView.setTextSize(18);
            ssidView.setTypeface(null, Typeface.BOLD);
            ssidView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ssidParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ssidParams.topMargin = (int) (12 * density);
            qrBox.addView(ssidView, ssidParams);
            TextView secView = new TextView(this);
            secView.setText(e.security + (e.password.isEmpty() ? "" : " : " + e.password));
            secView.setTextSize(14);
            secView.setGravity(Gravity.CENTER);
            secView.setTextIsSelectable(true);
            qrBox.addView(secView);
            new MaterialAlertDialogBuilder(this).setTitle("Share Wi-Fi").setView(qrBox)
                    .setPositiveButton("Share", (d, w) -> shareWifiText(config))
                    .setNeutralButton("Save image", (d, w) -> new Thread(() -> {
                        try {
                            QrUtil.saveToGallery(WifiManagerActivity.this, qr, e.ssid + "_wifi_qr");
                            handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, "QR image saved"));
                        } catch (Exception ex) {
                            handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, "Save failed"));
                        }
                    }).start())
                    .setNegativeButton(android.R.string.cancel, null).show();
        } catch (Exception ex) {
            Extensions.showMessage(this, "QR failed");
        }
    }

    private void exportEntries(List<WifiPasswordUtil.WifiEntry> entries) {
        if (entries.isEmpty()) {
            Extensions.showMessage(this, "Nothing to export");
            return;
        }
        new Thread(() -> {
            try {
                StringBuilder all = new StringBuilder();
                for (WifiPasswordUtil.WifiEntry e : entries) {
                    all.append("SSID: ").append(e.ssid).append('\n');
                    all.append("Security: ").append(e.security).append('\n');
                    all.append("Password: ").append(passOrOpen(e)).append("\n\n");
                }
                String fileName = "wifi_passwords.txt";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/MP Manager");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Cannot create file");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new Exception("Cannot open file");
                        os.write(all.toString().getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MP Manager");
                    if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new Exception("Cannot create folder");
                    File out = new File(dir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(all.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, "Exported to Download/MP Manager"));
            } catch (Exception ex) {
                handler.post(() -> Extensions.showMessage(WifiManagerActivity.this, "Export failed"));
            }
        }).start();
    }

    private String masked(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.max(8, s.length()); i++) b.append('*');
        return b.toString();
    }

    private void filterPass(String q) {
        String query = q == null ? "" : q.trim().toLowerCase();
        passVisible.clear();
        if (query.isEmpty()) {
            passVisible.addAll(passAll);
        } else {
            for (WifiPasswordUtil.WifiEntry e : passAll) {
                if (e.ssid.toLowerCase().contains(query) || e.security.toLowerCase().contains(query)) passVisible.add(e);
            }
        }
        passAdapter.notifyDataSetChanged();
        passCount.setText(passVisible.size() + " networks");
    }

    private void loadPasswords() {
        new Thread(() -> {
            boolean rooted = WifiPasswordUtil.isRooted(WifiManagerActivity.this);
            List<WifiPasswordUtil.WifiEntry> entries = rooted ? WifiPasswordUtil.loadWifiPasswords(WifiManagerActivity.this) : new ArrayList<>();
            handler.post(() -> {
                passAll.clear();
                passAll.addAll(entries);
                passVisible.clear();
                passVisible.addAll(entries);
                passAdapter.notifyDataSetChanged();
                passCount.setText(rooted ? entries.size() + " networks" : "Root needed for saved passwords");
            });
        }).start();
    }

    private void buildUsage(LinearLayout box) {
        sectionTitle(box, "Data Usage");
        usageText = bodyText(box);
        usageText.setText("Loading");
        MaterialButton refresh = button(box, "Refresh usage");
        refresh.setOnClickListener(v -> refreshUsage());
    }

    private void refreshUsage() {
        new Thread(() -> {
            StringBuilder b = new StringBuilder();
            try {
                b.append("Mobile down ").append(fmt(TrafficStats.getMobileRxBytes())).append("\n");
                b.append("Mobile up ").append(fmt(TrafficStats.getMobileTxBytes())).append("\n");
                b.append("Total down ").append(fmt(TrafficStats.getTotalRxBytes())).append("\n");
                b.append("Total up ").append(fmt(TrafficStats.getTotalTxBytes())).append("\n");
                int uid = Process.myUid();
                b.append("This app down ").append(fmt(TrafficStats.getUidRxBytes(uid))).append("\n");
                b.append("This app up ").append(fmt(TrafficStats.getUidTxBytes(uid)));
            } catch (Exception e) {
                b.append("Unavailable");
            }
            String out = b.toString();
            handler.post(() -> usageText.setText(out));
        }).start();
    }

    private String fmt(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return new DecimalFormat("0.0").format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return new DecimalFormat("0.0").format(mb) + " MB";
        double gb = mb / 1024.0;
        if (gb < 1024) return new DecimalFormat("0.00").format(gb) + " GB";
        return new DecimalFormat("0.00").format(gb / 1024.0) + " TB";
    }

    private void copyPlain(String label, String value) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            Extensions.showMessage(this, "Copied");
        } catch (Exception e) {
            Extensions.showMessage(this, "Copy failed");
        }
    }
}
