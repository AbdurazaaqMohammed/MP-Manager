package io.github.abdurazaaqmohammed.feature.apk.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import com.google.gson.Gson;

import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.lspatch.util.LoadedModules;
import org.matrix.vector.ipc.IModuleService;
import org.matrix.vector.ipc.LoadedModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipFile;

public final class LSPatchModuleRepository {
    public record Module(String packageName, String label, String path, boolean legacy, long versionCode) {}

    private final Context context;
    private final SharedPreferences preferences;

    public LSPatchModuleRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences("lspatch_manager", Context.MODE_PRIVATE);
    }

    public List<Module> modules() {
        List<Module> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (PackageInfo info : pm.getInstalledPackages(0)) {
            ApplicationInfo app = info.applicationInfo;
            if (app == null || app.sourceDir == null) continue;
            Boolean legacy = moduleKind(new File(app.sourceDir));
            if (legacy != null) result.add(new Module(info.packageName,
                    pm.getApplicationLabel(app).toString(), app.sourceDir, legacy, info.getLongVersionCode()));
        }
        result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }

    /** Classification follows the upstream loader; code is not mapped while listing modules. */
    static Boolean moduleKind(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            Properties properties = new Properties();
            var entry = zip.getEntry("META-INF/xposed/module.prop");
            if (entry != null) {
                try (var input = zip.getInputStream(entry)) { properties.load(input); }
            }
            String version = properties.getProperty("targetApiVersion", "0").trim();
            String digits = version.replaceFirst("[^0-9].*$", "");
            int api = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            String init = api >= 101 ? "META-INF/xposed/java_init.list" : "assets/xposed_init";
            return zip.getEntry(init) == null ? null : api < 101;
        } catch (IOException | IllegalArgumentException error) {
            return null;
        }
    }

    public Set<String> scope(String packageName) {
        return new LinkedHashSet<>(preferences.getStringSet("scope:" + packageName, Collections.emptySet()));
    }

    public synchronized void setScope(String app, Set<String> modules) throws IOException {
        Set<String> installed = new HashSet<>();
        for (Module module : modules()) installed.add(module.packageName);
        if (!installed.containsAll(modules)) throw new IOException("A selected Xposed module is not installed");
        Set<String> apps = rememberedApps();
        apps.add(app);
        if (!preferences.edit().putStringSet("apps", apps)
                .putStringSet("scope:" + app, new LinkedHashSet<>(modules)).commit()) {
            throw new IOException("Could not save Xposed module scope");
        }
    }

    public synchronized void forget(String app) throws IOException {
        Set<String> apps = rememberedApps();
        apps.remove(app);
        if (!preferences.edit().putStringSet("apps", apps).remove("scope:" + app).commit()) {
            throw new IOException("Could not remove Xposed module scope");
        }
    }

    public Set<String> rememberedApps() {
        return new LinkedHashSet<>(preferences.getStringSet("apps", Collections.emptySet()));
    }

    public List<String> managedApps() {
        Set<String> apps = rememberedApps();
        for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (managed(app)) apps.add(app.packageName);
        }
        List<String> sorted = new ArrayList<>(apps);
        Collections.sort(sorted);
        return sorted;
    }

    public boolean isManaged(String packageName) {
        try {
            return managed(context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    private boolean managed(ApplicationInfo app) {
        if (app.metaData == null) return false;
        String encoded = app.metaData.getString("lspatch");
        if (encoded == null) return false;
        try {
            PatchConfig config = new Gson().fromJson(new String(Base64.decode(encoded, Base64.DEFAULT),
                    StandardCharsets.UTF_8), PatchConfig.class);
            return config != null && config.useManager
                    && context.getPackageName().equals(config.resolveManagerPackageName());
        } catch (RuntimeException error) {
            return false;
        }
    }

    public Set<String> callerApps(int uid) {
        Set<String> result = new LinkedHashSet<>();
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages != null) for (String name : packages) if (isManaged(name)) result.add(name);
        return result;
    }

    public boolean canRead(int uid, String module) {
        if (uid == context.getApplicationInfo().uid) return true;
        for (String app : callerApps(uid)) if (scope(app).contains(module)) return true;
        return false;
    }

    public boolean isModuleCaller(int uid, String module) {
        if (uid == context.getApplicationInfo().uid) return true;
        try {
            return context.getPackageManager().getApplicationInfo(module, 0).uid == uid;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    public List<String> appsForModule(String module) {
        List<String> result = new ArrayList<>();
        for (String app : managedApps()) if (scope(app).contains(module)) result.add(app);
        return result;
    }

    public LoadedModule load(String module, boolean legacy, IModuleService service) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(module, 0);
            ApplicationInfo app = info.applicationInfo;
            if (app == null) return null;
            return LoadedModules.fromApk(module, app.sourceDir, app.uid % 100000, info.getLongVersionCode(),
                    app, legacy, service);
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }
    }

    public synchronized void moduleRemoved(String module) throws IOException {
        SharedPreferences.Editor editor = preferences.edit();
        for (String app : rememberedApps()) {
            Set<String> scope = scope(app);
            if (scope.remove(module)) editor.putStringSet("scope:" + app, scope);
        }
        if (!editor.commit()) throw new IOException("Could not update module scopes after uninstall");
    }
}
