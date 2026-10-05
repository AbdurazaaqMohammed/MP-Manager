package io.github.abdurazaaqmohammed.features.apk.translate;

import java.util.Locale;

/**
 * One language series of the app, i.e. one config of the {@code string} type.
 *
 * <p>This is what the translation screen lists first. MT parses {@code resources.arsc} and shows
 * every {@code res/values-xx/strings.xml} the APK was built from, so you can pick the language
 * you are working on before touching a single string - the entries list is the second level.
 */
public final class LocaleRow {

    /** How much of a language pack is filled in. The wording lives in the adapter, not here. */
    public enum State {
        /** The config every locale falls back to; must never be deleted. */
        DEFAULT,
        /** Created but nothing has been put in it yet. */
        EMPTY,
        /** Carries a value for every entry the app defines. */
        COMPLETE,
        /** Partially filled. */
        PARTIAL
    }

    public final String qualifier;
    /** Human name, resolved through {@link Locale} so unlisted locales still read properly. */
    public final String name;
    public final State state;
    public final int filled;
    public final int total;
    /** True when this config was produced by copying another one in this screen. */
    public final boolean createdHere;
    /** Config this one was copied from, or null. */
    public final String copiedFrom;

    public LocaleRow(String qualifier, String name, State state, int filled, int total,
                     boolean createdHere, String copiedFrom) {
        this.qualifier = qualifier;
        this.name = name;
        this.state = state;
        this.filled = filled;
        this.total = total;
        this.createdHere = createdHere;
        this.copiedFrom = copiedFrom;
    }

    public static LocaleRow of(String qualifier, String name, ArscConfigManager.Stats stats,
                               boolean createdHere, String copiedFrom) {
        int filled = stats == null ? 0 : stats.filled;
        int total = stats == null ? 0 : stats.total;
        State state;
        if (Locales.normalize(qualifier).isEmpty()) {
            state = State.DEFAULT;
        } else if (filled == 0) {
            state = State.EMPTY;
        } else if (total > 0 && filled >= total) {
            state = State.COMPLETE;
        } else {
            state = State.PARTIAL;
        }
        return new LocaleRow(qualifier, name, state, filled, total, createdHere, copiedFrom);
    }

    public boolean isDefault() {
        return state == State.DEFAULT;
    }

    /** Progress in percent, for the partial state. */
    public int percent() {
        return total <= 0 ? 0 : Math.min(100, (int) ((filled * 100L) / total));
    }

    /** BCP-47 tag the engines should be given for this config. */
    public String bcp47() {
        String bcp = Locales.guessBcp47(qualifier);
        return bcp == null || bcp.isEmpty() ? "en" : bcp;
    }

    public String searchKey() {
        return (name + '\n' + qualifier).toLowerCase(Locale.ROOT);
    }
}
