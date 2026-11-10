package io.github.abdurazaaqmohammed.features.files.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuDialog;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;

/** Use the normal file menu, but never dispatch real-path tools on remote entries. */
public final class NetworkFileMenu {
    private NetworkFileMenu() {}

    public static void show(MainActivity activity, String name, boolean pane1,
                            Set<String> additionalDisabled, Consumer<String> action) {
        Set<String> disabled = new HashSet<>(List.of(FileMenuOrder.SHARE, FileMenuOrder.OPEN_WITH,
                FileMenuOrder.COMPRESS, FileMenuOrder.BOOKMARK, FileMenuOrder.CMD, FileMenuOrder.CHECK));
        disabled.addAll(additionalDisabled);
        List<FileMenuOrder.MenuItem> items = new ArrayList<>();
        for (String id : new String[]{FileMenuOrder.COPY, FileMenuOrder.MOVE, FileMenuOrder.RENAME,
                FileMenuOrder.DELETE, FileMenuOrder.SHARE, FileMenuOrder.OPEN_WITH, FileMenuOrder.COMPRESS,
                FileMenuOrder.BOOKMARK, FileMenuOrder.PROPERTIES, FileMenuOrder.CMD, FileMenuOrder.CHECK}) {
            items.add(new FileMenuOrder.MenuItem(id, FileMenuOrder.labelFor(activity, id, pane1 ? "->" : "<-")));
        }
        FileMenuDialog.show(activity, name, items, false, disabled, action);
    }
}
