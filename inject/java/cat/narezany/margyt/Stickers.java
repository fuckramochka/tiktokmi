package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;

import com.ss.android.ugc.aweme.im.message.template.card.StickerTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saving a sticker out of a conversation, which TikTok does not offer.
 *
 * Touching one goes through `MessageListStickerClickAbility`, whose name is
 * real, and it is handed a `StickerTemplate`, whose name is real as well.
 * Five of its methods take that type -- the obfuscator renamed the methods,
 * not the types -- and all five are rewritten to come through here. Whatever
 * the app was going to do still happens: the sticker is noted on the way past
 * and the call is passed straight on.
 *
 * The receiver arrives as a plain Object rather than as the interface, and
 * deliberately: a call site names whatever static type it is holding, which is
 * the class implementing the interface and not the interface itself, so the
 * rewrite cannot ask for one particular owner. Which is why the call onwards
 * goes through reflection -- on a tap, where it costs nothing.
 *
 * The address inside the template is found by walking the object rather than
 * by naming its parts, because the parts are obfuscated and a walk of a few
 * dozen fields costs nothing on a tap. Anything that looks like an http
 * address and belongs to a sticker will do; the largest is preferred, since
 * these models keep the same picture at three sizes.
 */
public final class Stickers {

    private Stickers() {}

    private static volatile String latest;

    // ------------------------------------------------ where the taps land

    public static void UP(Object ability, Object view, Object a, Object b,
                          StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "UP", view, a, b, sticker);
    }

    public static void tR1(Object ability, Object view, Object a, Object b,
                           StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "tR1", view, a, b, sticker);
    }

    public static void dy1(Object ability, Object view, Object manager, Object a,
                           StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "dy1", view, manager, a, sticker);
    }

    public static void Yt1(Object ability, Object a, Object b, StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "Yt1", a, b, sticker);
    }

    public static void XQ1(Object ability, Object corner, StickerTemplate sticker) {
        seen(sticker);
        onwards(ability, "XQ1", corner, sticker);
    }

    /**
     * Hand the call back to TikTok.
     *
     * By name and by how many arguments it takes, rather than by their types:
     * the types are obfuscated, and the mod is holding the very objects that
     * were about to be passed, so there is nothing to resolve. If this fails
     * the tap does nothing -- which is why it happens last, after the mod has
     * already noted what it wanted.
     */
    private static void onwards(Object ability, String name, Object... args) {
        if (ability == null) return;
        try {
            for (Method method : ability.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(ability, args);
                return;
            }
            Diary.note("sticker tap: no " + name + " taking " + args.length);
        } catch (Throwable error) {
            Diary.note("sticker tap: " + error);
        }
    }

    private static void seen(Object sticker) {
        try {
            String url = find(sticker, new HashSet<Object>(), 0);
            if (url == null) return;
            latest = url;
            Screen.offer(Text.SAVE_STICKER, new Runnable() {
                @Override
                public void run() {
                    save();
                }
            });
        } catch (Throwable error) {
            Diary.note("sticker: " + error);
        }
    }

    // ---------------------------------------------------------- the walk

    private static final int DEEP = 4;

    /** The best http address anywhere inside an object, or null. */
    private static String find(Object thing, Set<Object> seen, int depth) {
        if (thing == null || depth > DEEP) return null;
        if (thing instanceof String) {
            String value = (String) thing;
            return value.startsWith("http") ? value : null;
        }
        if (thing instanceof List) {
            String best = null;
            for (Object item : (List) thing) {
                String found = find(item, seen, depth + 1);
                if (found != null && (best == null || found.length() > best.length())) best = found;
            }
            return best;
        }

        Class<?> type = thing.getClass();
        String name = type.getName();
        // the framework and the language are not where a sticker's url is
        if (name.startsWith("java.") || name.startsWith("android.")) return null;
        if (!seen.add(thing)) return null;

        String best = null;
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    String found = find(field.get(thing), seen, depth + 1);
                    // the same picture arrives at three sizes and the longest
                    // url is reliably the largest of them
                    if (found != null && (best == null || found.length() > best.length())) {
                        best = found;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return best;
    }

    // --------------------------------------------------------- saving it

    private static void save() {
        final String url = latest;
        final Context context = Margy.context();
        if (url == null || context == null) return;
        Net.away("sticker", new Runnable() {
            @Override
            public void run() {
                byte[] data = Net.bytes(url);
                boolean webp = url.contains(".webp") || url.contains("webp");
                String at = data == null ? null : Gallery.save(context, data,
                        Gallery.name("sticker", url, webp ? "webp" : "png"),
                        webp ? "image/webp" : "image/png");
                Screen.say(at == null ? Text.STICKER_FAILED : Text.SAVED);
            }
        });
    }

    /** Whether there is anything to save, for the settings screen to ask. */
    public static boolean have() {
        return latest != null;
    }
}
