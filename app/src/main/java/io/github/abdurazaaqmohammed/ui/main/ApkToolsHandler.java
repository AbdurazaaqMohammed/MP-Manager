package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.feature.apk.ApkBatchTools;
import io.github.abdurazaaqmohammed.feature.apk.ApkInfoDialogs;
import io.github.abdurazaaqmohammed.feature.apk.ApkOverlayTools;
import io.github.abdurazaaqmohammed.feature.apk.ApkSignatureTools;
import io.github.abdurazaaqmohammed.ui.main.UIHelper;

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

    public void showApkInfoDialog(File file, String fileName, boolean isApksSplit) {
        info.showApkInfoDialog(file, fileName, isApksSplit);
    }
}
