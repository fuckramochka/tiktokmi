package cat.narezany.margyt;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The mod's own dialog, in TikTok's language.
 *
 * TikTok has a good one -- rounded, centred, the screen dimmed behind it --
 * and it is behind an obfuscated name, which is the one thing this build will
 * not anchor on for something as small as a message: a renamed class would be
 * a crash where a sentence should have been.
 *
 * So it is drawn here instead, out of the same numbers. `Skin` measured the
 * card colour, the text colour and the corner radius off TikTok's own settings
 * screen, in whichever theme it was in, and the dialog is built from those --
 * which is why it matches without a single value being copied by eye.
 */
public final class Popup {

    private Popup() {}

    public static void show(Context context, String message) {
        show(context, null, message);
    }

    public static void show(Context context, String title, String message) {
        show(context, title, message, null);
    }

    public static void show(Context context, String title, String message, String button) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new Dialog(context);

            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                // the card is the rounded thing, so the window itself is not
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            dialog.setContentView(card(context, skin, title, message, button, dialog));
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 320),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.86f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    private static View card(Context context, Skin skin, String title, String message,
                             String button, final Dialog dialog) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 16));

        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(Math.max(skin.radius, dp(context, 16)));
        card.setBackground(background);

        if (title != null && title.length() > 0) {
            TextView head = new TextView(context);
            head.setText(title);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, 0, 0, dp(context, 8));
            card.addView(head);
        }

        TextView body = new TextView(context);
        body.setText(message);
        body.setTextColor(skin.text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        body.setGravity(Gravity.CENTER);
        card.addView(body);

        TextView close = new TextView(context);
        close.setText(button == null || button.length() == 0 ? Text.CLOSE : button);
        close.setTextColor(onAccent());
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        close.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(context, 12), 0, dp(context, 12));

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(context, 24));
        close.setBackground(pill);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 20);
        card.addView(close, params);
        return card;
    }

    /** Dark letters on a light accent, white on a dark one. */
    private static int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
