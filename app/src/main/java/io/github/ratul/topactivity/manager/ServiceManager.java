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
package io.github.ratul.topactivity.manager;

import android.content.Context;

import io.github.ratul.topactivity.utils.DatabaseUtil;

public class ServiceManager {

    private final Context context;
    private final PopupManager popupManager;
    private final NotificationUiManager notificationUiManager;

    public ServiceManager(Context context) {
        this.context = context;
        this.popupManager = new PopupManager(context);
        this.notificationUiManager = new NotificationUiManager(context);
    }

    public void show() {
        switch (DatabaseUtil.getServiceMode()) {
            case "0":
                popupManager.show();
                notificationUiManager.show();
                break;

            case "1":
                notificationUiManager.show();
                break;
        }
    }
}
