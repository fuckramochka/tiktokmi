package mi.tiktokmi;

import android.content.SharedPreferences;

import java.lang.reflect.Method;

/**
 * The scrubbing bar, on every video rather than some.
 *
 * TikTok decides per video whether to draw one, and passes that decision to a
 * view as a number: `setSeekBarShowType`. The method's name is real; the class
 * that has it is not, and will be spelled differently next release. So the
 * rewrite matches the method wherever it is -- any owner, that name, that
 * shape -- and the receiver arrives here as a plain Object.
 *
 * Which means the call back has to go through reflection, and that is fine:
 * this runs when a video is set up, not when one is drawn.
 */
public final class Seekbar {

    private Seekbar() {}

    public static final String KEY = "seekbar_always";

    /** The value that means "show it". The others hide or defer it. */
    private static final int ALWAYS = 0;

    private static volatile Boolean cached;
    private static volatile Method original;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return true;
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

    // ------------------------------------------------- where the call lands

    public static void setSeekBarShowType(Object view, int type) {
        if (view == null) return;
        try {
            Method setter = original;
            if (setter == null || !setter.getDeclaringClass().isInstance(view)) {
                setter = view.getClass().getMethod("setSeekBarShowType", int.class);
                setter.setAccessible(true);
                original = setter;
            }
            setter.invoke(view, Integer.valueOf(isEnabled() ? ALWAYS : type));
        } catch (Throwable error) {
            // a release that moved the method is a release without this
            // feature, not one where the video fails to open
            Diary.note("seekbar: " + error);
        }
    }
}
