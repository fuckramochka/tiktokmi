package cat.narezany.margyt;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * The chat folders in the share sheet.
 *
 * Over the row of people a video is sent to, a row of chips: «Все» and one for each
 * folder of the inbox. A folder chosen leaves in the row only the people in it. The
 * row is TikTok's own list; the people who are not in the folder are taken down to
 * nothing, the way the inbox does it, and given back when «Все» is chosen or the
 * sheet goes away.
 *
 * The sheet is found by its people: each has their name on a button of its own, and
 * every name in the app is written through Badge, which hands the button here.
 */
final class ShareFolders {

    private ShareFolders() {}

    private static final int TAG_STRIP = 0x7e0c0430, TAG_KEPT = 0x7e0c0431;

    private static int nameId = -1, listId = -1, actionLabelId, actionIconId, actionRowId, appLabelId;
    /** The folder the sheet's row is narrowed to; none for all of it. */
    private static String chosen;
    private static WeakReference<ViewGroup> hooked = new WeakReference<ViewGroup>(null);

    private static void ids(Context context) {
        if (nameId != -1) return;
        String pack = context.getPackageName();
        nameId = context.getResources().getIdentifier("psf", "id", pack);
        listId = context.getResources().getIdentifier("v2y", "id", pack);
        actionLabelId = context.getResources().getIdentifier("w51", "id", pack);
        actionIconId = context.getResources().getIdentifier("w4y", "id", pack);
        actionRowId = context.getResources().getIdentifier("a5x", "id", pack);
        appLabelId = context.getResources().getIdentifier("w5k", "id", pack);
    }

    /** A name was written: if it is on a person of the share sheet, the sheet gets its chips. */
    static void named(final TextView view) {
        if (Version.LITE) return;
        int id = view.getId();
        if (id == View.NO_ID) return;
        if (nameId == -1) ids(view.getContext());
        if (id == actionLabelId && actionLabelId != 0) {
            actions(view);
            return;
        }
        if (id != nameId || nameId == 0 || listId == 0) return;
        ViewGroup known = hooked.get();
        if (known != null && known.isAttachedToWindow()) return;
        // a name is written while its person is bound, before it is in the list
        view.post(new Runnable() {
            @Override public void run() {
                try {
                    attach(view);
                } catch (Throwable error) {
                    Diary.detail("share folders: " + error);
                }
            }
        });
    }

    // ------------------------------------------------------------ the row of actions

    private static final int TAG_ACTIONS = 0x7e0c0433, TAG_PAINTED = 0x7e0c0434;

    /**
     * The sheet's last row (Жалоба, Неинтересно, Скачать...) comes out with its words in a colour
     * with nothing of it left (alpha 0) and its pictures under a near-black filter, under a dark
     * theme of the mod. Its words are given the colour of the words of the row of apps above it,
     * which come out right, and its pictures the same colour. Only a word that cannot be seen is
     * touched, so a light theme, where all is well, is left alone.
     */
    private static void actions(final TextView label) {
        label.post(new Runnable() {
            @Override public void run() {
                try {
                    final ViewGroup row = rowAbove(label, actionRowId);
                    if (row == null || row.getTag(TAG_ACTIONS) != null) return;
                    row.setTag(TAG_ACTIONS, Boolean.TRUE);
                    final ViewTreeObserver.OnPreDrawListener keep = new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            try {
                                paint(row);
                            } catch (Throwable ignored) {
                            }
                            return true;
                        }
                    };
                    row.getViewTreeObserver().addOnPreDrawListener(keep);
                    row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View v) {}

                        @Override public void onViewDetachedFromWindow(View v) {
                            v.getViewTreeObserver().removeOnPreDrawListener(keep);
                            v.removeOnAttachStateChangeListener(this);
                            v.setTag(TAG_ACTIONS, null);
                        }
                    });
                    paint(row);
                } catch (Throwable error) {
                    Diary.detail("share actions: " + error);
                }
            }
        });
    }

    private static ViewGroup rowAbove(View view, int id) {
        Object up = view.getParent();
        for (int i = 0; i < 6 && up instanceof View; i++) {
            View candidate = (View) up;
            if (candidate.getId() == id && candidate instanceof ViewGroup) return (ViewGroup) candidate;
            up = candidate.getParent();
        }
        return null;
    }

    private static void paint(ViewGroup row) {
        int ink = 0;
        View apps = appLabelId == 0 ? null : row.getRootView().findViewById(appLabelId);
        if (apps instanceof TextView) ink = ((TextView) apps).getCurrentTextColor();
        if ((ink >>> 24) < 0x80) ink = Themes.text();
        if ((ink >>> 24) < 0x80) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            View label = cell.findViewById(actionLabelId);
            boolean broken = label instanceof TextView && (((TextView) label).getCurrentTextColor() >>> 24) < 0x40;
            if (broken) ((TextView) label).setTextColor(ink);
            if (broken || cell.getTag(TAG_PAINTED) != null) {
                View icon = cell.findViewById(actionIconId);
                if (icon instanceof android.widget.ImageView) {
                    Object painted = cell.getTag(TAG_PAINTED);
                    android.widget.ImageView picture = (android.widget.ImageView) icon;
                    if (!(painted instanceof Integer) || (Integer) painted != ink || picture.getColorFilter() == null) {
                        picture.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
                        cell.setTag(TAG_PAINTED, ink);
                    }
                }
            }
        }
    }

    private static ViewGroup listAbove(View view) {
        Object up = view.getParent();
        for (int i = 0; i < 8 && up instanceof View; i++) {
            View candidate = (View) up;
            if (candidate.getId() == listId && candidate instanceof ViewGroup) return (ViewGroup) candidate;
            up = candidate.getParent();
        }
        return null;
    }

    private static void attach(TextView name) {
        final ViewGroup list = listAbove(name);
        if (list == null || list.getTag(TAG_STRIP) != null) return;
        if (!(list.getParent() instanceof LinearLayout)) return;
        LinearLayout holder = (LinearLayout) list.getParent();
        if (holder.getOrientation() != LinearLayout.VERTICAL) return;
        List<String[]> folders = ChatFolders.list();
        if (folders.isEmpty()) return;

        chosen = null;
        hooked = new WeakReference<ViewGroup>(list);
        final HorizontalScrollView strip = strip(holder.getContext(), folders, name.getCurrentTextColor(), list);
        list.setTag(TAG_STRIP, strip);
        holder.addView(strip, holder.indexOfChild(list));

        final ViewTreeObserver.OnPreDrawListener keep = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                try {
                    sweep(list);
                } catch (Throwable ignored) {
                }
                return true;
            }
        };
        list.getViewTreeObserver().addOnPreDrawListener(keep);
        list.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnPreDrawListener(keep);
                v.removeOnAttachStateChangeListener(this);
                chosen = null;
            }
        });
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    private static HorizontalScrollView strip(Context context, List<String[]> folders, int ink, final ViewGroup list) {
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(context, 14), dp(context, 2), dp(context, 14), dp(context, 8));
        scroller.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(chip(context, Local.of("Все"), null, ink, row, list));
        for (String[] folder : folders) row.addView(chip(context, folder[1], folder[0], ink, row, list));
        dress(row, ink);
        return scroller;
    }

    private static TextView chip(Context context, String words, final String id, final int ink,
                                 final LinearLayout row, final ViewGroup list) {
        TextView chip = new TextView(context);
        chip.setText(words);
        chip.setTag(id == null ? "" : id);
        chip.setSingleLine(true);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(context, 32));
        chip.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        Fonts.apply(chip);
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        place.rightMargin = dp(context, 8);
        chip.setLayoutParams(place);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                choose(id, row, ink, list);
            }
        });
        return chip;
    }

    /** Chips in Material 3's way: the chosen one filled with the accent, the others outlined. */
    private static void dress(LinearLayout row, int ink) {
        int accent = Accent.colour() | 0xFF000000;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView chip = (TextView) child;
            Object tag = chip.getTag();
            boolean on = chosen == null ? "".equals(tag) : chosen.equals(tag);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(chip.getContext(), 8));
            if (on) {
                shape.setColor((accent & 0xFFFFFF) | 0x55000000);
                chip.setTextColor(ink);
            } else {
                shape.setColor(0);
                shape.setStroke(dp(chip.getContext(), 1), (ink & 0xFFFFFF) | 0x40000000);
                chip.setTextColor((ink & 0xFFFFFF) | 0xCC000000);
            }
            chip.setBackground(shape);
        }
    }

    private static void choose(String id, LinearLayout row, int ink, final ViewGroup list) {
        chosen = id;
        dress(row, ink);
        sweep(list);
        list.requestLayout();
        scrollTo(list, 0);
        if (id != null) fill(list, id, -1, 0, 0);
    }

    /** Takes the people of the row who are not in the folder down to nothing, and gives them back when they are. */
    private static void sweep(ViewGroup list) {
        for (int i = 0; i < list.getChildCount(); i++) shape(list.getChildAt(i));
    }

    private static boolean wanted(View person) {
        if (chosen == null) return true;
        CharSequence said = person.getContentDescription();
        return said != null && ChatFolders.holds(chosen, said.toString());
    }

    /** The room the list's decorations leave left and right of a person. */
    private static int[] gaps(View person) {
        try {
            Object list = person.getParent();
            Object manager = list.getClass().getMethod("getLayoutManager").invoke(list);
            int left = (Integer) manager.getClass().getMethod("getLeftDecorationWidth", View.class).invoke(manager, person);
            int right = (Integer) manager.getClass().getMethod("getRightDecorationWidth", View.class).invoke(manager, person);
            return new int[] {left, right};
        } catch (Throwable unknown) {
            return new int[] {0, 0};
        }
    }

    private static void shape(View person) {
        boolean hide = !wanted(person);
        ViewGroup.LayoutParams params = person.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        Object kept = person.getTag(TAG_KEPT);
        if (hide) {
            if (kept == null) {
                person.setTag(TAG_KEPT, new int[] {margins.width, margins.leftMargin, margins.rightMargin, person.getVisibility()});
            }
            // the gaps TikTok's list puts between people stay round a person taken down to nothing:
            // margins as wide the other way take them back out
            int[] gaps = gaps(person);
            if (margins.width != 0 || margins.leftMargin != -gaps[0] || margins.rightMargin != -gaps[1]) {
                margins.width = 0;
                margins.leftMargin = -gaps[0];
                margins.rightMargin = -gaps[1];
                person.setLayoutParams(margins);
            }
            if (person.getVisibility() != View.INVISIBLE) person.setVisibility(View.INVISIBLE);
        } else if (kept instanceof int[]) {
            int[] was = (int[]) kept;
            margins.width = was[0];
            margins.leftMargin = was[1];
            margins.rightMargin = was[2];
            person.setVisibility(was[3]);
            person.setTag(TAG_KEPT, null);
            person.setLayoutParams(margins);
        }
    }

    private static void scrollTo(ViewGroup list, int position) {
        try {
            list.getClass().getMethod("scrollToPosition", int.class).invoke(list, position);
        } catch (Throwable ignored) {
        }
    }

    private static int count(ViewGroup list) {
        try {
            Object adapter = list.getClass().getMethod("getAdapter").invoke(list);
            if (adapter == null) return -1;
            Object many = Class.forName("androidx.recyclerview.widget.RecyclerView$Adapter").getMethod("getItemCount").invoke(adapter);
            return many instanceof Integer ? (Integer) many : -1;
        } catch (Throwable error) {
            return -1;
        }
    }

    /**
     * People further along the row come only when it is scrolled there, and a row taken
     * down to a few does not scroll: it is taken to its end and back until everyone of
     * the folder has come, or nobody more does.
     */
    private static void fill(final ViewGroup list, final String id, final int before, final int quiet, final int steps) {
        list.postDelayed(new Runnable() {
            @Override public void run() {
                if (chosen == null || !chosen.equals(id) || !list.isAttachedToWindow()) return;
                int found = 0;
                for (int i = 0; i < list.getChildCount(); i++) if (wanted(list.getChildAt(i))) found++;
                int now = count(list);
                int still = now == before ? quiet + 1 : 0;
                if (found >= ChatFolders.size(id) || now < 0 || still >= 3 || steps >= 8) {
                    scrollTo(list, 0);
                    return;
                }
                scrollTo(list, now - 1);
                fill(list, id, now, still, steps + 1);
            }
        }, steps == 0 ? 120L : 350L);
    }
}
