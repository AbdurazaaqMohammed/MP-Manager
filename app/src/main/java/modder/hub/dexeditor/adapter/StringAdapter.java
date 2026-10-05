    /*
     * Dex-Editor-Android an Advanced Dex Editor for Android
     * Copyright 2024-26, developer-krushna
     *
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are
     * met:
     *
     *     * Redistributions of source code must retain the above copyright
     * notice, this list of conditions and the following disclaimer.
     *     * Redistributions in binary form must reproduce the above
     * copyright notice, this list of conditions and the following disclaimer
     * in the documentation and/or other materials provided with the
     * distribution.
     *     * Neither the name of developer-krushna nor the names of its
     * contributors may be used to endorse or promote products derived from
     * this software without specific prior written permission.
     *
     * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
     * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
     * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
     * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
     * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
     * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
     * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
     * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
     * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
     * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
     * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
     */

    package modder.hub.dexeditor.adapter;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;

    /*
     * Author - @developer-krushna
     * This class is responsible for listing strings extracted from dex files.
     * It now also supports:
     *  - live filtering (search within the list of strings)
     *  - a two-cell row: the original literal, and an inline field for its replacement
     *  - staging an edit without leaving the row, and reporting when the staged set changes
     */
    public class StringAdapter extends RecyclerView.Adapter<StringAdapter.ViewHolder> {

        private static final int COLOR_MODIFIED = 0xFF2E7D32; // green
        private final int COLOR_NORMAL;

        // Full, unfiltered dataset - kept as a reference to the activity's backing list
        private final List<String> allStrings;
        // Subset currently shown (equals allStrings when no filter is active)
        private List<String> displayedStrings;
        // original string -> pending new value (not yet applied to the dex classes)
        private final Map<String, String> modifiedStrings = new LinkedHashMap<>();

        private final OnStringClickListener listener;
        private String currentFilter = null;

        public interface OnStringClickListener {
            void onStringClick(String text);
        }

        public StringAdapter(List<String> strings, OnStringClickListener listener, int color) {
            this.allStrings = strings;
            this.displayedStrings = strings;
            this.listener = listener;
            COLOR_NORMAL = color;
        }

        /** Fired whenever the set of staged edits changes, so the host can toggle its Apply button. */
    public interface OnEditsChangedListener {
        void onEditsChanged(boolean hasEdits);
    }

    private OnEditsChangedListener editsListener;

    public void setOnEditsChangedListener(OnEditsChangedListener l) {
        this.editsListener = l;
    }

    private void notifyEditsChanged() {
        if (editsListener != null) editsListener.onEditsChanged(hasModifications());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.string_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final String original = displayedStrings.get(position);
        final String pending = modifiedStrings.get(original);

        // The original keeps its own cell and is never overwritten by the staged value, so a
        // replacement can be read against what it replaces.
        holder.stringText.setText(original);
        holder.stringText.setTextColor(COLOR_NORMAL);

        // Watcher detached across setText: rows are recycled, and a live watcher would write the
        // previous row's content into this one.
        holder.input.removeTextChangedListener(holder.watcher);
        holder.input.setText(pending == null ? "" : pending);
        holder.input.setSelection(holder.input.getText() == null ? 0 : holder.input.getText().length());
        holder.input.addTextChangedListener(holder.watcher);
        holder.watcher.original = original;
        holder.watcher.input = holder.input;
        // Colour the staged cell rather than the original, so "edited" is visible without
        // hiding the text being edited.
        holder.input.setTextColor(pending == null ? COLOR_NORMAL : COLOR_MODIFIED);

        holder.stringText.setOnClickListener(v -> {
            if (listener != null) listener.onStringClick(original);
        });
    }

    @Override
    public int getItemCount() {
        return displayedStrings.size();
    }

    /**
     * Re-applies the current dataset/filter. Call this after the backing list content
     * has changed (e.g. after a reload) if the list reference itself was swapped.
     */
    public void refresh() {
        applyFilter(currentFilter);
    }

    /** Filters the visible list by substring match (case-insensitive). Pass null/empty to clear. */
    public void setFilter(String query) {
        currentFilter = (query == null || query.trim().isEmpty()) ? null : query.trim();
        applyFilter(currentFilter);
    }

    public String getCurrentFilter() {
        return currentFilter;
    }

    private void applyFilter(String query) {
        if (query == null) {
            displayedStrings = allStrings;
            } else {
                String lower = query.toLowerCase();
                List<String> filtered = new ArrayList<>();
                for (String s : allStrings) {
                    if (s != null && s.toLowerCase().contains(lower)) filtered.add(s);
                }
                displayedStrings = filtered;
            }
            notifyDataSetChanged();
        }

    /** Marks (or unmarks, if newValue equals original) a string as having a pending edit. */
    public void markModified(String original, String newValue) {
        if (newValue == null || newValue.equals(original)) {
            modifiedStrings.remove(original);
        } else {
            modifiedStrings.put(original, newValue);
        }
        notifyDataSetChanged();
        notifyEditsChanged();
    }

    /**
     * Stages an edit typed straight into a row, without a full rebind.
     *
     * <p>markModified() calls notifyDataSetChanged(), which is wrong here: this runs on every
     * keystroke, and rebinding mid-typing would drop focus and reset the cursor. Only the two
     * views that actually show the change are touched.
     */
    private void stageEdit(String original, String newValue) {
        boolean wasStaged = modifiedStrings.containsKey(original);
        if (newValue == null || newValue.isEmpty() || newValue.equals(original)) {
            modifiedStrings.remove(original);
        } else {
            modifiedStrings.put(original, newValue);
        }
        // Only the staged/un-staged flip moves the Apply button; editing an already staged row
        // just changes what Apply will write.
        if (modifiedStrings.containsKey(original) != wasStaged) notifyEditsChanged();
    }

        /** Returns the pending edited value for a string, or the original if there is none. */
        public String getPendingValue(String original) {
            String v = modifiedStrings.get(original);
            return v != null ? v : original;
        }

        public boolean hasModifications() {
            return !modifiedStrings.isEmpty();
        }

        /** original -> new value, for every string the user has edited but not yet applied. */
        public Map<String, String> getModifiedStrings() {
            return modifiedStrings;
        }

        public void clearModifications() {
            modifiedStrings.clear();
            notifyDataSetChanged();
            notifyEditsChanged();
        }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView stringText;
        final TextInputEditText input;
        // Inner, not static: the watcher calls back into the adapter to stage the edit, and
        // a static ViewHolder cannot reach the outer instance.
        final Watcher watcher = new Watcher();

        ViewHolder(View itemView) {
            super(itemView);
            stringText = itemView.findViewById(R.id.string_text);
            input = itemView.findViewById(R.id.string_input);
        }
    }

    /** Feeds row edits into modifiedStrings. The row is identified by string identity, not by
     *  position, so recycling cannot route a keystroke to the wrong entry. */
    class Watcher implements TextWatcher {
        String original;
        /** The field this watcher drives, set at bind time. Held as a field because
         *  afterTextChanged has no handle on the holder. */
        TextInputEditText input;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (original == null) return;
            String value = editable == null ? "" : editable.toString();
            stageEdit(original, value);
            if (input == null) return;
            input.setTextColor(value.isEmpty() || value.equals(original)
                    ? COLOR_NORMAL : COLOR_MODIFIED);
        }
    }
}