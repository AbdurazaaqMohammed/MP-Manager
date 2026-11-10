package io.github.abdurazaaqmohammed.features.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;

public class MainSettingsActivity extends BaseActivity {

    /** One layout per bottom-navigation tab, in menu order. */
    private static final int[] PAGE_LAYOUTS = {
            R.layout.settings_page_files,
            R.layout.settings_page_root,
            R.layout.settings_page_general,
            R.layout.settings_page_behavior,
            R.layout.settings_page_other,
    };

    /** Tab index shown on open (General, the middle tab). */
    private static final int DEFAULT_TAB = 2;

    private SettingsController controller;
    private ViewPager2 pager;
    private BottomNavigationView nav;
    private MultiRootView viewFinder;
    private final List<View> pages = new ArrayList<>();
    private boolean syncingFromPager;

    private Consumer<ActivityResult> pendingExternalSetting;
    private final ActivityResultLauncher<Intent> externalSettingLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Consumer<ActivityResult> cb = pendingExternalSetting;
                pendingExternalSetting = null;
                if (cb != null) {
                    try {
                        cb.accept(result);
                    } catch (Exception ignored) {
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        pager = findViewById(R.id.settingsPager);
        LayoutInflater inflater = getLayoutInflater();
        for (int layout : PAGE_LAYOUTS) {
            pages.add(inflater.inflate(layout, pager, false));
        }
        pager.setAdapter(new PagesAdapter());
        // Each page is one statically wired view, so no page may ever be
        // recycled and rebound: keep every page attached.
        pager.setOffscreenPageLimit(PAGE_LAYOUTS.length - 1);

        nav = findViewById(R.id.settingsBottomNav);
        nav.setOnItemSelectedListener(item -> {
            if (syncingFromPager) return true;
            for (int i = 0; i < nav.getMenu().size(); i++) {
                if (nav.getMenu().getItem(i).getItemId() == item.getItemId()) {
                    pager.setCurrentItem(i, true);
                    break;
                }
            }
            return true;
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position < 0 || position >= nav.getMenu().size()) return;
                syncingFromPager = true;
                nav.getMenu().getItem(position).setChecked(true);
                syncingFromPager = false;
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // "_v2": the tab order changed once, so old saved indices are stale.
        int last = prefs.getInt("main_settings_tab_v2", DEFAULT_TAB);
        pager.setCurrentItem(Math.max(0, Math.min(last, PAGE_LAYOUTS.length - 1)), false);

        MainActivity host = resolveHost();
        controller = new SettingsController(this, host, this::launchExternalSetting);
        controller.attach(viewFinder = new MultiRootView(pages));
    }

    /**
     * Looks views up across every page, since the sections live inside the
     * ViewPager2 instead of one inflated layout.
     */
    private static final class MultiRootView implements SettingsController.ViewFinder {
        private final List<View> pages;

        MultiRootView(List<View> pages) {
            this.pages = pages;
        }

        @Nullable
        @Override
        public <T extends View> T findViewById(int id) {
            for (View page : pages) {
                T v = page.findViewById(id);
                if (v != null) return v;
            }
            return null;
        }
    }

    private final class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.PageHolder> {

        /**
         * The static page for each position. onCreateViewHolder receives a
         * view type, not a position — without this override every holder
         * would be handed page 0's already-attached view.
         */
        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PageHolder(pages.get(viewType));
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        final class PageHolder extends RecyclerView.ViewHolder {
            PageHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    private MainActivity resolveHost() {
        MainActivity host = MainActivity.current();
        return host != null && !host.isFinishing() && !host.isDestroyed() ? host : null;
    }

    private void launchExternalSetting(Intent intent, Consumer<ActivityResult> callback) {
        try {
            pendingExternalSetting = callback;
            externalSettingLauncher.launch(intent);
        } catch (Exception ignored) {
            pendingExternalSetting = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (controller != null && viewFinder != null) {
            controller.persist(viewFinder);
        }
        if (pager != null) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putInt("main_settings_tab_v2", pager.getCurrentItem()).apply();
        }
    }
}
