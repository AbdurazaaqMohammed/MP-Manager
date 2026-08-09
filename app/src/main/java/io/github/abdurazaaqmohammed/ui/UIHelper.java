package io.github.abdurazaaqmohammed.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.io.Files;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;

public class UIHelper {
    private final MainActivity context;
    public record AboutLibrary(String name, String author, String url, String licenseName,
                                String licenseFile) {
    }

    private void showLibraryDialog(AboutLibrary lib) {
        StringBuilder message = new StringBuilder(lib.licenseName).append('\n');
        try {
            message.append('\n').append(Files.asCharSource(FileUtils.copyFileFromAssetsAndGetFile(lib.licenseFile, context), StandardCharsets.UTF_8).read());
            //license.replaceFirst("[<\\[](?:yyyy|year)[]>]\\s+[\\[<]name of (?:author|copyright owner)[>\\]]", copyright[which])).
        } catch (Exception ignored) {
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(lib.name)
                .setMessage(message.toString())
                .setPositiveButton(context.rss.getString(R.string.github), (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))))
                .setNegativeButton(context.rss.getString(R.string.close), null)
                .show();
    }

    public void showAboutDialog() {
        View aboutView = LayoutInflater.from(context).inflate(R.layout.dialog_about, null);

        TextView versionText = aboutView.findViewById(R.id.aboutVersion);
        try {
            versionText.setText(context.rss.getString(R.string.about_version, context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        aboutView.findViewById(R.id.aboutGithub).setOnClickListener(v -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/MP-Manager"))));
        aboutView.findViewById(R.id.aboutTg).setOnClickListener(v -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/MP_Manager_Discussion"))));

        List<AboutLibrary> libraries = Arrays.asList(
                new AboutLibrary("DEX Editor", "Krushna Chandra", "https://github.com/developer-krushna/Dex-Editor-Android", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("APKEditor", "REAndroid", "https://github.com/REAndroid/APKEditor", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("ApkCloner", "Krushna Chandra", "https://github.com/developer-krushna/ApkCloner", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("JKS Signature Generator", "Krushna Chandra", "https://github.com/developer-krushna/JKS-SignKey-Generator", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("sun.security for Android", "Muntashir Al-Islam", "https://github.com/MuntashirAkon/sun-security-android", "GPL-2.0", "GPL-2.0.txt"),
                new AboutLibrary("apksig for Android", "Muntashir Al-Islam", "https://github.com/MuntashirAkon/apksig-android", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Sora Editor", "Rosemoe", "https://github.com/Rosemoe/sora-editor", "LGPL-2.1", "LGPL-2.1.txt"),
                new AboutLibrary("zip4j", "Srikanth Lingala", "https://github.com/srikanth-lingala/zip4j", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("aXML", "APK Explorer & Editor", "https://github.com/apk-editor/aXML", "GPL-3.0", "GPL-3.0+.txt"),
                new AboutLibrary("Commons Collections", "Apache Software Foundation", "https://github.com/apache/commons-collections", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Material Design Icons", "Google", "https://github.com/google/material-design-icons", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("android-filepicker", "Angad Singh", "https://github.com/singhangadin/android-filepicker", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("java-diff-utils", "java-diff-utils", "https://github.com/java-diff-utils/java-diff-utils", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Guava", "Google", "https://github.com/google/guava", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("jadx", "skylot", "https://github.com/skylot/jadx", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("AndroidX", "Google", "https://github.com/androidx/androidx", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Material Components", "Google", "https://github.com/material-components/material-components-android", "Apache-2.0", "Apache-2.0.txt"),
                //new AboutLibrary("OkHttp", "Square", "https://github.com/square/okhttp", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Gson", "Google", "https://github.com/google/gson", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Commons IO", "Apache Software Foundation", "https://github.com/apache/commons-io", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Markwon", "noties", "https://github.com/noties/Markwon", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("SLF4J", "QOS.ch", "https://github.com/qos-ch/slf4j", "MIT", "MIT.txt"),
                new AboutLibrary("Bouncy Castle", "The Legion of the Bouncy Castle", "https://github.com/bcgit/bc-java", "MIT", "MIT.txt"),
                new AboutLibrary("Android-EZ-FTP", "lilincpp", "https://github.com/lilincpp/Android-EZ-FTP", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("Apache MINA FTP Server", "Apache Software Foundation", "https://github.com/apache/mina-ftpserver", "Apache-2.0", "Apache-2.0.txt"),
                new AboutLibrary("ANTLR", "The ANTLR Project", "https://github.com/antlr/antlr4", "BSD-3-Clause", "BSD-3-Clause.txt"),
                new AboutLibrary("joni", "JRuby", "https://github.com/jruby/joni", "MIT", "MIT.txt"),
                new AboutLibrary("ftp4j", "Carlo Pelliccia (Sauron Software)", "http://www.sauronsoftware.it/projects/ftp4j/", "LGPL", "LGPL-2.1.txt"),
                new AboutLibrary("Volley", "Google", "https://github.com/google/volley", "Apache-2.0", "Apache-2.0.txt")
        );

        LinearLayout libsContainer = aboutView.findViewById(R.id.aboutLibs);
        for (AboutLibrary lib : libraries) {
            View row = LayoutInflater.from(context).inflate(R.layout.dialog_about_item, libsContainer, false);
            ((TextView) row.findViewById(R.id.aboutLibName)).setText(lib.name);
            ((TextView) row.findViewById(R.id.aboutLibAuthor)).setText(context.rss.getString(R.string.about_by_author, lib.author));
            TextView licenseText = row.findViewById(R.id.aboutLibLicense);
            licenseText.setText(lib.licenseName);
            if (licenseText.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) licenseText.getBackground()).setColor(
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, Color.TRANSPARENT));
            }
            row.setOnClickListener(v -> showLibraryDialog(lib));
            libsContainer.addView(row);
        }

        new MaterialAlertDialogBuilder(context)
                .setPositiveButton(context.rss.getString(R.string.close), null)
                .setView(aboutView)
                .show();
    }

    public UIHelper (MainActivity context) {
        this.context = context;
    }

    public View.OnClickListener showSignSettingsDialog() {
        return v -> SignatureKeyDialog.show(context);
    }

    public static String radioGroupValue(RadioGroup group, String defaultValue) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return defaultValue;
        android.widget.RadioButton rb = group.findViewById(id);
        Object tag = rb.getTag();
        return (tag == null) ? defaultValue : tag.toString();
    }

    public android.widget.RadioButton makeRadioButton(String value, String label) {
        android.widget.RadioButton rb = new android.widget.RadioButton(context);
        rb.setText(label);
        rb.setId(android.view.View.generateViewId());
        rb.setTag(value);
        return rb;
    }

    public static void selectRadioByValue(RadioGroup group, String valueToSelect) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.RadioButton rb) {
                Object tag = rb.getTag();
                if (tag != null && tag.toString().equals(valueToSelect)) {
                    group.check(rb.getId());
                    return;
                }
            }
        }
        if (group.getCheckedRadioButtonId() == -1 && group.getChildCount() > 0) {
            android.widget.RadioButton first = (android.widget.RadioButton) group.getChildAt(0);
            group.check(first.getId());
        }
    }

    public void styleEditText(EditText editText) {
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
    }

    public void scrollTextView(TextView textView) {
        textView.setSingleLine(true);

        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setMarqueeRepeatLimit(-1);
        textView.setHorizontallyScrolling(true);
        textView.setSelected(true); // Start without focus
    }

    public TextView getTitle(String text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16,16,16,16);
        return title;
    }

    public LinearLayout getProperties() {
        LinearLayout parentLayout = new LinearLayout(context);
        parentLayout.setOrientation(LinearLayout.HORIZONTAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout firstVerticalLayout = new LinearLayout(context);
        firstVerticalLayout.setOrientation(LinearLayout.VERTICAL);
        firstVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        String[] labels = {"Name", "Parent", "Type", "Size", "Modified", "Permissions", "Owner", "Group"};
        for (String label : labels) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textView.setText(label);
            textView.setTextColor(Color.WHITE);
            textView.setPadding(10, 10, 10, 10);
            firstVerticalLayout.addView(textView);
        }

        LinearLayout secondVerticalLayout = new LinearLayout(context);
        secondVerticalLayout.setOrientation(LinearLayout.VERTICAL);
        secondVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        for (int i = 0; i < labels.length; i++) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textView.setTextColor(Color.WHITE);
            textView.setPadding(10, 10, 10, 10);
            secondVerticalLayout.addView(textView);
        }

        parentLayout.addView(firstVerticalLayout);
        parentLayout.addView(secondVerticalLayout);
        return parentLayout;
    }
}