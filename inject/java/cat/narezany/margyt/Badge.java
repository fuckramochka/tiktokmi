package cat.narezany.margyt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.ss.android.ugc.aweme.profile.model.User;

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
        String name = user.getNickname();
        if (name == null || name.length() == 0) return name;
        try {
            char mark = Badges.markFor(user.getUid());
            // a thin space first: an emblem set flush against the last letter
            // reads as part of the word
            if (mark != 0) return name + ' ' + mark;
        } catch (Throwable ignored) {
        }
        return name;
    }

    /**
     * Every piece of text on its way into a TextView passes here.
     *
     * Which is a great many of them, so the common case is a type check and a
     * scan of a short string, and nothing else.
     */
    public static void setText(TextView view, CharSequence text) {
        view.setText(marked(view, text));
    }

    public static void setText(TextView view, CharSequence text, TextView.BufferType type) {
        view.setText(marked(view, text), type);
    }

    private static CharSequence marked(TextView view, CharSequence text) {
        if (!(text instanceof String)) return text;
        String plain = (String) text;

        int at = -1;
        for (int i = plain.length() - 1; i >= 0; i--) {
            if (Badges.isMark(plain.charAt(i))) {
                at = i;
                break;
            }
        }
        if (at < 0) return text;

        try {
            Badges.Badge badge = Badges.byMark(plain.charAt(at));
            Drawable picture = badge == null ? null : picture(view, badge);
            if (picture == null) return without(plain, at);

            SpannableString out = new SpannableString(plain);
            out.setSpan(new ImageSpan(picture, ImageSpan.ALIGN_BOTTOM), at, at + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new Tap(badge), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            listen(view);
            return out;
        } catch (Throwable ignored) {
            // a name with a stray invisible character is bad; a name that
            // crashes the screen it is on is worse
            return without(plain, at);
        }
    }

    /** The name as it was, when the badge cannot be drawn. */
    private static String without(String plain, int at) {
        String out = plain.substring(0, at) + plain.substring(at + 1);
        return out.endsWith(" ") ? out.substring(0, out.length() - 1) : out;
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
                if (!(text instanceof Spannable)) return null;
                Layout layout = view.getLayout();
                if (layout == null) return null;

                int x = (int) event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
                int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
                int line = layout.getLineForVertical(y);
                // an offset is answered even for a miss well past the end of
                // the line, so the touch has to be inside the line as well
                if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;

                int at = layout.getOffsetForHorizontal(line, x);
                ClickableSpan[] found = ((Spannable) text).getSpans(at, at, ClickableSpan.class);
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
