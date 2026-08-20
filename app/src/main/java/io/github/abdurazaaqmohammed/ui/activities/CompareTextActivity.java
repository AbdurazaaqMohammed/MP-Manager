package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.appcompat.app.AppCompatActivity;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.utils.ErrorUtil;

public class CompareTextActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(false);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        setContentView(webView);

        Intent intent = getIntent();
        String path1 = intent.getStringExtra("file1");
        String path2 = intent.getStringExtra("file2");
        boolean isZip1 = intent.getBooleanExtra("isZip1", false);
        boolean isZip2 = intent.getBooleanExtra("isZip2", false);
        String zip1 = intent.getStringExtra("zip1");
        String zip2 = intent.getStringExtra("zip2");

        try {
            List<String> lines1 = readLines(path1, isZip1, zip1);
            List<String> lines2 = readLines(path2, isZip2, zip2);

            DiffRowGenerator generator = DiffRowGenerator.create()
                    .showInlineDiffs(true)
                    .inlineDiffByWord(true)
                    .oldTag(f -> f ? "<span style=\"background-color:#ffcccc;text-decoration:line-through;\">" : "</span>")
                    .newTag(f -> f ? "<span style=\"background-color:#ccffcc;\">" : "</span>")
                    .build();

            List<DiffRow> rows = generator.generateDiffRows(lines1, lines2);

            StringBuilder html = new StringBuilder();
            html.append("<html><head><style>")
                .append("body { font-family: monospace; font-size: 14px; white-space: pre-wrap; word-wrap: break-word; } ")
                .append("table { width: 100%; border-collapse: collapse; table-layout: fixed; } ")
                .append("th, td { border: 1px solid #ddd; padding: 4px; vertical-align: top; overflow: hidden; } ")
                .append("th { background-color: #f2f2f2; } ")
                .append("</style></head><body>");

            html.append("<table><tr><th style=\"width:50%\">File 1</th><th style=\"width:50%\">File 2</th></tr>");

            for (DiffRow row : rows) {
                html.append("<tr>");
                
                String oldLine = row.getOldLine();
                String newLine = row.getNewLine();
                
                String oldBg = row.getTag() == DiffRow.Tag.DELETE ? "background-color:#ffe6e6;" : "";
                String newBg = row.getTag() == DiffRow.Tag.INSERT ? "background-color:#e6ffe6;" : "";

                html.append("<td style=\"").append(oldBg).append("\">").append(oldLine).append("</td>");
                html.append("<td style=\"").append(newBg).append("\">").append(newLine).append("</td>");
                
                html.append("</tr>");
            }

            html.append("</table></body></html>");

            webView.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);

        } catch (Exception e) {
            Extensions.showMessage(this, "Error comparing text: " + e.getMessage());
            new ErrorUtil(this).showError(e);
        }
    }

    private List<String> readLines(String path, boolean isZip, String zipPath) throws Exception {
        List<String> lines = new ArrayList<>();
        if (isZip) {
            try (ZipFile zf = new ZipFile(zipPath)) {
                ZipEntry ze = zf.getEntry(path);
                if (ze != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)))) {
                        String line;
                        while ((line = reader.readLine()) != null) lines.add(line);
                    }
                }
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
        }
        return lines;
    }
}
