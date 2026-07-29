package io.github.abdurazaaqmohammed.adapters;

import android.graphics.Color;
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
            switch(position) {
                case 0:
                    ic = R.drawable.baseline_content_copy_24;
                   break;
                case 1:
                    if(context.pane1Folder == context.pane2Folder) {

                        iconView.setEnabled(false);
                        tv.setEnabled(false);
                    }
                    ic = R.drawable.baseline_content_cut_24;
                    break;
                case 2:
                    ic = R.drawable.baseline_drive_file_rename_outline_24;
                    break;
                case 3:
                    ic = R.drawable.baseline_delete_24;
                    break;
                case 4:
                    if(((MainFilesArrayAdapter) context.getCurrentPane().getAdapter()).isInZip) {
                        iconView.setEnabled(false);
                        tv.setEnabled(false);
                    }
                    ic = R.drawable.baseline_compress_24;
                    break;
                case 5:
                    ic = R.drawable.baseline_info_24;
                    break;
                case 6:
                    ic = R.drawable.baseline_share_24;
                    break;
                case 7:
                    ic = R.drawable.baseline_open_in_new_24;
                    break;
                case 8:
                    if(((MainFilesArrayAdapter) context.getCurrentPane().getAdapter()).isInZip) {
                        iconView.setEnabled(false);
                        tv.setEnabled(false);
                    }
                    ic = android.R.drawable.ic_input_get;
                    break;
                case 9:
                    ic = R.drawable.terminal_24px;
                    break;
                case 10:
                case 11:
                case 12:
                    ic = R.drawable.baseline_swap_horiz_24;
                    break;
                default:
                    ic = 0;
                    break;
            }
            iconView.setImageResource(ic);
            iconView.setPadding(0,0,10,0);
            ColorUtil.changeImageColor(iconView.getDrawable(), Color.WHITE);
            iconView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            linearLayout.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(iconView);
            linearLayout.addView(tv);
            return linearLayout;
        }
        return convertView;
    }
}