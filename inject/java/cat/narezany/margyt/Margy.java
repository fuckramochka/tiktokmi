package cat.narezany.margyt;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * The mod's settings, and the one piece of context it needs to read them.
 *
 * Nothing calls an init() from the app's startup: there is no hook to put one
 * in without patching TikTok's Application class, and that class moves between
 * releases. The context comes from ActivityThread instead, which every Android
 * process has had since 2008 and which is what Application itself is built on.
 */
public final class Margy {

    private Margy() {}

    public static final String PREFS = "margyt";
    public static final String KEY_ENABLED = "region_enabled";
    public static final String KEY_COUNTRY = "region_country";

    public static final String DEFAULT_ISO = "nl";

    /** iso, mcc+mnc, carrier, display name */
    public static final String[][] COUNTRIES = {
            {"nl", "20408",  "KPN",        "Netherlands"},
            {"us", "310410", "AT&T",       "United States"},
            {"gb", "23430",  "EE",         "United Kingdom"},
            {"de", "26201",  "Telekom",    "Germany"},
            {"fr", "20801",  "Orange",     "France"},
            {"es", "21401",  "Movistar",   "Spain"},
            {"it", "22201",  "TIM",        "Italy"},
            {"se", "24001",  "Telia",      "Sweden"},
            {"pl", "26003",  "Orange",     "Poland"},
            {"ua", "25503",  "Kyivstar",   "Ukraine"},
            {"kz", "40101",  "Beeline",    "Kazakhstan"},
            {"ru", "25001",  "MTS",        "Russia"},
            {"tr", "28601",  "Turkcell",   "Turkey"},
            {"br", "72406",  "Vivo",       "Brazil"},
            {"mx", "33403",  "Telcel",     "Mexico"},
            {"ca", "302220", "Telus",      "Canada"},
            {"au", "50501",  "Telstra",    "Australia"},
            {"jp", "44010",  "NTT Docomo", "Japan"},
            {"kr", "45005",  "SK Telecom", "South Korea"},
            {"in", "40410",  "Airtel",     "India"},
            {"id", "51010",  "Telkomsel",  "Indonesia"},
            {"vn", "45201",  "Viettel",    "Vietnam"},
            {"th", "52001",  "AIS",        "Thailand"},
            {"ph", "51502",  "Globe",      "Philippines"},
    };

    public static final int ISO = 0, MCCMNC = 1, CARRIER = 2, LABEL = 3;

    private static volatile Context sContext;

    // ------------------------------------------------------------- context

    public static Context context() {
        Context known = sContext;
        if (known != null) return known;
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object app = thread.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) {
                sContext = (Context) app;
            }
        } catch (Throwable ignored) {
            // too early in startup, or a stripped-down runtime: fall back to
            // the defaults until someone asks again
        }
        return sContext;
    }

    /** Set from the settings screen, where a context is never in doubt. */
    public static void attach(Context context) {
        if (context != null) sContext = context.getApplicationContext();
    }

    private static SharedPreferences prefs() {
        Context context = context();
        if (context == null) return null;
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------ settings

    public static boolean isEnabled() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String iso() {
        SharedPreferences p = prefs();
        String iso = p == null ? DEFAULT_ISO : p.getString(KEY_COUNTRY, DEFAULT_ISO);
        return row(iso)[ISO];
    }

    public static void setIso(String iso) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString(KEY_COUNTRY, iso).apply();
    }

    /** The row for `iso`, or the default one when the list has never heard of it. */
    public static String[] row(String iso) {
        if (iso != null) {
            for (String[] country : COUNTRIES) {
                if (country[ISO].equals(iso)) return country;
            }
        }
        for (String[] country : COUNTRIES) {
            if (country[ISO].equals(DEFAULT_ISO)) return country;
        }
        return COUNTRIES[0];
    }

    /** The row the mod is currently reporting. */
    public static String[] current() {
        return row(iso());
    }

    /** True when the mod should be answering for the device at all. */
    public static boolean active() {
        return context() != null && isEnabled();
    }
}
