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

import java.util.Collections;
import java.util.List;

public final class ServiceState {
    private final boolean running;
    private final String pkg;
    private final String cls;
    private final List<HistoryItem> history;

    public ServiceState() {
        this(false, "", "", Collections.emptyList());
    }

    public ServiceState(boolean running, String pkg, String cls, List<HistoryItem> history) {
        this.running = running;
        this.pkg = pkg == null ? "" : pkg;
        this.cls = cls == null ? "" : cls;
        this.history = history == null ? Collections.emptyList() : history;
    }

    public boolean isRunning() {
        return running;
    }

    public String getPkg() {
        return pkg;
    }

    public String getCls() {
        return cls;
    }

    public List<HistoryItem> getHistory() {
        return history;
    }
}
