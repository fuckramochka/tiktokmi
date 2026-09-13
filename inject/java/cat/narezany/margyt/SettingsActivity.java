package cat.narezany.margyt;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * MargyT's own screen, drawn in TikTok's settings language.
 *
 * Page, cards, title, grey section labels: the shapes are TikTok's, and so are
 * the colours -- not copied out of a screenshot but the ones the row measured
 * off the real settings screen and kept. Opened from the launcher with TikTok
 * never having been on screen, it falls back to plain black or white.
 *
 * Every view is built here in code. Adding a layout or a style would mean
 * adding resources, and adding resources means rewriting a 25 MB resource
 * table -- the one thing this build refuses to do.
 */
public class SettingsActivity extends Activity {

    private Skin skin;
    private FrameLayout root;
    private LinearLayout column;
    private View restartBar;

    /**
     * Whether a setting was changed in this process.
     *
     * Static, because the bar is about the process and not about the screen:
     * leaving the settings and coming back does not un-change what was changed,
     * and only a restart -- which is a new process, where this is false again --
     * does.
     */
    private static boolean pending;

    /** Where the mod, its people and its money live. */
    private static final String CHANNEL = "https://t.me/margytiktok";
    private static final String FORUM = "https://t.me/margeletforum";
    private static final String OWNER = "https://t.me/narezany";
    private static final String HELPER = "https://t.me/OPlusAce5";
    private static final String DOCS =
            "https://github.com/narezany/MargyT/blob/main/docs/plugins.md";
    private static final String YOOMONEY = "https://yoomoney.ru/to/4100118196133693";
    private static final String CARD_NUMBER = "2204120143055305";

    private boolean countriesOpen;
    private boolean accentOpen;
    private boolean thanksOpen;
    private boolean diaryOpen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Margy.attach(this);
        skin = Skin.remembered(this);
        dressTheWindow();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(skin.page);
        scroll.setFillViewport(true);

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root = new FrameLayout(this);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void dressTheWindow() {
        try {
            getWindow().setStatusBarColor(skin.page);
            getWindow().setNavigationBarColor(skin.page);
            if (!skin.dark()) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------- the screen

    private void rebuild() {
        column.removeAllViews();
        column.addView(backArrow());
        column.addView(title("MargyT"));

        column.addView(section(Text.REGION));
        LinearLayout head = card();
        head.addView(switchRow());
        column.addView(wrap(head));

        column.addView(section(Text.COUNTRY));
        LinearLayout countries = card();
        countries.addView(countryHead());
        if (countriesOpen) {
            countries.addView(line());
            for (String[] country : Margy.COUNTRIES) {
                countries.addView(countryRow(country));
            }
        }
        column.addView(wrap(countries));

        column.addView(section(Text.ACCENT));
        LinearLayout accent = card();
        accent.addView(accentHead());
        if (accentOpen) {
            accent.addView(line());
            accent.addView(palette());
        }
        column.addView(wrap(accent));

        column.addView(caption(Text.ABOUT));

        column.addView(section(Text.DOWNLOADS));
        LinearLayout downloads = card();
        downloads.addView(watermarkRow());
        column.addView(wrap(downloads));

        column.addView(section(Text.PLUGINS));
        LinearLayout plugins = card();
        plugins.addView(installRow());
        plugins.addView(line());
        plugins.addView(linkRow(Text.PLUGIN_DOCS, Text.PLUGIN_DOCS_NOTE, DOCS));
        List<Plugins.Info> installed = Plugins.list();
        if (installed.isEmpty()) {
            plugins.addView(line());
            plugins.addView(quiet(Text.PLUGIN_NONE));
        } else {
            for (Plugins.Info info : installed) {
                plugins.addView(line());
                plugins.addView(pluginRow(info));
            }
        }
        column.addView(wrap(plugins));
        column.addView(caption(Text.PLUGIN_WARNING));

        column.addView(section(Text.LINKS));
        LinearLayout links = card();
        links.addView(linkRow(Text.CHANNEL, "@margytiktok", CHANNEL));
        links.addView(line());
        links.addView(linkRow(Text.FORUM, "@margeletforum", FORUM));
        links.addView(line());
        links.addView(thanksHead());
        if (thanksOpen) {
            links.addView(line());
            links.addView(thanks());
        }
        column.addView(wrap(links));

        column.addView(section(Text.DIARY));
        LinearLayout diary = card();
        diary.addView(diaryHead());
        if (diaryOpen) {
            diary.addView(line());
            diary.addView(diaryLines());
        }
        column.addView(wrap(diary));

        showRestartBar();
    }

    /**
     * The bar that says a restart is due, pinned to the foot of the screen.
     *
     * It sits in the root frame rather than in the column, so it stays put
     * while the page scrolls under it, and it is built again on every rebuild
     * because the accent it is painted with may be what just changed.
     */
    private void showRestartBar() {
        if (restartBar != null) {
            root.removeView(restartBar);
            restartBar = null;
        }
        if (pending) {
            restartBar = restartBar();
            root.addView(restartBar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        }
        // the last card has to be able to clear the bar when it is there
        column.setPadding(0, statusBar(), 0,
                dp(32) + (pending ? dp(64) + navigationBar() : 0));
    }

    /** A setting changed: redraw, and from now on the bar is up. */
    private void markChanged() {
        pending = true;
        rebuild();
    }

    // ----------------------------------------------------------- the rows

    private View switchRow() {
        LinearLayout row = row();
        row.addView(label(Text.CHANGE_REGION), grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Margy.isEnabled());
        toggle.setOnChanged(checked -> {
            Margy.setEnabled(checked);
            markChanged();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.setChecked(!toggle.isChecked(), true);
            Margy.setEnabled(toggle.isChecked());
            markChanged();
        });
        return sized(row, 56);
    }

    private View countryHead() {
        String[] current = Margy.current();
        LinearLayout row = row();
        row.setAlpha(Margy.isEnabled() ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(current[Margy.LABEL]));
        text.addView(detail(current[Margy.CARRIER] + "  ·  " + current[Margy.MCCMNC]
                + "  ·  " + current[Margy.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        TextView chevron = new TextView(this);
        chevron.setText(countriesOpen ? "⌃" : "⌄");
        chevron.setTextColor(skin.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(chevron);

        if (Margy.isEnabled()) {
            row.setOnClickListener(v -> {
                countriesOpen = !countriesOpen;
                rebuild();
            });
        }
        return sized(row, 64);
    }

    private View countryRow(final String[] country) {
        boolean selected = country[Margy.ISO].equals(Margy.iso());
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(country[Margy.LABEL]));
        text.addView(detail(country[Margy.CARRIER] + "  ·  " + country[Margy.MCCMNC]
                + "  ·  " + country[Margy.ISO].toUpperCase(Locale.US)));
        row.addView(text, grow());

        if (selected) row.addView(new Check(this, Accent.colour()));

        row.setOnClickListener(v -> {
            Margy.setIso(country[Margy.ISO]);
            countriesOpen = false;
            markChanged();
        });
        return sized(row, 60);
    }

    private View accentHead() {
        LinearLayout row = row();
        row.addView(label(Text.ACCENT_COLOUR), grow());
        row.addView(new Dot(this, Accent.colour(), false));

        TextView chevron = new TextView(this);
        chevron.setText(accentOpen ? "⌃" : "⌄");
        chevron.setTextColor(skin.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        chevron.setPadding(dp(12), 0, 0, 0);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            accentOpen = !accentOpen;
            rebuild();
        });
        return sized(row, 56);
    }

    private View palette() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(12), dp(16), dp(16));

        int[] palette = Accent.palette();
        LinearLayout line = null;
        for (int i = 0; i < palette.length; i++) {
            if (i % 5 == 0) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(0, dp(6), 0, dp(6));
                rows.addView(line);
            }
            final int colour = palette[i];
            Dot dot = new Dot(this, colour, colour == Accent.colour());
            dot.setOnClickListener(v -> {
                Accent.set(colour);
                markChanged();
            });
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(36), dp(36), 1f);
            line.addView(dot, params);
        }

        TextView note = new TextView(this);
        note.setText(Text.ACCENT_NOTE);
        note.setTextColor(skin.muted());
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setPadding(0, dp(10), 0, 0);
        rows.addView(note);
        return rows;
    }

    private View watermarkRow() {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.NO_WATERMARK));
        text.addView(detail(Text.NO_WATERMARK_NOTE));
        row.addView(text, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Download.isEnabled());
        toggle.setOnChanged(checked -> {
            Download.setEnabled(checked);
            markChanged();
        });
        row.addView(toggle);

        row.setOnClickListener(v -> {
            toggle.setChecked(!toggle.isChecked(), true);
            Download.setEnabled(toggle.isChecked());
            markChanged();
        });
        return sized(row, 64);
    }

    // ----------------------------------------------------------- the plugins

    private static final int PICK_PLUGIN = 0x4D50;  // "MP"

    private View installRow() {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(Text.PLUGIN_INSTALL));
        text.addView(detail(Text.PLUGIN_INSTALL_NOTE));
        row.addView(text, grow());

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(Accent.colour());
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        plus.setPadding(dp(12), 0, 0, 0);
        row.addView(plus);

        row.setOnClickListener(v -> pickPlugin());
        return sized(row, 64);
    }

    /**
     * One installed plugin: what it says about itself, and a switch.
     *
     * Not `sized()` like the other rows -- a description is as tall as it is,
     * and a plugin whose author wrote two sentences should not have the second
     * one clipped.
     */
    private View pluginRow(final Plugins.Info info) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        Bitmap icon = info.icon();
        if (icon != null) {
            ImageView view = new ImageView(this);
            view.setImageBitmap(icon);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(view, size);
        } else {
            LinearLayout.LayoutParams size =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            size.rightMargin = dp(14);
            row.addView(new Dot(this, Accent.colour(), false), size);
        }

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(info.name + "  " + info.version));

        String by = info.author;
        if (!info.description.isEmpty()) by = by + "  ·  " + info.description;
        text.addView(detail(by));
        if (info.trouble != null) text.addView(detail(info.trouble));
        row.addView(text, grow());

        final M3Switch toggle = new M3Switch(this);
        toggle.colours(Accent.colour(), skin.muted(), skin.card);
        toggle.setChecked(Plugins.isEnabled(info.id));
        toggle.setEnabled(info.trouble == null || Plugins.isEnabled(info.id));
        toggle.setOnChanged(checked -> {
            Plugins.setEnabled(info.id, checked);
            markChanged();
        });
        row.addView(toggle);

        row.setOnLongClickListener(v -> {
            askToRemove(info);
            return true;
        });
        return row;
    }

    private void pickPlugin() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // .mtp is nobody's registered type, so the picker is shown everything
            intent.setType("*/*");
            startActivityForResult(intent, PICK_PLUGIN);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PLUGIN || result != RESULT_OK || data == null) return;
        Uri source = data.getData();
        if (source == null) return;
        try {
            Plugins.install(this, source);
            Toast.makeText(this, Text.PLUGIN_INSTALLED, Toast.LENGTH_SHORT).show();
            markChanged();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error.getMessage() == null
                    ? error : error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void askToRemove(final Plugins.Info info) {
        try {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(Text.PLUGIN_REMOVE_ASK)
                    .setMessage(info.name + "  " + info.version)
                    .setNegativeButton(Text.CANCEL, null)
                    .setPositiveButton(Text.PLUGIN_REMOVE, (dialog, which) -> {
                        Plugins.uninstall(this, info.id);
                        markChanged();
                    })
                    .show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------- the links

    private View linkRow(String title, String handle, final String url) {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(title));
        text.addView(detail(handle));
        row.addView(text, grow());
        row.addView(away());

        row.setOnClickListener(v -> open(url));
        return sized(row, 64);
    }

    private View thanksHead() {
        LinearLayout row = row();
        row.addView(label(Text.THANKS), grow());

        TextView chevron = new TextView(this);
        chevron.setText(thanksOpen ? "⌃" : "⌄");
        chevron.setTextColor(skin.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            thanksOpen = !thanksOpen;
            rebuild();
        });
        return sized(row, 56);
    }

    /** Who made this, and the two ways to pay for it. */
    private View thanks() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(6), 0, dp(10));

        rows.addView(quiet(Text.THANKS_NOTE));
        rows.addView(person("@narezany", Text.THANKS_OWNER, OWNER));
        rows.addView(person("Claude Opus 5", Text.THANKS_CLAUDE, null));
        rows.addView(person("@OPlusAce5", Text.THANKS_HELPER, HELPER));

        rows.addView(line());

        TextView heading = new TextView(this);
        heading.setText(Text.DONATE);
        heading.setTextColor(skin.text);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(dp(16), dp(14), dp(16), dp(2));
        rows.addView(heading);

        rows.addView(quiet(Text.DONATE_NOTE));

        LinearLayout card = row();
        LinearLayout cardText = new LinearLayout(this);
        cardText.setOrientation(LinearLayout.VERTICAL);
        cardText.addView(label(spaced(CARD_NUMBER)));
        cardText.addView(detail(Text.CARD + "  ·  " + Text.TAP_TO_COPY));
        card.addView(cardText, grow());
        card.setOnClickListener(v -> copy(Text.CARD, CARD_NUMBER));
        rows.addView(sized(card, 64));

        rows.addView(linkRow(Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY));
        return rows;
    }

    private View person(String name, String what, final String url) {
        LinearLayout row = row();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(name));
        text.addView(detail(what));
        row.addView(text, grow());

        if (url != null) {
            row.addView(away());
            row.setOnClickListener(v -> open(url));
        }
        return sized(row, 60);
    }

    /** The mark on a row that leaves the app. */
    private TextView away() {
        TextView arrow = new TextView(this);
        arrow.setText("↗");
        arrow.setTextColor(skin.muted());
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        arrow.setPadding(dp(12), 0, 0, 0);
        return arrow;
    }

    /** A paragraph that is there to be read once and then ignored. */
    private TextView quiet(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setPadding(dp(16), dp(2), dp(16), dp(8));
        return view;
    }

    /** A card number is read off the screen by a person, so it is grouped. */
    private static String spaced(String digits) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) out.append(' ');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    private void open(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
            // a phone with no browser and no Telegram is a phone that says so
            Toast.makeText(this, Text.NO_BROWSER, Toast.LENGTH_SHORT).show();
        }
    }

    private void copy(String what, String text) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(what, text));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private View diaryHead() {
        LinearLayout row = row();
        row.addView(label(Text.DIARY_TITLE), grow());

        TextView copy = new TextView(this);
        copy.setText(Text.COPY);
        copy.setTextColor(Accent.colour());
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        copy.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.setPadding(dp(12), dp(8), dp(4), dp(8));
        copy.setOnClickListener(v -> copyDiary());
        row.addView(copy);

        TextView chevron = new TextView(this);
        chevron.setText(diaryOpen ? "⌃" : "⌄");
        chevron.setTextColor(skin.muted());
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        chevron.setPadding(dp(12), 0, 0, 0);
        row.addView(chevron);

        row.setOnClickListener(v -> {
            diaryOpen = !diaryOpen;
            rebuild();
        });
        return sized(row, 56);
    }

    private View diaryLines() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(12));
        for (String entry : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText(entry);
            view.setTextColor(skin.muted());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        TextView clear = new TextView(this);
        clear.setText(Text.CLEAR);
        clear.setTextColor(skin.muted());
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        clear.setPadding(0, dp(12), 0, 0);
        clear.setOnClickListener(v -> {
            Diary.clear();
            rebuild();
        });
        box.addView(clear);
        return box;
    }

    /**
     * Start TikTok over, so everything is drawn again in the new colour.
     *
     * The launcher's own intent, then out: what comes back is a fresh process
     * with nothing of the old one's colours cached in it.
     */
    private void restartTikTok() {
        try {
            android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (intent == null) return;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            Runtime.getRuntime().exit(0);
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    private void copyDiary() {
        try {
            StringBuilder out = new StringBuilder("MargyT\n");
            List<String> lines = Diary.lines();
            for (String line : lines) out.append(line).append('\n');
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("MargyT", out.toString()));
            Toast.makeText(this, Text.COPIED, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, String.valueOf(error), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------- the parts

    private View backArrow() {
        TextView arrow = new TextView(this);
        arrow.setText("←");
        arrow.setTextColor(skin.text);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        arrow.setPadding(skin.margin, dp(12), skin.margin, dp(12));
        arrow.setOnClickListener(v -> finish());
        return arrow;
    }

    private View title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(skin.margin, dp(8), skin.margin, dp(20));
        return view;
    }

    private View section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(skin.margin + dp(4), dp(16), skin.margin, dp(8));
        return view;
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(skin.margin + dp(4), dp(16), skin.margin + dp(4), dp(4));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(skin.radius);
        card.setBackground(background);
        card.setPadding(0, dp(4), 0, dp(4));
        return card;
    }

    private View wrap(View card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(skin.margin, 0, skin.margin, 0);
        box.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View line() {
        View line = new View(this);
        line.setBackgroundColor((skin.text & 0x00FFFFFF) | 0x14000000);
        return sized(line, 1);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return view;
    }

    private TextView detail(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return view;
    }

    private LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View sized(View view, int height) {
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height)));
        return view;
    }

    /**
     * The bar itself: one line of why, and the button that does it.
     *
     * Painted in the card colour with a hairline above, so it reads as resting
     * on the page rather than floating over it, and padded underneath by
     * whatever the navigation bar takes -- otherwise the button sits under the
     * gesture pill.
     */
    private View restartBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(skin.card);
        bar.addView(line());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(skin.margin, dp(12), skin.margin, dp(12) + navigationBar());

        TextView why = new TextView(this);
        why.setText(Text.RESTART_PENDING);
        why.setTextColor(skin.text);
        why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        content.addView(why, grow());

        TextView button = new TextView(this);
        button.setText(Text.RESTART);
        button.setTextColor(onAccent());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setPadding(dp(18), dp(9), dp(18), dp(9));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Accent.colour());
        pill.setCornerRadius(dp(20));
        button.setBackground(pill);
        button.setOnClickListener(v -> restartTikTok());
        content.addView(button);

        bar.addView(content);
        return bar;
    }

    /**
     * What to write on the accent: the palette holds a mint and a near-white
     * as well as the pink, and white letters on either of those are unreadable.
     */
    private int onAccent() {
        int colour = Accent.colour();
        int red = (colour >> 16) & 0xFF, green = (colour >> 8) & 0xFF, blue = colour & 0xFF;
        int brightness = (red * 299 + green * 587 + blue * 114) / 1000;
        return brightness > 150 ? 0xFF1C2C24 : 0xFFFFFFFF;
    }

    private int navigationBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) return insets.getSystemWindowInsetBottom();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int statusBar() {
        try {
            android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null && insets.getSystemWindowInsetTop() > 0) {
                return insets.getSystemWindowInsetTop();
            }
        } catch (Throwable ignored) {
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----------------------------------------------------- the small shapes

    /** A tick, drawn rather than typed: the glyph fonts have is never the one. */
    private static final class Check extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context context, int colour) {
            super(context);
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.round(22 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float unit = getWidth() / 22f;
            paint.setStrokeWidth(unit * 2.2f);
            float y = getHeight() / 2f;
            canvas.drawLine(unit * 4, y + unit, unit * 9, y + unit * 5.5f, paint);
            canvas.drawLine(unit * 9, y + unit * 5.5f, unit * 18, y - unit * 5f, paint);
        }
    }

    /** One colour of the palette, and a ring around the one in use. */
    private static final class Dot extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean chosen;

        Dot(Context context, int colour, boolean chosen) {
            super(context);
            this.chosen = chosen;
            fill.setColor(colour);
            ring.setColor(colour);
            ring.setStyle(Paint.Style.STROKE);
            setClickable(chosen ? false : true);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.round(26 * getResources().getDisplayMetrics().density);
            setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float density = getResources().getDisplayMetrics().density;
            float centreX = getWidth() / 2f, centreY = getHeight() / 2f;
            float radius = Math.min(centreX, centreY) - (chosen ? 5 * density : 0);
            canvas.drawCircle(centreX, centreY, radius, fill);
            if (chosen) {
                ring.setStrokeWidth(2 * density);
                canvas.drawCircle(centreX, centreY, radius + 3.5f * density, ring);
            }
        }
    }
}
