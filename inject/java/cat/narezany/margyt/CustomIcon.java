package cat.narezany.margyt;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;

/**
 * A picture of one's own on the home screen, opening MargyT.
 *
 * Android has no way to give an app an icon it was not built with: the icons
 * to choose from are components in the manifest (Launcher). What it does
 * allow is a pinned shortcut with any picture, which the home screen asks
 * about once and then shows like an icon. Nothing leaves the phone.
 */
final class CustomIcon {
    private CustomIcon() {}

    static final int PICK = 0x4D43; // "MC"

    /** The adaptive canvas, 108dp at four pixels a dp; the mask shows the middle 72dp. */
    private static final int SIDE = 432, SHOWN = 288;

    static void pick(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(intent, PICK);
        } catch (Throwable error) {
            Screen.say(Text.CUSTOM_ICON_FAILED);
        }
    }

    /** The picture is in: how it will look, both ways, and the button. */
    static void offer(final Activity activity, final Uri source) {
        Net.away("custom icon", () -> {
            final Bitmap picture = read(activity, source);
            activity.runOnUiThread(() -> {
                if (picture == null) { Screen.say(Text.CUSTOM_ICON_FAILED); return; }
                sheet(activity, picture);
            });
        });
    }

    private static Bitmap read(Context context, Uri source) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(source)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2;
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            try (InputStream in = context.getContentResolver().openInputStream(source)) {
                Bitmap whole = BitmapFactory.decodeStream(in, null, decode);
                if (whole == null) return null;
                // the middle square: an icon is square, and a photo seldom is
                int side = Math.min(whole.getWidth(), whole.getHeight());
                Bitmap square = Bitmap.createBitmap(whole, (whole.getWidth() - side) / 2,
                        (whole.getHeight() - side) / 2, side, side);
                return Bitmap.createScaledBitmap(square, SIDE, SIDE, true);
            }
        } catch (Throwable error) {
            Diary.note("custom icon: " + error);
            return null;
        }
    }

    /**
     * The picture on the adaptive canvas. Filled, it covers all of it and the
     * home screen's shape crops the edges; whole, it fits the part the shape
     * shows, on its own edge colour.
     */
    static Bitmap render(Bitmap picture, boolean fill) {
        Bitmap out = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (fill) {
            canvas.drawBitmap(picture, null, new Rect(0, 0, SIDE, SIDE), paint);
        } else {
            canvas.drawColor(edge(picture));
            int at = (SIDE - SHOWN) / 2;
            canvas.drawBitmap(picture, null, new Rect(at, at, at + SHOWN, at + SHOWN), paint);
        }
        return out;
    }

    /** The average colour round the picture's edge, for the background behind a whole one. */
    private static int edge(Bitmap picture) {
        long r = 0, g = 0, b = 0;
        int n = 0, w = picture.getWidth(), h = picture.getHeight();
        for (int i = 0; i < 64; i++) {
            int x = i * (w - 1) / 63, y = i * (h - 1) / 63;
            int[] seen = {picture.getPixel(x, 0), picture.getPixel(x, h - 1), picture.getPixel(0, y), picture.getPixel(w - 1, y)};
            for (int c : seen) { r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++; }
        }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    /** The render through a round mask, the way most home screens will show it. */
    private static Bitmap preview(Bitmap rendered) {
        Bitmap out = Bitmap.createBitmap(SHOWN, SHOWN, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Path round = new Path();
        round.addOval(new RectF(0, 0, SHOWN, SHOWN), Path.Direction.CW);
        canvas.clipPath(round);
        int at = (SIDE - SHOWN) / 2;
        canvas.drawBitmap(rendered, new Rect(at, at, at + SHOWN, at + SHOWN), new Rect(0, 0, SHOWN, SHOWN),
                new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    private static void sheet(final Activity activity, final Bitmap picture) {
        final Skin skin = Skin.remembered(activity);
        final Panel panel = Panel.with(activity, skin, Text.CUSTOM_ICON).subtitle(Text.CUSTOM_ICON_NOTE);
        final boolean[] fill = {true};
        final Bitmap[] made = {render(picture, true), render(picture, false)};

        LinearLayout both = new LinearLayout(activity);
        both.setGravity(Gravity.CENTER);
        final FrameLayout[] frames = new FrameLayout[2];
        String[] names = {Text.CUSTOM_ICON_FILL, Text.CUSTOM_ICON_WHOLE};
        for (int i = 0; i < 2; i++) {
            final int which = i;
            LinearLayout one = new LinearLayout(activity);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            one.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
            FrameLayout frame = new FrameLayout(activity);
            frame.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            ImageView shot = new ImageView(activity);
            shot.setImageBitmap(preview(made[i]));
            frame.addView(shot, new FrameLayout.LayoutParams(dp(activity, 72), dp(activity, 72)));
            frames[i] = frame;
            one.addView(frame);
            TextView name = new TextView(activity);
            name.setText(names[i]);
            name.setTextColor(skin.text);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setPadding(0, dp(activity, 8), 0, 0);
            one.addView(name);
            one.setOnClickListener(v -> { fill[0] = which == 0; outline(activity, frames, which); });
            both.addView(one);
        }
        outline(activity, frames, 0);
        panel.view(both);

        final android.widget.EditText label = panel.field(Text.CUSTOM_ICON_NAME);
        label.setText(activity.getApplicationInfo().loadLabel(activity.getPackageManager()));
        panel.primary(Text.CUSTOM_ICON_PIN, () -> pin(activity, made[fill[0] ? 0 : 1], label.getText().toString()));
        panel.quiet(Text.CANCEL, null).show();
    }

    private static void outline(Context context, FrameLayout[] frames, int chosen) {
        for (int i = 0; i < frames.length; i++) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            if (i == chosen) ring.setStroke(dp(context, 2), Accent.colour());
            frames[i].setBackground(ring);
        }
    }

    private static void pin(Activity activity, Bitmap icon, String label) {
        try {
            ShortcutManager shortcuts = Build.VERSION.SDK_INT >= 26 ? activity.getSystemService(ShortcutManager.class) : null;
            if (shortcuts == null || !shortcuts.isRequestPinShortcutSupported()) {
                Screen.say(Text.CUSTOM_ICON_UNSUPPORTED);
                return;
            }
            String name = label == null || label.trim().length() == 0
                    ? String.valueOf(activity.getApplicationInfo().loadLabel(activity.getPackageManager())) : label.trim();
            ShortcutInfo info = new ShortcutInfo.Builder(activity, "margyt_icon_" + System.currentTimeMillis())
                    .setShortLabel(name)
                    .setIcon(Icon.createWithAdaptiveBitmap(icon))
                    .setIntent(opening(activity))
                    .build();
            shortcuts.requestPinShortcut(info, null);
        } catch (Throwable error) {
            Diary.note("custom icon pin: " + error);
            Screen.say(Text.CUSTOM_ICON_FAILED);
        }
    }

    /**
     * What the shortcut starts: the screen behind every icon, not the icon
     * chosen now. Choosing another icon switches that one off, and a shortcut
     * to a component that is off does not open.
     */
    private static Intent opening(Context context) {
        PackageManager packages = context.getPackageManager();
        String self = context.getPackageName();
        String screen = null;
        try {
            ActivityInfo entry = packages.getActivityInfo(new ComponentName(self, Shots.DEFAULT),
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            screen = entry.targetActivity != null ? entry.targetActivity : entry.name;
        } catch (Throwable ignored) {
        }
        Intent intent;
        if (screen != null) {
            intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(self, screen));
        } else {
            intent = packages.getLaunchIntentForPackage(self);
            if (intent == null) intent = new Intent(Intent.ACTION_MAIN).setPackage(self);
        }
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
