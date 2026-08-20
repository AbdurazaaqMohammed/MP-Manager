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

package modder.hub.dexeditor.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.nio.charset.StandardCharsets;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import modder.hub.dexeditor.activity.DexEditorActivity;

/*
Author @developer-krushna
Viz.js 3.14.0
Copyright (c) 2023 Michael Daines

This distribution contains other software in object code form:
Graphviz https://www.graphviz.org
Expat https://libexpat.github.io
*/

public class GraphFragment extends Fragment {

    private String className;
    private String title;
    private String subtitle;
    private String dotContent;

    public static GraphFragment newInstance(String className, String title, String subtitle, String dot) {
        GraphFragment fragment = new GraphFragment();
        Bundle args = new Bundle();
        args.putString("className", className);
        args.putString("title", title);
        args.putString("subtitle", subtitle);
        args.putString("dot", dot);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            className = getArguments().getString("className");
            title = getArguments().getString("title");
            subtitle = getArguments().getString("subtitle");
            dotContent = getArguments().getString("dot");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_graph_view, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View menuBtn = view.findViewById(R.id.linear_left);

        if (getActivity() instanceof DexEditorActivity activity) {
            activity.setToolbarTitle(className.replace("/", "."));
            activity.setToolbarSubtitle(subtitle);
        }

        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (GraphFragment.this.getActivity() instanceof DexEditorActivity) {
                    ((DexEditorActivity) GraphFragment.this.getActivity()).toggleDrawer();
                }
            }
        });

        menuBtn.setOnLongClickListener(new View.OnLongClickListener() {
            private boolean onMenuItemClick(MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == 5) { // Locate
                    if (GraphFragment.this.getActivity() instanceof DexEditorActivity) {
                        ((DexEditorActivity) GraphFragment.this.getActivity()).locateClass(className);
                    }
                } else {
                    Activity context = GraphFragment.this.requireContext();
                    CopyUtil.copyToClipboard(context, menuItem.getTitle().toString());
                }
                return true;
            }

            @Override
            public boolean onLongClick(View v) {
                PopupMenu popupMenu = new PopupMenu(GraphFragment.this.requireContext(), v);
                Menu menu = popupMenu.getMenu();
                menu.add(1, 1, 1, title);
                menu.add(2, 2, 2, className.replace('/', '.'));
                menu.add(3, 3, 3, className);
                menu.add(4, 4, 4, "L" + className + ";");
                menu.add(5, 5, 5, "Locate");

                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        return onMenuItemClick(menuItem);
                    }
                });

                popupMenu.show();
                return true;
            }
        });

        WebView webView = view.findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setDomStorageEnabled(true);

        String base64Dot = Base64.encodeToString(dotContent.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);

        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <style>\n" +
                "        body { margin: 0; padding: 10px; display: flex; justify-content: center; background: #f5f5f5; }\n" +
                "        #graph { width: 100%; text-align: center; }\n" +
                "        svg { width: 100%; height: auto; }\n" +
                "        .error { color: red; font-family: monospace; padding: 20px; text-align: left; background: #ffebee; border: 1px solid #ffcdd2; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div id=\"graph\">Rendering Graph...</div>\n" +
                "    <script src=\"file:///android_asset/viz-standalone.js\"></script>\n" +
                "    <script>\n" +
                "        (function() {\n" +
                "            var base64Dot = '" + base64Dot + "';\n" +
                "            var dot = decodeURIComponent(escape(window.atob(base64Dot)));\n" +
                "            \n" +
                "            function render() {\n" +
                "                if (typeof Viz === 'undefined') {\n" +
                "                    document.getElementById('graph').innerHTML = '<div class=\"error\">Error: Viz library not found in assets.</div>';\n" +
                "                    return;\n" +
                "                }\n" +
                "                \n" +
                "                Viz.instance().then(function(viz) {\n" +
                "                    var svg = viz.renderSVGElement(dot);\n" +
                "                    var graphDiv = document.getElementById('graph');\n" +
                "                    graphDiv.innerHTML = '';\n" +
                "                    graphDiv.appendChild(svg);\n" +
                "                }).catch(function(err) {\n" +
                "                    document.getElementById('graph').innerHTML = '<div class=\"error\">Viz Error: ' + err.message + '</div>';\n" +
                "                });\n" +
                "            }\n" +
                "\n" +
                "            if (window.Viz) {\n" +
                "                render();\n" +
                "            } else {\n" +
                "                window.onload = render;\n" +
                "                setTimeout(function() {\n" +
                "                    if (document.getElementById('graph').innerHTML.indexOf('Loading') !== -1) {\n" +
                "                        render();\n" +
                "                    }\n" +
                "                }, 5000);\n" +
                "            }\n" +
                "        })();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        webView.loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null);
    }
}
