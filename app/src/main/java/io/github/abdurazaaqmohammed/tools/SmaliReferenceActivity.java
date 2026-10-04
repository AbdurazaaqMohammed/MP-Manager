package io.github.abdurazaaqmohammed.tools;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.SmaliReference;

/**
 * Offline Smali instruction lookup, reachable from the sidebar.
 *
 * <p>Useful while reading decompiled output without leaving the file manager to
 * check the Dalvik spec; searching matches the mnemonic, the operand form, the
 * group and the description, in Chinese or English.
 */
public class SmaliReferenceActivity extends BaseActivity {

    private final List<SmaliReference.Op> shown = new ArrayList<>();
    private Adapter adapter;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smali_reference);

        MaterialToolbar toolbar = findViewById(R.id.smali_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView list = findViewById(R.id.smali_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        countView = findViewById(R.id.smali_count);
        EditText search = findViewById(R.id.smali_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter(s == null ? "" : s.toString());
            }
        });

        filter("");
    }

    /** Shows the opcodes matching {@code query} and updates the count line. */
    private void filter(String query) {
        shown.clear();
        shown.addAll(SmaliReference.search(query));
        adapter.notifyDataSetChanged();
        int total = SmaliReference.all().size();
        if (query == null || query.trim().isEmpty()) {
            countView.setText(getString(R.string.smali_reference_count, shown.size()));
        } else {
            countView.setText(getString(R.string.smali_reference_count_filtered, shown.size(), total));
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView opcode;
            final TextView form;
            final TextView desc;

            Holder(@NonNull View v) {
                super(v);
                opcode = v.findViewById(R.id.smali_opcode);
                form = v.findViewById(R.id.smali_form);
                desc = v.findViewById(R.id.smali_desc);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_smali_opcode, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SmaliReference.Op op = shown.get(position);
            holder.opcode.setText(op.opcode);
            holder.form.setText(op.form);
            holder.desc.setText(op.description + "  ·  " + op.group);
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}