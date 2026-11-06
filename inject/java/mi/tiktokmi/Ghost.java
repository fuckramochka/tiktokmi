package mi.tiktokmi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

public final class Ghost {

    static final String KEY = "ghost";
    static final String ONLINE = "ghost_online", READ = "ghost_read", TYPING = "ghost_typing",
            STORIES = "ghost_stories", PROFILES = "ghost_profiles";
    static final String LONG_COMMENTS = "comments_long";

    private static GhostMode mode;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Activity shown;
    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (shown == null || !on(KEY)) return;
            apply();
            main.postDelayed(this, 1000L);
        }
    };

    private Ghost() {}

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(String key) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(key, false);
    }

    static void set(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
        main.post(Ghost::apply);
    }

    static void start() {
        try {
            mode = new GhostMode(Ghost::on, message -> Diary.note("ghost: " + message));
            mode.bootstrap();
        } catch (Throwable error) {
            Diary.note("ghost start: " + error);
        }
    }

    static void resumed(Activity activity) {
        shown = activity;
        main.removeCallbacks(tick);
        apply();
        // once a second only while the ghost is on: off, nothing needs keeping up
        if (on(KEY)) main.postDelayed(tick, 1000L);
    }

    static void paused(Activity activity) {
        if (shown == activity) {
            shown = null;
            main.removeCallbacks(tick);
        }
    }

    static void apply() {
        try {
            if (mode != null) mode.apply();
        } catch (Throwable error) {
            Diary.note("ghost apply: " + error);
        }
        // nobody wants a comment cut short: always on, with no switch
        LongComments.apply(true);
    }

    static String report() {
        return mode == null ? "не запущен" : mode.report();
    }
}
