package io.github.abdurazaaqmohammed.plugins.ext;

import java.util.ArrayList;
import java.util.List;

/**
 * In-process registry for third-party host extensions.
 *
 * <p>Populated by the host when a pack APK loads
 * ({@code ToolPack.extensions()}); packs never touch this class directly.
 * All methods are thread-safe and return defensive copies.
 */
public final class ExtensionRegistry {

    private static final List<SidebarAction> SIDEBAR = new ArrayList<>();
    private static final List<SettingToggle> TOGGLES = new ArrayList<>();
    private static final List<SettingAction> SETTINGS = new ArrayList<>();
    private static final List<FileMenuAction> FILE_MENU = new ArrayList<>();
    private static final List<EditorAction> EDITOR = new ArrayList<>();
    private static final List<ApkMoreAction> APK = new ArrayList<>();

    private ExtensionRegistry() {
    }

    /** Register one extension; same-kind duplicates by id are replaced. */
    public static synchronized void register(AppExtension ext) {
        if (ext instanceof SidebarAction) putById(SIDEBAR, (SidebarAction) ext);
        else if (ext instanceof SettingToggle) putById(TOGGLES, (SettingToggle) ext);
        else if (ext instanceof SettingAction) putById(SETTINGS, (SettingAction) ext);
        else if (ext instanceof FileMenuAction) putById(FILE_MENU, (FileMenuAction) ext);
        else if (ext instanceof EditorAction) putById(EDITOR, (EditorAction) ext);
        else if (ext instanceof ApkMoreAction) putById(APK, (ApkMoreAction) ext);
    }

    /** Drop one extension (pack unload). Matches by instance or id. */
    public static synchronized void unregister(AppExtension ext) {
        if (ext == null) return;
        removeFrom(SIDEBAR, ext);
        removeFrom(TOGGLES, ext);
        removeFrom(SETTINGS, ext);
        removeFrom(FILE_MENU, ext);
        removeFrom(EDITOR, ext);
        removeFrom(APK, ext);
    }

    public static synchronized List<SidebarAction> sidebarActions() {
        return new ArrayList<>(SIDEBAR);
    }

    public static synchronized List<SettingToggle> settingToggles() {
        return new ArrayList<>(TOGGLES);
    }

    public static synchronized List<SettingAction> settingActions() {
        return new ArrayList<>(SETTINGS);
    }

    public static synchronized List<FileMenuAction> fileMenuActions() {
        return new ArrayList<>(FILE_MENU);
    }

    public static synchronized List<EditorAction> editorActions() {
        return new ArrayList<>(EDITOR);
    }

    public static synchronized List<ApkMoreAction> apkActions() {
        return new ArrayList<>(APK);
    }

    public static synchronized SidebarAction findSidebar(String id) {
        for (SidebarAction a : SIDEBAR) {
            if (id != null && id.equals(a.id())) return a;
        }
        return null;
    }

    public static synchronized FileMenuAction findFileMenu(String id) {
        for (FileMenuAction a : FILE_MENU) {
            if (id != null && id.equals(a.id())) return a;
        }
        return null;
    }

    public static synchronized EditorAction findEditor(String id) {
        for (EditorAction a : EDITOR) {
            if (id != null && id.equals(a.id())) return a;
        }
        return null;
    }

    /** Ids for FileMenuOrder.load() so plugin items persist in menu order. */
    public static synchronized List<String> fileMenuIds() {
        List<String> ids = new ArrayList<>();
        for (FileMenuAction a : FILE_MENU) {
            if (a.id() != null) ids.add(a.id());
        }
        return ids;
    }

    public static synchronized String fileMenuTitle(String id) {
        FileMenuAction a = findFileMenu(id);
        return a == null ? null : a.label();
    }

    public static synchronized String fileMenuIconName(String id) {
        FileMenuAction a = findFileMenu(id);
        return a == null ? null : a.iconName();
    }

    public static synchronized String editorTitle(String id) {
        EditorAction a = findEditor(id);
        return a == null ? null : a.title();
    }

    public static synchronized String editorIconName(String id) {
        EditorAction a = findEditor(id);
        return a == null ? null : a.iconName();
    }

    private static <T extends AppExtension> void putById(List<T> list, T ext) {
        String id = idOf(ext);
        for (int i = 0; i < list.size(); i++) {
            if (id != null && id.equals(idOf(list.get(i)))) {
                list.set(i, ext);
                return;
            }
        }
        list.add(ext);
    }

    private static <T extends AppExtension> void removeFrom(List<T> list, AppExtension ext) {
        String id = idOf(ext);
        for (int i = list.size() - 1; i >= 0; i--) {
            T cur = list.get(i);
            if (cur == ext || (id != null && id.equals(idOf(cur)))) list.remove(i);
        }
    }

    private static String idOf(AppExtension ext) {
        if (ext instanceof SidebarAction) return ((SidebarAction) ext).id();
        if (ext instanceof SettingToggle) return ((SettingToggle) ext).id();
        if (ext instanceof SettingAction) return ((SettingAction) ext).id();
        if (ext instanceof FileMenuAction) return ((FileMenuAction) ext).id();
        if (ext instanceof EditorAction) return ((EditorAction) ext).id();
        if (ext instanceof ApkMoreAction) return ((ApkMoreAction) ext).id();
        return null;
    }
}
