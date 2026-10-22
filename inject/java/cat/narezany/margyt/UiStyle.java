package cat.narezany.margyt;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An interface style: what Material 3, Liquid Glass and Fluent are made of, as
 * numbers, so that anybody can make another in a .mtui file.
 *
 * A style starts from one of the three ("base"), which decides what the
 * surfaces are made of (solid, refracting glass, or blurred acrylic) and how
 * groups of rows are laid out. On top of it a file can change the corners,
 * the gaps, the material's blur, tint, refraction and grain, the motion, the
 * controls (switches, sliders, loading) and the shade of pages and cards.
 * The three built-in styles are these same numbers with the values the mod
 * always had, so choosing one of them looks exactly as before.
 *
 * A .mtui is JSON and runs nothing. It is written up in docs/mtui.md.
 */
final class UiStyle {

    static final int MD3 = 0, GLASS = 1, FLUENT = 2;
    static final int FORMAT = 1;
    static final String EXTENSION = "mtui";
    private static final String[] BASES = {"md3", "glass", "fluent"};
    private static final int MOST_BYTES = 32 * 1024;

    /** "md3", "glass", "fluent", or "custom:" and the id of an installed file. */
    final String key, name, author, description;
    final int base, controls;
    /** Corners in dp: cards and groups, rows inside a group, sheets, small pieces; bar -1 is a pill. */
    final float card, inner, sheet, small, bar;
    /** Between the rows of a group, dp. */
    final float gap;
    /** The material of glass and acrylic surfaces. */
    final float blur, tint, refraction, dispersion, grain, windowBlur;
    /** Motion: how long, how much overshoot (0 none), how much a pressed surface scales. */
    final int duration;
    final float bounce, press;
    /** Pages in neutral greys rather than TikTok's own; how much lighter or darker a card is than its page. */
    final boolean neutral;
    final float cardLift;

    private UiStyle(String key, String name, String author, String description, int base, int controls,
                    float card, float inner, float sheet, float small, float bar, float gap,
                    float blur, float tint, float refraction, float dispersion, float grain, float windowBlur,
                    int duration, float bounce, float press, boolean neutral, float cardLift) {
        this.key = key; this.name = name; this.author = author; this.description = description;
        this.base = base; this.controls = controls;
        this.card = card; this.inner = inner; this.sheet = sheet; this.small = small; this.bar = bar; this.gap = gap;
        this.blur = blur; this.tint = tint; this.refraction = refraction; this.dispersion = dispersion;
        this.grain = grain; this.windowBlur = windowBlur;
        this.duration = duration; this.bounce = bounce; this.press = press;
        this.neutral = neutral; this.cardLift = cardLift;
    }

    static final UiStyle MATERIAL = new UiStyle("md3", "Material 3", "", "", MD3, MD3,
            16, 4, 28, 24, -1, 2, 1.45f, .24f, 1f, .065f, .003f, 0, 300, 1.6f, .985f, false, .045f);
    static final UiStyle LIQUID = new UiStyle("glass", "Liquid Glass", "", "", GLASS, GLASS,
            26, 6, 28, 24, -1, 0, 1.45f, .24f, 1f, .065f, .003f, 26, 300, 1.6f, 1.025f, false, .045f);
    static final UiStyle ACRYLIC = new UiStyle("fluent", "Fluent", "", "", FLUENT, FLUENT,
            8, 6, 8, 4, 8, 4, 1.15f, .24f, 0f, 0f, .003f, 4, 180, 0f, .995f, true, .075f);

    static UiStyle builtIn(int base) {
        return base == GLASS ? LIQUID : base == FLUENT ? ACRYLIC : MATERIAL;
    }

    static boolean custom(String key) { return key != null && key.startsWith("custom:"); }

    // ------------------------------------------------------------ reading

    /** A file that is not a style, said so a person can tell what is wrong with it. */
    static final class Refused extends Exception {
        Refused(String why) { super(why); }
    }

    static UiStyle read(byte[] raw, String id) throws Refused {
        if (raw == null || raw.length == 0) throw new Refused(Text.UI_FILE_EMPTY);
        if (raw.length > MOST_BYTES) throw new Refused(Text.UI_FILE_BIG);
        JSONObject json;
        try {
            json = new JSONObject(new String(raw, "UTF-8"));
        } catch (Throwable error) {
            throw new Refused(Text.UI_FILE_NOT_JSON);
        }
        int format = json.optInt("mtui", -1);
        if (format < 1) throw new Refused(Text.UI_FILE_NOT_UI);
        if (format > FORMAT) throw new Refused(Text.UI_FILE_NEWER);
        String name = json.optString("name", "").trim();
        if (name.isEmpty()) throw new Refused(Text.UI_FILE_NO_NAME);
        int base = engine(json.optString("base", ""), -1);
        if (base < 0) throw new Refused(Text.UI_FILE_NO_BASE);
        UiStyle from = builtIn(base);
        int controls = engine(json.optString("controls", BASES[base]), -1);
        if (controls < 0) throw new Refused(Text.UI_FILE_BAD_VALUE + " controls");
        JSONObject corners = section(json, "corners"), material = section(json, "material"),
                motion = section(json, "motion"), page = section(json, "page");
        return new UiStyle("custom:" + (id == null ? id(name) : id), cut(name, 40), cut(json.optString("author", "").trim(), 60),
                cut(json.optString("description", "").trim(), 400), base, controls,
                number(corners, "card", from.card, 0, 40), number(corners, "inner", from.inner, 0, 40),
                number(corners, "sheet", from.sheet, 0, 40), number(corners, "small", from.small, 0, 40),
                number(corners, "bar", from.bar, -1, 40), number(json, "gap", from.gap, 0, 16),
                number(material, "blur", from.blur, 0, 4), number(material, "tint", from.tint, 0, 1),
                number(material, "refraction", from.refraction, 0, 2), number(material, "dispersion", from.dispersion, 0, .3f),
                number(material, "grain", from.grain, 0, .05f), number(material, "windowBlur", from.windowBlur, 0, 60),
                Math.round(number(motion, "duration", from.duration, 60, 1000)), number(motion, "bounce", from.bounce, 0, 4),
                number(motion, "press", from.press, .9f, 1.1f),
                page != null && page.has("neutral") ? page.optBoolean("neutral", from.neutral) : from.neutral,
                number(page, "cardLift", from.cardLift, 0, .3f));
    }

    private static int engine(String text, int fallback) {
        for (int i = 0; i < BASES.length; i++) if (BASES[i].equals(text)) return i;
        return fallback;
    }

    private static JSONObject section(JSONObject json, String key) throws Refused {
        if (!json.has(key)) return null;
        JSONObject part = json.optJSONObject(key);
        if (part == null) throw new Refused(Text.UI_FILE_BAD_VALUE + " " + key);
        return part;
    }

    /** A number in its range, or what the base has when it is left out; anything else refuses the file. */
    private static float number(JSONObject json, String key, float fallback, float least, float most) throws Refused {
        if (json == null || !json.has(key)) return fallback;
        Object value = json.opt(key);
        if (!(value instanceof Number)) throw new Refused(Text.UI_FILE_BAD_VALUE + " " + key);
        float number = ((Number) value).floatValue();
        if (Float.isNaN(number) || number < least || number > most)
            throw new Refused(Text.UI_FILE_BAD_VALUE + " " + key + " (" + trim(least) + "…" + trim(most) + ")");
        return number;
    }

    private static String trim(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
    }

    private static String cut(String text, int most) {
        return text.length() <= most ? text : text.substring(0, most);
    }

    /** The file's name on the phone, from the style's: letters and digits only. */
    static String id(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        if (id.isEmpty()) id = "style";
        return id.length() > 40 ? id.substring(0, 40) : id;
    }

    // ------------------------------------------------------------ writing out

    /** This style as a file: every number written, so it is a starting point to change. */
    String json() {
        try {
            JSONObject json = new JSONObject();
            json.put("mtui", FORMAT);
            json.put("name", custom(key) ? name : name + " (" + Text.UI_FILE_COPY + ")");
            json.put("author", author);
            json.put("description", description);
            json.put("base", BASES[base]);
            json.put("controls", BASES[controls]);
            json.put("corners", new JSONObject().put("card", card).put("inner", inner).put("sheet", sheet)
                    .put("small", small).put("bar", bar));
            json.put("gap", gap);
            json.put("material", new JSONObject().put("blur", blur).put("tint", tint).put("refraction", refraction)
                    .put("dispersion", dispersion).put("grain", grain).put("windowBlur", windowBlur));
            json.put("motion", new JSONObject().put("duration", duration).put("bounce", bounce).put("press", press));
            json.put("page", new JSONObject().put("neutral", neutral).put("cardLift", cardLift));
            return json.toString(2);
        } catch (Throwable error) {
            return null;
        }
    }

    // ------------------------------------------------------------ the installed ones

    private static File folder(Context context) {
        File dir = new File(context.getFilesDir(), "margyt/ui");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** Kept on the phone under its id; a style of the same name is replaced. */
    static UiStyle install(Context context, byte[] raw) throws Refused {
        UiStyle style = read(raw, null);
        String id = style.key.substring("custom:".length());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(folder(context), id + "." + EXTENSION))) {
            out.write(raw);
        } catch (Throwable error) {
            throw new Refused(Text.UI_FILE_NOT_SAVED);
        }
        return style;
    }

    static UiStyle load(Context context, String key) {
        if (!custom(key) || context == null) return null;
        String id = key.substring("custom:".length());
        if (!id.matches("[\\p{L}\\p{N}-]{1,40}")) return null;
        File file = new File(folder(context), id + "." + EXTENSION);
        try {
            if (!file.isFile()) return null;
            byte[] raw = new byte[(int) Math.min(file.length(), MOST_BYTES + 1)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int at = 0;
                for (int n; at < raw.length && (n = in.read(raw, at, raw.length - at)) > 0; ) at += n;
            }
            return read(raw, id);
        } catch (Throwable error) {
            Diary.note("interface style " + id + ": " + error.getMessage());
            return null;
        }
    }

    static List<UiStyle> installed(Context context) {
        List<UiStyle> out = new ArrayList<>();
        File[] files = folder(context).listFiles();
        if (files == null) return out;
        java.util.Arrays.sort(files);
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith("." + EXTENSION)) continue;
            UiStyle style = load(context, "custom:" + name.substring(0, name.length() - EXTENSION.length() - 1));
            if (style != null) out.add(style);
        }
        return out;
    }

    static void remove(Context context, String key) {
        if (!custom(key)) return;
        new File(folder(context), key.substring("custom:".length()) + "." + EXTENSION).delete();
    }

    static String fileName(String name) {
        return id(name) + "." + EXTENSION;
    }
}
