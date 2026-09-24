
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


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */

package modder.hub.dexeditor.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;

import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.AlertCircularProgress;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.smali.SmaliOptions;
import com.android.tools.smali.smali2.Smali;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import modder.hub.dexeditor.GraphDot.DrawFlowDiagram;
import modder.hub.dexeditor.GraphDot.Method;
import io.github.abdurazaaqmohammed.MPManager.R;
import modder.hub.dexeditor.activity.AIOverViewActivity;
import modder.hub.dexeditor.activity.DexEditorActivity;
import modder.hub.dexeditor.smali.Smali2Java;
import modder.hub.dexeditor.smali.SmaliFieldAccessParser;
import modder.hub.dexeditor.smali.SmaliMethodBody;
import modder.hub.dexeditor.smali.SmaliMethodInvokeParser;
import modder.hub.dexeditor.utils.Notify_MT;
import modder.hub.dexeditor.smali.SmaliHelper;
import modder.hub.dexeditor.utils.DexUsageHelper;
import modder.hub.dexeditor.utils.ViewAnimationHelper;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;
import io.github.abdurazaaqmohammed.ui.fragment.UnifiedEditorFragment;

/*
Author @developer-krushna
Code fixed comments by ChatGPT
*/


public class SmaliMethodFieldListFragment extends DialogFragment {
    private AlertCircularProgress progressDialog;
    private AlertCircularProgress secondaryProgressDialog;
    private int dexVersion;
    private int editorLineNumber;
    private String lineNumber;
    private FastScrollerRecyclerView methodRecyclerView;
    private FastScrollerRecyclerView stringsRecyclerView;
    private Toolbar toolbar;
    private String savedMethodData = "";
    private String savedStringsData = "";
    private String searchQuery = "";
    private String smaliFilePath = "";
    private String className = "";
    private List<HashMap<String, Object>> methodOrFieldInfo = new ArrayList<>();
    private List<HashMap<String, Object>> stringListInfo = new ArrayList<>();
    private String fullClassName = "???";
    private final String smaliCallSyntax = "->";
    private static Typeface monoTypeface;

    private Typeface getMonoTypeface() {
        if (monoTypeface == null && getActivity() != null) {
            monoTypeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/mono.ttf");
        }
        return monoTypeface;
    }

    private boolean isFirstLoad = true;

    // Update the UI with the smali file path, class name, editor line number, and dex version
    public void updateUi(String smaliFilePath, String className, int editorLineNumber, int dexVersion) {
        this.smaliFilePath = smaliFilePath;
        this.className = className;
        this.editorLineNumber = editorLineNumber;
        this.dexVersion = dexVersion;
        this.isFirstLoad = true; // Always mark as first load to trigger data refresh from file

        if (isAdded()) {
            // Refresh data if already showing
            new LoadDataRunnable().run();
        }
    }

    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_method_layout, container, false);
        initialize(savedInstanceState, view);
        initializeLogic();
        return view;
    }

    private void initialize(Bundle savedInstanceState, View view) {
        toolbar = view.findViewById(R.id.toolbar);
        methodRecyclerView = view.findViewById(R.id.recyclerview_method_list);
        methodRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        stringsRecyclerView = view.findViewById(R.id.recyclerview_strings_list);
        stringsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

    }

    private void initializeLogic() {
//        if (getDialog() != null && getDialog().getWindow() != null) {
//            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//            getDialog().getWindow().requestFeature(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
//        }

        ViewAnimationHelper.enableSwipeViewToggle(methodRecyclerView, stringsRecyclerView);

        toolbar.setTitle(R.string.navigation);
        toolbar.inflateMenu(R.menu.smali_navigation_menu);

        Menu menu = toolbar.getMenu();
        MenuItem searchItem = menu.findItem(R.id.search);
        int tint = getTheme() == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE;
        DrawableCompat.setTint(menu.findItem(R.id.strings_list).getIcon(), tint);
        DrawableCompat.setTint(menu.findItem(R.id.close).getIcon(), tint);
        DrawableCompat.setTint(searchItem.getIcon(), tint);
        searchItem.setVisible(true);

        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
			searchView.setQueryHint("Search");
			searchView.setMaxWidth(Integer.MAX_VALUE);
			searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
				@Override
				public boolean onQueryTextSubmit(String query) {
					searchQuery = query;
					if (stringsRecyclerView.getVisibility() == View.VISIBLE) {
						performStringsSearch(searchQuery);
					} else {
						performSearch(searchQuery);
					}
					return false;
				}

				@Override
				public boolean onQueryTextChange(String newText) {
					searchQuery = newText;
					if (stringsRecyclerView.getVisibility() == View.VISIBLE) {
						performStringsSearch(searchQuery);
					} else {
						performSearch(searchQuery);
					}
					return true;
				}
			});
		}
        if (isFirstLoad) {
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable loadDataRunnable = new LoadDataRunnable();
            handler.postDelayed(loadDataRunnable, 200L);
            isFirstLoad = false;
        } else {
            // Restore adapters without reloading
            methodRecyclerView.setAdapter(new MethodListAdapter(methodOrFieldInfo));
            stringsRecyclerView.setAdapter(new StringListAdapter(stringListInfo));
            restoreRecyclerViewState();
        }

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.close) {
                saveCurrentState(); // Save state before dismissing
                dismiss();
                return true;
            }
            if (item.getItemId() == R.id.strings_list) {
                if (!stringListInfo.isEmpty()) {
                    if (stringsRecyclerView.getVisibility() == View.VISIBLE) {
                        ViewAnimationHelper.hideViewAndShowViewWithAnimation(stringsRecyclerView, methodRecyclerView);
                        item.setTitle(R.string.show_strings);
                    } else {
                        ViewAnimationHelper.hideViewAndShowViewWithAnimation(methodRecyclerView, stringsRecyclerView);
                        item.setTitle(R.string.show_methods);
                    }
                } else {
                    Activity _context = getActivity();
                    Extensions.showMessage(_context, "No strings found");
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            // Get screen width
            DisplayMetrics displayMetrics = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

            // Set fixed width (80% of screen width)
            int dialogWidth = (int) (displayMetrics.widthPixels * 0.8);

            // Height will WRAP_CONTENT automatically
            Objects.requireNonNull(dialog.getWindow()).setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void saveCurrentState() {
        // Save RecyclerView scroll states
        if (methodRecyclerView.getLayoutManager() != null) {
            DexEditorActivity.methodRecyclerViewState = methodRecyclerView.getLayoutManager().onSaveInstanceState();
        }
        if (stringsRecyclerView.getLayoutManager() != null) {
            DexEditorActivity.stringsRecyclerViewState = stringsRecyclerView.getLayoutManager().onSaveInstanceState();
        }
        DexEditorActivity.wasStringsVisible = stringsRecyclerView.getVisibility() == View.VISIBLE;
    }

    public void restorePreviousState(Parcelable methodState, Parcelable stringsState, boolean wasStringsVisible) {
        // Restore visibility
        MenuItem item = toolbar.getMenu().findItem(R.id.strings_list);
        if (wasStringsVisible) {
            methodRecyclerView.setVisibility(View.GONE);
            stringsRecyclerView.setVisibility(View.VISIBLE);
            item.setTitle(R.string.show_methods);
        } else {
            methodRecyclerView.setVisibility(View.VISIBLE);
            stringsRecyclerView.setVisibility(View.GONE);
            item.setTitle(R.string.show_strings);
        }

        // Restore scroll positions
        if (methodState != null) {
            if (methodRecyclerView.getLayoutManager() != null) {
                methodRecyclerView.getLayoutManager().onRestoreInstanceState(methodState);
            }
        }
        if (stringsState != null) {
            if (stringsRecyclerView.getLayoutManager() != null) {
                stringsRecyclerView.getLayoutManager().onRestoreInstanceState(stringsState);
            }
        }
    }

    private void restoreRecyclerViewState() {
        restorePreviousState(DexEditorActivity.methodRecyclerViewState, DexEditorActivity.stringsRecyclerViewState, DexEditorActivity.wasStringsVisible);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        saveCurrentState(); // Save state when dialog is dismissed
    }

    @SuppressLint("NotifyDataSetChanged")
    public void performSearch(final String _charSeq) {
        try {
            methodOrFieldInfo.clear();
            methodOrFieldInfo = new Gson().fromJson(savedMethodData, new TypeToken<ArrayList<HashMap<String, Object>>>() {
            }.getType());
            int mapNumber = methodOrFieldInfo.size();
            int currentIndex = mapNumber - 1;
            for (int i = 0; i < mapNumber; i++) {
                String methodName = Objects.requireNonNull(methodOrFieldInfo.get(currentIndex).get("MethodOrFieldName")).toString();
                if (!(_charSeq.length() > methodName.length()) && methodName.toLowerCase().contains(_charSeq.toLowerCase())) {

                } else {
                    methodOrFieldInfo.remove(currentIndex);
                }
                currentIndex--;
            }
            methodRecyclerView.setAdapter(new MethodListAdapter(methodOrFieldInfo));
            if (methodRecyclerView.getAdapter() != null) {
                methodRecyclerView.getAdapter().notifyDataSetChanged();
            }

        } catch (NullPointerException ignored) {
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void performStringsSearch(final String _charSeq) {
        try {
            stringListInfo.clear();
            stringListInfo = new Gson().fromJson(savedStringsData, new TypeToken<ArrayList<HashMap<String, Object>>>() {
            }.getType());
            int mapNumber = stringListInfo.size();
            int currentIndex = mapNumber - 1;
            for (int i = 0; i < mapNumber; i++) {
                String stringName = Objects.requireNonNull(stringListInfo.get(currentIndex).get("StringName")).toString();
                if (!(_charSeq.length() > stringName.length()) && stringName.toLowerCase().contains(_charSeq.toLowerCase())) {

                } else {
                    stringListInfo.remove(currentIndex);
                }
                currentIndex--;
            }
            stringsRecyclerView.setAdapter(new StringListAdapter(stringListInfo));
            if (stringsRecyclerView.getAdapter() != null) {
                stringsRecyclerView.getAdapter().notifyDataSetChanged();
            }

        } catch (NullPointerException ignored) {
        }
    }

    @NonNull
	private GradientDrawable createHolderBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(50);
        drawable.setColor(color);
        return drawable;
    }

    // Update the editor line number in the parent activity or current fragment
    public void updateEditorLineNumber(String lineNumber) {
        if (getActivity() instanceof DexEditorActivity activity) {
            UnifiedEditorFragment editorFragment = activity.getCurrentFragment();
            if (editorFragment != null) {
                editorFragment._updateEditorLineNumber(lineNumber);
            }
        } else if (getActivity() instanceof DialogLineNumberListener) {
            ((DialogLineNumberListener) getActivity())._updateEditorLineNumber(lineNumber);
        }
    }

    // Generate and display a flowchart for the given method
    public void methodFlowChart(final String methodName) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;

        if (activity instanceof DexEditorActivity dexActivity) {
            String cleanedClassName = SmaliHelper.smali2OnlySlash(fullClassName);
            String title = SmaliHelper.extractSimpleName(fullClassName) + "." + _getTextBefore(methodName, "(");
            String subtitle = "(" + _getTextAfter(methodName, "(");

            // Check if tab already exists to avoid redundant generation
            for (int i = 0; i < DexEditorActivity.tabs.size(); i++) {
                DexEditorActivity.EditorTab tab = DexEditorActivity.tabs.get(i);
                if (tab.className.equals(cleanedClassName) && tab.title.equals(title) &&
                    tab.subtitle != null && tab.subtitle.equals(subtitle) && tab.type == 2) {
                    dexActivity.showEditor(i);
                    dismiss();
                    return;
                }
            }
        }
        
        dismiss(); // Close dialog fragment
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new MethodFlowChartTask(activity, methodName).start(); // Start the flowchart generation task
        }, 200L);
    }

    // Helper method to get the text before a specific delimiter
    public String _getTextBefore(String text, String delimiter) {
        int index = text.indexOf(delimiter);
        return index != -1 ? text.substring(0, index) : "";
    }

    // Helper method to get the text after a specific delimiter
    public String _getTextAfter(String text, String delimiter) {
        int index = text.indexOf(delimiter);
        return index != -1 ? text.substring(index + delimiter.length()) : "";
    }

    // Convert Smali code to Java code for the given method
    public void smali2Java(final String methodName) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;

        if (activity instanceof DexEditorActivity dexActivity) {
            String cleanedClassName = SmaliHelper.smali2OnlySlash(fullClassName);
            String title = SmaliHelper.extractSimpleName(fullClassName) + "." + _getTextBefore(methodName, "(");

            // Check if tab already exists to avoid redundant decompilation
            for (int i = 0; i < DexEditorActivity.tabs.size(); i++) {
                DexEditorActivity.EditorTab tab = DexEditorActivity.tabs.get(i);
                if (tab.className.equals(cleanedClassName) && tab.title.equals(title) && tab.type == 1) {
                    dexActivity.showEditor(i);
                    dismiss();
                    return;
                }
            }
        }
        
        dismiss();
        new Handler(Looper.getMainLooper()).postDelayed(new SmaliToJavaTask(activity, methodName), 200L);
    }

    private String[] splitMethodTarget(String methodOrFieldName) {
        if (methodOrFieldName == null) return null;
        int paren = methodOrFieldName.indexOf('(');
        if (paren == -1) return null;
        return new String[]{methodOrFieldName.substring(0, paren), methodOrFieldName.substring(paren)};
    }

    private String[] splitFieldTarget(String methodOrFieldName) {
        if (methodOrFieldName == null) return null;
        int colon = methodOrFieldName.indexOf(':');
        if (colon == -1) return null;
        return new String[]{methodOrFieldName.substring(0, colon), methodOrFieldName.substring(colon + 1)};
    }

    private void findClassUsages(String slashClass) {
        DexEditorActivity activity = (DexEditorActivity) getActivity();
        if (activity == null || slashClass == null || slashClass.isEmpty()) return;
        dismiss();
        activity.searchClassUsages(slashClass);
    }

    private void findClassSubclasses(String slashClass) {
        DexEditorActivity activity = (DexEditorActivity) getActivity();
        if (activity == null || slashClass == null || slashClass.isEmpty()) return;
        dismiss();
        activity.searchSubclasses(slashClass);
    }

    private void showFieldUsageSubMenu(View anchor, String methodOrFieldName) {
        String[] parsed = splitFieldTarget(methodOrFieldName);
        if (parsed == null || getActivity() == null) return;
        PopupMenu sub = new PopupMenu(getActivity(), anchor);
        android.view.Menu m = sub.getMenu();
        m.add(0, 23, 0, "Find all usages");
        m.add(0, 24, 1, "Find get usages");
        m.add(0, 25, 2, "Find put usages");
        sub.setOnMenuItemClickListener(item1 -> {
            DexEditorActivity activity = (DexEditorActivity) getActivity();
            if (activity == null) return false;
            String slashClass = SmaliHelper.smali2OnlySlash(fullClassName);
            int mode = item1.getItemId() == 24 ? 1 : item1.getItemId() == 25 ? 2 : 0;
            dismiss();
            activity.searchFieldUsages(slashClass, parsed[0], parsed[1], mode);
            return true;
        });
        sub.show();
    }

    private void findMethodUsagesWithOverridePrompt(String methodOrFieldName) {
        String[] parsed = splitMethodTarget(methodOrFieldName);
        DexEditorActivity activity = (DexEditorActivity) getActivity();
        if (parsed == null || activity == null) return;
        String slashClass = SmaliHelper.smali2OnlySlash(fullClassName);
        String declaring = DexUsageHelper.toType(slashClass);
        int overrideCount = 0;
        try {
            if (DexEditorActivity.classTree != null) {
                List<ClassDef> all;
                synchronized (DexEditorActivity.classTree) {
                    all = new ArrayList<>(DexEditorActivity.classTree.classMap.values());
                }
                overrideCount = DexUsageHelper.findOverrides(declaring, parsed[0], parsed[1], all, DexEditorActivity.classTree.classMap).size();
            }
        } catch (Exception ignored) {
        }
        final String mName = parsed[0];
        final String proto = parsed[1];
        final String sClass = slashClass;
        if (overrideCount > 0) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.find_usages)
                    .setMessage(getString(R.string.overriding_methods_found, overrideCount))
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        DexEditorActivity a = (DexEditorActivity) getActivity();
                        dismiss();
                        if (a != null) a.searchMethodUsages(sClass, mName, proto, true);
                    })
                    .setNegativeButton(R.string.only_this_method, (d, w) -> {
                        DexEditorActivity a = (DexEditorActivity) getActivity();
                        dismiss();
                        if (a != null) a.searchMethodUsages(sClass, mName, proto, false);
                    })
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        } else {
            dismiss();
            activity.searchMethodUsages(slashClass, mName, proto, false);
        }
    }

    private void findMethodOverrides(String methodOrFieldName) {
        String[] parsed = splitMethodTarget(methodOrFieldName);
        DexEditorActivity activity = (DexEditorActivity) getActivity();
        if (parsed == null || activity == null) return;
        String slashClass = SmaliHelper.smali2OnlySlash(fullClassName);
        dismiss();
        activity.searchMethodOverrides(slashClass, parsed[0], parsed[1]);
    }

    private void clearMethod(HashMap<String, Object> item) {
        Activity act = getActivity();
        if (act == null || DexEditorActivity.classTree == null || item == null) return;
        String methodOrFieldName = Objects.requireNonNull(item.get("MethodOrFieldName")).toString();
        String[] parsed = splitMethodTarget(methodOrFieldName);
        if (parsed == null) return;
        String mName = parsed[0];
        String proto = parsed[1];
        String slashClass = SmaliHelper.smali2OnlySlash(fullClassName);
        DexEditorActivity activity = (DexEditorActivity) act;
        AlertCircularProgress pd = new AlertCircularProgress(activity);
        try {
            pd.setMessage(getString(R.string.clearing_method));
        } catch (Exception ignored) {
        }
        pd.show();
        new Thread(() -> {
            try {
                ClassDef def;
                synchronized (DexEditorActivity.classTree) {
                    def = DexEditorActivity.classTree.classMap.get(slashClass);
                }
                if (def == null) throw new Exception("Class not found");
                com.android.tools.smali.dexlib2.iface.Method target = DexUsageHelper.findMethod(def, mName, proto);
                if (target == null) throw new Exception("Method not found");
                int flags = target.getAccessFlags();
                if (AccessFlags.ABSTRACT.isSet(flags) || AccessFlags.NATIVE.isSet(flags))
                    throw new Exception("Cannot clear abstract or native method");
                boolean isStatic = AccessFlags.STATIC.isSet(flags);
                int paramCount = target.getParameters().size();
                String ret = target.getReturnType();
                int numParams = paramCount + (isStatic ? 0 : 1);
                char rc = (ret == null || ret.isEmpty()) ? 'V' : ret.charAt(0);
                boolean wide = rc == 'J' || rc == 'D';
                int registers = numParams + (wide ? 2 : 1);
                List<String> body = new ArrayList<>();
                body.add("    .registers " + registers);
                if (rc == 'V') {
                    body.add("    return-void");
                } else if (rc == 'Z' || rc == 'B' || rc == 'S' || rc == 'C' || rc == 'I') {
                    body.add("    const/4 v0, 0x0");
                    body.add("    return v0");
                } else if (rc == 'F') {
                    body.add("    const v0, 0x0");
                    body.add("    return v0");
                } else if (wide) {
                    body.add("    const-wide v0, 0x0");
                    body.add("    return-wide v0");
                } else {
                    body.add("    const v0, 0x0");
                    body.add("    return-object v0");
                }
                String current;
                synchronized (DexEditorActivity.classTree) {
                    String pending = DexEditorActivity.classTree.getPendingSmaliMap().get(slashClass);
                    current = pending != null ? pending : DexEditorActivity.classTree.getSmaliByType(def);
                }
                if (current == null) throw new Exception("Cannot read class");
                String[] lines = current.split("\n", -1);
                int start = -1;
                int end = -1;
                String needle = mName + proto;
                for (int i = 0; i < lines.length; i++) {
                    String t = lines[i].trim();
                    if (t.startsWith(".method") && t.contains(needle)) {
                        start = i;
                        break;
                    }
                }
                if (start == -1) throw new Exception("Method block not found");
                for (int i = start + 1; i < lines.length; i++) {
                    if (lines[i].trim().startsWith(".end method")) {
                        end = i;
                        break;
                    }
                }
                if (end == -1) throw new Exception("Method block not found");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= start; i++) sb.append(lines[i]).append("\n");
                for (String b : body) sb.append(b).append("\n");
                for (int i = end; i < lines.length; i++) {
                    sb.append(lines[i]);
                    if (i < lines.length - 1) sb.append("\n");
                }
                String newSmali = sb.toString();
                try {
                    ClassDef newDef = Smali.assemble(newSmali, new SmaliOptions(), activity.dexVersion);
                    DexEditorActivity.classTree.saveClassDef(newDef);
                } catch (Throwable e) {
                    DexEditorActivity.classTree.saveSmali(slashClass, newSmali);
                }
                try {
                    String pure = newSmali.replaceFirst("(?s)^#.*?\\n\\n", "");
                    FileWriter fw = new FileWriter(smaliFilePath);
                    fw.write(pure);
                    fw.close();
                } catch (Exception ignored) {
                }
                final String doneSmali = newSmali;
                final int clearedLine = start;
                activity.runOnUiThread(() -> {
                    try {
                        if (pd != null) pd.dismiss();
                    } catch (Exception ignored) {
                    }
                    try {
                        dismiss();
                    } catch (Exception ignored) {
                    }
                    for (int i = 0; i < DexEditorActivity.tabs.size(); i++) {
                        DexEditorActivity.EditorTab tab = DexEditorActivity.tabs.get(i);
                        if (tab.className.equals(slashClass) && tab.type == 0) {
                            tab.content = doneSmali;
                            tab.isModified = false;
                            UnifiedEditorFragment ef = activity.getFragmentAtIndex(i);
                            if (ef != null && ef.getEditor() != null) {
                                ef.getEditor().setText(doneSmali);
                                ef.navigateTo(clearedLine, -1, null);
                            }
                        }
                    }
                    DexEditorActivity.isChanged = true;
                    activity.refreshExplorerPage(1);
                    new LoadDataRunnable().run();
                    Extensions.showMessage(activity, "Method cleared");
                });
            } catch (Throwable e) {
                final String msg = String.valueOf(e.getMessage());
                activity.runOnUiThread(() -> {
                    try {
                        if (pd != null) pd.dismiss();
                    } catch (Exception ignored) {
                    }
                    Notify_MT.Notify(activity, "Error", msg == null ? "Failed" : msg, "Close");
                });
            }
        }).start();
    }

    public void showExceptionDlg(final Activity activity, final Exception e) {        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> Notify_MT.Notify(activity, activity.getString(R.string.error), e.getMessage(), activity.getString(R.string.close)));
    }

    public interface DialogLineNumberListener {
        void _updateEditorLineNumber(String lineNumber);
    }

    private class LoadDataRunnable implements Runnable {
        @Override
        public void run() {
            saveCurrentState(); // Save current scroll position before reloading data
            new LoadDataTask().execute();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class LoadDataTask {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        void execute() {
            new Thread(() -> {
                final Map<String, List<HashMap<String, Object>>> results = doInBackground();
                mainHandler.post(() -> onPostExecute(results));
            }).start();
        }

        protected Map<String, List<HashMap<String, Object>>> doInBackground() {
            Map<String, List<HashMap<String, Object>>> parsedDataMap = new HashMap<>();
            List<HashMap<String, Object>> methodInfoList = new ArrayList<>();
            List<HashMap<String, Object>> fieldInfoList = new ArrayList<>();
            List<HashMap<String, Object>> classInfoList = new ArrayList<>();
            List<HashMap<String, Object>> stringList = new ArrayList<>();

            try (BufferedReader smaliFileReader = new BufferedReader(new FileReader(smaliFilePath))) {
                // Open the smali file for reading
                String currentLine;
                int currentLineNumber = 0;
                boolean isInsideMethod = false;
                String currentMethodName = "";
                String currentFullMethodSignature = "";
                int methodStartLine = -1;

                // Read the smali file line by line
                while ((currentLine = smaliFileReader.readLine()) != null) {
                    currentLineNumber++;
                    String trimmedLine = currentLine.trim();

                    // Skip empty lines
                    if (trimmedLine.isEmpty()) {
                        continue;
                    }

                    // Extract strings
                    if (trimmedLine.startsWith("const-string") || trimmedLine.startsWith("const-string/jumbo")) {
                        int startIndex = trimmedLine.indexOf("\"");
                        int endIndex = trimmedLine.lastIndexOf("\"");
                        if (startIndex != -1 && endIndex != -1) {
                            String extractedString = trimmedLine.substring(startIndex + 1, endIndex);

                            HashMap<String, Object> stringInfo = new HashMap<>();
                            stringInfo.put("StringName", extractedString);
                            stringInfo.put("StartLineNumber", currentLineNumber - 1);
                            stringList.add(stringInfo);
                        }
                    }

                    // Split the line into tokens for easier parsing
                    String[] tokens = trimmedLine.split("\\s+");

                    // Check if the line defines a method
                    if (tokens[0].equals(".method")) {
                        isInsideMethod = true;
                        currentMethodName = tokens[tokens.length - 1]; // Last token is the method name
                        currentFullMethodSignature = currentLine.trim(); // Store full method signature
                        methodStartLine = currentLineNumber - 1;
                    }
                    // Check if the line ends a method
                    else if (tokens[0].equals(".end") && tokens[1].equals("method")) {
                        if (isInsideMethod && methodStartLine != -1) {
                            // Create a method info entry
                            HashMap<String, Object> methodInfo = new HashMap<>();
                            methodInfo.put("MethodOrFieldName", currentMethodName); // Original behavior
                            methodInfo.put("FullMethodOrField", currentFullMethodSignature); // New full signature
                            methodInfo.put("StartLineNumber", methodStartLine);
                            methodInfo.put("EndLineNumber", currentLineNumber - 1);
                            methodInfoList.add(methodInfo);

                            // Reset method tracking variables
                            isInsideMethod = false;
                            currentMethodName = "";
                            currentFullMethodSignature = "";
                            methodStartLine = -1;
                        }
                    }
                    // Check if the line defines a field
                    else if (tokens[0].equals(".field")) {
                        String fieldSignature = trimmedLine.substring(trimmedLine.indexOf(".field") + 7).trim();
                        int colonIndex = fieldSignature.indexOf(58);
                        if (colonIndex != -1) {
                            String fieldName = fieldSignature.substring(0, colonIndex).trim();

                            HashMap<String, Object> fieldInfo = new HashMap<>();
                            // Original behavior
                            fieldInfo.put("MethodOrFieldName",
                                    fieldName.substring(fieldName.lastIndexOf(32) + 1) +
                                            ":" + fieldSignature.substring(colonIndex + 1).trim());
                            // New full signature
                            fieldInfo.put("FullMethodOrField", currentLine.trim());
                            fieldInfo.put("StartLineNumber", currentLineNumber - 1);
                            fieldInfoList.add(fieldInfo);
                        }
                    }

                    // Check if the line defines a class
                    else if (tokens[0].equals(".class") && trimmedLine.endsWith(";")) {
                        String className = tokens[tokens.length - 1]; // Last token is the class name
                        fullClassName = className;
                        // Read the next line to check for the superclass
                        String nextLine = smaliFileReader.readLine();
                        if (nextLine != null && nextLine.trim().startsWith(".super")) {
                            String superClassName = nextLine.trim().substring(nextLine.indexOf(".super") + 7).trim();

                            // Create a class info entry
                            HashMap<String, Object> classInfo = new HashMap<>();
                            classInfo.put("MethodOrFieldName", className);
                            classInfo.put("StartLineNumber", (currentLineNumber - 1));
                            classInfo.put("SuperClass", superClassName);
                            classInfoList.add(classInfo);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Close the file reader

            // Add all parsed data to the map
            parsedDataMap.put("MethodInfo", methodInfoList);
            parsedDataMap.put("FieldInfo", fieldInfoList);
            parsedDataMap.put("ClassInfo", classInfoList);

            // Add the string list to the parsed data map
            parsedDataMap.put("StringInfo", stringList);
            return parsedDataMap;
        }


        @SuppressLint("NotifyDataSetChanged")
        protected void onPostExecute(Map<String, List<HashMap<String, Object>>> parsedDataMap) {
            if (parsedDataMap != null && !parsedDataMap.isEmpty()) {
                methodOrFieldInfo.clear();
                stringListInfo.clear();

                methodOrFieldInfo.addAll(Objects.requireNonNull(parsedDataMap.get("ClassInfo")));
                methodOrFieldInfo.addAll(Objects.requireNonNull(parsedDataMap.get("FieldInfo")));
                methodOrFieldInfo.addAll(Objects.requireNonNull(parsedDataMap.get("MethodInfo")));
                stringListInfo.addAll(Objects.requireNonNull(parsedDataMap.get("StringInfo")));

                savedMethodData = new Gson().toJson(methodOrFieldInfo);

                savedStringsData = new Gson().toJson(stringListInfo);

                methodRecyclerView.setAdapter(new MethodListAdapter(methodOrFieldInfo));
                stringsRecyclerView.setAdapter(new StringListAdapter(stringListInfo));


                int methodPositionToScroll = -1;
                int stringPositionToScroll = -1;

                // Step 1: Find position in methodOrFieldInfo
                for (int i = 0; i < methodOrFieldInfo.size(); i++) {
                    Map<String, Object> item = methodOrFieldInfo.get(i);
                    String startLineNumber = Objects.requireNonNull(item.get("StartLineNumber")).toString();
                    int startLine = (int) Math.floor(Double.parseDouble(startLineNumber));

                    if (item.containsKey("EndLineNumber")) {
                        // This is a method - check line range
                        String endLineNumber = Objects.requireNonNull(item.get("EndLineNumber")).toString();
                        int endLine = (int) Math.floor(Double.parseDouble(endLineNumber));
                        if (editorLineNumber >= startLine && editorLineNumber <= endLine) {
                            methodPositionToScroll = i;
                            break;
                        }
                    } else {
                        if (editorLineNumber == startLine) {
                            methodPositionToScroll = i;
                            break;
                        }
                    }

                }

                // Step 2: Find position in stringListInfo
                for (int i = 0; i < stringListInfo.size(); i++) {
                    String startLineNumber = stringListInfo.get(i).get("StartLineNumber").toString();
                    int startLine = (int) Math.floor(Double.parseDouble(startLineNumber));
                    if (editorLineNumber == startLine) {
                        stringPositionToScroll = i;
                        break;
                    }
                }

                // Step 3: Update adapters for both RecyclerViews
                if (methodRecyclerView.getAdapter() != null) {
                    methodRecyclerView.getAdapter().notifyDataSetChanged();
                }
                if (stringsRecyclerView.getAdapter() != null) {
                    stringsRecyclerView.getAdapter().notifyDataSetChanged();
                }

                // Step 4: Scroll the appropriate RecyclerView based on the found position
                if (methodPositionToScroll != -1) {
                    // Scroll methodRecyclerView and ensure it's visible
                    methodRecyclerView.scrollToPosition(methodPositionToScroll); // Immediate scroll
                } else if (stringPositionToScroll != -1) {
                    // Scroll stringsRecyclerView and ensure it's visible
                    stringsRecyclerView.scrollToPosition(stringPositionToScroll); // Immediate scroll

                }

                // Restore state after silent reload
                restoreRecyclerViewState();
            }
        }
    }

    public class MethodListAdapter extends RecyclerView.Adapter<MethodListAdapter.ViewHolder> {
        final List<HashMap<String, Object>> data;

        public MethodListAdapter(List<HashMap<String, Object>> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.method_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            HashMap<String, Object> item = data.get(position);
            String methodOrFieldName = Objects.requireNonNull(item.get("MethodOrFieldName")).toString();
            String startLineNumber = Objects.requireNonNull(item.get("StartLineNumber")).toString();

            holder.indexNameTextView.setTypeface(getMonoTypeface(), Typeface.NORMAL);
            GradientDrawable dynamicBackground = null;

            try {
                LayerDrawable layerDrawable = (LayerDrawable) holder.backgroundLayout.getBackground();
                dynamicBackground = (GradientDrawable) layerDrawable.findDrawableByLayerId(R.id.dynamic_background);
            } catch (Exception e) {
            }

            if (methodOrFieldName.startsWith("L") && methodOrFieldName.endsWith(";")) {
                holder.indexNameContainer.setBackground(createHolderBackground(Color.parseColor("#3860AF")));
                holder.indexNameTextView.setBackground(createHolderBackground(Color.parseColor("#3860AF")));
                holder.indexNameTextView.setText("C");
                fullClassName = methodOrFieldName;
                holder.methodNameTextView.setText(SmaliHelper.extractSimpleName(methodOrFieldName));
                holder.returnTypeTextView.setText(methodOrFieldName);

                if(dynamicBackground != null) {
                    if (editorLineNumber == ((int) Math.floor(Double.parseDouble(startLineNumber)))) {
                        dynamicBackground.setColor(Color.parseColor("#67C1DF"));
                    } else {
                        dynamicBackground.setColor(Color.TRANSPARENT);
                    }
                }
            } else if (methodOrFieldName.contains(":")) {
                holder.indexNameContainer.setBackground(createHolderBackground(Color.parseColor("#FB8C00")));
                holder.indexNameTextView.setBackground(createHolderBackground(Color.parseColor("#FB8C00")));
                holder.indexNameTextView.setText("F");
                holder.methodNameTextView.setText(_getTextBefore(methodOrFieldName, ":"));
                holder.returnTypeTextView.setText(_getTextAfter(methodOrFieldName, ":"));

                if(dynamicBackground != null) {
                    if (editorLineNumber == ((int) Math.floor(Double.parseDouble(startLineNumber)))) {
                        dynamicBackground.setColor(Color.parseColor("#67C1DF"));
                    } else {
                        dynamicBackground.setColor(Color.TRANSPARENT);
                    }
                }
            } else {
                holder.indexNameContainer.setBackground(createHolderBackground(Color.parseColor("#E53935")));
                holder.indexNameTextView.setBackground(createHolderBackground(Color.parseColor("#E53935")));
                holder.indexNameTextView.setText("M");
                String methodName = _getTextBefore(methodOrFieldName, "(");
                String parameters = "(" + _getTextAfter(methodOrFieldName, "(");
                int startLine = (int) Math.floor(Double.parseDouble(startLineNumber));
                int endLine = (int) Math.floor(Double.parseDouble(Objects.requireNonNull(item.get("EndLineNumber")).toString()));

                holder.methodNameTextView.setText(methodName);
                holder.returnTypeTextView.setText(parameters);

                if(dynamicBackground != null) {
                    if (editorLineNumber >= startLine && editorLineNumber <= endLine) {
                    dynamicBackground.setColor(Color.parseColor("#67C1DF"));
                } else {
                    dynamicBackground.setColor(Color.TRANSPARENT);
                }
                }
            }

            holder.backgroundLayout.setOnLongClickListener(_view -> {
                // Get the method or field name from the clicked position
                final String methodOrFieldName1 = Objects.requireNonNull(methodOrFieldInfo.get(position).get("MethodOrFieldName")).toString();

                // Create a popup menu attached to the clicked view
                PopupMenu popupMenu = new PopupMenu(getActivity(), _view);
                Menu menu = popupMenu.getMenu();

                // Check if this is a class signature (starts with L and ends with ;)
                if (methodOrFieldName1.startsWith("L") && methodOrFieldName1.endsWith(";")) {
                    menu.add(20, 20, 20, R.string.find_usages);
                    menu.add(21, 21, 21, R.string.find_subclasses);
                    menu.add(1, 1, 1, R.string.copy_class_signature);
                    menu.add(2, 2, 2, R.string.copy_subclass_signature);
                }

                // Check if this is a field (contains :)
                if (methodOrFieldName1.contains(":")) {
                    menu.add(22, 22, 22, R.string.find_usages);
                    menu.add(3, 3, 3, R.string.copy_field_signature);
                    menu.add(9, 9, 9, R.string.copy_field_get_code);  // Generate smali get instruction
                    menu.add(10, 10, 10, R.string.copy_field_put_code); // Generate smali put instruction
                }

                // Check if this is a method (contains ( but not :)
                if (methodOrFieldName1.contains("(") && !methodOrFieldName1.contains(":")) {
                    menu.add(26, 26, 26, R.string.find_usages);
                    menu.add(27, 27, 27, R.string.find_overriding_methods);
                    menu.add(4, 4, 4, R.string.copy_method_signature);
                    menu.add(5, 5, 5, R.string.copy_method_code);      // Get full method body
                    menu.add(6, 6, 6, R.string.copy_method_invoke_code); // Generate invoke instruction
                    menu.add(7, 7, 7, R.string.view_flowchart);       // Show method flowchart
                    menu.add(8, 8, 8, R.string.smali_to_java).setEnabled(Build.VERSION.SDK_INT > 23);        // Convert smali to Java
                    menu.add(11, 11, 11, R.string.ai_explanation).setEnabled(Build.VERSION.SDK_INT > 20);
                    menu.add(28, 28, 28, R.string.clear_method);
                }
                // Set click listener for popup menu items
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @SuppressLint("NewApi")
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case 1:  // Copy class signature
                                CopyUtil.copyToClipboard(requireActivity(), methodOrFieldName1);
                                return true;

                            case 2:  // Copy subclass signature
                                CopyUtil.copyToClipboard(requireActivity(), Objects.requireNonNull(methodOrFieldInfo.get(position).get("SuperClass")).toString());
                                return true;

                            case 3:  // Copy field signature
                                String fieldSignature = fullClassName + smaliCallSyntax + methodOrFieldName1;
                                // Clean up the signature by removing extra parts after space
                                int spaceIndex = fieldSignature.indexOf(" ");
                                if (spaceIndex != -1) {
                                    fieldSignature = fieldSignature.substring(0, spaceIndex);
                                }
                                CopyUtil.copyToClipboard(requireActivity(), fieldSignature);
                                return true;

                            case 4:  // Copy method signature
                                CopyUtil.copyToClipboard(requireActivity(), fullClassName + smaliCallSyntax + methodOrFieldName1);
                                return true;

                            case 5:  // Copy method code
                                // Parse and copy the full method body from smali file
                                SmaliMethodBody smaliMethodBody = new SmaliMethodBody(
                                        smaliFilePath,
                                        new String[]{Objects.requireNonNull(methodOrFieldInfo.get(position).get("MethodOrFieldName")).toString()},
                                        false
                                );
                                CopyUtil.copyToClipboard(requireActivity(), smaliMethodBody.parseClassInSmali());
                                return true;

                            case 6:  // Copy method invoke code
                                // Generate and copy smali invoke instruction
                                SmaliMethodInvokeParser parser = new SmaliMethodInvokeParser(fullClassName);
                                // Using register v0
                                CopyUtil.copyToClipboard(requireActivity(), parser.generateInvokeCode(
                                                                    Objects.requireNonNull(item.get("FullMethodOrField")).toString(),
                                                                    "v0"  // Using register v0
                                                            ));
                                return true;

                            case 7:  // View flowchart
                                methodFlowChart(Objects.requireNonNull(methodOrFieldInfo.get(position).get("MethodOrFieldName")).toString());
                                return true;

                            case 8:  // Smali to Java
                                smali2Java(Objects.requireNonNull(methodOrFieldInfo.get(position).get("MethodOrFieldName")).toString());
                                return true;

                            case 9:  // Copy field get code
                                // Generate and copy smali get instruction for field
                                SmaliFieldAccessParser parser2 = new SmaliFieldAccessParser(fullClassName);
                                CopyUtil.copyToClipboard(requireActivity(), parser2.generateGetCode(Objects.requireNonNull(item.get("FullMethodOrField")).toString()));
                                return true;

                            case 10:  // Copy field put code
                                // Generate and copy smali put instruction for field
                                SmaliFieldAccessParser parser3 = new SmaliFieldAccessParser(fullClassName);
                                CopyUtil.copyToClipboard(requireActivity(), parser3.generatePutCode(Objects.requireNonNull(item.get("FullMethodOrField")).toString()));
                                return true;

                            case 11:  // AI Explanation
                                SmaliMethodBody smaliMethodBody2 = new SmaliMethodBody(smaliFilePath, new String[]{methodOrFieldInfo.get(position).get("MethodOrFieldName").toString()}, false);
                                Intent intent = new Intent(requireContext().getApplicationContext(), AIOverViewActivity.class);
                                intent.putExtra("smali", smaliMethodBody2.parseClassInSmali());
                                startActivity(intent);
                                return true;

                            case 20:  // Find usages of class
                                findClassUsages(SmaliHelper.smali2OnlySlash(methodOrFieldName1));
                                return true;

                            case 21:  // Find subclasses of class
                                findClassSubclasses(SmaliHelper.smali2OnlySlash(methodOrFieldName1));
                                return true;

                            case 22:  // Find usages of field (submenu)
                                showFieldUsageSubMenu(_view, methodOrFieldName1);
                                return true;

                            case 26:  // Find usages of method
                                findMethodUsagesWithOverridePrompt(methodOrFieldName1);
                                return true;

                            case 27:  // Find overriding methods
                                findMethodOverrides(methodOrFieldName1);
                                return true;

                            case 28:  // Clear method
                                clearMethod(item);
                                return true;

                            default:
                                return false;
                        }
                    }
                });

                // Show the popup menu
                popupMenu.show();
                return true;  // Consume the long click event
            });

            holder.backgroundLayout.setOnClickListener(_view -> {
                lineNumber = Objects.requireNonNull(methodOrFieldInfo.get(position).get("StartLineNumber")).toString();
                updateEditorLineNumber(lineNumber);
                dismiss();
            });

        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout backgroundLayout;
            final LinearLayout indexNameContainer;
            final TextView indexNameTextView;
            final TextView methodNameTextView;
            final TextView returnTypeTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                backgroundLayout = itemView.findViewById(R.id.linear_bg);
                indexNameContainer = itemView.findViewById(R.id.indexName_container);
                indexNameTextView = itemView.findViewById(R.id.indexName);
                methodNameTextView = itemView.findViewById(R.id.method_name);
                returnTypeTextView = itemView.findViewById(R.id.return_type);
            }
        }
    }

    // Task to generate a method flowchart using viz-js locally
    private class MethodFlowChartTask extends Thread {
        private final Activity activity;
        private final String methodName;
        private AlertCircularProgress pd;

        public MethodFlowChartTask(Activity activity, String methodName) {
            this.activity = activity;
            this.methodName = methodName;
        }

        @Override
        public void run() {
            activity.runOnUiThread(() -> {
                pd = new AlertCircularProgress(activity);
                pd.setMessage(getString(R.string.generating_flowchart));
                pd.show();
            });

            try {
                ArrayList<String> methodList = new ArrayList<>();
                methodList.add(methodName);
                DrawFlowDiagram drawFlowDiagram = new DrawFlowDiagram(smaliFilePath, methodList.toArray(new String[0]));
                drawFlowDiagram.run();

                activity.runOnUiThread(() -> {
                    if (pd != null) pd.dismiss();

                    for (Method method : drawFlowDiagram.getClassInSmali().getMethodDict().values()) {
                        final String dotDiagram = drawFlowDiagram.drawMethodFlowDiagram(method);

                        if (activity instanceof DexEditorActivity) {
                            String cleanedClassName = SmaliHelper.smali2OnlySlash(fullClassName);
                            String title = SmaliHelper.extractSimpleName(fullClassName) + "." + _getTextBefore(methodName, "(");
                            String subtitle = "(" + _getTextAfter(methodName, "(");
                            ((DexEditorActivity) activity).addTab(cleanedClassName, title, subtitle, dotDiagram, 2);
                        }
                    }
                });
            } catch (final Exception e) {
                activity.runOnUiThread(() -> {
                    if (pd != null) pd.dismiss();
                    showExceptionDlg(activity, e);
                });
            }
        }
    }

    // Task to convert Smali code to Java code
    private class SmaliToJavaTask implements Runnable {
        private final Activity activity;
        private final String methodName;
        private AlertCircularProgress pd;

        public SmaliToJavaTask(Activity activity, String methodName) {
            this.activity = activity;
            this.methodName = methodName;
        }

        @SuppressLint("StaticFieldLeak")
        @Override
        public void run() {
            activity.runOnUiThread(() -> {
                pd = new AlertCircularProgress(activity);
                pd.setMessage(getString(R.string.decompiling));
                pd.show();
            });

            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    try {
                        // Parse the Smali method and convert it to Java
                        SmaliMethodBody smaliMethodBody = new SmaliMethodBody(smaliFilePath, new String[]{methodName}, true);
                        return Smali2Java.translate(smaliMethodBody.parseClassInSmali(), dexVersion);
                    } catch (final Exception e) {
                        activity.runOnUiThread(() -> {
                            if (pd != null) pd.dismiss();
                            showExceptionDlg(activity, e);
                        });
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(String javaCode) {
                    activity.runOnUiThread(() -> {
                        if (pd != null) pd.dismiss();
                    });
                    
                    if (javaCode != null) {
                        activity.runOnUiThread(() -> {
                            if (activity instanceof DexEditorActivity dexActivity) {
                                String cleanedClassName = SmaliHelper.smali2OnlySlash(fullClassName);
                                String title = SmaliHelper.extractSimpleName(fullClassName) + "." + _getTextBefore(methodName, "(");
                                dexActivity.addTab(cleanedClassName, title, javaCode, 1);
                            }
                        });
                    }
                }
            }.execute();
        }
    }

    private class StringListAdapter extends RecyclerView.Adapter<StringListAdapter.ViewHolder> {
        final List<HashMap<String, Object>> data;

        public StringListAdapter(List<HashMap<String, Object>> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.string_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HashMap<String, Object> item = data.get(position);
            String stringName = Objects.requireNonNull(item.get("StringName")).toString();
            String startLineNumber = Objects.requireNonNull(item.get("StartLineNumber")).toString();
            holder.indexNameTextView.setTypeface(getMonoTypeface(), Typeface.NORMAL);
            holder.indexNameContainer.setBackground(createHolderBackground(Color.parseColor("#40AD3E")));
            holder.indexNameTextView.setBackground(createHolderBackground(Color.parseColor("#40AD3E")));
            holder.stringTextView.setText(stringName);

            GradientDrawable dynamicBackground = null;

            try {
                LayerDrawable layerDrawable = (LayerDrawable) holder.backgroundLayout.getBackground();
                dynamicBackground = (GradientDrawable) layerDrawable.findDrawableByLayerId(R.id.dynamic_background);
            } catch (Exception e) {
            }
            if(dynamicBackground != null) {
                if (editorLineNumber == ((int) Math.floor(Double.parseDouble(startLineNumber)))) {
                    dynamicBackground.setColor(Color.parseColor("#67C1DF"));
                } else {
                    dynamicBackground.setColor(Color.TRANSPARENT);
                }
            }

            holder.backgroundLayout.setOnLongClickListener(_view -> {
                PopupMenu popupMenu = new PopupMenu(getActivity(), _view);
                Menu menu = popupMenu.getMenu();
                menu.add(1, 1, 1, android.R.string.copy);
                popupMenu.setOnMenuItemClickListener(item1 -> {
                    if (item1.getItemId() == 1) {
                        CopyUtil.copyToClipboard(requireActivity(), stringName);
                        return true;
                    }
                    return false;
                });
                popupMenu.show();
                return true;
            });

            holder.backgroundLayout.setOnClickListener(_view -> {
                updateEditorLineNumber(startLineNumber);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView stringTextView;
            final LinearLayout backgroundLayout;
            final LinearLayout indexNameContainer;
            final TextView indexNameTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                backgroundLayout = itemView.findViewById(R.id.linear_bg);
                stringTextView = itemView.findViewById(R.id.string_name);
                indexNameContainer = itemView.findViewById(R.id.indexName_container);
                indexNameTextView = itemView.findViewById(R.id.indexName);
            }
        }
    }

}
