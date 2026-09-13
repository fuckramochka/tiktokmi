package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What the mod saw, kept so it can be read off the phone.
 *
 * A mod cannot be attached to a debugger and nobody is going to run logcat
 * against a modded TikTok. So the few things worth knowing -- did the start-up
 * hook run, which screen was that, what was its view made of, did the row go in
 * -- are written down here and shown at the bottom of MargyT's own settings.
 *
 * It survives the app being killed, which is the whole point: reaching MargyT's
 * settings can mean leaving TikTok first, and a diary that lives only in memory
 * is empty by the time anyone reads it.
 */
public final class Diary {

    private Diary() {}

    private static final String KEY = "diary";
    private static final int KEEP = 40;

    private static final List<String> LINES = new ArrayList<String>();
    private static boolean loaded;

    public static void note(String line) {
        String stamped = stamp() + "  " + line;
        synchronized (LINES) {
            load();
            String last = LINES.isEmpty() ? "" : LINES.get(LINES.size() - 1);
            if (last.length() > 10 && last.substring(10).equals(stamped.substring(10))) return;
            LINES.add(stamped);
            while (LINES.size() > KEEP) LINES.remove(0);
            save();
        }
    }

    public static List<String> lines() {
        synchronized (LINES) {
            load();
            return new ArrayList<String>(LINES);
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
            loaded = true;
            save();
        }
    }

    // ------------------------------------------------------------- storage

    private static void load() {
        if (loaded) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;  // no context yet: try again next time
        loaded = true;
        String stored = prefs.getString(KEY, "");
        if (stored.length() > 0) LINES.addAll(Arrays.asList(stored.split("\n")));
    }

    private static void save() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        StringBuilder out = new StringBuilder();
        for (String line : LINES) {
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        prefs.edit().putString(KEY, out.toString()).apply();
    }

    private static SharedPreferences prefs() {
        try {
            Context context = Margy.context();
            if (context == null) return null;
            return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
