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
 * It is put there while the screen is drawn, not written into the code that
 * builds it. That code is Jetpack Compose: the whole screen, title and back
 * arrow included, is one ComposeView, and there is no list in the view tree to
 * find and no way in from outside it. So the row goes above the screen's own
 * view -- the fragment's root, whatever it happens to be made of, which is the
 * one thing that is there in every release.
 *
 * Nothing here holds a reference past the activity on screen, and every step
 * gives up quietly: a settings screen that has changed shape means no row,
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

    /** The fragment the row belongs to, so its sub-pages do not get one. */
    private String rootFragment;

    // ------------------------------------------------------- the lifecycle

    @Override
    public void onActivityResumed(Activity activity) {
        if (!SETTINGS_ACTIVITY.equals(activity.getClass().getName())) {
            Diary.note("saw " + activity.getClass().getName());
            return;
        }
        Diary.note("settings screen is up");
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
                            inject(host);
                        } catch (Throwable error) {
                            Diary.note("row failed: " + error);
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

    private void inject(Activity activity) {
        Object fragment = visibleFragment(activity);
        if (fragment == null) return;
        View content = viewOf(fragment);
        if (content == null || !content.isShown()) return;

        ViewGroup parent = content.getParent() instanceof ViewGroup
                ? (ViewGroup) content.getParent() : null;
        if (parent == null || parent.getTag(TAG) != null) return;

        // Sub-pages of this screen are more fragments in the same activity. The
        // first one to appear is the settings screen itself; the rest are pages
        // inside it, and the row does not belong there.
        String owner = fragment.getClass().getName();
        if (rootFragment == null) {
            rootFragment = owner;
            Diary.note("settings fragment: " + owner);
            Diary.note("its view is a " + content.getClass().getName());
        } else if (!rootFragment.equals(owner)) {
            return;
        }

        int index = parent.indexOfChild(content);
        ViewGroup.LayoutParams params = content.getLayoutParams();

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag(TAG, Boolean.TRUE);

        parent.removeViewAt(index);
        column.addView(row(activity, content), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        parent.addView(column, index, params);
        Diary.note("row added");
    }

    /**
     * The fragment on screen, through the manager.
     *
     * Reflection rather than a compile-time dependency: androidx is inside the
     * apk already, and the mod's own dex has no business carrying a second copy.
     */
    private Object visibleFragment(Activity activity) {
        try {
            Object manager = activity.getClass()
                    .getMethod("getSupportFragmentManager").invoke(activity);
            Object fragments = manager.getClass().getMethod("getFragments").invoke(manager);
            Object first = null;
            for (Object fragment : (List<?>) fragments) {
                View view = viewOf(fragment);
                if (view != null && view.isShown() && first == null) first = fragment;
            }
            if (first == null) Diary.note("no fragment on screen yet");
            return first;
        } catch (Throwable error) {
            Diary.note("no fragment manager: " + error);
            return null;
        }
    }

    private static View viewOf(Object fragment) {
        try {
            Object view = fragment.getClass().getMethod("getView").invoke(fragment);
            return view instanceof View ? (View) view : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------- the row

    private View row(final Activity activity, View screen) {
        // The screen is Compose and has no TextView to read a colour off, so
        // the background it is drawn on decides: TikTok's settings are white on
        // light and nearly black on dark.
        boolean dark = isDark(activity);
        int text = dark ? 0xFFFFFFFF : INK;
        int card = dark ? 0xFF161823 : 0xFFFFFFFF;
        int muted = dark ? 0x99FFFFFF : 0x99000000;

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(activity, 12));
        row.setBackground(background);

        TextView glyph = new TextView(activity);
        glyph.setText("♪");  // a note, the same one as on the icon
        glyph.setTextColor(0xFF16281F);
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
        box.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
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

    /** Whether the app is drawing itself dark, read off the window background. */
    private static boolean isDark(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
                int colour = ((android.graphics.drawable.ColorDrawable)
                        decor.getBackground()).getColor();
                if (Color.alpha(colour) > 0) return luminance(colour) < 0.5f;
            }
        } catch (Throwable ignored) {
        }
        return (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static float luminance(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
