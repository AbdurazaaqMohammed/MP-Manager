package io.github.abdurazaaqmohammed.adapters;

import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.utils.ColorUtil;

public class DialogAdapter extends ArrayAdapter<String> {

    private final MainActivity context;
    private final List<FileMenuOrder.MenuItem> items;
    private final boolean isInZip;

    public DialogAdapter(MainActivity context, String[] values) {
        super(context, 0, values);
        this.context = context;
        this.items = null;
        this.isInZip = false;
    }

    public DialogAdapter(MainActivity context, List<FileMenuOrder.MenuItem> items, boolean isInZip) {
        super(context, 0, labelsOf(items));
        this.context = context;
        this.items = items;
        this.isInZip = isInZip;
    }

    private static String[] labelsOf(List<FileMenuOrder.MenuItem> items) {
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).label;
        return labels;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);

            TextView tv = new TextView(context);
            tv.setText(getItem(position));
            tv.setTextSize(20);

            ImageView iconView = new ImageView(context);
            int ic;
            int size = 72;
            if (items != null) {
                String id = items.get(position).id;
                if (FileMenuOrder.MOVE.equals(id) && context.pane1Folder == context.pane2Folder) {
                    iconView.setEnabled(false);
                    tv.setEnabled(false);
                }
                if ((FileMenuOrder.COMPRESS.equals(id) || FileMenuOrder.BOOKMARK.equals(id)) && isInZip) {
                    iconView.setEnabled(false);
                    tv.setEnabled(false);
                }
                ic = FileMenuOrder.iconFor(context, id, false, false);
            } else {
                ic = legacyIcon(position, tv, iconView);
            }
            iconView.setImageResource(ic);
            iconView.setPadding(0, 0, 10, 0);
            Drawable drawable = iconView.getDrawable();
            if (drawable != null) {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                ColorUtil.changeImageColor(drawable, typedValue.data);
            }
            iconView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            linearLayout.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(iconView);
            linearLayout.addView(tv);
            return linearLayout;
        }
        return convertView;
    }

    private int legacyIcon(int position, TextView tv, ImageView iconView) {
        int ic;
        ic = switch (position) {
            case 0 -> R.drawable.baseline_content_copy_24;
            case 1 -> {
                if (context.pane1Folder == context.pane2Folder) {
                    iconView.setEnabled(false);
                    tv.setEnabled(false);
                }
                yield R.drawable.baseline_content_cut_24;
            }
            case 2 -> R.drawable.baseline_drive_file_rename_outline_24;
            case 3 -> R.drawable.baseline_delete_24;
            case 4 -> {
                if (((MainFilesArrayAdapter) context.getCurrentPane().getAdapter()).isInZip) {
                    iconView.setEnabled(false);
                    tv.setEnabled(false);
                }
                yield R.drawable.baseline_compress_24;
            }
            case 5 -> R.drawable.baseline_info_24;
            case 6 -> R.drawable.baseline_share_24;
            case 7 -> R.drawable.baseline_open_in_new_24;
            case 8 -> {
                if (((MainFilesArrayAdapter) context.getCurrentPane().getAdapter()).isInZip) {
                    iconView.setEnabled(false);
                    tv.setEnabled(false);
                }
                yield android.R.drawable.ic_input_get;
            }
            case 9 -> R.drawable.terminal_24px;
            case 10 -> R.drawable.tag_24px;
            case 11, 12 -> R.drawable.apk_document_24px;
            case 13, 14 -> R.drawable.baseline_swap_horiz_24;
            default -> 0;
        };
        return ic;
    }
}
