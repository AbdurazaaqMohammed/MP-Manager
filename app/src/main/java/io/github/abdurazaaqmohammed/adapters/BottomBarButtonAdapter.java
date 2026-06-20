package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.abdurazaaqmohammed.MPManager.R;

public class BottomBarButtonAdapter extends BaseAdapter {
    private final Context context;
    private final JSONArray buttons;
    private final OnButtonActionListener listener;

    public interface OnButtonActionListener {
        void onEdit(int position, JSONObject button);

        void onDelete(int position);
    }

    public BottomBarButtonAdapter(Context context, JSONArray buttons, OnButtonActionListener listener) {
        this.context = context;
        this.buttons = buttons;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return buttons.length();
    }

    @Override
    public Object getItem(int position) {
        return buttons.optJSONObject(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_bottom_bar_config, parent, false);
        }
        JSONObject obj = (JSONObject) getItem(position);
        TextView textLabel = convertView.findViewById(R.id.text_label);
        TextView textAction = convertView.findViewById(R.id.text_action);
        ImageButton btnEdit = convertView.findViewById(R.id.btn_edit);
        ImageButton btnDelete = convertView.findViewById(R.id.btn_delete);
        String action = obj.optString("action");
        String label = obj.optString("label", action);
        textLabel.setText(label);
        textAction.setText(action);
        btnEdit.setOnClickListener(v -> listener.onEdit(position, obj));
        btnDelete.setOnClickListener(v -> listener.onDelete(position));
        return convertView;
    }
}
