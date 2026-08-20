package modder.hub.dexeditor.utils;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;

/**
 * UIHelper: Common UI utilities to avoid code duplication across activities and fragments.
 */
public class UIHelper {

    public static void setMenuItemColor(MenuItem item, int color) {
        SpannableString s = new SpannableString(item.getTitle());
        s.setSpan(new ForegroundColorSpan(color), 0, s.length(), 0);
        item.setTitle(s);
    }
}
