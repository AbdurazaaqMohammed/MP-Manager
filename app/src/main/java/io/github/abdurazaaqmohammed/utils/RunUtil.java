package io.github.abdurazaaqmohammed.utils;


import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

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

    public static void runAble(Runnable runnable) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(runnable).get();
    }

    public static void runInBg(Callable<Boolean> callable, Handler handler, Runnable runnable, Activity context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(callable);

        executor.submit(() -> {
            try {
                future.get();
                handler.post(runnable);
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }


        });
    }

    public void runInBackground(Callable<Boolean> callable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(callable);

        executor.submit(() -> {
            try {
                Boolean success = future.get();

                if (success && handler != null && !TextUtils.isEmpty(msg))
                    handler.post(() -> {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                        if(reloadFolder) context.reloadCurrentFolder();
                    });
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }

    public void runInBackground(Callable<Boolean> callable, Runnable doAfter) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(callable);

        executor.submit(() -> {
            try {
                if (future.get() && handler != null) handler.post(doAfter);
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }
}