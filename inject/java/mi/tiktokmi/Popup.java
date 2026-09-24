package mi.tiktokmi;

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

    public static void show(Context context, String title, String message,
                            String button, String[][] pictures, String[] captions) {
        try {
            Skin skin = Skin.remembered(context);
            Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.6f);
            }

            View card = card(context, skin, title, message, button, dialog);
            View gallery = gallery(context, skin, pictures, captions);
            if (gallery != null && card instanceof LinearLayout) {
                LinearLayout holder = (LinearLayout) card;
                LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                where.topMargin = dp(context, 14);
                holder.addView(gallery, Math.max(0, holder.getChildCount() - 1), where);
            }

            dialog.setContentView(card);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 360),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f));
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("popup: " + error);
        }
    }

    /**
     * Pictures side by side, one screenful at a time, each with its caption.
     *
     * A row inside a scroller rather than a pager: a pager is a library this
     * build does not have and would be a dependency for four photographs. The
     * page width is measured from the card, so a swipe lands on the next one
     * whatever the phone is.
     */
    private static View gallery(final Context context, Skin skin,
                                String[][] pictures, String[] captions) {
        if (pictures == null || pictures.length == 0) return null;

        final int width = Math.min(dp(context, 360),
                (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f))
                - dp(context, 48);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < pictures.length; i++) {
            LinearLayout page = new LinearLayout(context);
            page.setOrientation(LinearLayout.VERTICAL);

            android.graphics.Bitmap shot = decode(pictures[i]);
            if (shot != null) {
                android.widget.ImageView view = new android.widget.ImageView(context);
                view.setImageBitmap(round(context, shot, dp(context, 12)));
                view.setAdjustViewBounds(true);
                view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                page.addView(view, new LinearLayout.LayoutParams(
                        width, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            if (captions != null && i < captions.length) {
                TextView caption = new TextView(context);
                caption.setText(captions[i]);
                caption.setTextColor(skin.muted());
                caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                caption.setGravity(Gravity.CENTER);
                caption.setPadding(0, dp(context, 8), 0, 0);
                page.addView(caption, new LinearLayout.LayoutParams(
                        width, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout.LayoutParams beside = new LinearLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) beside.leftMargin = dp(context, 16);
            row.addView(page, beside);
        }

        android.widget.HorizontalScrollView scroller =
                new android.widget.HorizontalScrollView(context) {
                    @Override
                    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                        // the card is not scrollable, so nothing else wants these
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return super.onInterceptTouchEvent(event);
                    }
                };
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row);

        TextView hint = new TextView(context);
        hint.setText("‹ " + pictures.length + " ›");
        hint.setTextColor(skin.muted());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(context, 6), 0, 0);

        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        holder.addView(hint);
        return holder;
    }

    /** The pieces the build split it into, put back together once. */
    private static android.graphics.Bitmap decode(String[] pieces) {
        if (pieces == null || pieces.length == 0) return null;
        android.graphics.Bitmap known = decoded.get(pieces);
        if (known != null) return known;
        try {
            StringBuilder whole = new StringBuilder();
            for (String piece : pieces) whole.append(piece);
            byte[] raw = android.util.Base64.decode(whole.toString(),
                    android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
            if (bitmap != null) decoded.put(pieces, bitmap);
            return bitmap;
        } catch (Throwable error) {
            Diary.note("popup picture: " + error);
            return null;
        }
    }

    private static final java.util.Map<String[], android.graphics.Bitmap> decoded =
            new java.util.HashMap<String[], android.graphics.Bitmap>();

    /** The same picture with its corners taken off. */
    private static android.graphics.Bitmap round(Context context,
                                                 android.graphics.Bitmap source,
                                                 float radius) {
        try {
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                    source.getWidth(), source.getHeight(),
                    android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(out);
            android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.RectF bounds = new android.graphics.RectF(
                    0, 0, source.getWidth(), source.getHeight());
            canvas.drawRoundRect(bounds, radius * 2, radius * 2, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(source, 0, 0, paint);
            return out;
        } catch (Throwable ignored) {
            return source;
        }
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
     * A line of text to be edited, in the same card as everything else.
     *
     * Used where a setting is a word rather than a switch. Empty is allowed
     * and means the same as whatever the setting's own default is -- nothing
     * here decides that.
     */
    public static void write(Context context, String title, String current,
                             String button, final Written onDone) {
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

            TextView head = new TextView(context);
            head.setText(title);
            head.setTextColor(skin.text);
            head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, 0, 0, dp(context, 12));
            card.addView(head);

            final android.widget.EditText field = new android.widget.EditText(context);
            field.setText(current == null ? "" : current);
            field.setTextColor(skin.text);
            field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            field.setSingleLine(true);
            field.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            GradientDrawable box = new GradientDrawable();
            box.setColor(skin.page);
            box.setCornerRadius(dp(context, 10));
            box.setStroke(dp(context, 1), Accent.colour());
            field.setBackground(box);
            field.setSelection(field.getText().length());
            card.addView(field);

            TextView go = new TextView(context);
            go.setText(button);
            go.setTextColor(onAccent());
            go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            go.setTypeface(Typeface.DEFAULT_BOLD);
            go.setGravity(Gravity.CENTER);
            go.setPadding(0, dp(context, 12), 0, dp(context, 12));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Accent.colour());
            pill.setCornerRadius(dp(context, 12));
            go.setBackground(pill);
            LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            below.topMargin = dp(context, 16);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        onDone.wrote(field.getText().toString());
                    } catch (Throwable ignored) {
                    }
                    dialog.dismiss();
                }
            });
            card.addView(go, below);

            dialog.setContentView(card);
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

    public interface Written {
        void wrote(String text);
    }

    /**
     * A question with two answers, and a box that can be ticked.
     *
     * The same card as `show`, with a second button drawn quietly beside the
     * accent one -- refusing should not look like the harder thing to do.
     */
    public static void ask(Context context, String title, String message,
                           String yes, final Runnable onYes,
                           String no, final Runnable onNo) {
        ask(context, title, message, yes, onYes, no, onNo, null, null);
    }

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
