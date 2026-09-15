package mi.tiktokmi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.ss.android.ugc.aweme.profile.model.User;
import com.ss.android.ugc.profile.platform.base.data.UserProfileInfo;

/**
 * A mark after a name, wherever that name is written.
 *
 * There is no screen to patch for this. TikTok writes an account's name in a
 * dozen places -- the profile, every comment, the line under a video -- and
 * every one of them asks the same model the same question. So the answer
 * carries the mark: `getNickname()` comes back with one invisible character on
 * the end, and every one of those places puts it on screen without knowing.
 *
 * Then the text is caught on its way into a TextView and the character is
 * swapped for a picture. Nothing about the layout changes: an ImageSpan takes
 * the place of a character, so the name is measured and wrapped exactly as the
 * app intended.
 *
 * Which badge it is comes from `Badges`, which reads it out of the repository
 * rather than out of this file -- and the character itself says which one,
 * because by the time a view has the text there is nothing left to ask.
 */
public final class Badge {

    private Badge() {}

    /** The colour of the mod's own note, when a badge names none. */
    private static final int MINT = 0xFF8DD1B0;

    private static volatile Drawable note;
    private static volatile boolean noteTried;

    // ------------------------------------------------- where the name lands

    public static String getNickname(User user) {
        if (user == null) return null;
        return marked(user.getNickname(), user.getUid());
    }

    /** The same name, off the model a loaded profile uses instead. */
    public static String getNickname(UserProfileInfo user) {
        if (user == null) return null;
        return marked(user.getNickname(), user.getUid());
    }

    /**
     * What a name becomes: a word in front of it, and marks after it.
     *
     * Done once and not twice. A profile is built out of the model the feed
     * already had, so by the time the profile's own model is asked for the
     * name it is handing back a name this has already been through -- and
     * marking it again gave everybody two badges and two prefixes.
     */
    private static String marked(String name, String uid) {
        if (name == null || name.length() == 0) return name;
        try {
            for (int i = 0; i < name.length(); i++) {
                if (Badges.isMark(name.charAt(i))) return name;  // already done
            }
            remember(uid, name);
            String marks = Badges.marksFor(uid);
            if (marks.length() > 0) return name + '\u2009' + marks;
        } catch (Throwable ignored) {
        }
        return name;
    }

    // ------------------------------------------- names already on the screen

    /**
     * Names the mod has seen, so one already drawn can be recognised.
     *
     * A profile finishes loading and writes the name again by a road the mod
     * does not stand on -- the mark never gets added, and what was there is
     * replaced by the plain name. Rather than hunt for every such road, the
     * mod remembers which name belongs to which account and puts the mark back
     * on whatever is showing it.
     *
     * Only names that are worth marking are kept, and only the last few
     * hundred: this is a lookup that runs against every piece of text on a
     * screen, so it has to stay small and exact.
     */
    private static final java.util.LinkedHashMap<String, String> plain =
            new java.util.LinkedHashMap<String, String>();

    private static void remember(String uid, String name) {
        if (uid == null || name == null || name.length() == 0) return;
        try {
            if (Badges.marksFor(uid).length() == 0) {
                return;  // nothing would be added, so nothing to put back
            }
            synchronized (plain) {
                if (plain.size() > 300) {
                    plain.remove(plain.keySet().iterator().next());
                }
                plain.put(name, uid);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Put the marks back on names already drawn.
     *
     * Walked after the screen has settled. A view is only touched when its
     * text is exactly a name the mod knows belongs to an account with
     * something to show -- so nothing else on the screen can be caught by it.
     */
    public static void rewrite(View root) {
        if (root == null) return;
        synchronized (plain) {
            if (plain.isEmpty()) return;
        }
        try {
            seen = 0;
            walk(root, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * How deep to go, and how much to do at once.
     *
     * Fourteen was far too shallow and it showed: a comment sheet is a few
     * levels down and was reached, while a profile or the inbox -- a fragment
     * inside a pager inside a list inside a coordinator -- is twenty and more,
     * and the walk simply stopped before it got there. The budget is what
     * keeps this bounded now, rather than the depth: a screen is a few hundred
     * views, and anything claiming to be tens of thousands is not a screen.
     */
    private static final int DEEP = 40;
    private static final int BUDGET = 4000;

    private static int seen;

    private static volatile boolean said;

    private static void walk(View view, int depth) {
        if (view == null || depth > DEEP || ++seen > BUDGET) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence showing = text.getText();
            if (showing != null && showing.length() > 0 && showing.length() < 80) {
                String uid;
                synchronized (plain) {
                    uid = plain.get(showing.toString());
                }
                if (uid != null) {
                    String out = marked(showing.toString(), uid);
                    if (!out.equals(showing.toString())) {
                        setText(text, out);
                        if (!said) {
                            said = true;
                            Diary.note("badge: a name already drawn was marked again");
                        }
                    }
                }
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            int many = group.getChildCount();
            for (int i = 0; i < many; i++) walk(group.getChildAt(i), depth + 1);
        }
    }

    /**
     * Every piece of text on its way into a TextView passes here.
     *
     * Which is a great many of them, so the common case is a type check and a
     * scan of a short string, and nothing else.
     */
    public static void setText(TextView view, CharSequence text) {
        Fonts.apply(view);
        CharSequence out = marked(view, text);
        // asking for it to be kept spannable, because a TextView told to store
        // plain text copies the spans into an immutable SpannedString and the
        // tap has nothing left to find
        if (out != text) {
            view.setText(out, TextView.BufferType.SPANNABLE);
        } else {
            view.setText(out);
        }
    }

    public static void setText(TextView view, CharSequence text, TextView.BufferType type) {
        Fonts.apply(view);
        CharSequence out = marked(view, text);
        view.setText(out, out != text ? TextView.BufferType.SPANNABLE : type);
    }

    private static CharSequence marked(TextView view, CharSequence text) {
        if (text == null) return text;

        boolean any = false;
        for (int i = text.length() - 1; i >= 0 && !any; i--) {
            any = Badges.isMark(text.charAt(i));
        }
        if (!any) return text;

        try {
            // Anything that is text, not only a plain String. A profile writes
            // the name once while it loads and again when it is loaded, and
            // the second time it is rich text -- which is why the badge used
            // to appear on the way in and then vanish: the mark was still
            // there, invisible, and nothing turned it into a picture.
            //
            // Walked backwards so that dropping a mark cannot move the ones
            // not yet looked at.
            SpannableStringBuilder out = new SpannableStringBuilder(text);
            boolean drew = false;
            for (int i = out.length() - 1; i >= 0; i--) {
                char c = out.charAt(i);
                if (!Badges.isMark(c)) continue;
                Badges.Badge badge = Badges.byMark(c);
                Drawable picture = badge == null ? null : picture(view, badge);
                if (picture == null) {
                    out.delete(i, i + 1);
                    continue;
                }
                out.setSpan(new ImageSpan(picture, ImageSpan.ALIGN_BOTTOM), i, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new Tap(badge), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                drew = true;
            }
            if (!drew) return tidy(out.toString());
            listen(view);
            return out;
        } catch (Throwable ignored) {
            // a name with a stray invisible character is bad; a name that
            // crashes the screen it is on is worse
            return strip(text.toString());
        }
    }

    /** The name with no marks left in it at all. */
    private static String strip(String plain) {
        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (!Badges.isMark(c)) out.append(c);
        }
        return tidy(out.toString());
    }

    /** No dangling separator, for a name whose marks all went away. */
    private static String tidy(String name) {
        return name.endsWith(" ") || name.endsWith(" ")
                ? name.substring(0, name.length() - 1) : name;
    }

    /** Sized to the text it sits in, so it matches whatever draws it. */
    private static Drawable picture(TextView view, Badges.Badge badge) {
        Bitmap bitmap = badge.image.length() == 0
                ? null : Badges.picture(view.getContext(), badge.image);

        Drawable drawable;
        if (bitmap != null) {
            drawable = new BitmapDrawable(bitmap);
            if (badge.colour != 0) drawable.setColorFilter(badge.colour, PorterDuff.Mode.SRC_IN);
        } else {
            Drawable own = note();
            if (own == null) return null;
            drawable = own.getConstantState() == null
                    ? own : own.getConstantState().newDrawable().mutate();
            drawable.setColorFilter(badge.colour != 0 ? badge.colour : MINT,
                    PorterDuff.Mode.SRC_IN);
        }

        int size = Math.round(view.getTextSize());
        if (size <= 0) size = Math.round(14 * view.getResources().getDisplayMetrics().density);
        drawable.setBounds(0, 0, size, size);
        return drawable;
    }

    /** The note that ships with the mod, decoded once. */
    private static Drawable note() {
        Drawable known = note;
        if (known != null) return known;
        if (noteTried) return null;
        noteTried = true;
        try {
            byte[] png = Base64.decode(Emblem.PNG, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bitmap == null) return null;
            known = new BitmapDrawable(crop(bitmap));
            note = known;
            return known;
        } catch (Throwable error) {
            Diary.note("badge: " + error);
            return null;
        }
    }

    // ------------------------------------------------------------- the tap

    private static final class Tap extends ClickableSpan {
        private final Badges.Badge badge;

        Tap(Badges.Badge badge) {
            this.badge = badge;
        }

        @Override
        public void onClick(View widget) {
            Popup.show(widget.getContext(), badge.title, badge.text, badge.button);
        }

        @Override
        public void updateDrawState(android.text.TextPaint paint) {
            // no underline, no colour: the picture is the whole of it
        }
    }

    /**
     * Take the badge's taps, and only the badge's.
     *
     * A movement method is the usual way and it is the wrong one here. By the
     * time TextView consults it, View.onTouchEvent has already run and already
     * decided a click happened -- so tapping the emblem fired both the emblem
     * and whatever the name does. A touch listener runs before all of that: it
     * swallows the press and the release when they land on the emblem, so the
     * view never sees a click, and answers false everywhere else, so the name
     * goes on behaving exactly as it did.
     */
    private static void listen(TextView view) {
        if (Boolean.TRUE.equals(view.getTag(LISTENING))) return;
        try {
            view.setTag(LISTENING, Boolean.TRUE);
            view.setOnTouchListener(new Touch());
        } catch (Throwable ignored) {
        }
    }

    // setTag(int, ...) wants a key that looks like a resource id: "Marg" + 1
    private static final int LISTENING = 0x4D617268;

    private static final class Touch implements View.OnTouchListener {
        private boolean pressed;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                pressed = span(v, event) != null;
                return pressed;
            }
            if (!pressed) return false;
            if (action == MotionEvent.ACTION_UP) {
                pressed = false;
                ClickableSpan tapped = span(v, event);
                if (tapped != null) tapped.onClick(v);
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) pressed = false;
            return true;
        }

        /** The badge under the finger, or null. */
        private ClickableSpan span(View v, MotionEvent event) {
            try {
                TextView view = (TextView) v;
                CharSequence text = view.getText();
                // Spanned, not Spannable: what comes back out of a TextView is
                // read-only, and asking for the writable interface was why this
                // answered "not mine" to every tap it should have taken
                if (!(text instanceof Spanned)) return null;
                Layout layout = view.getLayout();
                if (layout == null) return null;

                int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
                int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
                int line = layout.getLineForVertical(y);
                // an offset is answered even for a miss well past the end of
                // the line, so the touch has to be inside the line as well
                if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;

                int at = layout.getOffsetForHorizontal(line, x);
                ClickableSpan[] found = ((Spanned) text).getSpans(at, at, ClickableSpan.class);
                return found.length == 0 ? null : found[0];
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /**
     * Cut away what is fully transparent.
     *
     * An adaptive icon's foreground is drawn small inside a large square,
     * because the launcher masks and moves it. Beside a name none of that
     * applies and the empty margin is just a smaller emblem.
     */
    private static Bitmap crop(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) < 8) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        if (right < left || bottom < top) return bitmap;
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }
}
