package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.TextView;

import java.io.File;

/**
 * One typeface, everywhere TikTok writes.
 *
 * There is no single place an app's font is decided, so this works from two
 * ends. Anywhere TikTok sets a typeface itself the call is rewritten to come
 * through here and is answered with the chosen one. And every piece of text on
 * its way into a view already passes the mod -- that is how a badge becomes a
 * picture -- so the font is set there too, which reaches the great many views
 * that never ask for a typeface at all and simply inherit one.
 *
 * The choices are the ones the phone already has, plus any `.ttf` or `.otf`
 * file you point at: it is copied into the mod's own folder, because a file
 * picked out of Downloads is a borrowed handle that will not be readable on
 * the next start.
 */
public final class Fonts {

    private Fonts() {}

    public static final String KEY = "font";
    public static final String KEY_EMOJI = "font_emoji";

    /**
     * The emoji packs on offer, each fetched the first time it is chosen.
     *
     * All of them are open: Twemoji is CC-BY, Noto is under the Open Font
     * Licence, Blobmoji is Noto's older round faces kept going by somebody
     * else under the same licence. Apple's is not here and will not be -- it
     * is theirs and not redistributable -- but any font file will do, so
     * nothing stops you pointing at one you own.
     */
    public static final String TWEMOJI = "twemoji";
    public static final String NOTO = "noto";
    public static final String BLOBMOJI = "blobmoji";
    public static final String EMOJI_FILE = "emoji_file";

    /**
     * Every pack rides inside the apk. Nothing is fetched, ever.
     *
     * They came to twenty-four megabytes between them, on an apk that is three
     * hundred and sixty -- which is a better trade than a setting that says it
     * is downloading something and gives no sign of when it will be done.
     */
    public static final String TWEMOJI_ASSET = "margyt/twemoji.ttf";

    private static String packAsset(String which) {
        if (TWEMOJI.equals(which)) return TWEMOJI_ASSET;
        if (NOTO.equals(which)) return "margyt/noto.ttf";
        if (BLOBMOJI.equals(which)) return "margyt/blobmoji.ttf";
        return null;
    }

    /** The names of the ones that need no file. */
    public static final String SYSTEM = "";
    public static final String SANS = "sans-serif";
    public static final String SANS_LIGHT = "sans-serif-light";
    public static final String SANS_CONDENSED = "sans-serif-condensed";
    public static final String SERIF = "serif";
    public static final String MONOSPACE = "monospace";
    public static final String CURSIVE = "cursive";
    public static final String FILE = "file";

    public static final String[] EMOJI_PACKS = {SYSTEM, TWEMOJI, NOTO, BLOBMOJI};

    public static final String[] PRESETS = {
            SYSTEM, SANS, SANS_LIGHT, SANS_CONDENSED, SERIF, MONOSPACE, CURSIVE,
    };

    private static volatile String chosen;
    private static volatile String chosenEmoji;
    private static volatile Typeface face;
    private static volatile boolean looked;

    // ---------------------------------------------------------- the choice

    public static String name() {
        String known = chosen;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        String value = prefs == null ? SYSTEM : prefs.getString(KEY, SYSTEM);
        chosen = value;
        return value;
    }

    public static void choose(String value) {
        if (value == null) value = SYSTEM;
        chosen = value;
        forget();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY, value).apply();
    }

    public static String emoji() {
        String known = chosenEmoji;
        if (known != null) return known;
        SharedPreferences prefs = prefs();
        String value = prefs == null ? SYSTEM : prefs.getString(KEY_EMOJI, SYSTEM);
        chosenEmoji = value;
        return value;
    }

    public static void chooseEmoji(Context context, String value) {
        if (value == null) value = SYSTEM;
        chosenEmoji = value;
        forget();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_EMOJI, value).apply();
    }

    private static void forget() {
        face = null;
        looked = false;
    }

    /** Every pack is in the apk, so one is ready the moment it is chosen. */
    public static boolean emojiReady(Context context) {
        String which = emoji();
        if (EMOJI_FILE.equals(which)) return emojiFile(context, which).isFile();
        return true;
    }

    /**
     * Twemoji as a typeface, straight out of the apk.
     *
     * Used for the flags beside the countries whatever else is chosen: a flag
     * is the one emoji where the phone's own is often a pair of letters in a
     * box, and this one is always a flag.
     */
    public static Typeface twemoji(Context context) {
        Typeface known = bundled;
        if (known != null) return known;
        try {
            known = Typeface.createFromAsset(context.getAssets(), TWEMOJI_ASSET);
            bundled = known;
            return known;
        } catch (Throwable error) {
            Diary.note("twemoji: " + error);
            return null;
        }
    }

    private static volatile Typeface bundled;

    /** Where a font picked out of the phone's storage is kept. */
    public static File file(Context context) {
        return new File(context.getFilesDir(), "margyt/font");
    }

    /**
     * Take a font the person picked.
     *
     * Copied rather than remembered: the uri a picker hands over is readable
     * now and not after a restart, and a font that vanishes overnight would be
     * a mystery rather than a setting.
     */
    public static boolean take(Context context, android.net.Uri uri) {
        return take(context, uri, false);
    }

    public static boolean take(Context context, android.net.Uri uri, boolean forEmoji) {
        try {
            java.io.InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            File out = forEmoji ? emojiFile(context, EMOJI_FILE) : file(context);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            java.io.FileOutputStream sink = new java.io.FileOutputStream(out);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) sink.write(buffer, 0, read);
            sink.close();
            in.close();

            // refuse it here rather than have every screen fall back silently
            Typeface test = Typeface.createFromFile(out);
            if (test == null) return false;
            if (forEmoji) chooseEmoji(context, EMOJI_FILE);
            else choose(FILE);
            return true;
        } catch (Throwable error) {
            Diary.note("font: " + error);
            return false;
        }
    }

    private static File emojiFile(Context context, String which) {
        return new File(context.getFilesDir(), "margyt/emoji-" + which);
    }

    // ----------------------------------------------------------- using it

    /** The chosen typeface, or null to leave whatever was there alone. */
    public static Typeface chosenFace() {
        if (looked) return face;
        looked = true;
        face = build();
        return face;
    }

    /**
     * The letters and the emoji, as one typeface.
     *
     * Android will not let an app replace only the emoji, because a typeface
     * covers whatever it covers and an emoji font has no letters in it. What
     * it will do, from Android 10, is take a family and a list of families to
     * fall back to -- so the answer is one typeface built out of two: the
     * letters from the chosen font, and anything the letters do not cover from
     * the emoji font. Which is exactly what the phone's own font does, with
     * its own emoji font at the end.
     *
     * Below Android 10 there is no such thing, so the letters are changed and
     * the emoji stay the phone's own.
     */
    private static Typeface build() {
        Context context = Margy.context();
        String letters = name();
        String emoji = emoji();

        Typeface plain = null;
        try {
            if (FILE.equals(letters)) {
                File font = context == null ? null : file(context);
                if (font != null && font.isFile()) plain = Typeface.createFromFile(font);
            } else if (!SYSTEM.equals(letters)) {
                plain = Typeface.create(letters, Typeface.NORMAL);
            }
        } catch (Throwable error) {
            Diary.note("font: " + error);
        }

        if (context == null || SYSTEM.equals(emoji)
                || android.os.Build.VERSION.SDK_INT < 29) {
            if (context == null) looked = false;
            return plain;
        }

        String asset = packAsset(emoji);
        if (asset != null) return hybrid(context, letters, null, asset);

        File pack = emojiFile(context, emoji);
        if (!pack.isFile()) return plain;
        return hybrid(context, letters, pack, null);
    }

    /**
     * The letters and the emoji as one typeface.
     *
     * `pack` is a file on disk, or null for the one inside the apk -- which is
     * read through the asset manager rather than as a file, because an asset
     * is not one.
     */
    private static Typeface hybrid(Context context, String letters, File pack,
                                   String asset) {
        try {
            File base = FILE.equals(letters) ? file(context) : systemFont();
            if (base == null || !base.isFile()) return null;

            android.graphics.fonts.FontFamily letterFamily =
                    new android.graphics.fonts.FontFamily.Builder(
                            new android.graphics.fonts.Font.Builder(base).build()).build();

            android.graphics.fonts.Font.Builder emojiFont = asset != null
                    ? new android.graphics.fonts.Font.Builder(context.getAssets(), asset)
                    : new android.graphics.fonts.Font.Builder(pack);
            android.graphics.fonts.FontFamily emojiFamily =
                    new android.graphics.fonts.FontFamily.Builder(emojiFont.build()).build();

            return new Typeface.CustomFallbackBuilder(letterFamily)
                    .addCustomFallback(emojiFamily)
                    .build();
        } catch (Throwable error) {
            Diary.note("emoji: " + error);
            return null;
        }
    }

    /** A plain font file the phone already has, to build the letters from. */
    private static File systemFont() {
        String[] candidates = {
                "/system/fonts/Roboto-Regular.ttf",
                "/system/fonts/NotoSans-Regular.ttf",
                "/system/fonts/DroidSans.ttf",
        };
        for (String path : candidates) {
            File file = new File(path);
            if (file.isFile()) return file;
        }
        return null;
    }

    /**
     * Set the font on a view that is about to show text.
     *
     * Keeps the weight the view already had: a bold name stays bold, because
     * what is being changed is the shape of the letters and not the emphasis.
     */
    public static void apply(TextView view) {
        Typeface wanted = chosenFace();
        if (wanted == null || view == null) return;
        try {
            Typeface had = view.getTypeface();
            int style = had == null ? Typeface.NORMAL : had.getStyle();
            Typeface out = style == Typeface.NORMAL
                    ? wanted : Typeface.create(wanted, style);
            if (out != had) view.setTypeface(out);
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------- where TikTok sets one

    public static void setTypeface(TextView view, Typeface face) {
        Typeface wanted = chosenFace();
        view.setTypeface(wanted == null ? face : keep(wanted, face));
    }

    public static void setTypeface(TextView view, Typeface face, int style) {
        Typeface wanted = chosenFace();
        view.setTypeface(wanted == null ? face : wanted, style);
    }

    public static Typeface setTypeface(android.graphics.Paint paint, Typeface face) {
        Typeface wanted = chosenFace();
        return paint.setTypeface(wanted == null ? face : keep(wanted, face));
    }

    /** Ours, at the weight theirs was going to be. */
    private static Typeface keep(Typeface wanted, Typeface theirs) {
        try {
            if (theirs == null || theirs.getStyle() == Typeface.NORMAL) return wanted;
            return Typeface.create(wanted, theirs.getStyle());
        } catch (Throwable ignored) {
            return wanted;
        }
    }

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        if (context == null) return null;
        return context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }
}
