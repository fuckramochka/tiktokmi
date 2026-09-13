package cat.narezany.margyt;

import android.content.SharedPreferences;

import com.ss.android.ugc.aweme.music.model.Music;

/**
 * The sound TikTok switched off.
 *
 * When a track is pulled -- for a copyright claim, for a region, for whatever
 * the label asked -- the video is not removed. It stays, and its sound is
 * turned off, through four answers on the sound's own model: whether it is
 * available at all, its status, whether sharing it is muted, and how.
 *
 * All four are real names on a real class, so all four are rewritten to come
 * through here, and with the switch on they say the sound is fine. The audio
 * itself was always in the file; nothing is fetched and nothing is decoded
 * differently. What changes is that the app stops muting it.
 *
 * On by default, because a video whose sound is missing is a broken video and
 * everyone who installs this mod is asking for the app to behave.
 */
public final class Sound {

    private Sound() {}

    public static final String KEY = "sound_available";

    /** What TikTok's own model calls a sound that is fine. */
    private static final int AVAILABLE = 1;
    private static final int NOT_MUTED = 0;

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true;  // the default, before anything is read
        boolean on = true;
        try {
            on = prefs.getBoolean(KEY, true);
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
        android.content.Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE);
    }

    // ------------------------------------------------ where the calls land

    public static boolean available(Music music) {
        if (music == null) return false;
        return isEnabled() || music.available();
    }

    public static int getMusicStatus(Music music) {
        if (music == null) return 0;
        return isEnabled() ? AVAILABLE : music.getMusicStatus();
    }

    public static boolean isMuteShare(Music music) {
        if (music == null) return false;
        return !isEnabled() && music.isMuteShare();
    }

    public static int getMuteType(Music music) {
        if (music == null) return NOT_MUTED;
        return isEnabled() ? NOT_MUTED : music.getMuteType();
    }
}
