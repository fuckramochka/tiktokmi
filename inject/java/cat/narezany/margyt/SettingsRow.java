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

    // ------------------------------------------------------- the lifecycle

    @Override
    public void onActivityResumed(Activity activity) {
        String name = activity.getClass().getName();
        if (!SETTINGS_ACTIVITY.equals(name)) {
            // every screen would drown the diary; the ones worth knowing about
            // are the ones that might be the settings screen under a new name
            if (name.toLowerCase(Locale.US).contains("setting")) Diary.note("saw " + name);
            return;
        }
        Diary.note("settings screen is up");
        final View decor = activity.getWindow().getDecorView();
        if (decor.getTag(TAG) != null) return;
        decor.setTag(TAG, Boolean.TRUE);

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
    public void onActivityDestroyed(Activity activity) {}

    // ------------------------------------------------------------ the work

    private void inject(Activity activity) {
        ViewGroup container = fragmentContainer(activity);
        if (container == null) return;
        ViewGroup parent = container.getParent() instanceof ViewGroup
                ? (ViewGroup) container.getParent() : null;
        if (parent == null || parent.getTag(TAG) != null) return;
        if (container.getWidth() == 0) return;  // not laid out yet

        int index = parent.indexOfChild(container);
        ViewGroup.LayoutParams params = container.getLayoutParams();

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag(TAG, Boolean.TRUE);

        // The container is what the fragment manager adds pages to and takes
        // them out of, so it is left exactly where it is -- the row goes above
        // it, wrapped around the outside, and nothing the app does has to know.
        parent.removeViewAt(index);
        column.addView(row(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(container, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        parent.addView(column, index, params);
        Diary.note("row added above " + container.getClass().getName());
    }

    /**
     * The view TikTok puts the settings pages into.
     *
     * Through its own method rather than the fragment manager: androidx is in
     * this apk with its method names obfuscated -- getFragments() and the rest
     * are gone -- while TikTok's own getFragmentContainer() keeps its name,
     * because TikTok's own code calls it.
     */
    private ViewGroup fragmentContainer(Activity activity) {
        try {
            Object id = activity.getClass().getMethod("getFragmentContainer").invoke(activity);
            View view = activity.findViewById(((Integer) id).intValue());
            if (view instanceof ViewGroup) return (ViewGroup) view;
            Diary.note("container 0x" + Integer.toHexString(((Integer) id).intValue())
                    + " is " + view);
        } catch (Throwable error) {
            Diary.note("no container: " + error);
        }
        // whatever the activity put on screen, then
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            View first = ((ViewGroup) content).getChildAt(0);
            if (first instanceof ViewGroup) {
                Diary.note("falling back to " + first.getClass().getName());
                return (ViewGroup) first;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- the row

    private View row(final Activity activity) {
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
        // the row sits above whatever handles the window insets, so it has to
        // keep clear of the status bar itself
        box.setPadding(dp(activity, 16), statusBar(activity) + dp(activity, 8),
                dp(activity, 16), dp(activity, 8));
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

    private static int statusBar(Activity activity) {
        try {
            android.view.WindowInsets insets =
                    activity.getWindow().getDecorView().getRootWindowInsets();
            if (insets != null && insets.getSystemWindowInsetTop() > 0) {
                return insets.getSystemWindowInsetTop();
            }
        } catch (Throwable ignored) {
        }
        try {
            int id = activity.getResources()
                    .getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return activity.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static float luminance(int colour) {
        return (0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                + 0.114f * Color.blue(colour)) / 255f;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
