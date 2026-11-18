package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Intent;

import androidx.annotation.Keep;

/**
 * Keeps the overridable SDK hook methods non-final in release builds.
 *
 * <p>First-party tool packs extend {@link BaseToolPlugin} in their own dex and
 * override some of these hooks. The app itself never subclasses it, so R8
 * treats the class as closed-world and marks the unoverridden methods
 * {@code final synthetic}. The pack's dex is then rejected during verification
 * with "overrides final method", and the whole pack fails to load. Keeping a
 * host-side subclass that overrides every hook makes R8 keep them virtual.
 */
@Keep
public abstract class ExtensibleToolPlugin extends BaseToolPlugin {

    protected ExtensibleToolPlugin(String id, String title, String subtitle, String category) {
        super(id, title, subtitle, category);
    }

    @Override public boolean fillViewport() { return super.fillViewport(); }
    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); }
    @Override public void onDestroy() { super.onDestroy(); }
}
