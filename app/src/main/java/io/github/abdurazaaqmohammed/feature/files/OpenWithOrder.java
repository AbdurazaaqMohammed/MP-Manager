package io.github.abdurazaaqmohammed.feature.files;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.ui.common.util.ProgressiveEdgeScroller;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Registry, persisted display order, and hidden state for the built-in
 * "Open with" grid options. Reuses {@code R.layout.item_dropdown_option}
 * with drag-to-reorder and eye hide/show controls matching {@code FileMenuCustomizer}.
 */
public final class OpenWithOrder {

    public static final String BUILTIN_TEXT = "text";
    public static final String BUILTIN_ARCHIVE = "archive";
    public static final String BUILTIN_IMAGE = "image";
    public static final String BUILTIN_IMAGE_EDITOR = "image_editor";
    public static final String BUILTIN_HEX = "hex";
    public static final String BUILTIN_MEDIA = "media";
    public static final String BUILTIN_APK = "apk";
    public static final String BUILTIN_DEX = "dex";
    public static final String BUILTIN_FONT = "font";
    public static final String BUILTIN_PDF = "pdf";
    public static final String BUILTIN_HTML = "html";
    public static final String BUILTIN_ARSC = "arsc";
    public static final String BUILTIN_XML = "xml";
    public static final String BUILTIN_SQLITE = "sqlite";
    public static final String BUILTIN_ARM64 = "arm64";
    public static final String BUILTIN_BOOT = "boot";
    public static final String BUILTIN_DRAWABLE = "drawable_preview";
    public static final String BUILTIN_SIG = "sig";

    private static final String PREF_FILE = "open_with_prefs";
    private static final String KEY_ORDER = "open_with_order";
    private static final String KEY_HIDDEN = "open_with_hidden";

    public static final class Option {
        public final String id;
        public final int labelRes;
        public final int iconRes;

        public Option(String id, int labelRes, int iconRes) {
            this.id = id;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }
    }

    private static final List<Option> ALL_OPTIONS = Arrays.asList(
            new Option(BUILTIN_TEXT, R.string.text_editor, R.drawable.baseline_text_snippet_24),
            new Option(BUILTIN_ARCHIVE, R.string.archive_viewer, R.drawable.baseline_folder_zip_24),
            new Option(BUILTIN_IMAGE, R.string.image_viewer, R.drawable.image_24px),
            new Option(BUILTIN_IMAGE_EDITOR, R.string.image_editor, R.drawable.edit_24px),
            new Option(BUILTIN_HEX, R.string.hex_editor, R.drawable.ic_hash_mt),
            new Option(BUILTIN_MEDIA, R.string.media_player, R.drawable.video_24px),
            new Option(BUILTIN_APK, R.string.apk_info, R.drawable.apk_document_24px),
            new Option(BUILTIN_DEX, R.string.dex_editor, R.drawable.code_24px),
            new Option(BUILTIN_FONT, R.string.font_preview, R.drawable.uppercase_24px),
            new Option(BUILTIN_PDF, R.string.pdf_viewer, R.drawable.pdf_24px),
            new Option(BUILTIN_HTML, R.string.html_viewer, R.drawable.language_24px),
            new Option(BUILTIN_ARSC, R.string.arsc_functions, R.drawable.apk_document_24px),
            new Option(BUILTIN_XML, R.string.xml_functions, R.drawable.code_24px),
            new Option(BUILTIN_SQLITE, R.string.sqlite_viewer, R.drawable.ic_disk_drive_24px),
            new Option(BUILTIN_ARM64, R.string.arm64_disassembler, R.drawable.code_24px),
            new Option(BUILTIN_BOOT, R.string.boot_image_inspector, R.drawable.ic_disk_drive_24px),
            new Option(BUILTIN_DRAWABLE, R.string.drawable_preview, R.drawable.image_24px),
            new Option(BUILTIN_SIG, R.string.import_signature, R.drawable.lock_24px)
    );

    private static final Map<String, Option> OPTION_MAP = new LinkedHashMap<>();
    static {
        for (Option opt : ALL_OPTIONS) {
            OPTION_MAP.put(opt.id, opt);
        }
    }

    private OpenWithOrder() {}

    public static List<Option> loadAllOrdered(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_ORDER, "");
        List<Option> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (saved != null && !saved.isEmpty()) {
            for (String rawId : saved.split(",")) {
                String id = rawId.trim();
                Option opt = OPTION_MAP.get(id);
                if (opt != null && seen.add(id)) {
                    ordered.add(opt);
                }
            }
        }
        for (Option opt : ALL_OPTIONS) {
            if (seen.add(opt.id)) {
                ordered.add(opt);
            }
        }
        return ordered;
    }

    public static Set<String> getHiddenIds(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_HIDDEN, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    public static List<Option> visibleOptions(Context context) {
        List<Option> all = loadAllOrdered(context);
        Set<String> hidden = getHiddenIds(context);
        if (hidden.isEmpty()) return all;
        List<Option> visible = new ArrayList<>();
        for (Option opt : all) {
            if (!hidden.contains(opt.id)) visible.add(opt);
        }
        return visible.isEmpty() ? all : visible;
    }

    private static void saveOrder(Context context, List<Option> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(items.get(i).id);
        }
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY_ORDER, sb.toString()).apply();
    }

    private static void saveHiddenIds(Context context, Set<String> hidden) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    private static void resetDefaults(Context context) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
                .remove(KEY_ORDER).remove(KEY_HIDDEN).apply();
    }

    public static void showCustomizer(Activity context, @Nullable Runnable onDismiss) {
        DialogUtil dialogUtil = new DialogUtil(context);
        List<Option> items = new ArrayList<>(loadAllOrdered(context));
        Set<String> hidden = new HashSet<>(getHiddenIds(context));

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        int pad = (int) (4 * context.getResources().getDisplayMetrics().density + 0.5f);
        list.setPadding(0, pad, 0, pad);
        list.setClipToPadding(false);

        OrderAdapter adapter = new OrderAdapter(context, items, hidden);
        list.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                if (a < 0 || b < 0) return false;
                Collections.swap(items, a, b);
                adapter.notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView, int viewSize,
                                                    int viewSizeOutOfBounds, int totalSize, long msSinceStartScroll) {
                return ProgressiveEdgeScroller.interpolateOutOfBoundsScroll(
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
            }
        });
        helper.attachToRecyclerView(list);
        adapter.setTouchHelper(helper);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.customize_open_with))
                .setMessage(context.getString(R.string.customize_open_with_hint))
                .setView(list)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    resetDefaults(context);
                    hidden.clear();
                    items.clear();
                    items.addAll(ALL_OPTIONS);
                    adapter.notifyDataSetChanged();
                    Extensions.showMessage(context, R.string.menu_order_reset);
                })
                .create();
        dialog.setOnDismissListener(d -> {
            saveOrder(context, items);
            saveHiddenIds(context, hidden);
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        dialogUtil.styleAlertDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.95f), (int) (dm.heightPixels * 0.8f));
        }
    }

    private static final class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.Holder> {
        private final Activity context;
        private final List<Option> items;
        private final Set<String> hidden;
        private ItemTouchHelper touchHelper;

        OrderAdapter(Activity context, List<Option> items, Set<String> hidden) {
            this.context = context;
            this.items = items;
            this.hidden = hidden;
        }

        void setTouchHelper(ItemTouchHelper touchHelper) {
            this.touchHelper = touchHelper;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_dropdown_option, parent, false);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Option opt = items.get(position);
            holder.text.setText(context.getString(opt.labelRes));
            boolean isHidden = hidden.contains(opt.id);

            holder.icon.setImageResource(isHidden ? R.drawable.visibility_off_24px : opt.iconRes);
            holder.icon.setAlpha(isHidden ? 0.45f : 1f);
            holder.text.setAlpha(isHidden ? 0.45f : 1f);
            holder.itemView.setAlpha(isHidden ? 0.6f : 1f);

            holder.hideButton.setVisibility(View.VISIBLE);
            holder.hideButton.setImageResource(isHidden ? R.drawable.visibility_off_24px : R.drawable.ic_eye_mt);
            holder.hideButton.setContentDescription(context.getString(
                    isHidden ? R.string.restore_sidebar_item : R.string.hide));
            holder.hideButton.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos < 0) return;
                Option cur = items.get(pos);
                if (hidden.contains(cur.id)) hidden.remove(cur.id);
                else hidden.add(cur.id);
                saveHiddenIds(context, hidden);
                notifyItemChanged(pos);
            });

            holder.dragHandle.setVisibility(View.VISIBLE);
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView text;
            final ImageButton hideButton;
            final ImageView dragHandle;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.optionIcon);
                text = itemView.findViewById(R.id.optionText);
                hideButton = itemView.findViewById(R.id.optionHideButton);
                dragHandle = itemView.findViewById(R.id.optionDragHandle);
            }
        }
    }
}
