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

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;

public class DialogAdapter extends ArrayAdapter<String> {

    private final MainActivity context;
    private final String[] values;

    public DialogAdapter(MainActivity context, String[] values) {
        super(context,0, values);
        this.context = context;
        this.values = values;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);

            TextView tv = new TextView(context);
            tv.setText(values[position]);
            tv.setTextSize(20);

            ImageView iconView = new ImageView(context);
            int ic;
            int size = 72;
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
            iconView.setImageResource(ic);
            iconView.setPadding(0,0,10,0);
            Drawable drawable = iconView.getDrawable();
            if(drawable != null) {
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
}