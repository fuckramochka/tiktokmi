package cat.narezany.margyt;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

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

    private static final int MINT = 0xFF8DD1B0;

    private Skin skin;
    private LinearLayout list;
    private Switch toggle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Margy.attach(this);
        skin = Skin.remembered(this);
        dressTheWindow();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(skin.page);
        scroll.setFillViewport(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(backArrow());
        column.addView(title("MargyT"));

        column.addView(section(Text.REGION));
        LinearLayout head = card();
        head.addView(switchRow());
        column.addView(wrap(head));

        column.addView(section(Text.COUNTRY));
        list = card();
        column.addView(wrap(list));
        fillCountries();

        column.addView(caption(Text.ABOUT));
        column.addView(diary());

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (toggle != null) toggle.setChecked(Margy.isEnabled());
        fillCountries();
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

    // ------------------------------------------------------------ the parts

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
        view.setPadding(skin.margin + dp(4), dp(12), skin.margin, dp(8));
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

    /** A card, with the margin TikTok's own cards keep from the edge. */
    private View wrap(View card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(skin.margin, 0, skin.margin, 0);
        box.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        return row;
    }

    private View switchRow() {
        LinearLayout row = row();

        TextView label = new TextView(this);
        label.setText(Text.CHANGE_REGION);
        label.setTextColor(skin.text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        toggle = new Switch(this);
        toggle.setChecked(Margy.isEnabled());
        try {
            toggle.setThumbTintList(ColorStateList.valueOf(MINT));
            toggle.setTrackTintList(ColorStateList.valueOf(skin.muted()));
        } catch (Throwable ignored) {
        }
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Margy.setEnabled(checked);
            fillCountries();
        });
        row.addView(toggle);

        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return row;
    }

    private void fillCountries() {
        if (list == null) return;
        list.removeAllViews();
        boolean on = Margy.isEnabled();
        String current = Margy.iso();
        for (String[] country : Margy.COUNTRIES) {
            list.addView(countryRow(country, country[Margy.ISO].equals(current), on));
        }
    }

    private View countryRow(final String[] country, boolean selected, boolean enabled) {
        LinearLayout row = row();
        row.setAlpha(enabled ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(country[Margy.LABEL]);
        name.setTextColor(skin.text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.addView(name);

        TextView detail = new TextView(this);
        detail.setText(country[Margy.CARRIER] + "  ·  " + country[Margy.MCCMNC]
                + "  ·  " + country[Margy.ISO].toUpperCase(Locale.US));
        detail.setTextColor(skin.muted());
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.addView(detail);

        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tick = new TextView(this);
        tick.setText(selected ? "✓" : "");
        tick.setTextColor(MINT);
        tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        row.addView(tick);

        if (enabled) {
            row.setOnClickListener(v -> {
                Margy.setIso(country[Margy.ISO]);
                fillCountries();
            });
        }
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
        return row;
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(skin.margin + dp(4), dp(16), skin.margin + dp(4), dp(8));
        return view;
    }

    /**
     * What the mod saw on its way here.
     *
     * Nobody is going to run logcat against a modded TikTok, so the handful of
     * things worth knowing when something does not turn up are shown here.
     */
    private View diary() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(skin.margin + dp(4), dp(8), skin.margin + dp(4), dp(8));

        TextView heading = new TextView(this);
        heading.setText(Text.DIARY);
        heading.setTextColor(skin.muted());
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heading.setOnClickListener(v -> {
            Diary.clear();
            recreate();
        });
        box.addView(heading);

        for (String line : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText("· " + line);
            view.setTextColor(skin.muted());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        return box;
    }

    // ------------------------------------------------------------ the small

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
}
