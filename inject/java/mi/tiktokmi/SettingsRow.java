package mi.tiktokmi;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Typeface;
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
 * The TikTok MI row, at the top of TikTok's own settings screen.
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

    // setTag(int, ...) spells "TTMI"
    private static final int TAG = 0x54544D49;
    private static final int GESTURE_TAG = 0x54544D47; // "TTMG"

    // ------------------------------------------------------- the lifecycle

    /** The two screens that show an avatar filling the display. */
    private static final String[] AVATAR_SCREENS = {
            "com.ss.android.ugc.profile.business.ur.enlarge.EnlargeAvatarActivity",
            "com.ss.android.ugc.profile.business.ur.enlarge.EnlargeAvatarOptActivity",
    };

    @Override
    public void onActivityResumed(Activity activity) {
        Plugins.onActivityResumed(activity);
        Screen.at(activity);
        Updater.resumed(activity);
        Themes.watch(activity);
        attachGestures(activity);

        String name = activity.getClass().getName();

        for (String screen : AVATAR_SCREENS) {
            if (screen.equals(name)) {
                addSaveAvatar(activity);
                return;
            }
        }
        if (!SETTINGS_ACTIVITY.equals(name)) {
            if (name.toLowerCase(Locale.US).contains("setting")) Diary.note("saw " + name);
            return;
        }
        Diary.note("settings screen is up");
        final View decor = activity.getWindow().getDecorView();
        if (decor.getTag(TAG) != null) return;
        decor.setTag(TAG, Boolean.TRUE);

        decor.getViewTreeObserver().addOnGlobalLayoutListener(new Injector(activity));
    }

    /**
     * Attaches global gestures (2-finger long press) to open TikTok MI settings anywhere.
     */
    private static void attachGestures(final Activity activity) {
        try {
            final View decor = activity.getWindow().getDecorView();
            if (decor.getTag(GESTURE_TAG) != null) return;
            decor.setTag(GESTURE_TAG, Boolean.TRUE);

            decor.setOnTouchListener(new View.OnTouchListener() {
                private long touchDownTime;
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    if (event.getPointerCount() >= 2) {
                        int action = event.getActionMasked();
                        if (action == android.view.MotionEvent.ACTION_POINTER_DOWN) {
                            touchDownTime = System.currentTimeMillis();
                        } else if (action == android.view.MotionEvent.ACTION_POINTER_UP) {
                            if (touchDownTime > 0 && (System.currentTimeMillis() - touchDownTime) > 600) {
                                touchDownTime = 0;
                                try {
                                    activity.startActivity(new Intent(activity, SettingsActivity.class));
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /**
     * A button over the enlarged avatar, because TikTok offers none.
     *
     * Put on the window rather than inside the screen's own layout: whatever
     * that layout is called this month, a window has a content view, and a
     * child added to it sits on top of everything already there.
     */
    private static void addSaveAvatar(final Activity activity) {
        try {
            ViewGroup content = (ViewGroup) activity.getWindow()
                    .getDecorView().findViewById(android.R.id.content);
            if (!Avatars.isEnabled()) return;
            if (content == null || content.getTag(SAVE_TAG) != null) return;
            content.setTag(SAVE_TAG, Boolean.TRUE);

            TextView save = new TextView(activity);
            save.setText(Text.SAVE_AVATAR);
            save.setTextColor(0xFFFFFFFF);
            save.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            save.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            save.setGravity(Gravity.CENTER);
            save.setPadding(dp(activity, 22), dp(activity, 11), dp(activity, 22), dp(activity, 11));

            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(activity, 22));
            save.setBackground(pill);
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Avatars.save(activity);
                }
            });

            android.widget.FrameLayout.LayoutParams params =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = (int) (activity.getResources()
                    .getDisplayMetrics().heightPixels * 0.74f);
            content.addView(save, params);
            Diary.note("save-avatar button added");
        } catch (Throwable error) {
            Diary.note("save-avatar button failed: " + error);
        }
    }

    private static final int SAVE_TAG = 0x54544D41;  // "TTMA"

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {
        Plugins.onActivityCreated(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {
        Plugins.onActivityPaused(activity);
        Screen.gone(activity);
        try {
            WatchHistory.flush();
            ChatSearch.flush();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onActivityStopped(Activity activity) {
        try {
            WatchHistory.flush();
            ChatSearch.flush();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}

    private final class Injector implements ViewTreeObserver.OnGlobalLayoutListener {

        private final Activity activity;
        private View box;
        private ViewGroup container;

        Injector(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onGlobalLayout() {
            try {
                if (box == null) inject(this);
            } catch (Throwable error) {
                Diary.note("row failed: " + error);
            }
        }
    }

    // ------------------------------------------------------------ the work

    private void inject(Injector injector) {
        Activity activity = injector.activity;
        ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
        if (content == null || content.getTag(TAG) != null) return;
        content.setTag(TAG, Boolean.TRUE);

        Skin skin = Skin.remembered(activity);
        final View fab = floatingButton(activity, skin);

        android.widget.FrameLayout.LayoutParams fabParams = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 46),
                Gravity.BOTTOM | Gravity.END);
        fabParams.bottomMargin = dp(activity, 28);
        fabParams.rightMargin = dp(activity, 20);

        content.addView(fab, fabParams);
        injector.box = fab;
        Diary.note("TikTok MI floating action pill added cleanly");
    }

    private View floatingButton(final Activity activity, Skin skin) {
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 20), dp(activity, 8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Accent.colour());
        bg.setCornerRadius(dp(activity, 24));
        bg.setStroke(dp(activity, 1), 0x33FFFFFF);
        pill.setBackground(bg);
        pill.setElevation(dp(activity, 8));

        TextView glyph = new TextView(activity);
        glyph.setText("\u266A");
        glyph.setTextColor(0xFFFFFFFF);
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        pill.addView(glyph);

        TextView title = new TextView(activity);
        title.setText("  TikTok MI");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        pill.addView(title);

        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    activity.startActivity(new Intent(activity, SettingsActivity.class));
                } catch (Throwable ignored) {
                }
            }
        });
        return pill;
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

    private View row(final Activity activity, Skin skin) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(skin.radius);
        row.setBackground(background);

        TextView glyph = new TextView(activity);
        glyph.setText("\u266A");  // a note, the same one as on the icon
        glyph.setTextColor(skin.text);
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)));

        TextView title = new TextView(activity);
        title.setText(Text.ROW);
        title.setTextColor(skin.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 16);
        row.addView(title, titleParams);

        TextView chevron = new TextView(activity);
        chevron.setText("\u203A");
        chevron.setTextColor(skin.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
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
        box.setPadding(skin.margin, statusBar(activity) + dp(activity, 8),
                skin.margin, dp(activity, 8));
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56)));
        return box;
    }

    /**
     * One status bar between the two of us.
     *
     * The screen below already keeps clear of the status bar itself, and with
     * the row above it that gap ends up drawn twice -- so the row takes the
     * inset and passes the screen a set without it.
     */
    private void keepClearOfTheStatusBar(final LinearLayout column, final View box,
                                         final ViewGroup screen, final Skin skin) {
        try {
            column.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public android.view.WindowInsets onApplyWindowInsets(
                        View view, android.view.WindowInsets insets) {
                    int top = insets.getSystemWindowInsetTop();
                    box.setPadding(skin.margin, top + dp(column.getContext(), 8),
                            skin.margin, dp(column.getContext(), 8));
                    screen.dispatchApplyWindowInsets(insets.replaceSystemWindowInsets(
                            insets.getSystemWindowInsetLeft(), 0,
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom()));
                    return insets.consumeSystemWindowInsets();
                }
            });
            column.requestApplyInsets();
        } catch (Throwable error) {
            Diary.note("insets left alone: " + error);
        }
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

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
