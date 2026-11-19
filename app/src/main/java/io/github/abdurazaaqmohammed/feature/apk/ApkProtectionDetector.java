package io.github.abdurazaaqmohammed.feature.apk;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Robust detector for Android APK protections, packers, anti-tamper shields,
 * and reinforcement shells (PairIP, Pangle Armor, Jiagu, SecNeo, Legu, etc.).
 *
 * Can inspect standalone APKs, split bundle directories, and .apks/.xapk archives.
 */
public final class ApkProtectionDetector {

    public static class Result {
        public final boolean isProtected;
        public final List<String> protections;

        public Result(List<String> protections) {
            this.protections = protections != null ? protections : Collections.emptyList();
            this.isProtected = !this.protections.isEmpty();
        }

        public String getSummary() {
            if (!isProtected) return null;
            return String.join(" + ", protections);
        }
    }

    private ApkProtectionDetector() {}

    public static Result detect(File file) {
        if (file == null || !file.exists()) {
            return new Result(Collections.emptyList());
        }
        Set<String> detected = new LinkedHashSet<>();
        if (file.isDirectory()) {
            File[] files = file.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".apk"));
            if (files != null) {
                for (File f : files) {
                    scanApkFile(f, detected);
                }
            }
        } else {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".apk")) {
                scanApkFile(file, detected);
            } else if (lower.endsWith(".apks") || lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".aspk") || lower.endsWith(".zip")) {
                scanArchiveFile(file, detected);
            }
        }
        return new Result(new ArrayList<>(detected));
    }

    public static Result detect(List<File> files) {
        if (files == null || files.isEmpty()) return new Result(Collections.emptyList());
        Set<String> detected = new LinkedHashSet<>();
        for (File f : files) {
            if (f != null && f.exists()) {
                Result r = detect(f);
                if (r.isProtected) detected.addAll(r.protections);
            }
        }
        return new Result(new ArrayList<>(detected));
    }

    public static Result detect(ApkModule module) {
        if (module == null) return new Result(Collections.emptyList());
        Set<String> detected = new LinkedHashSet<>();
        try {
            Iterator<InputSource> it = module.getZipEntryMap().iterator();
            while (it.hasNext()) {
                InputSource is = it.next();
                if (is != null) checkEntryPath(is.getName(), detected);
            }
        } catch (Throwable ignored) {}
        return new Result(new ArrayList<>(detected));
    }

    public static Result detect(ApkBundle bundle) {
        if (bundle == null) return new Result(Collections.emptyList());
        Set<String> detected = new LinkedHashSet<>();
        try {
            for (ApkModule module : bundle.getApkModuleList()) {
                Result r = detect(module);
                if (r.isProtected) detected.addAll(r.protections);
            }
        } catch (Throwable ignored) {}
        return new Result(new ArrayList<>(detected));
    }

    private static void scanApkFile(File apkFile, Set<String> detected) {
        try (ZipFile zf = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                checkEntryPath(ze.getName(), detected);
            }
        } catch (Throwable ignored) {}
    }

    private static void scanArchiveFile(File archiveFile, Set<String> detected) {
        try (ZipFile zf = new ZipFile(archiveFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                checkEntryPath(name, detected);
                // If it is a nested APK inside the bundle, scan its inner contents
                if (name.toLowerCase(Locale.ROOT).endsWith(".apk") && !ze.isDirectory()) {
                    try (InputStream is = zf.getInputStream(ze);
                         ZipInputStream zis = new ZipInputStream(is)) {
                        ZipEntry innerEntry;
                        while ((innerEntry = zis.getNextEntry()) != null) {
                            checkEntryPath(innerEntry.getName(), detected);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void checkEntryPath(String path, Set<String> detected) {
        if (path == null) return;
        String lower = path.toLowerCase(Locale.ROOT);

        // 1. Google Play Integrity / PairIP Protection (Google加固)
        if (lower.contains("libpairipcore.so") || lower.contains("libpairip.so") || lower.contains("assets/pairip")) {
            detected.add("Google加固 (PairIP)");
        }

        // 2. Pangle / ByteDance SecArmor & APM Insight
        if (lower.contains("libpglarmor.so") || lower.contains("libfile_lock_pgl.so") || lower.contains("libbuffer_pgl.so")
                || lower.contains("libapminsighta.so") || lower.contains("libapminsightb.so") || lower.contains("libsigner.so")) {
            detected.add("Pangle / ByteDance SecArmor");
        }

        // 3. Qihoo 360 Jiagu (360加固)
        if (lower.contains("libjiagu") || lower.contains("assets/libjiagu") || lower.contains("assets/tosversion")
                || lower.contains("com/stub/stubapp") || lower.contains("com/qihoo/jiagu")) {
            detected.add("Qihoo 360 Jiagu");
        }

        // 4. Tencent Legu / Tencent Shell (腾讯乐固)
        if (lower.contains("libshella") || lower.contains("libshell-") || lower.contains("libtxcore.so")
                || lower.contains("libtup.so") || lower.contains("assets/0oo00oo") || lower.contains("assets/o0oooo00")) {
            detected.add("Tencent Legu");
        }

        // 5. Bangcle / SecNeo (梆梆安全)
        if (lower.contains("libsecexe.so") || lower.contains("libsecmain.so") || lower.contains("libsecshell")
                || lower.contains("libdexhelper") || lower.contains("assets/secdata0.jar")) {
            detected.add("Bangcle / SecNeo");
        }

        // 6. Alibaba / Ali Reinforce (阿里聚安全)
        if (lower.contains("libmobisec.so") || lower.contains("libaliusercheck.so") || lower.contains("libsgmain.so")
                || lower.contains("aliprotect.dat")) {
            detected.add("Ali Reinforce");
        }

        // 7. Baidu Protection (百度加固)
        if (lower.contains("libbaiduprotect") || lower.contains("baiduprotect1.jar")) {
            detected.add("Baidu Protect");
        }

        // 8. Nagravision / Kudelski
        if (lower.contains("libkudelski")) {
            detected.add("Nagravision / Kudelski");
        }

        // 9. DexGuard / GuardSquare
        if (lower.contains("libdexguard.so") || lower.contains("dexguard-rasp") || lower.contains("assets/dexguard")) {
            detected.add("DexGuard");
        }

        // 10. Arxan / Digital.ai
        if (lower.contains("libarx.so") || lower.contains("libarxan.so")) {
            detected.add("Arxan");
        }

        // 11. Medusa / VMP
        if (lower.contains("libmedusa.so") || lower.contains("assets/medusa.dex")) {
            detected.add("Medusa / VMP");
        }

        // 12. VKey (V-OS)
        if (lower.contains("libvkey.so") || lower.contains("libvscore.so") || lower.contains("libvos.so")) {
            detected.add("VKey");
        }

        // 13. MT Protector (MT加固)
        if (lower.contains("libmtprotect.so") || lower.contains("assets/mtprotect")) {
            detected.add("MT Protector");
        }

        // 14. NP Protector (NP加固)
        if (lower.contains("libnp.so") || lower.contains("libnprotect.so") || lower.contains("assets/np/")) {
            detected.add("NP Protector");
        }

        // 15. APKToolPlus / Box
        if (lower.contains("libapktoolplus.so") || lower.contains("assets/jiagu_data.bin")) {
            detected.add("APKToolPlus");
        }

        // 16. NetEase Dun (网易易盾)
        if (lower.contains("libnesec.so") || lower.contains("assets/nesec")) {
            detected.add("NetEase Dun");
        }

        // 17. Ijiami (爱加密)
        if (lower.contains("libexec.so") || lower.contains("libexecmain.so") || lower.contains("libijm_rom.so")
                || lower.contains("assets/ijm_lib")) {
            detected.add("Ijiami");
        }

        // 18. SafeDK (AppLovin Quality Shield)
        if (lower.contains("libsafedk.so")) {
            detected.add("SafeDK (AppLovin)");
        }
    }
}
