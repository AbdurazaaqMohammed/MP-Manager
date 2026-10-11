package io.github.abdurazaaqmohammed.packs.network;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;

import java.util.ArrayList;
import java.util.List;

public class NetworkSuiteTool extends BaseToolPlugin {

    private static final int TAB_HUB = 1;
    private static final int TAB_QR = 2;

    private PagedShell shell;
    private BottomNavigationView bottomNav;
    private int currentTab;
    private QrGenTool qrGen;

    public NetworkSuiteTool() {
        super("network", "Network Tools", "Connectivity hub and QR generator", ToolCategories.NETWORK);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        bottomNav = new BottomNavigationView(context);
        Menu menu = bottomNav.getMenu();
        navItem(context, menu, TAB_HUB, "Hub", new String[]{"baseline_wifi_24", "wifi_24px"}, android.R.drawable.ic_menu_view);
        navItem(context, menu, TAB_QR, "QR", new String[]{"qr_code_24", "qr_code_2_24px"}, android.R.drawable.ic_menu_camera);
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
                return "QR";
            }

            @Override
            public View build(Context ctx) {
                qrGen = new QrGenTool();
                View genView = qrGen.createView(ctx, null);
                return wrap(ctx, genView);
            }

            @Override
            public void destroy() {
                try {
                    if (qrGen != null) qrGen.onDestroy();
                } catch (Exception ignored) {
                }
            }
        });
        shell = new PagedShell(context, pages, null, bottomNav, currentTab);
        bottomNav.setSelectedItemId(currentTab == 1 ? TAB_QR : TAB_HUB);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            shell.select(id == TAB_QR ? 1 : 0);
            return true;
        });
        shell.onSelect(position -> {
            currentTab = position;
            int id = position == 1 ? TAB_QR : TAB_HUB;
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
    public void onDestroy() {
        try {
            if (shell != null) shell.destroy();
        } catch (Exception ignored) {
        }
        shell = null;
    }
}
