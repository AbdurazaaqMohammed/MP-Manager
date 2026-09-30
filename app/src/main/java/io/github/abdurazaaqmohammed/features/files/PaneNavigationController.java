package io.github.abdurazaaqmohammed.features.files;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns dual-pane back/forward history. Extracted from MainActivity;
 * the activity implements Host for view/loading access so this class
 * stays free of findViewById and adapter details.
 */
public class PaneNavigationController {

    public interface Host {
        void loadFolder(File folder, boolean pane1, boolean addToHistory);

        void loadZip(File zipFile, String path, boolean pane1, boolean addToHistory);

        void applyNavButtons(boolean canGoBack, boolean canGoForward);

        void showHistory(List<NavigationHistoryEntry> history);

        String shownPath();

        /** 1 or 2, whichever pane is currently selected. */
        int shownPane();
    }

    private final Host host;
    private final List<NavigationHistoryEntry> pane1History = new ArrayList<>();
    private final List<NavigationHistoryEntry> pane2History = new ArrayList<>();
    private int pane1HistoryIndex = -1;
    private int pane2HistoryIndex = -1;

    public PaneNavigationController(Host host) {
        this.host = host;
    }

    public List<NavigationHistoryEntry> historyFor(boolean pane1) {
        return pane1 ? pane1History : pane2History;
    }

    public boolean navigateBack(boolean pane1) {
        String suffix = " (Search Results)";
        String s = host.shownPath();
        // We should not add search results on history but look for better way to do this
        if (s.endsWith(suffix)) {
            host.loadFolder(new File(s.replace(suffix, "")), pane1, false);
            return true;
        } else {
            List<NavigationHistoryEntry> history = historyFor(pane1);
            int historyIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
            if (historyIndex > 0 && historyIndex <= history.size()) {
                NavigationHistoryEntry entry = history.get(--historyIndex);
                setIndex(pane1, historyIndex);
                open(entry, pane1);
                return true;
            } else return false;
        }
    }

    public void navigateForward(boolean pane1) {
        List<NavigationHistoryEntry> history = historyFor(pane1);
        int historyIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
        if (historyIndex >= -1 && historyIndex < history.size() - 1) {
            NavigationHistoryEntry entry = history.get(++historyIndex);
            setIndex(pane1, historyIndex);
            open(entry, pane1);
        }
    }

    /** History-tab tap: jump to an existing entry without pushing. */
    public void jumpTo(NavigationHistoryEntry entry, boolean pane1) {
        if (entry == null) return;
        List<NavigationHistoryEntry> history = historyFor(pane1);
        int idx = history.indexOf(entry);
        if (idx >= 0) setIndex(pane1, idx);
        open(entry, pane1);
        refreshButtons();
    }

    public void push(boolean pane1, NavigationHistoryEntry entry) {
        List<NavigationHistoryEntry> history = historyFor(pane1);
        int historyIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
        if (historyIndex < -1) historyIndex = -1;
        if (historyIndex > history.size() - 1) historyIndex = history.size() - 1;
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        if (!history.isEmpty() && history.get(history.size() - 1).equals(entry)) {
            historyIndex = history.size() - 1;
        } else {
            history.add(entry);
            historyIndex = history.size() - 1;
        }
        setIndex(pane1, historyIndex);
        refresh();
    }

    public void refresh() {
        host.showHistory(historyFor(host.shownPane() == 1));
    }

    public void refreshButtons() {
        boolean pane1 = host.shownPane() == 1;
        int backIndex = pane1 ? pane1HistoryIndex : pane2HistoryIndex;
        int backSize = pane1 ? pane1History.size() : pane2History.size();
        boolean canGoBack = backIndex > 0 && backIndex <= backSize;
        boolean canGoForward = pane1 ? pane1HistoryIndex < pane1History.size() - 1
                : pane2HistoryIndex < pane2History.size() - 1;
        host.applyNavButtons(canGoBack, canGoForward);
    }

    private void setIndex(boolean pane1, int index) {
        if (pane1) pane1HistoryIndex = index;
        else pane2HistoryIndex = index;
    }

    private void open(NavigationHistoryEntry entry, boolean pane1) {
        if (entry.isZip()) {
            host.loadZip(entry.file(), entry.zipPath(), pane1, false);
        } else {
            host.loadFolder(entry.file(), pane1, false);
        }
    }
}
