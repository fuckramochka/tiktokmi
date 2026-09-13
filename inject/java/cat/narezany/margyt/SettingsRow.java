package cat.narezany.margyt;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * The MargyT row, at the top of TikTok's own settings screen.
 *
 * It is put there when the screen is drawn, not written into the bytecode that
 * builds the list. The list is assembled by obfuscated classes that are renamed
 * with every release; the screen's own class name is not obfuscated, and the
 * view tree underneath it is plain Android. So the mod waits for that screen,
 * finds the list in it by what the class is called, and puts a row above it.
 *
 * Nothing here holds a reference past the activity that is on screen, and every
 * step gives up quietly: a settings screen that has changed shape means no row,
 * never a crash in someone else's app.
 */
public final class SettingsRow implements Application.ActivityLifecycleCallbacks {

    /** TikTok's settings screen. A real class name, not an obfuscated one. */
    public static final String SETTINGS_ACTIVITY =
            "com.ss.android.ugc.aweme.setting.ui.SettingContainerActivity";

    // setTag(int, ...) refuses a key that does not look like a resource id:
    // the top byte has to be 2 or more. This one spells "Marg".
    private static final int TAG = 0x4D617267;

    private static final int MINT = 0xFF8DD1B0;
    private static final int INK = 0xFF1C2C24;

    /** The class name of the fragment the row belongs in, per activity. */
    private String rootFragment;

    // ------------------------------------------------------- the lifecycle

    @Override
    public void onActivityResumed(Activity activity) {
        if (!SETTINGS_ACTIVITY.equals(activity.getClass().getName())) return;
        final View decor = activity.getWindow().getDecorView();
        if (decor.getTag(TAG) != null) return;
        decor.setTag(TAG, Boolean.TRUE);
        rootFragment = null;

        final Activity host = activity;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            inject(host, decor);
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (SETTINGS_ACTIVITY.equals(activity.getClass().getName())) rootFragment = null;
    }

    // ------------------------------------------------------------ the work

    private void inject(Activity activity, View decor) {
        View list = findVisibleList(decor);
        if (list == null) return;
        ViewGroup parent = (ViewGroup) list.getParent();
        if (parent == null) return;
        if (parent.getTag(TAG) != null) return;  // already ours

        // The screen keeps its sub-pages in the same activity, one fragment
        // each. The first list to appear is the settings list itself; anything
        // else is a page inside it, and does not get a row.
        String owner = fragmentOf(activity, list);
        if (rootFragment == null) {
            rootFragment = owner == null ? "" : owner;
        } else if (owner != null && !rootFragment.equals(owner)) {
            return;
        }

        int index = parent.indexOfChild(list);
        ViewGroup.LayoutParams params = list.getLayoutParams();

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag(TAG, Boolean.TRUE);

        parent.removeViewAt(index);
        column.addView(row(activity, list), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        parent.addView(column, index, params);
    }

    /** The first list on screen, found by what its class is called. */
    private View findVisibleList(View view) {
        if (!view.isShown()) return null;
        if (isList(view.getClass())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findVisibleList(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isList(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.endsWith(".RecyclerView") || name.endsWith(".ListView")) return true;
        }
        return false;
    }

    /**
     * Which fragment the list belongs to, by name.
     *
     * Reflection rather than a compile-time dependency: androidx is inside the
     * apk already, and the mod's own dex has no business carrying a second copy.
     */
    private String fragmentOf(Activity activity, View list) {
        try {
            Method getManager = activity.getClass().getMethod("getSupportFragmentManager");
            Object manager = getManager.invoke(activity);
            Object fragments = manager.getClass().getMethod("getFragments").invoke(manager);
            for (Object fragment : (List<?>) fragments) {
                Object view = fragment.getClass().getMethod("getView").invoke(fragment);
                if (view instanceof View && contains((View) view, list)) {
                    return fragment.getClass().getName();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean contains(View root, View child) {
        for (View v = child; v != null; ) {
            if (v == root) return true;
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    // ------------------------------------------------------------- the row

    private View row(final Activity activity, View list) {
        // TikTok's settings are white on light and white text on dark. Reading
        // the colour off a row that is already there is what keeps this looking
        // like the screen it is in rather than like a mod.
        int text = textColourOf(list, INK);
        boolean dark = luminance(text) > 0.5f;
        int card = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        int muted = dark ? 0x99FFFFFF : 0x99000000;

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));

        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(activity, 12));
        row.setBackground(background);

        TextView glyph = new TextView(activity);
        glyph.setText("♪");  // a note, the same one as on the icon
        glyph.setTextColor(dark ? INK : 0xFF16281F);
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        glyph.setGravity(Gravity.CENTER);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(MINT);
        badge.setCornerRadius(dp(activity, 8));
        glyph.setBackground(badge);
        int size = dp(activity, 28);
        row.addView(glyph, new LinearLayout.LayoutParams(size, size));

        TextView title = new TextView(activity);
        title.setText(label());
        title.setTextColor(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 12);
        row.addView(title, titleParams);

        TextView chevron = new TextView(activity);
        chevron.setText("›");
        chevron.setTextColor(muted);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        row.addView(chevron);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    activity.startActivity(new Intent(activity, SettingsActivity.class));
                } catch (Throwable ignored) {
                }
            }
        });

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 4));
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static String label() {
        String language = Locale.getDefault().getLanguage();
        if ("ru".equals(language)) return "Настройки MargyT";
        if ("uk".equals(language)) return "Налаштування MargyT";
        return "MargyT settings";
    }

    /** The colour the rows next door are written in, or `fallback`. */
    private static int textColourOf(View view, int fallback) {
        if (view instanceof TextView) {
            return ((TextView) view).getCurrentTextColor();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                int found = textColourOf(group.getChildAt(i), 0);
                if (found != 0) return found;
            }
        }
        return fallback;
    }

    private static float luminance(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
