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
package io.github.ratul.topactivity.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public final class AutostartUtil {

    private AutostartUtil() {
    }

    public static boolean isAutoStartPermissionAvailable(Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (Intent intent : getAutostartIntents(context)) {
            if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return true;
            }
        }
        return false;
    }

    public static void requestAutoStartPermission(Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (Intent intent : getAutostartIntents(context)) {
            if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return;
                } catch (Exception ignored) {
                    // Ignore crashes, move to next intent
                }
            }
        }
    }

    private static Intent[] getAutostartIntents(Context context) {
        String brand = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase(Locale.ROOT);

        if (containsAny(brand, "oppo", "realme", "oneplus")) {
            return oppoRealmeOnePlusIntents();
        }
        if (containsAny(brand, "xiaomi", "redmi", "poco", "blackshark")) {
            return xiaomiIntents();
        }
        if (containsAny(brand, "vivo", "iqoo")) {
            return vivoIntents();
        }
        if (containsAny(brand, "huawei", "honor")) {
            return huaweiHonorIntents();
        }
        if (containsAny(brand, "transsion", "tecno", "infinix", "itel")) {
            return tecnoInfinixIntents();
        }
        if (brand.contains("samsung")) {
            return samsungIntents();
        }
        return new Intent[]{
                classIntent(
                        "com.letv.android.letvsafe",
                        "com.letv.android.letvsafe.AutobootManageActivity"
                ),
                new Intent(Intent.ACTION_MAIN).setComponent(
                        new ComponentName(
                                "com.android.settings",
                                "com.android.settings.Settings$AccessLockSummaryActivity"
                        )
                ).putExtra("packageName", context.getPackageName())
        };
    }

    private static boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static Intent[] xiaomiIntents() {
        return new Intent[]{
                classIntent(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                actionIntent("miui.intent.action.OP_AUTO_START"),
        };
    }

    private static Intent[] tecnoInfinixIntents() {
        return new Intent[]{
                classIntent("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
                actionIntent("com.cyin.himgr.applicationmanager.view.activities.AUTO_START_ACTIVITY"),
        };
    }

    private static Intent[] oppoRealmeOnePlusIntents() {
        return new Intent[]{
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                classIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.FakeActivity"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startupapp.StartupAppListActivity"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startupmanager.StartupAppListActivity"
                ),
                classIntent(
                        "com.coloros.safe",
                        "com.coloros.safe.permission.startup.StartupAppListActivity"
                ),
                classIntent(
                        "com.coloros.safe",
                        "com.coloros.safe.permission.startupapp.StartupAppListActivity"
                ),
                classIntent(
                        "com.coloros.safe",
                        "com.coloros.safe.permission.startupmanager.StartupAppListActivity"
                ),
                classIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startsettings"),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startupapp.startupmanager"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startupmanager.startupActivity"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.startupapp.startupmanager"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.privacypermissionsentry.PermissionTopActivity.Startupmanager"
                ),
                classIntent(
                        "com.coloros.safecenter",
                        "com.coloros.privacypermissionsentry.PermissionTopActivity"
                ),
                classIntent("com.coloros.safecenter", "com.coloros.safecenter.FakeActivity"),
        };
    }

    private static Intent[] vivoIntents() {
        return new Intent[]{
                classIntent(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                classIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                classIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                actionIntent("com.iqoo.secure.BGSTARTUPMANAGER"),
        };
    }

    private static Intent[] huaweiHonorIntents() {
        return new Intent[]{
                classIntent(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                classIntent(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                ),
                classIntent(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
                ),
                actionIntent("huawei.intent.action.HSM_STARTUPAPP_MANAGER"),
                actionIntent("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
        };
    }

    private static Intent[] samsungIntents() {
        return new Intent[]{
                classIntent(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                ),
                classIntent("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                classIntent("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity"),
                actionIntent("com.samsung.android.sm.ACTION_BATTERY"),
        };
    }

    private static Intent classIntent(String pkg, String cls) {
        return new Intent().setComponent(new ComponentName(pkg, cls));
    }

    private static Intent actionIntent(String action) {
        return new Intent(action).addCategory(Intent.CATEGORY_DEFAULT);
    }
}
