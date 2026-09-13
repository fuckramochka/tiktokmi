package cat.narezany.margyt;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.comment.model.CommentImageStruct;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
