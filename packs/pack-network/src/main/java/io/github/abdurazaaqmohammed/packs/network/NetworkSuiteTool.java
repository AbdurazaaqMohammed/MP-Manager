package io.github.abdurazaaqmohammed.packs.network;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;

import java.util.ArrayList;
import java.util.List;

public class NetworkSuiteTool extends BaseToolPlugin {

    private static final int TAB_HUB = 1;
    private static final int TAB_BLUETOOTH = 2;
    private static final int TAB_NFC = 3;
    private static final int TAB_QR = 4;

    private PagedShell shell;
    private BottomNavigationView bottomNav;
    private int currentTab;
    private BluetoothTool bluetooth;
    private NfcTool nfc;
    private QrScanTool qrScan;
    private QrGenTool qrGen;

    public NetworkSuiteTool() {
        super("network", "Network Tools", "Connectivity hub, radios, QR tools", ToolCategories.NETWORK);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        bottomNav = new BottomNavigationView(context);
        Menu menu = bottomNav.getMenu();
        navItem(context, menu, TAB_HUB, "Hub", new String[]{"baseline_wifi_24", "wifi_24px"}, android.R.drawable.ic_menu_view);
        navItem(context, menu, TAB_BLUETOOTH, "Bluetooth", new String[]{"bluetooth_24", "bluetooth_24px"}, android.R.drawable.ic_menu_slideshow);
        navItem(context, menu, TAB_NFC, "NFC", new String[]{"nfc_24px", "baseline_nfc_settings_24"}, android.R.drawable.ic_menu_myplaces);
        navItem(context, menu, TAB_QR, "QR", new String[]{"qr_code_scanner_24", "qr_code_scanner_24px"}, android.R.drawable.ic_menu_camera);
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);

        List<PagedShell.Page> pages = new ArrayList<>();
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Hub";
            }

            @Override
            public View build(Context ctx) {
                View v = new ConnectivityTool().createView(ctx, null);
                return wrap(ctx, v);
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "Bluetooth";
            }

            @Override
            public View build(Context ctx) {
                bluetooth = new BluetoothTool();
                return wrap(ctx, bluetooth.createView(ctx, null));
            }

            @Override
            public void destroy() {
                try {
                    if (bluetooth != null) bluetooth.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "NFC";
            }

            @Override
            public View build(Context ctx) {
                nfc = new NfcTool();
                return wrap(ctx, nfc.createView(ctx, null));
            }

            @Override
            public void destroy() {
                try {
                    if (nfc != null) nfc.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return "QR";
            }

            @Override
            public View build(Context ctx) {
                qrGen = new QrGenTool();
                qrScan = new QrScanTool();
                return buildQrPage(ctx);
            }

            @Override
            public void destroy() {
                try {
                    if (qrGen != null) qrGen.onDestroy();
                    if (qrScan != null) qrScan.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        shell = new PagedShell(context, pages, null, bottomNav, currentTab);
        bottomNav.setSelectedItemId(currentTab == 1 ? TAB_BLUETOOTH : currentTab == 2 ? TAB_NFC : currentTab == 3 ? TAB_QR : TAB_HUB);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            shell.select(id == TAB_BLUETOOTH ? 1 : id == TAB_NFC ? 2 : id == TAB_QR ? 3 : 0);
            return true;
        });
        shell.onSelect(position -> {
            currentTab = position;
            int id = position == 1 ? TAB_BLUETOOTH : position == 2 ? TAB_NFC : position == 3 ? TAB_QR : TAB_HUB;
            if (bottomNav.getSelectedItemId() != id) bottomNav.setSelectedItemId(id);
        });
        return shell.view();
    }

    private static View wrap(Context context, View v) {
        ScrollView sc = new ScrollView(context);
        sc.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private View buildQrPage(Context context) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout segRow = new LinearLayout(context);
        segRow.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton genBtn = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        genBtn.setText(PackRes.str("network", R.string.s_generate, "Generate"));
        MaterialButton scanBtn = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        scanBtn.setText(PackRes.str("network", R.string.s_scan, "Scan"));
        segRow.addView(genBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        segRow.addView(scanBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(segRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout qrHolder = new FrameLayout(context);
        page.addView(qrHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final ScrollView genWrap = new ScrollView(context);
        View genView = qrGen.createView(context, qrHolder);
        if (genView != null) {
            genWrap.addView(genView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        qrHolder.addView(genWrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ScrollView scanWrap = new ScrollView(context);
        View scanView = qrScan.createView(context, qrHolder);
        if (scanView != null) {
            scanWrap.addView(scanView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scanWrap.setVisibility(View.GONE);
        qrHolder.addView(scanWrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable showGen = () -> {
            genWrap.setVisibility(View.VISIBLE);
            scanWrap.setVisibility(View.GONE);
            stylePair(genBtn, scanBtn, 0);
        };
        final Runnable showScan = () -> {
            genWrap.setVisibility(View.GONE);
            scanWrap.setVisibility(View.VISIBLE);
            stylePair(genBtn, scanBtn, 1);
        };
        genBtn.setOnClickListener(v -> showGen.run());
        scanBtn.setOnClickListener(v -> showScan.run());
        showGen.run();
        return page;
    }

    private static void stylePair(MaterialButton a, MaterialButton b, int selected) {
        int primary = MaterialColors.getColor(a.getContext(),
                com.google.android.material.R.attr.colorPrimary, 0xFF1B73E8);
        int surface = MaterialColors.getColor(a.getContext(),
                com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFEEEEEE);
        a.setBackgroundTintList(ColorStateList.valueOf(selected == 0 ? primary : surface));
        a.setTextColor(selected == 0 ? Color.WHITE : MaterialColors.getColor(a.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        b.setBackgroundTintList(ColorStateList.valueOf(selected == 1 ? primary : surface));
        b.setTextColor(selected == 1 ? Color.WHITE : MaterialColors.getColor(a.getContext(), com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
    }

    private static void navItem(Context context, Menu menu, int id, String title,
                                String[] candidates, int fallback) {
        MenuItem item = menu.add(0, id, id, title);
        int res = 0;
        try {
            for (String name : candidates) {
                res = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (res != 0) break;
            }
        } catch (Exception ignored) {
        }
        try {
            item.setIcon(ContextCompat.getDrawable(context, res != 0 ? res : fallback));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        try {
            if (nfc != null) nfc.onNewIntent(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (qrScan != null) qrScan.onActivityResult(requestCode, resultCode, data);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (shell != null) shell.destroy();
        } catch (Exception ignored) {
        }
        shell = null;
    }
}
