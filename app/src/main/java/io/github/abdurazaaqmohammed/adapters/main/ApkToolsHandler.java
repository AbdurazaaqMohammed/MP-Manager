package io.github.abdurazaaqmohammed.adapters.main;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.SignatureStripUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.android.apksig.ApkVerifier;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.decompile.DecompileOptions;
import com.reandroid.apkeditor.decompile.Decompiler;
import com.reandroid.apkeditor.protect.ProtectorOptions;
import com.reandroid.apkeditor.refactor.RefactorOptions;
import com.reandroid.archive.ArchiveFile;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.apk.ApkBatchTools;
import io.github.abdurazaaqmohammed.features.apk.ApkInfoDialogs;
import io.github.abdurazaaqmohammed.features.apk.ApkOverlayTools;
import io.github.abdurazaaqmohammed.features.apk.ApkSignatureTools;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ApkCompareUtil;
import io.github.abdurazaaqmohammed.utils.ApkInfoUtil;
import io.github.abdurazaaqmohammed.utils.ApkOptimizer;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ToastInjectorUtil;
import io.github.abdurazaaqmohammed.utils.OverlayInjectorUtil;
import io.github.abdurazaaqmohammed.utils.OverlayProfiles;
import io.github.abdurazaaqmohammed.utils.PairipRemoverUtil;
import io.github.abdurazaaqmohammed.utils.ApkDeepOptimizer;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.SignatureKillerUtil;
import mt.modder.hub.apkCloner.util.ApkCloner;

public class ApkToolsHandler {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;
    private final ApkManifestEditor manifestEditor;
    private final ApkSignatureTools signatures;
    private final ApkBatchTools batch;
    private final ApkOverlayTools overlay;
    private final ApkInfoDialogs info;

    public ApkToolsHandler(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper,
                           boolean pane1, ApkManifestEditor manifestEditor) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
        this.manifestEditor = manifestEditor;
        this.signatures = new ApkSignatureTools(context, dialogUtil, uiHelper, pane1);
        this.batch = new ApkBatchTools(context, dialogUtil, uiHelper, pane1);
        this.overlay = new ApkOverlayTools(context, dialogUtil, uiHelper, pane1);
        this.info = new ApkInfoDialogs(context, dialogUtil, uiHelper, pane1, manifestEditor, signatures, overlay);
    }

    public void batchSignApks(List<File> apks) {
        signatures.batchSignApks(apks);
    }

    public void showCompareApksDialog(File f1, File f2) {
        batch.showCompareApksDialog(f1, f2);
    }

    public void batchOptimizeApks(List<File> apks) {
        batch.batchOptimizeApks(apks);
    }

    public void showDecompileOptionsDialog(File file, String fileName) {
        info.showDecompileOptionsDialog(file, fileName);
    }

    public void showApkInfoDialog(File file, String fileName) {
        info.showApkInfoDialog(file, fileName);
    }
}
