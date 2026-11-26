package io.github.abdurazaaqmohammed.util.misc;

import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;



import android.app.Activity;
import android.os.Handler;
import android.text.TextUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.util.concurrent.Callable;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;

public class RunUtil {

    private final Handler handler;
    private final MainActivity context;
    private final CharSequence msg;
    private final boolean reloadFolder;

    public RunUtil(Handler handler, MainActivity context, CharSequence msg) {
        this(handler, context, msg, false);
    }

    public RunUtil(MainActivity context) {
        this(null, context, null, false);
    }

    public RunUtil(Handler handler, MainActivity context, CharSequence msg, boolean reloadFolder) {
        this.handler = handler;
        this.context = context;
        this.msg = msg;
        this.reloadFolder = reloadFolder;
    }

    public static void runInBg(Callable<Boolean> callable, Handler handler, Runnable runnable, Activity context) {
        AppExecutors.io().execute(() -> {
            try {
                callable.call();
                if (!AppExecutors.isActivityAlive(context)) return;
                if (handler != null) {
                    handler.post(() -> {
                        if (AppExecutors.isActivityAlive(context)) runnable.run();
                    });
                } else {
                    AppExecutors.runOnUiIfAlive(context, runnable);
                }
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(context, () -> new ErrorUtil(context).showError(e));
            }
        });
    }

    public void runInBackground(Callable<Boolean> callable) {
        AppExecutors.io().execute(() -> {
            try {
                Boolean success = callable.call();
                if (Boolean.TRUE.equals(success) && !TextUtils.isEmpty(msg)) {
                    Runnable notifyUi = () -> {
                        if (!AppExecutors.isActivityAlive(context)) return;
                        Extensions.showMessage(context, msg);
                        if (reloadFolder) context.reloadCurrentFolder();
                    };
                    if (handler != null) handler.post(notifyUi);
                    else AppExecutors.runOnUiIfAlive(context, notifyUi);
                }
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(context, () -> new ErrorUtil(context).showError(e));
            }
        });
    }

    public void runInBackground(Callable<Boolean> callable, Runnable doAfter) {
        AppExecutors.io().execute(() -> {
            try {
                if (Boolean.TRUE.equals(callable.call()) && doAfter != null) {
                    if (handler != null) {
                        handler.post(() -> {
                            if (AppExecutors.isActivityAlive(context)) doAfter.run();
                        });
                    } else {
                        AppExecutors.runOnUiIfAlive(context, doAfter);
                    }
                }
            } catch (Exception e) {
                AppExecutors.runOnUiIfAlive(context, () -> new ErrorUtil(context).showError(e));
            }
        });
    }
}