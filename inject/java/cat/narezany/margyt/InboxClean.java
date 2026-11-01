package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;

final class InboxClean {

    static final String STRIP = "inbox_hide_strip", CREATE = "inbox_hide_create", STORIES = "inbox_hide_stories",
            LIVE = "inbox_hide_live", NOTES = "inbox_hide_notes", SUGGESTED = "inbox_hide_suggested",
            EXTRAS = "inbox_hide_extras";

    private static final int TAG_MINE = 0x7f5a0101, TAG_KEPT = 0x7f5a0102, TAG_NOTE = 0x7f5a0103;

    private static int listId = -1, noteId, headerId, personId, followId, removeId, faceId;
    private static WeakReference<ViewGroup> found = new WeakReference<ViewGroup>(null);
    private static long lastPass, lastSearch;

    private InboxClean() {}

    private static SharedPreferences prefs() {
        Context context = Margy.context();
        return context == null ? null : context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(String key) {
        SharedPreferences prefs = prefs();
        return prefs != null && prefs.getBoolean(key, false);
    }

    static void set(String key, boolean value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(key, value).apply();
        lastPass = 0;
    }

    private static boolean any() {
        return on(STRIP) || on(CREATE) || on(STORIES) || on(LIVE) || on(NOTES) || on(SUGGESTED) || on(EXTRAS);
    }

    static void attach(final Activity activity) {
        if (Version.LITE) return;
        try {
            final View decor = activity.getWindow().getDecorView();
            if (decor.getTag(TAG_MINE) != null) return;
            decor.setTag(TAG_MINE, Boolean.TRUE);
            decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    try {
                        frame(decor);
                    } catch (Throwable error) {
                        Diary.detail("inbox clean: " + error);
                    }
                    return true;
                }
            });
        } catch (Throwable error) {
            Diary.note("inbox clean attach: " + error);
        }
    }

    private static void ids(Context context) {
        if (listId != -1) return;
        android.content.res.Resources res = context.getResources();
        String pkg = context.getPackageName();
        listId = res.getIdentifier("l78", "id", pkg);
        noteId = res.getIdentifier("q4n", "id", pkg);
        headerId = res.getIdentifier("q3l", "id", pkg);
        personId = res.getIdentifier("p3w", "id", pkg);
        followId = res.getIdentifier("u96", "id", pkg);
        removeId = res.getIdentifier("fy3", "id", pkg);
        faceId = res.getIdentifier("wq0", "id", pkg);
    }

    private static void frame(View decor) {
        long now = SystemClock.uptimeMillis();

        if (now - lastPass < 120L) return;
        lastPass = now;
        ids(decor.getContext());
        if (listId == 0) return;
        ViewGroup list = found.get();
        if (list == null || !list.isAttachedToWindow()) {
            if (now - lastSearch < 1000L) return;
            lastSearch = now;
            View view = decor.findViewById(listId);
            if (!(view instanceof ViewGroup)) return;
            list = (ViewGroup) view;
            found = new WeakReference<ViewGroup>(list);
        }
        if (!list.isShown()) return;

        boolean wanted = any();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (!wanted && row.getTag(TAG_KEPT) == null && !(row instanceof ViewGroup && holdsStrip((ViewGroup) row))) continue;
            ViewGroup strip = row instanceof ViewGroup ? stripIn((ViewGroup) row) : null;
            if (strip != null) {

                items(strip);
                fold(row, on(STRIP), true);
            } else if (suggested(row)) {
                fold(row, on(SUGGESTED), true);
            } else if (row.getTag(TAG_KEPT) != null) {

                fold(row, false, true);
            }
        }
    }

    private static boolean holdsStrip(ViewGroup row) {
        return stripIn(row) != null;
    }

    private static ViewGroup stripIn(ViewGroup row) {
        if (isRecycler(row)) return row;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof ViewGroup && isRecycler(child)) return (ViewGroup) child;
        }
        return null;
    }

    private static boolean isRecycler(View view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("androidx.recyclerview.widget.RecyclerView")) return true;
        }
        return false;
    }

    private static boolean suggested(View row) {
        if (!(row instanceof ViewGroup)) return false;
        if (headerId != 0 && row.findViewById(headerId) != null) return true;
        return personId != 0 && row.findViewById(personId) != null
                && (followId == 0 || row.findViewById(followId) != null)
                && (removeId == 0 || row.findViewById(removeId) != null);
    }

    private static boolean items(ViewGroup strip) {
        boolean left = false;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View item = strip.getChildAt(i);

            if (!(item instanceof ViewGroup) || faceId == 0 || item.findViewById(faceId) == null) {
                fold(item, on(EXTRAS), false);
                if (!on(EXTRAS) && item instanceof ViewGroup && ((ViewGroup) item).getChildCount() > 0
                        && item.getWidth() > item.getHeight() / 2) left = true;
                continue;
            }
            ViewGroup group = (ViewGroup) item;
            View note = noteId == 0 ? null : group.findViewById(noteId);
            if (note != null) {

                float alpha = on(NOTES) ? 0f : 1f;
                if (note.getAlpha() != alpha) note.setAlpha(alpha);
                note.setClickable(!on(NOTES));
                note.setTag(TAG_NOTE, Boolean.TRUE);
            }
            boolean hide;
            if (own(group)) {
                hide = on(CREATE);
            } else if (live(group, 0)) {
                hide = on(LIVE);
            } else if (story(group, 0)) {
                hide = on(STORIES);
            } else {

                hide = on(NOTES);
            }
            fold(item, hide, false);
            if (!hide) left = true;
        }
        return left;
    }

    private static boolean own(ViewGroup item) {
        return described(item, "storybadgenone", 0);
    }

    private static boolean story(View view, int depth) {
        return described(view, "storyring", depth);
    }

    private static boolean live(View view, int depth) {
        if (depth > 8 || view == null) return false;
        CharSequence said = view.getContentDescription();
        if (said != null && said.toString().toLowerCase(java.util.Locale.ROOT).contains("live")) return true;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && "LIVE".equalsIgnoreCase(text.toString().trim())) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (live(group.getChildAt(i), depth + 1)) return true;
        }
        return false;
    }

    private static boolean described(View view, String start, int depth) {
        if (depth > 8 || view == null) return false;
        CharSequence said = view.getContentDescription();
        if (said != null && said.toString().startsWith(start)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (described(group.getChildAt(i), start, depth + 1)) return true;
        }
        return false;
    }

    private static void fold(View view, boolean hide, boolean tall) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        Object kept = view.getTag(TAG_KEPT);
        if (hide) {
            if (kept == null) {
                view.setTag(TAG_KEPT, new int[] {tall ? margins.height : margins.width,
                        tall ? margins.topMargin : margins.leftMargin, tall ? margins.bottomMargin : margins.rightMargin});
            }
            boolean changed;
            if (tall) {
                changed = margins.height != 0 || margins.topMargin != 0 || margins.bottomMargin != 0;
                margins.height = 0;
                margins.topMargin = 0;
                margins.bottomMargin = 0;
            } else {
                changed = margins.width != 0 || margins.leftMargin != 0 || margins.rightMargin != 0;
                margins.width = 0;
                margins.leftMargin = 0;
                margins.rightMargin = 0;
            }
            if (changed) view.setLayoutParams(margins);
            if (view.getVisibility() != View.INVISIBLE) view.setVisibility(View.INVISIBLE);
        } else if (kept instanceof int[]) {
            int[] was = (int[]) kept;
            view.setTag(TAG_KEPT, null);
            if (tall) {
                margins.height = was[0];
                margins.topMargin = was[1];
                margins.bottomMargin = was[2];
            } else {
                margins.width = was[0];
                margins.leftMargin = was[1];
                margins.rightMargin = was[2];
            }
            view.setLayoutParams(margins);
            view.setVisibility(View.VISIBLE);
        }
    }
}
