package cat.narezany.margyt;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Video;

/**
 * Saving a video without the stamp burned into it.
 *
 * TikTok's own model carries two addresses for the same video:
 *
 *     Video.getDownloadAddr()             -- the one the save button uses
 *     Video.getDownloadNoWatermarkAddr()  -- the one beside it, unstamped
 *
 * Both are real, unobfuscated names, both return the same type, and the second
 * is TikTok's, not ours: the app already models it, for the cases where it
 * serves the file clean itself. So the swap is a rewrite of the call site --
 * the same 35c instruction, the same register count, the same return type --
 * rather than anything that has to understand the download.
 *
 * It is off by default and it falls back on itself: when the switch is off, or
 * when the unstamped address is missing for this post, what comes back is
 * exactly what TikTok asked for. A post whose clean address the server did not
 * send still saves, stamped, instead of failing to save at all.
 */
public final class Download {

    private Download() {}

    public static final String KEY = "download_no_watermark";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;  // too early to know; do not cache it
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY, false);
        } catch (Throwable ignored) {
        }
        cached = on;
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cached = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
    }

    private static SharedPreferences prefs() {
        try {
            android.content.Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------ where the call lands

    /**
     * Where every `Video.getDownloadAddr()` in TikTok's bytecode now goes.
     *
     * The receiver arrives as the first argument, which is what keeps the
     * rewrite from having to renumber anything around it.
     */
    public static UrlModel getDownloadAddr(Video video) {
        if (video == null) return null;
        UrlModel stamped = video.getDownloadAddr();
        if (!isEnabled()) return stamped;
        try {
            UrlModel clean = video.getDownloadNoWatermarkAddr();
            if (clean != null) return clean;
        } catch (Throwable ignored) {
            // a release that moved the second address is a release that saves
            // the stamped one, not one that cannot save at all
        }
        return stamped;
    }
}
