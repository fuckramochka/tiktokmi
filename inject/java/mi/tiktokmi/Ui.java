package mi.tiktokmi;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

/**
 * Taking the shop out of the page.
 *
 * The feed filter drops whole posts, but the Shop tab, the sponsored blocks
 * and the commerce banners are furniture, not posts: they are views, drawn
 * from resources whose names -- unlike the code around them -- the obfuscator
 * leaves alone. So this walks the same tree the OLED pass walks, matches
 * whole name segments rather than substrings (so "leader" never reads as
 * "ad"), and sets the match aside instead of drawing it.
 *
 * Off means off: every view remembers what it was, and disabling the switch
 * puts it back. A build that renamed its resources is a build where nothing
 * matches, which is a switch that does nothing rather than a screen with
 * holes in it.
 */
public final class Ui {

    private Ui() {}

    public static final String KEY = "ui_clean";

    private static volatile Boolean cached;

    public static boolean isEnabled() {
        Boolean known = cached;
        if (known != null) return known.booleanValue();
        boolean on = false;
        try {
            SharedPreferences prefs = prefs();
            if (prefs != null) on = prefs.getBoolean(KEY, false);
        } catch (Throwable ignored) {
        }
        cached = Boolean.valueOf(on);
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cached = Boolean.valueOf(enabled);
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

    // setTag(int, ...) wants a key that looks like a resource id, and every
    // key the mod uses has to differ from every other one.
    private static final int CLEAN_TAG = 0x55634C6E;  // "UcLn"
    private static final int BUDGET = 2000;
    private static int seen;

    private static final String[] SEGMENTS = {
        "shop", "commerce", "store",
        "sponsored", "promoted", "promotion", "advertisement",
        "ad", "ads"
    };

    /** Hides the commerce furniture under the given root, or puts it back. */
    public static void applyClean(View root) {
        if (root == null) return;
        try {
            seen = 0;
            walk(root, isEnabled(), 0);
        } catch (Throwable ignored) {
        }
    }

    private static void walk(View view, boolean enabled, int depth) {
        if (view == null || depth > 30 || ++seen > BUDGET) return;
        try {
            if (enabled) {
                if (view.getVisibility() == View.VISIBLE && matches(view)) {
                    view.setTag(CLEAN_TAG, Integer.valueOf(view.getVisibility()));
                    view.setVisibility(View.GONE);
                }
            } else if (view.getTag(CLEAN_TAG) instanceof Integer) {
                view.setVisibility(((Integer) view.getTag(CLEAN_TAG)).intValue());
                view.setTag(CLEAN_TAG, null);
            }
        } catch (Throwable ignored) {
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                walk(group.getChildAt(i), enabled, depth + 1);
            }
        }
    }

    private static boolean matches(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return false;
        String name;
        try {
            name = view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return false;
        }
        if (name == null) return false;
        String[] parts = name.toLowerCase(java.util.Locale.US).split("_");
        for (String part : parts) {
            for (String segment : SEGMENTS) {
                if (part.equals(segment)) return true;
            }
        }
        return false;
    }
}
