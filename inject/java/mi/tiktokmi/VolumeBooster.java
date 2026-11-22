package mi.tiktokmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.LoudnessEnhancer;

/**
 * Boost audio loudness digitally above standard system limits.
 */
public final class VolumeBooster {

    private VolumeBooster() {}

    public static final String KEY = "volume_boost_enabled";
    public static final String KEY_GAIN = "volume_boost_gain";

    public static final int[] GAIN_VALUES = {500, 1000, 1500, 2000};
    public static final String[] GAIN_LABELS = {"+5 dB", "+10 dB", "+15 dB", "+20 dB"};

    private static volatile Boolean cachedEnabled;
    private static volatile Integer cachedGain;
    private static LoudnessEnhancer enhancer;

    static void reloadSettings() {
        cachedEnabled = null;
        cachedGain = null;
    }

    public static boolean isEnabled() {
        Boolean known = cachedEnabled;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        boolean on = false;
        try {
            on = prefs.getBoolean(KEY, false);
        } catch (Throwable ignored) {}
        cachedEnabled = on;
        return on;
    }

    public static void setEnabled(boolean enabled) {
        cachedEnabled = enabled;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY, enabled).apply();
        apply();
    }

    public static int gain() {
        Integer known = cachedGain;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        if (prefs == null) return 1000;
        int g = 1000;
        try {
            g = prefs.getInt(KEY_GAIN, 1000);
        } catch (Throwable ignored) {}
        cachedGain = g;
        return g;
    }

    public static void setGain(int value) {
        cachedGain = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_GAIN, value).apply();
        apply();
    }

    public static int gainIndex() {
        int current = gain();
        for (int i = 0; i < GAIN_VALUES.length; i++) {
            if (GAIN_VALUES[i] == current) return i;
        }
        return 1;
    }

    public static void setGainIndex(int index) {
        if (index >= 0 && index < GAIN_VALUES.length) {
            setGain(GAIN_VALUES[index]);
        }
    }

    public static String label() {
        if (!isEnabled()) return Text.VOLUME_BOOST_OFF;
        return GAIN_LABELS[gainIndex()];
    }

    public static synchronized void apply() {
        if (!isEnabled()) {
            release();
            return;
        }
        try {
            if (enhancer == null) {
                enhancer = new LoudnessEnhancer(0);
            }
            enhancer.setTargetGain(gain());
            enhancer.setEnabled(true);
        } catch (Throwable t) {
            Diary.note("volume booster: " + t);
        }
    }

    public static synchronized void release() {
        try {
            if (enhancer != null) {
                enhancer.setEnabled(false);
                enhancer.release();
                enhancer = null;
            }
        } catch (Throwable ignored) {}
    }

    public static void choose(Context context) {
        new ModDialog.Builder(context).setItems(new String[]{
            Text.VOLUME_BOOST_OFF,
            GAIN_LABELS[0],
            GAIN_LABELS[1],
            GAIN_LABELS[2],
            GAIN_LABELS[3]
        }, (dialog, index) -> {
            if (index == 0) {
                setEnabled(false);
            } else {
                setEnabled(true);
                setGainIndex(index - 1);
            }
        }).show();
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
}
