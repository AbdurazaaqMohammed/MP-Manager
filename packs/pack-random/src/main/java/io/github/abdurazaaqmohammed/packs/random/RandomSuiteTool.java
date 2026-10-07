package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.navigation.NavigationBarView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;

public class RandomSuiteTool extends BaseToolPlugin {

    private static final int NAV_ROLL = 1;
    private static final int NAV_PASSWORDS = 2;
    private static final int NAV_IDS = 3;

    private static final String[] ROLL = {"random", "pick"};
    private static final String[] PASSWORDS = {"password", "strength"};
    private static final String[] IDS = {"uuid", "token"};

    private static final String[] GROUP_NAMES = {"Roll", "Passwords", "IDs"};

    private static String[][] groupIds() {
        return new String[][]{ROLL, PASSWORDS, IDS};
    }

    private Context host;
    private final FrameLayout[] groupHolders = new FrameLayout[]{null, null, null};
    private PagedShell shell;
    private final List<ToolPlugin> hosted = new ArrayList<>();

    public RandomSuiteTool() {
        super("random", "Random Tools", "Randomizer, passwords, UUIDs, tokens", ToolCategories.RAND);
    }

    private static ToolPlugin newTool(String id) {
        switch (id) {
            case "random":
                return new RandomTool();
            case "pick":
                return new PickListTool();
            case "password":
                return new PasswordTool();
            case "strength":
                return new StrengthTool();
            case "uuid":
                return new UuidTool();
            case "token":
                return new TokenTool();
            default:
                return null;
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        host = context;
        BottomNavigationView nav = new BottomNavigationView(context);
        Menu menu = nav.getMenu();
        navItem(context, menu, NAV_ROLL, "Roll", new String[]{"shuffle_24px"}, android.R.drawable.ic_menu_sort_by_size);
        navItem(context, menu, NAV_PASSWORDS, "Passwords", new String[]{"lock_24px"}, android.R.drawable.ic_lock_lock);
        navItem(context, menu, NAV_IDS, "IDs", new String[]{"tag_24px"}, android.R.drawable.ic_menu_my_calendar);
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setSelectedItemId(NAV_ROLL);
        List<PagedShell.Page> pages = new ArrayList<>();
        for (int g = 0; g < 3; g++) {
            final int group = g;
            pages.add(new PagedShell.Page() {
                @Override
                public String title() {
                    return GROUP_NAMES[group];
                }

                @Override
                public View build(Context ctx) {
                    if (groupHolders[group] == null) {
                        groupHolders[group] = new FrameLayout(ctx);
                    }
                    return groupHolders[group];
                }

                @Override
                public void shown() {
                    try {
                        if (groupHolders[group] != null && groupHolders[group].getChildCount() == 0) {
                            showMenu(group);
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void hidden() {
                    try {
                        destroyHosted();
                        if (groupHolders[group] != null) groupHolders[group].removeAllViews();
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void destroy() {
                    try {
                        destroyHosted();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        shell = new PagedShell(context, pages, null, nav, 0);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showTab(id == NAV_PASSWORDS ? 1 : id == NAV_IDS ? 2 : 0);
            return true;
        });
        shell.onSelect(position -> {
            try {
                int id = position == 1 ? NAV_PASSWORDS : position == 2 ? NAV_IDS : NAV_ROLL;
                if (nav.getSelectedItemId() != id) nav.setSelectedItemId(id);
            } catch (Exception ignored) {
            }
        });
        return shell.view();
    }

    private void showTab(int index) {
        try {
            if (shell != null) shell.select(index);
        } catch (Exception ignored) {
        }
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

    private void destroyHosted() {
        for (ToolPlugin p : hosted) {
            try {
                p.onDestroy();
            } catch (Exception ignored) {
            }
        }
        hosted.clear();
    }

    private void showMenu(int group) {
        String[] ids = groupIds()[group];
        String name = GROUP_NAMES[group];
        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        ToolViewFactory.addLabel(page, name + " tools");
        for (String id : ids) {
            ToolPlugin tool = newTool(id);
            if (tool == null) continue;
            String title;
            String sub;
            try {
                title = tool.title(host);
                sub = tool.subtitle(host);
            } catch (Exception ignored) {
                continue;
            }
            MaterialCardView card = new MaterialCardView(host);
            card.setRadius(ToolViewFactory.dp(host, 14));
            card.setCardElevation(ToolViewFactory.dp(host, 1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = ToolViewFactory.dp(host, 5);
            cp.setMargins(0, m, 0, m);
            card.setLayoutParams(cp);
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout row = new LinearLayout(host);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int p = ToolViewFactory.dp(host, 12);
            row.setPadding(p, p, p, p);
            LinearLayout texts = new LinearLayout(host);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = new TextView(host);
            t.setText(title);
            t.setTextSize(15);
            t.setTextColor(MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
            texts.addView(t);
            TextView s = new TextView(host);
            s.setText(sub == null ? "" : sub);
            s.setTextSize(12);
            s.setAlpha(0.7f);
            s.setTextColor(MaterialColors.getColor(host,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888));
            texts.addView(s);
            row.addView(texts);
            TextView chev = new TextView(host);
            chev.setText(PackRes.str("random", R.string.s_x_2, "›"));
            chev.setTextSize(24);
            chev.setAlpha(0.5f);
            row.addView(chev);
            card.addView(row);
            final ToolPlugin open = tool;
            final int groupIndex = group;
            card.setOnClickListener(v -> showTool(groupIndex, open));
            page.addView(card);
        }
        ScrollView sc = new ScrollView(host);
        sc.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        groupHolders[group].removeAllViews();
        groupHolders[group].addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showTool(int group, ToolPlugin tool) {
        LinearLayout page = new LinearLayout(host);
        page.setOrientation(LinearLayout.VERTICAL);
        MaterialButton back = new MaterialButton(host, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        back.setText("‹ " + GROUP_NAMES[group]);
        back.setOnClickListener(v -> {
            destroyHosted();
            showMenu(group);
        });
        page.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            View body = tool.createView(host, page);
            if (body != null && body.getParent() == null) page.addView(body);
            hosted.add(tool);
        } catch (Exception e) {
            TextView err = new TextView(host);
            err.setText(PackRes.str("random", R.string.s_could_not_open_tool, "Could not open tool"));
            page.addView(err);
        }
        ScrollView sc = new ScrollView(host);
        sc.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        groupHolders[group].removeAllViews();
        groupHolders[group].addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}

