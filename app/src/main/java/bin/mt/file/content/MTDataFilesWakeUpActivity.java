package bin.mt.file.content;

import android.app.Activity;
import android.os.Bundle;

/**
 * Trampoline activity injected alongside {@link MTDataFilesProvider}. Launching
 * it starts the target app's process (its data only shows once the process is
 * up) and immediately finishes so no UI is ever shown.
 */
public class MTDataFilesWakeUpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
