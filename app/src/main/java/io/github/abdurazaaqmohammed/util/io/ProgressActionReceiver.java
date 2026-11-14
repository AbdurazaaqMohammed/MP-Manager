package io.github.abdurazaaqmohammed.util.io;

import io.github.abdurazaaqmohammed.util.io.ProgressManager;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Applies a pause / resume / cancel tapped in the progress notification.
 *
 * <p>The dialog is the primary place for those controls, but it can be hidden
 * while an operation keeps running. Without this receiver, hiding the dialog
 * would leave work running with no way to stop it.
 */
public class ProgressActionReceiver extends BroadcastReceiver {

    /** Pauses, or resumes when already paused. */
    public static final String ACTION_TOGGLE_PAUSE =
            "io.github.abdurazaaqmohammed.progress.TOGGLE_PAUSE";
    /** Cancels the running operation. */
    public static final String ACTION_CANCEL =
            "io.github.abdurazaaqmohammed.progress.CANCEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        int operationId = intent.getIntExtra("operation_id", -1);
        if (ACTION_TOGGLE_PAUSE.equals(action)) {
            if (operationId >= 0) ProgressManager.togglePause(operationId); else ProgressManager.togglePauseActive();
        } else if (ACTION_CANCEL.equals(action)) {
            if (operationId >= 0) ProgressManager.cancel(operationId); else ProgressManager.cancelActive();
        }
    }
}
