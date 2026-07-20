/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.repository;

import androidx.multidex.BuildConfig;

import io.github.ratul.topactivity.ui.ClipboardActivity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DataRepository {

    public interface StateListener {
        void onStateChanged(ServiceState state);
    }

    private static final int HISTORY_LIMIT = 50;

    private static final DataRepository INSTANCE = new DataRepository();

    public static DataRepository getInstance() {
        return INSTANCE;
    }

    private final ArrayDeque<HistoryItem> historyDeque = new ArrayDeque<>(HISTORY_LIMIT);
    private ServiceState appState = new ServiceState();
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    private DataRepository() {
    }

    public ServiceState getAppState() {
        return appState;
    }

    public void addListener(StateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (StateListener listener : listeners) {
            listener.onStateChanged(appState);
        }
    }

    public synchronized void updateStatus(boolean isRunning) {
        appState = new ServiceState(
                isRunning,
                appState.getPkg(),
                appState.getCls(),
                appState.getHistory()
        );
        notifyListeners();
    }

    public synchronized void updateData(String newPkg, String newCls) {
        // Prevent adding copy activity
        if (newPkg.equals(BuildConfig.APPLICATION_ID)
                && newCls.equals(ClipboardActivity.class.getName())) {
            return;
        }

        // Prevent rapid duplicate emissions
        if (newPkg.equals(appState.getPkg()) && newCls.equals(appState.getCls())) {
            return;
        }

        HistoryItem newItem = new HistoryItem(newPkg, newCls);
        historyDeque.addFirst(newItem);
        if (historyDeque.size() >= HISTORY_LIMIT) {
            historyDeque.removeLast();
        }

        List<HistoryItem> history = new ArrayList<>(historyDeque);
        appState = new ServiceState(true, newPkg, newCls, history);
        notifyListeners();
    }

    public synchronized void clearHistory() {
        historyDeque.clear();
        appState = new ServiceState(
                appState.isRunning(),
                appState.getPkg(),
                appState.getCls(),
                new ArrayList<>()
        );
        notifyListeners();
    }
}
