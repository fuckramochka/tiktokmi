package cat.narezany.tiktok;

import android.app.Activity;
import android.graphics.Color;
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
 * Экран «Настройки MargyT».
 *
 * UI собран кодом, без layout-ресурсов: так не надо добавлять ничего
 * в res/ тиктока и ловить конфликты ID при пересборке aapt.
 */
public class MargyTSettingsActivity extends Activity {

    private static final int BG        = Color.parseColor("#101010");
    private static final int BG_ROW    = Color.parseColor("#1A1A1A");
    private static final int MINT      = Color.parseColor("#8DD1B0");
    private static final int TEXT      = Color.parseColor("#FFFFFF");
    private static final int TEXT_DIM  = Color.parseColor("#9A9A9A");

    private LinearLayout mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Region.init(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(24), 0, dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header("Настройки MargyT"));
        root.addView(caption("Подмена региона"));
        root.addView(toggleRow());
        root.addView(caption("Страна"));

        mList = new LinearLayout(this);
        mList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mList);
        buildCountries();

        root.addView(footer(
                "Меняется то, что приложение читает с устройства: страна SIM, "
                        + "оператор, MCC/MNC и регион в Locale. Язык интерфейса не трогается.\n\n"
                        + "Регион аккаунта (store_region) задан на сервере при регистрации "
                        + "и отсюда не меняется. IP — отдельно, под него нужен VPN.\n\n"
                        + "После смены страны перезапусти приложение."));

        setContentView(scroll);
    }

    // ------------------------------------------------------------------ строки

    private View toggleRow() {
        LinearLayout row = rowBox();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams grow =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.addView(title("Подменять регион"));
        texts.addView(subtitle(Region.labelFor(Region.currentIso())
                + " · " + Region.carrierFor(Region.currentIso())));
        row.addView(texts, grow);

        final Switch sw = new Switch(this);
        sw.setChecked(Region.isEnabled());
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                Region.setEnabled(checked);
                buildCountries();
            }
        });
        row.addView(sw);
        return row;
    }

    private void buildCountries() {
        mList.removeAllViews();
        final String current = Region.currentIso();
        final boolean on = Region.isEnabled();

        for (final String[] c : Region.COUNTRIES) {
            final String iso = c[0];
            LinearLayout row = rowBox();
            row.setAlpha(on ? 1f : 0.4f);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(title(c[4]));
            texts.addView(subtitle(c[2] + " · " + c[1] + " · " + iso.toUpperCase()));
            row.addView(texts,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView mark = new TextView(this);
            mark.setText(iso.equals(current) ? "✓" : "");
            mark.setTextColor(MINT);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            row.addView(mark);

            if (on) {
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Region.setCountry(iso);
                        recreate();
                    }
                });
            }
            mList.addView(row);
        }
    }

    // ------------------------------------------------------------- примитивы

    private LinearLayout rowBox() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(BG_ROW);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), 0, dp(12), dp(2));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView header(String s) {
        TextView t = base(s, 26, TEXT);
        t.setPadding(dp(20), dp(8), dp(20), dp(20));
        return t;
    }

    private TextView caption(String s) {
        TextView t = base(s.toUpperCase(), 12, MINT);
        t.setPadding(dp(20), dp(20), dp(20), dp(8));
        return t;
    }

    private TextView title(String s)    { return base(s, 16, TEXT); }
    private TextView subtitle(String s) { return base(s, 12, TEXT_DIM); }

    private TextView footer(String s) {
        TextView t = base(s, 12, TEXT_DIM);
        t.setPadding(dp(20), dp(24), dp(20), 0);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private TextView base(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
