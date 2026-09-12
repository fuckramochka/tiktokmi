package cat.narezany.tiktok;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Where the rewritten call sites land.
 *
 * Every
 *     invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
 * becomes
 *     invoke-static  {v0}, Lcat/narezany/tiktok/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
 *
 * The instruction format (35c), the register count and the return type all
 * match, so nothing has to be renumbered.
 *
 * The receiver arrives as the first argument and is ignored -- it is there only
 * to keep the arity the same as the method it replaced.
 */
public final class Region {

    private Region() {}

    public static final String PREFS = "margyt";
    public static final String KEY_ENABLED = "region_enabled";
    public static final String KEY_COUNTRY = "region_country";

    /** iso, mcc+mnc, carrier, time zone, display name */
    public static final String[][] COUNTRIES = {
            {"nl", "20408",  "KPN",        "Europe/Amsterdam",    "Netherlands"},
            {"us", "310410", "AT&T",       "America/New_York",    "United States"},
            {"gb", "23430",  "EE",         "Europe/London",       "United Kingdom"},
            {"de", "26201",  "Telekom",    "Europe/Berlin",       "Germany"},
            {"fr", "20801",  "Orange",     "Europe/Paris",        "France"},
            {"es", "21401",  "Movistar",   "Europe/Madrid",       "Spain"},
            {"it", "22201",  "TIM",        "Europe/Rome",         "Italy"},
            {"se", "24001",  "Telia",      "Europe/Stockholm",    "Sweden"},
            {"pl", "26003",  "Orange",     "Europe/Warsaw",       "Poland"},
            {"ua", "25503",  "Kyivstar",   "Europe/Kyiv",         "Ukraine"},
            {"kz", "40101",  "Beeline",    "Asia/Almaty",         "Kazakhstan"},
            {"ru", "25001",  "MTS",        "Europe/Moscow",       "Russia"},
            {"tr", "28601",  "Turkcell",   "Europe/Istanbul",     "Turkey"},
            {"br", "72406",  "Vivo",       "America/Sao_Paulo",   "Brazil"},
            {"mx", "33403",  "Telcel",     "America/Mexico_City", "Mexico"},
            {"ca", "302220", "Telus",      "America/Toronto",     "Canada"},
            {"au", "50501",  "Telstra",    "Australia/Sydney",    "Australia"},
            {"jp", "44010",  "NTT Docomo", "Asia/Tokyo",          "Japan"},
            {"kr", "45005",  "SK Telecom", "Asia/Seoul",          "South Korea"},
            {"in", "40410",  "Airtel",     "Asia/Kolkata",        "India"},
            {"id", "51010",  "Telkomsel",  "Asia/Jakarta",        "Indonesia"},
            {"vn", "45201",  "Viettel",    "Asia/Ho_Chi_Minh",    "Vietnam"},
            {"th", "52001",  "AIS",        "Asia/Bangkok",        "Thailand"},
            {"ph", "51502",  "Globe",      "Asia/Manila",         "Philippines"},
    };

    private static final int ISO = 0, MCCMNC = 1, CARRIER = 2, TZ = 3, LABEL = 4;
    private static final String DEFAULT_ISO = "nl";

    private static Context sContext;
    private static volatile String[] sRow;

    // ---------------------------------------------------------------- bootstrap

    /**
     * Called from the injections in AwemeHostApplication -- once from
     * attachBaseContext, which is early enough to beat the app's own startup
     * work, and again from onCreate.
     *
     * getApplicationContext() can still be null that early, so the base context
     * is kept instead of giving up: worse than the application context, but it
     * opens SharedPreferences all the same.
     */
    public static void init(Context context) {
        if (context == null) return;
        Context app = null;
        try { app = context.getApplicationContext(); } catch (Throwable ignored) {}
        sContext = app != null ? app : context;
        sRow = null;
        applyLocale();
    }

    public static SharedPreferences prefs() {
        return sContext == null ? null : sContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(KEY_ENABLED, enabled).apply();
        sRow = null;
    }

    public static String currentIso() {
        SharedPreferences p = prefs();
        return p == null ? DEFAULT_ISO : p.getString(KEY_COUNTRY, DEFAULT_ISO);
    }

    public static void setCountry(String iso) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putString(KEY_COUNTRY, iso).apply();
        sRow = null;
        applyLocale();
    }

    private static String[] row() {
        String[] cached = sRow;
        if (cached != null) return cached;
        String iso = currentIso();
        for (String[] c : COUNTRIES) {
            if (c[ISO].equals(iso)) { sRow = c; return c; }
        }
        sRow = COUNTRIES[0];
        return sRow;
    }

    /**
     * Country only. The language stays whatever the user had -- set the whole
     * locale and TikTok switches its interface to Dutch along with the region.
     */
    private static void applyLocale() {
        if (!isEnabled()) return;
        try {
            Locale cur = Locale.getDefault();
            String lang = cur.getLanguage();
            if (lang == null || lang.isEmpty()) lang = "en";
            Locale.setDefault(new Locale(lang, row()[ISO].toUpperCase(Locale.ROOT)));
        } catch (Throwable ignored) {}
    }

    /** Off by default: moving the clock moves the timestamps on posts. */
    public static void applyTimeZone() {
        if (!isEnabled()) return;
        try { TimeZone.setDefault(TimeZone.getTimeZone(row()[TZ])); } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------- call-site targets

    public static String getSimCountryIso(TelephonyManager tm) {
        return isEnabled() ? row()[ISO] : safeIso(tm, true);
    }

    public static String getSimCountryIso(TelephonyManager tm, int subId) {
        return getSimCountryIso(tm);
    }

    public static String getNetworkCountryIso(TelephonyManager tm) {
        return isEnabled() ? row()[ISO] : safeIso(tm, false);
    }

    public static String getNetworkCountryIso(TelephonyManager tm, int subId) {
        return getNetworkCountryIso(tm);
    }

    public static String getSimOperator(TelephonyManager tm) {
        return isEnabled() ? row()[MCCMNC] : orEmpty(tm == null ? null : tm.getSimOperator());
    }

    public static String getSimOperator(TelephonyManager tm, int subId) {
        return getSimOperator(tm);
    }

    public static String getNetworkOperator(TelephonyManager tm) {
        return isEnabled() ? row()[MCCMNC] : orEmpty(tm == null ? null : tm.getNetworkOperator());
    }

    public static String getNetworkOperator(TelephonyManager tm, int subId) {
        return getNetworkOperator(tm);
    }

    public static String getSimOperatorName(TelephonyManager tm) {
        return isEnabled() ? row()[CARRIER] : orEmpty(tm == null ? null : tm.getSimOperatorName());
    }

    public static String getNetworkOperatorName(TelephonyManager tm) {
        return isEnabled() ? row()[CARRIER] : orEmpty(tm == null ? null : tm.getNetworkOperatorName());
    }

    /**
     * Needed on a phone with no card at all: without it the app takes the
     * "no SIM" branch and never reads any of the rest.
     */
    public static int getSimState(TelephonyManager tm) {
        if (isEnabled()) return TelephonyManager.SIM_STATE_READY;
        return tm == null ? TelephonyManager.SIM_STATE_UNKNOWN : tm.getSimState();
    }

    public static int getSimState(TelephonyManager tm, int slot) {
        return getSimState(tm);
    }

    public static boolean hasIccCard(TelephonyManager tm) {
        return isEnabled() || (tm != null && tm.hasIccCard());
    }

    public static boolean isNetworkRoaming(TelephonyManager tm) {
        return !isEnabled() && tm != null && tm.isNetworkRoaming();
    }

    public static int getSimCarrierId(TelephonyManager tm) {
        if (isEnabled()) return 1;
        return tm == null ? -1 : tm.getSimCarrierId();
    }

    /** android.os.SystemProperties.get(...), which is read by reflection. */
    public static String systemProperty(String key, String fallback) {
        if (!isEnabled() || key == null) return fallback;
        String[] r = row();
        switch (key) {
            case "gsm.sim.operator.iso-country":
            case "gsm.operator.iso-country":
                return r[ISO];
            case "gsm.sim.operator.numeric":
            case "gsm.operator.numeric":
                return r[MCCMNC];
            case "gsm.sim.operator.alpha":
            case "gsm.operator.alpha":
                return r[CARRIER];
            case "persist.sys.country":
            case "ro.csc.countryiso_code":
                return r[ISO].toUpperCase(Locale.ROOT);
            default:
                return fallback;
        }
    }

    // ------------------------------------------------------------------ utils

    private static String safeIso(TelephonyManager tm, boolean sim) {
        if (tm == null) return "";
        try { return orEmpty(sim ? tm.getSimCountryIso() : tm.getNetworkCountryIso()); }
        catch (Throwable t) { return ""; }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    public static String labelFor(String iso) {
        for (String[] c : COUNTRIES) if (c[ISO].equals(iso)) return c[LABEL];
        return iso;
    }

    public static String carrierFor(String iso) {
        for (String[] c : COUNTRIES) if (c[ISO].equals(iso)) return c[CARRIER] + " · " + c[MCCMNC];
        return "";
    }
}
