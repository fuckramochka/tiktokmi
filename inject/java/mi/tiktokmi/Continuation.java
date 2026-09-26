package mi.tiktokmi;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.profile.model.User;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-Search Continuation / Next Part (Автопоиск проды).
 *
 * Automatically detects whether a video is part of a series (e.g. "часть 1", "part 1", "1/2"),
 * determines the next part number, and provides 1-tap native search deep links
 * so users never have to manually search for "проду".
 */
public final class Continuation {

    private Continuation() {}

    public static final String KEY_AUTO_PRODA = "auto_proda_enabled";

    public static boolean isEnabled() {
        try {
            Context ctx = Margy.context();
            if (ctx == null) return true;
            return ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_AUTO_PRODA, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            Context ctx = Margy.context();
            if (ctx != null) {
                ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_AUTO_PRODA, enabled).apply();
            }
        } catch (Throwable ignored) {}
    }

    public static final class ProdaInfo {
        public boolean hasContinuation;
        public int currentPart;
        public int nextPart;
        public String authorHandle;
        public String authorName;
        public String titleClean;
        public List<String> queries = new ArrayList<String>();
    }

    private static final Pattern P_PART = Pattern.compile(
            "(?i)(?:^|\\s|#)(?:часть|ч\\.?|серия|сер\\.?|part|pt\\.?)\\s*(\\d+)");
    private static final Pattern P_FRACTION = Pattern.compile(
            "(?i)(?:^|\\s)(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern P_GENERIC = Pattern.compile(
            "(?i)(?:прода|продолжение|след(?:ующая)?\\s*часть)");

    // Remember last detected continuation for HUD / UI actions
    private static volatile ProdaInfo sLastDetected;

    public static void onVideoSeen(Aweme aweme) {
        if (aweme == null || !isEnabled()) return;
        try {
            String desc = aweme.getDesc();
            String authorHandle = "";
            String authorName = "";
            User author = aweme.getAuthor();
            if (author != null) {
                try { authorHandle = author.getUniqueId(); } catch (Throwable ignored) {}
                try { authorName = author.getNickname(); } catch (Throwable ignored) {}
            }
            String partN = "";
            try { partN = aweme.getPartN(); } catch (Throwable ignored) {}

            ProdaInfo info = analyze(desc, authorHandle, authorName, partN);
            if (info.hasContinuation) {
                sLastDetected = info;
            }
        } catch (Throwable error) {
            Diary.note("proda onVideoSeen: " + error);
        }
    }

    public static ProdaInfo getLastDetected() {
        return sLastDetected;
    }

    public static ProdaInfo analyze(String desc, String authorHandle, String authorName, String partN) {
        ProdaInfo info = new ProdaInfo();
        info.authorHandle = (authorHandle != null && !authorHandle.isEmpty()) ? authorHandle : "";
        info.authorName = (authorName != null && !authorName.isEmpty()) ? authorName : "";

        String text = (desc != null) ? desc : "";
        info.titleClean = sanitizeTitle(text);

        int current = -1;
        int next = -1;

        // 1. Check TikTok partN metadata
        if (partN != null && !partN.isEmpty()) {
            try {
                int p = Integer.parseInt(partN.trim());
                if (p > 0) {
                    current = p;
                    next = p + 1;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Check regex in description
        if (current <= 0) {
            Matcher m = P_PART.matcher(text);
            if (m.find()) {
                try {
                    current = Integer.parseInt(m.group(1));
                    next = current + 1;
                } catch (Throwable ignored) {}
            }
        }

        // 3. Check fraction (e.g. 1/3)
        if (current <= 0) {
            Matcher m = P_FRACTION.matcher(text);
            if (m.find()) {
                try {
                    current = Integer.parseInt(m.group(1));
                    int total = Integer.parseInt(m.group(2));
                    if (current < total) {
                        next = current + 1;
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 4. Check generic keywords (прода / продолжение)
        boolean genericProda = false;
        if (current <= 0) {
            Matcher m = P_GENERIC.matcher(text);
            if (m.find()) {
                genericProda = true;
                current = 1;
                next = 2;
            }
        }

        if (next > 0 || genericProda) {
            info.hasContinuation = true;
            info.currentPart = Math.max(1, current);
            info.nextPart = Math.max(2, next);

            String authorPrefix = !info.authorHandle.isEmpty() ? ("@" + info.authorHandle) : info.authorName;

            if (authorPrefix.length() > 0) {
                info.queries.add(authorPrefix + " часть " + info.nextPart);
                info.queries.add(authorPrefix + " part " + info.nextPart);
                info.queries.add(authorPrefix + " прода");
                info.queries.add(authorPrefix + " продолжение");
            }

            if (!info.titleClean.isEmpty()) {
                info.queries.add(info.titleClean + " часть " + info.nextPart);
            }
        } else {
            // Even if not explicitly numbered, add author search for continuation
            String authorPrefix = !info.authorHandle.isEmpty() ? ("@" + info.authorHandle) : info.authorName;
            if (authorPrefix.length() > 0) {
                info.queries.add(authorPrefix + " часть 2");
                info.queries.add(authorPrefix + " прода");
            }
        }

        return info;
    }

    private static String sanitizeTitle(String raw) {
        if (raw == null) return "";
        // Remove hashtags and emojis/extra spaces
        String s = raw.replaceAll("#\\S+", " ").replaceAll("@\\S+", " ").trim();
        if (s.length() > 30) s = s.substring(0, 30).trim();
        return s;
    }

    // ----------------------------------------------------------- Actions

    public static void searchProda(Context context, Aweme aweme) {
        if (context == null || aweme == null) return;
        String desc = aweme.getDesc();
        String handle = "";
        String name = "";
        User a = aweme.getAuthor();
        if (a != null) {
            try { handle = a.getUniqueId(); } catch (Throwable ignored) {}
            try { name = a.getNickname(); } catch (Throwable ignored) {}
        }
        String partN = "";
        try { partN = aweme.getPartN(); } catch (Throwable ignored) {}

        ProdaInfo info = analyze(desc, handle, name, partN);
        showProdaPicker(context, info);
    }

    public static void searchProda(Context context, WatchHistory.Entry entry) {
        if (context == null || entry == null) return;
        ProdaInfo info = analyze(entry.desc, entry.authorUniqueId, entry.authorNickname, entry.partN);
        showProdaPicker(context, info);
    }

    public static void showProdaPicker(final Context context, final ProdaInfo info) {
        if (context == null || info == null || info.queries.isEmpty()) {
            Toast.makeText(context, "Не вдалося визначити автора чи тему", Toast.LENGTH_SHORT).show();
            return;
        }

        final Skin skin = Skin.remembered(context);
        final Dialog dialog = new Dialog(context);
        Window w = dialog.getWindow();
        if (w != null) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.6f);
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(skin.card);
        bg.setCornerRadius(dp(context, 16));
        root.setBackground(bg);

        TextView head = new TextView(context);
        String titleStr = info.hasContinuation
                ? "🔍 Автопошук проди: Частина " + info.nextPart
                : "🔍 Пошук продовження у автора";
        head.setText(titleStr);
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(0, 0, 0, dp(context, 12));
        root.addView(head);

        TextView sub = new TextView(context);
        sub.setText("Оберіть запит для швидкого пошуку в TikTok:");
        sub.setTextColor(skin.muted());
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setPadding(0, 0, 0, dp(context, 10));
        root.addView(sub);

        for (final String q : info.queries) {
            TextView btn = new TextView(context);
            btn.setText("🔎 " + q);
            btn.setTextColor(skin.text);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));

            GradientDrawable bgb = new GradientDrawable();
            bgb.setColor(skin.dark() ? 0x14FFFFFF : 0x08000000);
            bgb.setCornerRadius(dp(context, 8));
            btn.setBackground(bgb);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    launchSearch(context, q);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(context, 8);
            root.addView(btn, lp);
        }

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        if (w != null) {
            WindowManager.LayoutParams params = w.getAttributes();
            params.width = Math.min(dp(context, 380),
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.95f));
            w.setAttributes(params);
        }
    }

    public static void launchSearch(Context context, String query) {
        if (context == null || query == null || query.trim().isEmpty()) return;
        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8");
            Uri uri = Uri.parse("snssdk1233://search?keyword=" + encoded + "&keyword_type=general");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable e1) {
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                Uri uri = Uri.parse("snssdk1180://search?keyword=" + encoded);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setPackage(context.getPackageName());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Throwable e2) {
                Toast.makeText(context, "Помилка відкриття пошуку: " + e2.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics());
    }
}
