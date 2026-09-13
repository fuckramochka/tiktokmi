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

    /**
     * A question with two answers, and a box that can be ticked.
     *
     * The same card as `show`, with a second button drawn quietly beside the
     * accent one -- refusing should not look like the harder thing to do.
     */
    public static void ask(Context context, String title, String message,
                           String yes, final Runnable onYes,
                           String no, final Runnable onNo,
                           String tick, final Ticked onTick) {
        try {
            Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

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

            if (tick != null) {
                final M3Switch box = new M3Switch(context);
                box.colours(Accent.colour(), skin.muted(), skin.card);
                box.setChecked(false);
                box.setOnChanged(new M3Switch.OnChanged() {
                    @Override
                    public void onChanged(boolean on) {
                        if (onTick != null) onTick.ticked(on);
                    }
                });

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(context, 18), 0, 0);
                TextView label = new TextView(context);
                label.setText(tick);
                label.setTextColor(skin.muted());
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                row.addView(label, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(box);
                card.addView(row);
            }

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            place.topMargin = dp(context, 18);
            card.addView(buttons, place);

            if (no != null) {
                TextView refuse = new TextView(context);
                refuse.setText(no);
                refuse.setTextColor(skin.muted());
                refuse.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                refuse.setGravity(Gravity.CENTER);
                refuse.setPadding(0, dp(context, 12), 0, dp(context, 12));
                refuse.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        close(dialog);
                        if (onNo != null) onNo.run();
                    }
                });
                buttons.addView(refuse, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }

            TextView accept = new TextView(context);
            accept.setText(yes);
            accept.setTextColor(onAccent());
            accept.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            accept.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            accept.setGravity(Gravity.CENTER);
            accept.setPadding(0, dp(context, 12), 0, dp(context, 12));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 24));
            accept.setBackground(pill);
            accept.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    close(dialog);
                    if (onYes != null) onYes.run();
                }
            });
            LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
            grow.leftMargin = no == null ? 0 : dp(context, 10);
            buttons.addView(accept, grow);

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 340),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.88f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("ask: " + error);
        }
    }

    /** What a ticked box says. */
    public interface Ticked {
        void ticked(boolean on);
    }

    private static void close(Dialog dialog) {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Throwable ignored) {
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
