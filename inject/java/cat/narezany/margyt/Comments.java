package cat.narezany.margyt;

import android.view.View;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.comment.model.CommentImageStruct;
import com.ss.android.ugc.aweme.comment.model.CommentStickerStruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stamp on an image saved out of a comment.
 *
 * A photo post keeps a clean copy beside the stamped one and the mod hands
 * back the clean one. A comment image has no such pair -- and for a while it
 * looked like there was nothing to take. The words are nowhere in the apk, in
 * any language, and the saved file is a standard CDN width with a colour
 * profile and no exif, so the stamp is not drawn on the phone: a server makes
 * it.
 *
 * What settled it was reading both addresses off a running app. The same image
 * arrives twice, under one uri, differing by one word:
 *
 *     shown:  .../<hash>~tplv-jj85edgx6n-image-medium.image
 *     saved:  .../<hash>~tplv-jj85edgx6n-image-origin.image
 *
 * The template in the path is what tells the server which rendering to make,
 * and `origin` is the one it stamps. `medium` is the one on screen, which is
 * why the picture in the comment has no stamp while the file on disk does --
 * at the same 640x480, so this is not a matter of size.
 *
 * So the fix asks for the other rendering: the address is copied with the
 * template swapped, and everything else about it left alone. When the switch
 * is off, or when the address does not look like this, what comes back is
 * exactly what TikTok asked for.
 */
public final class Comments {

    private Comments() {}

    /** The rendering the server stamps, and the one it does not. */
    private static final String STAMPED = "-image-origin.";
    private static final String PLAIN = "-image-medium.";

    private static final Set<String> told = new HashSet<String>();

    /**
     * A sticker in a comment, tapped.
     *
     * In a conversation the tap goes through an interface with a real name and
     * the mod simply hears it. In the comments it goes to a static on a class
     * whose name changes every release -- so the build finds that class by
     * what the method takes rather than by what it is called, and writes down
     * where it landed. The call is handed straight back, so the panel TikTok
     * opens on a tap still opens.
     */
    public static void stickerTapped(View view, CommentStickerStruct sticker,
                                     boolean flag, String from,
                                     Map extras, String where) {
        try {
            if (sticker != null && Stickers.isEnabled()) Stickers.seen(sticker);
        } catch (Throwable error) {
            Diary.note("comment sticker: " + error);
        }
        onwards(view, sticker, flag, from, extras, where);
    }

    /** Back to TikTok's own, wherever this release keeps it. */
    private static void onwards(Object... args) {
        try {
            Class<?> owner = Class.forName(Anchors.COMMENT_STICKER_TAPPED);
            for (java.lang.reflect.Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(Anchors.COMMENT_STICKER_TAPPED_METHOD)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(null, args);
                return;
            }
            Diary.note("comment sticker tap: nothing to hand it back to");
        } catch (Throwable error) {
            Diary.note("comment sticker tap: " + error);
        }
    }

    /** The sticker a comment is carrying, remembered for the view that shows it. */
    public static CommentStickerStruct getStickerStruct(Object comment) {
        CommentStickerStruct sticker = null;
        try {
            java.lang.reflect.Method method =
                    comment.getClass().getMethod("getStickerStruct");
            method.setAccessible(true);
            sticker = (CommentStickerStruct) method.invoke(comment);
        } catch (Throwable error) {
            Diary.note("comment sticker: " + error);
            return null;
        }
        if (sticker != null) lastBound = sticker;
        return sticker;
    }

    /** The sticker most recently bound, for the view about to be wrapped. */
    private static volatile CommentStickerStruct lastBound;

    /**
     * A long press on a sticker in a comment.
     *
     * TikTok sets its own listener on that view, so there is a gesture already
     * and the mod does not need to invent one: this wraps what was about to be
     * set. The press still does what TikTok made it do -- our listener says so
     * by handing the event on and returning what TikTok's returns -- and the
     * offer to save appears alongside.
     *
     * Which sticker it is comes from the listener itself where it can be found
     * there, and from the last one bound where it cannot: the view is wrapped
     * in the same breath as the comment is read, so the two go together.
     */
    public static void setOnLongClickListener(View view,
                                              final View.OnLongClickListener theirs) {
        if (view == null) return;
        final CommentStickerStruct sticker = hunt(theirs, new HashSet<Object>(), 0);
        final CommentStickerStruct fallback = lastBound;
        if (sticker == null && fallback == null) {
            view.setOnLongClickListener(theirs);
            return;
        }
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View pressed) {
                try {
                    if (Stickers.isEnabled()) {
                        Stickers.seen(sticker != null ? sticker : fallback);
                    }
                } catch (Throwable error) {
                    Diary.note("comment sticker: " + error);
                }
                return theirs != null && theirs.onLongClick(pressed);
            }
        });
    }

    /** A comment's sticker, anywhere inside the object holding the listener. */
    private static CommentStickerStruct hunt(Object thing, Set<Object> seen, int depth) {
        if (thing == null || depth > 3) return null;
        if (thing instanceof CommentStickerStruct) return (CommentStickerStruct) thing;
        Class<?> type = thing.getClass();
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("android.")) return null;
        if (!seen.add(thing)) return null;
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    CommentStickerStruct found = hunt(field.get(thing), seen, depth + 1);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    public static UrlModel getCropUrl(CommentImageStruct image) {
        return image == null ? null : image.getCropUrl();
    }

    public static UrlModel getOriginUrl(CommentImageStruct image) {
        if (image == null) return null;
        UrlModel origin = image.getOriginUrl();
        if (origin == null || !Download.isEnabled()) return origin;
        try {
            List urls = origin.getUrlList();
            if (urls == null || urls.isEmpty()) return origin;

            List plain = new ArrayList(urls.size());
            boolean swapped = false;
            for (Object entry : urls) {
                String url = String.valueOf(entry);
                String other = url.replace(STAMPED, PLAIN);
                if (!other.equals(url)) swapped = true;
                plain.add(other);
            }
            if (!swapped) return origin;

            // a copy rather than the app's own object: the model is shared
            // with whatever else is holding this comment, and the stamped
            // address is still the right answer for everyone who did not ask
            // through here
            UrlModel clean = new UrlModel();
            clean.setUrlList(plain);
            clean.setUri(origin.getUri());
            say(origin.getUri());
            return clean;
        } catch (Throwable error) {
            Diary.note("comment image: " + error);
            return origin;
        }
    }

    /** Once per image: a diary full of the same line helps nobody. */
    private static void say(String uri) {
        synchronized (told) {
            if (told.size() > 40 || !told.add(String.valueOf(uri))) return;
        }
        Diary.note("comment image: asked for the unstamped rendering");
    }
}
