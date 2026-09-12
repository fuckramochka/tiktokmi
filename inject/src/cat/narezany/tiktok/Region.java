package cat.narezany.tiktok;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Цель перенаправления call-site'ов.
 *
 * Каждый вызов вида
 *     invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
 * заменяется на
 *     invoke-static  {v0}, Lcat/narezany/tiktok/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
 *
 * Формат инструкции (35c), количество регистров и возвращаемый тип совпадают,
 * поэтому подстановка не требует пересчёта регистров.
 *
 * Ресивер принимается первым аргументом и игнорируется — он нужен только
 * чтобы арность совпала с оригиналом.
 */
public final class Region {

    private Region() {}

    public static final String PREFS = "margyt";
    public static final String KEY_ENABLED = "region_enabled";
    public static final String KEY_COUNTRY = "region_country";

    /** iso, mcc+mnc, оператор, таймзона, человеческое имя */
    public static final String[][] COUNTRIES = {
            {"nl", "20408", "KPN",            "Europe/Amsterdam",  "Нидерланды"},
            {"us", "310410", "AT&T",          "America/New_York",  "США"},
            {"gb", "23430", "EE",             "Europe/London",     "Великобритания"},
            {"de", "26201", "Telekom",        "Europe/Berlin",     "Германия"},
            {"fr", "20801", "Orange",         "Europe/Paris",      "Франция"},
            {"es", "21401", "Movistar",       "Europe/Madrid",     "Испания"},
            {"it", "22201", "TIM",            "Europe/Rome",       "Италия"},
            {"se", "24001", "Telia",          "Europe/Stockholm",  "Швеция"},
            {"pl", "26003", "Orange",         "Europe/Warsaw",     "Польша"},
            {"ua", "25503", "Kyivstar",       "Europe/Kyiv",       "Украина"},
            {"kz", "40101", "Beeline",        "Asia/Almaty",       "Казахстан"},
            {"ru", "25001", "MTS",            "Europe/Moscow",     "Россия"},
            {"tr", "28601", "Turkcell",       "Europe/Istanbul",   "Турция"},
            {"br", "72406", "Vivo",           "America/Sao_Paulo", "Бразилия"},
            {"mx", "33403", "Telcel",         "America/Mexico_City", "Мексика"},
            {"ca", "302220", "Telus",         "America/Toronto",   "Канада"},
            {"au", "50501", "Telstra",        "Australia/Sydney",  "Австралия"},
            {"jp", "44010", "NTT Docomo",     "Asia/Tokyo",        "Япония"},
            {"kr", "45005", "SK Telecom",     "Asia/Seoul",        "Южная Корея"},
            {"in", "40410", "Airtel",         "Asia/Kolkata",      "Индия"},
            {"id", "51010", "Telkomsel",      "Asia/Jakarta",      "Индонезия"},
            {"vn", "45201", "Viettel",        "Asia/Ho_Chi_Minh",  "Вьетнам"},
            {"th", "52001", "AIS",            "Asia/Bangkok",      "Таиланд"},
            {"ph", "51502", "Globe",          "Asia/Manila",       "Филиппины"},
    };

    private static final int ISO = 0, MCCMNC = 1, CARRIER = 2, TZ = 3, LABEL = 4;
    private static final String DEFAULT_ISO = "nl";

    private static Context sContext;
    private static volatile String[] sRow;

    // ---------------------------------------------------------------- bootstrap

    /** Вызывается из инжекта в AwemeHostApplication.onCreate(). */
    public static void init(Context context) {
        if (context == null) return;
        sContext = context.getApplicationContext();
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
     * Меняем только страну в Locale — язык интерфейса оставляем как есть,
     * иначе TikTok уедет на нидерландский.
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

    /** Таймзону по умолчанию не трогаем: ломает время публикаций. */
    public static void applyTimeZone() {
        if (!isEnabled()) return;
        try { TimeZone.setDefault(TimeZone.getTimeZone(row()[TZ])); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------- цели перенаправления

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
     * Без этого на телефоне без SIM приложение уходит в ветку «SIM нет»
     * и всё остальное просто не читает.
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

    /** android.os.SystemProperties.get(...) — читается рефлексией. */
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
