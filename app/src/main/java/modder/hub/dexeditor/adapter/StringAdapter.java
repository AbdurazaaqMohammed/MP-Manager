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

    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.TextView;

    import androidx.annotation.NonNull;
    import androidx.recyclerview.widget.RecyclerView;

    import java.util.ArrayList;
    import java.util.HashSet;
    import java.util.LinkedHashMap;
    import java.util.LinkedHashSet;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;
    import java.util.regex.Pattern;

    import io.github.abdurazaaqmohammed.MPManager.R;

    /*
     * Author - @developer-krushna
     * This class is responsible for listing strings extracted from dex files.
     * It now also supports:
     *  - MT-Manager-style two-column rows: original on the left, the pending
     *    translation on the right (green while unapplied, dash when untranslated)
     *  - a modification counter callback so the toolbar can show "x/y"
     *  - live filtering with a full FilterSpec (query, translated scope,
     *    exclude/match-case/regex/exactly-match)
     *  - multi-select mode (long-press to enter, tap to toggle)
     */
    public class StringAdapter extends RecyclerView.Adapter<StringAdapter.ViewHolder> {

        private static final int COLOR_MODIFIED = 0xFF2E7D32; // green
        private final int COLOR_NORMAL;
        private final int COLOR_HINT;

        /** Filter options shared by translation mode's Filter dialog. */
        public static class FilterSpec {
            public String query = "";
            public int status;      // 0 = all, 1 = translated, 2 = untranslated
            public boolean exclude; // invert the query match
            public boolean matchCase;
            public boolean regex;
            public boolean exact;
            public boolean active;  // whether any filter is currently applied
        }

        // Full, unfiltered dataset - kept as a reference to the activity's backing list
        private final List<String> allStrings;
        // Subset currently shown (equals allStrings when no filter is active)
        private List<String> displayedStrings;
        // original string -> pending translation (not yet applied to the dex classes)
        private final Map<String, String> modifiedStrings = new LinkedHashMap<>();

        private final OnStringClickListener listener;
        private OnStringLongClickListener longClickListener;
        private String currentFilter = null;
        private FilterSpec currentSpec = null;
        private boolean untranslatedOnly;
        private OnModifiedCountChangedListener modifiedCountListener;

        // multi-select state
        private boolean selectionMode;
        private final Set<String> selected = new LinkedHashSet<>();
        private int selectionHighlightColor = 0x66323232;
        private OnSelectionChangedListener selectionListener;

        public interface OnStringClickListener {
            void onStringClick(String text);
        }

        public interface OnStringLongClickListener {
            void onStringLongClick(String text);
        }

        public interface OnModifiedCountChangedListener {
            void onModifiedCountChanged(int modified, int total);
        }

        public interface OnSelectionChangedListener {
            void onSelectionChanged(boolean selectionMode, int selectedCount);
        }

        public StringAdapter(List<String> strings, OnStringClickListener listener, int color) {
            this(strings, listener, color, 0xFF9E9E9E);
        }

        public StringAdapter(List<String> strings, OnStringClickListener listener, int color, int hintColor) {
            this.allStrings = strings;
            this.displayedStrings = strings;
            this.listener = listener;
            COLOR_NORMAL = color;
            COLOR_HINT = hintColor;
        }

        public void setModifiedCountListener(OnModifiedCountChangedListener listener) {
            this.modifiedCountListener = listener;
        }

        public void setSelectionListener(OnSelectionChangedListener listener) {
            this.selectionListener = listener;
        }

        public void setLongClickListener(OnStringLongClickListener listener) {
            this.longClickListener = listener;
        }

        public void setSelectionHighlightColor(int color) {
            this.selectionHighlightColor = color;
        }

        /** Preloads translations (e.g. from a dictionary) as pending edits. */
        public void loadTranslations(java.util.Map<String, String> map) {
            modifiedStrings.clear();
            if (map != null) modifiedStrings.putAll(map);
            applyCurrentFilter();
        }

        /** Records a translation for a string, keeping it even when identical
         *  to the original (translation-mode semantics: the counter counts
         *  recorded translations). */
        public void setTranslation(String original, String translation) {
            if (translation == null) {
                modifiedStrings.remove(original);
            } else {
                modifiedStrings.put(original, translation);
            }
            notifyDataSetChanged();
            notifyCountChanged();
        }

        public boolean hasTranslation(String original) {
            return modifiedStrings.containsKey(original);
        }

        /** Marks a pending edit, removing the entry when the value matches the
         *  original (Dex Editor Plus strings-tab semantics). */
        public void markModified(String original, String newValue) {
            if (newValue == null || newValue.equals(original)) {
                modifiedStrings.remove(original);
            } else {
                modifiedStrings.put(original, newValue);
            }
            notifyDataSetChanged();
            notifyCountChanged();
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
            applyCurrentFilter();
        }

        // ------------------------------------------------------------------
        // selection (multi-select) mode
        // ------------------------------------------------------------------

        public boolean isSelectionMode() {
            return selectionMode;
        }

        public Set<String> getSelected() {
            return selected;
        }

        public void enterSelectionMode(String first) {
            selectionMode = true;
            selected.clear();
            if (first != null) selected.add(first);
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        public void exitSelectionMode() {
            selectionMode = false;
            selected.clear();
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        public void toggleSelected(String original) {
            if (selected.contains(original)) {
                selected.remove(original);
                if (selected.isEmpty()) {
                    exitSelectionMode();
                    return;
                }
            } else {
                selected.add(original);
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        public void selectAll() {
            selectionMode = true;
            selected.clear();
            if (displayedStrings != null) selected.addAll(displayedStrings);
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        public void invertSelection() {
            if (!selectionMode) {
                enterSelectionMode(null);
            }
            Set<String> inverted = new LinkedHashSet<>();
            if (displayedStrings != null) {
                for (String s : displayedStrings) {
                    if (!selected.contains(s)) inverted.add(s);
                }
            }
            selected.clear();
            selected.addAll(inverted);
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        public void clearSelection() {
            selected.clear();
            if (selectionMode) exitSelectionMode();
        }

        private void notifySelectionChanged() {
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(selectionMode, selected.size());
            }
        }

        // ------------------------------------------------------------------
        // filtering
        // ------------------------------------------------------------------

        /**
         * Re-applies the current dataset/filter. Call this after the backing list content
         * has changed (e.g. after a reload) if the list reference itself was swapped.
         */
        public void refresh() {
            applyCurrentFilter();
        }

        /** Simple query-only filter (Dex Editor Plus strings-tab compatibility). */
        public void setFilter(String query) {
            FilterSpec spec = new FilterSpec();
            spec.query = query == null ? "" : query;
            spec.active = query != null && !query.trim().isEmpty();
            currentSpec = spec;
            applyCurrentFilter();
        }

        /** Full filter (translation mode Filter dialog). */
        public void applyFilter(FilterSpec spec) {
            currentSpec = spec;
            applyCurrentFilter();
        }

        public FilterSpec getCurrentSpec() {
            FilterSpec out = currentSpec != null ? currentSpec : new FilterSpec();
            out.query = currentFilter != null ? currentFilter : (currentSpec != null ? currentSpec.query : "");
            return out;
        }

        public String getCurrentFilter() {
            return currentFilter;
        }

        /** Legacy untranslated-only quick toggle (bottom bar no5). */
        public void setUntranslatedOnly(boolean only) {
            FilterSpec spec = currentSpec != null ? currentSpec : new FilterSpec();
            spec.status = only ? 2 : 0;
            spec.active = only || (spec.query != null && !spec.query.trim().isEmpty());
            currentSpec = spec;
            applyCurrentFilter();
        }

        private void applyCurrentFilter() {
            FilterSpec spec = currentSpec;
            List<String> filtered = new ArrayList<>();
            Pattern regex = null;
            if (spec != null && spec.regex && spec.query != null && !spec.query.isEmpty()) {
                try {
                    regex = Pattern.compile(spec.query,
                            spec.matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (Exception ignored) {
                    regex = null;
                }
            }
            for (String s : allStrings) {
                if (s == null) continue;
                if (spec != null) {
                    if (spec.status == 1 && !modifiedStrings.containsKey(s)) continue;
                    if (spec.status == 2 && modifiedStrings.containsKey(s)) continue;
                    boolean match = true;
                    if (spec.query != null && !spec.query.isEmpty()) {
                        if (spec.exact) {
                            match = spec.matchCase ? s.equals(spec.query)
                                    : s.equalsIgnoreCase(spec.query);
                        } else if (regex != null) {
                            match = regex.matcher(s).find();
                        } else {
                            match = spec.matchCase ? s.contains(spec.query)
                                    : s.toLowerCase().contains(spec.query.toLowerCase());
                        }
                        if (spec.exclude) match = !match;
                    }
                    if (!match) continue;
                } else if (untranslatedOnly && modifiedStrings.containsKey(s)) {
                    continue;
                }
                filtered.add(s);
            }
            displayedStrings = filtered;
            notifyDataSetChanged();
            notifyCountChanged();
        }

        private void notifyCountChanged() {
            if (modifiedCountListener != null) {
                modifiedCountListener.onModifiedCountChanged(modifiedStrings.size(), allStrings.size());
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_string_translation, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String original = displayedStrings.get(position);
            final String pending = modifiedStrings.get(original);

            holder.original.setText(original);
            if (pending != null) {
                holder.translation.setText(pending);
                holder.translation.setTextColor(COLOR_MODIFIED);
            } else {
                holder.translation.setText("—");
                holder.translation.setTextColor(COLOR_HINT);
            }

            if (selectionMode) {
                holder.itemView.setBackgroundColor(selected.contains(original)
                        ? selectionHighlightColor : 0x00000000);
            } else {
                holder.itemView.setBackgroundColor(0x00000000);
            }

            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelected(original);
                } else if (listener != null) {
                    listener.onStringClick(original);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onStringLongClick(original);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return displayedStrings == null ? 0 : displayedStrings.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView original;
            final TextView translation;

            ViewHolder(View itemView) {
                super(itemView);
                original = itemView.findViewById(R.id.string_original);
                translation = itemView.findViewById(R.id.string_translation);
            }
        }
    }
