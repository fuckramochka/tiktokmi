package cat.narezany.margyt;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * MargyT's own screen, on the launcher next to TikTok itself.
 *
 * Every view is built here in code and coloured here in code. Adding layouts or
 * styles would mean adding resources, and adding resources means rewriting a
 * 25 MB resource table -- the one thing this build refuses to do. It also means
 * the screen looks the same whatever theme the app is carrying that week.
 */
public class SettingsActivity extends Activity {

    private static final int MINT = 0xFF8DD1B0;
    private static final int INK = 0xFF1C2C24;
    private static final int PAPER = 0xFFFFFFFF;
    private static final int MUTED = 0xFF6B7B72;
    private static final int LINE = 0x14000000;

    private LinearLayout list;
    private Switch toggle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Margy.attach(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PAPER);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(header());
        column.addView(switchRow());
        column.addView(caption("The country the app is told it is in. The interface "
                + "language is left alone, and your IP address is a separate matter -- "
                + "that one wants a VPN."));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        column.addView(list);
        fillCountries();

        column.addView(caption("Region reaches the feed and most of what is gated by "
                + "country. It does not reach the store region, which the server fixes "
                + "when the account is created."));

        column.addView(diary());

        setContentView(scroll);
    }

    // --------------------------------------------------------------- pieces

    private View header() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(MINT);
        box.setPadding(dp(20), dp(28), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("MargyT");
        title.setTextColor(INK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("a mod of the app you are already holding");
        subtitle.setTextColor(INK);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        box.addView(subtitle);
        return box;
    }

    private View switchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView label = new TextView(this);
        label.setText("Change the region");
        label.setTextColor(INK);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        toggle = new Switch(this);
        toggle.setChecked(Margy.isEnabled());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Margy.setEnabled(checked);
            fillCountries();
        });
        row.addView(toggle);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(row);
        box.addView(divider());
        return box;
    }

    private void fillCountries() {
        list.removeAllViews();
        boolean on = Margy.isEnabled();
        String current = Margy.iso();
        for (String[] country : Margy.COUNTRIES) {
            list.addView(countryRow(country, country[Margy.ISO].equals(current), on));
        }
    }

    private View countryRow(final String[] country, boolean selected, boolean enabled) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.4f);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(country[Margy.LABEL]);
        name.setTextColor(INK);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.addView(name);

        TextView detail = new TextView(this);
        detail.setText(country[Margy.CARRIER] + "  ·  " + country[Margy.MCCMNC]
                + "  ·  " + country[Margy.ISO].toUpperCase());
        detail.setTextColor(MUTED);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.addView(detail);

        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tick = new TextView(this);
        tick.setText(selected ? "✓" : "");
        tick.setTextColor(MINT);
        tick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        row.addView(tick);

        if (enabled) {
            row.setOnClickListener(v -> {
                Margy.setIso(country[Margy.ISO]);
                fillCountries();
            });
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(row);
        box.addView(divider());
        return box;
    }

    /**
     * What the mod saw on its way here.
     *
     * Nobody is going to run logcat against a modded TikTok, so the handful of
     * things worth knowing when the row does not turn up are shown here.
     */
    private View diary() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(28));

        TextView heading = new TextView(this);
        heading.setText("What the mod saw  (tap to clear)");
        heading.setTextColor(MUTED);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heading.setOnClickListener(v -> {
            Diary.clear();
            recreate();
        });
        box.addView(heading);

        for (String line : Diary.lines()) {
            TextView view = new TextView(this);
            view.setText("· " + line);
            view.setTextColor(MUTED);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            view.setPadding(0, dp(2), 0, 0);
            box.addView(view);
        }
        return box;
    }

    private View caption(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextColor(MUTED);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(20), dp(16), dp(20), dp(16));
        return view;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(LINE);
        line.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return line;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (toggle != null) toggle.setChecked(Margy.isEnabled());
        fillCountries();
    }
}
