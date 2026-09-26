package mi.tiktokmi;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.profile.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * High-reliability Local Watch History for TikTok MI.
 *
 * Fixes TikTok's native history bug where recent videos are dropped or not synced
 * when swiping away or closing the app. Every video that appears or plays is
 * committed immediately to local persistent non-volatile storage.
 */
public final class WatchHistory {

    private WatchHistory() {}

    public static final String KEY_ENABLED = "watch_history_enabled";
    private static final String FILE_NAME = "tiktokmi_watch_history.json";
    private static final int MAX_ENTRIES = 1000;

    public static final class Entry {
        public String aid;
        public String desc;
        public String authorUid;
        public String authorUniqueId;
        public String authorNickname;
        public String coverUrl;
        public String playUrl;
        public String downloadUrl;
        public long timestamp;
        public int duration;
        public String partN;
        public int collectStatus;

        public String getShareUrl() {
            if (authorUniqueId != null && !authorUniqueId.isEmpty()) {
                return "https://www.tiktok.com/@" + authorUniqueId + "/video/" + aid;
            }
            return "https://www.tiktok.com/video/" + aid;
        }

        public String getAuthorDisplay() {
            if (authorUniqueId != null && !authorUniqueId.isEmpty()) {
                return "@" + authorUniqueId;
            }
            if (authorNickname != null && !authorNickname.isEmpty()) {
                return authorNickname;
            }
            return "TikTok creator";
        }
    }

    private static final Map<String, Entry> sEntries = new LinkedHashMap<String, Entry>(128, 0.75f, true);
    private static boolean sLoaded = false;
    private static volatile boolean sDirty = false;
    private static final Object LOCK = new Object();

    // ------------------------------------------------------------- Persistence

    private static void ensureLoaded() {
        if (sLoaded) return;
        synchronized (LOCK) {
            if (sLoaded) return;
            Context ctx = Margy.context();
            if (ctx == null) return;
            File file = new File(ctx.getFilesDir(), FILE_NAME);
            if (!file.exists()) {
                sLoaded = true;
                return;
            }
            try {
                byte[] raw = Net.read(file);
                if (raw != null && raw.length > 0) {
                    JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Entry e = new Entry();
                        e.aid = obj.optString("aid");
                        e.desc = obj.optString("desc");
                        e.authorUid = obj.optString("author_uid");
                        e.authorUniqueId = obj.optString("author_unique_id");
                        e.authorNickname = obj.optString("author_nickname");
                        e.coverUrl = obj.optString("cover_url");
                        e.playUrl = obj.optString("play_url");
                        e.downloadUrl = obj.optString("download_url");
                        e.timestamp = obj.optLong("timestamp");
                        e.duration = obj.optInt("duration");
                        e.partN = obj.optString("part_n");
                        e.collectStatus = obj.optInt("collect_status");
                        if (e.aid != null && !e.aid.isEmpty()) {
                            sEntries.put(e.aid, e);
                        }
                    }
                }
            } catch (Throwable error) {
                Diary.note("history load: " + error);
            }
            sLoaded = true;
        }
    }

    public static void flush() {
        if (!sDirty) return;
        synchronized (LOCK) {
            if (!sDirty) return;
            Context ctx = Margy.context();
            if (ctx == null) return;
            try {
                JSONArray arr = new JSONArray();
                // Copy entries in reverse order so latest are first
                List<Entry> list = new ArrayList<Entry>(sEntries.values());
                for (int i = list.size() - 1; i >= 0; i--) {
                    Entry e = list.get(i);
                    JSONObject obj = new JSONObject();
                    obj.put("aid", e.aid);
                    obj.put("desc", e.desc);
                    obj.put("author_uid", e.authorUid);
                    obj.put("author_unique_id", e.authorUniqueId);
                    obj.put("author_nickname", e.authorNickname);
                    obj.put("cover_url", e.coverUrl);
                    obj.put("play_url", e.playUrl);
                    obj.put("download_url", e.downloadUrl);
                    obj.put("timestamp", e.timestamp);
                    obj.put("duration", e.duration);
                    obj.put("part_n", e.partN);
                    obj.put("collect_status", e.collectStatus);
                    arr.put(obj);
                }
                byte[] bytes = arr.toString().getBytes("UTF-8");
                File file = new File(ctx.getFilesDir(), FILE_NAME);
                File tmp = new File(ctx.getFilesDir(), FILE_NAME + ".tmp");
                if (Net.save(tmp, bytes)) {
                    if (file.exists()) file.delete();
                    tmp.renameTo(file);
                    sDirty = false;
                }
            } catch (Throwable error) {
                Diary.note("history flush: " + error);
            }
        }
    }

    private static void scheduleFlush() {
        sDirty = true;
        Net.away("history-flush", new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (Throwable ignored) {}
                flush();
            }
        });
    }

    // ------------------------------------------------------------- Recording

    public static void onVideoWatched(Aweme aweme) {
        if (aweme == null) return;
        try {
            String aid = aweme.getAid();
            if (aid == null || aid.isEmpty()) return;

            ensureLoaded();

            Entry entry = new Entry();
            entry.aid = aid;
            entry.desc = aweme.getDesc();
            entry.timestamp = System.currentTimeMillis();
            entry.collectStatus = aweme.getCollectStatus();
            try {
                entry.partN = aweme.getPartN();
            } catch (Throwable ignored) {}

            User author = aweme.getAuthor();
            if (author != null) {
                try { entry.authorUid = author.getUid(); } catch (Throwable ignored) {}
                try { entry.authorUniqueId = author.getUniqueId(); } catch (Throwable ignored) {}
                try { entry.authorNickname = author.getNickname(); } catch (Throwable ignored) {}
            }

            Video video = aweme.getVideo();
            if (video != null) {
                try {
                    entry.duration = video.getDuration();
                } catch (Throwable ignored) {}
                try {
                    UrlModel cover = video.getCover();
                    if (cover != null && cover.getUrlList() != null && !cover.getUrlList().isEmpty()) {
                        entry.coverUrl = String.valueOf(cover.getUrlList().get(0));
                    }
                } catch (Throwable ignored) {}
                try {
                    UrlModel play = video.getPlayAddr();
                    if (play != null && play.getUrlList() != null && !play.getUrlList().isEmpty()) {
                        entry.playUrl = String.valueOf(play.getUrlList().get(0));
                    }
                } catch (Throwable ignored) {}
                try {
                    UrlModel dl = video.getDownloadAddr();
                    if (dl != null && dl.getUrlList() != null && !dl.getUrlList().isEmpty()) {
                        entry.downloadUrl = String.valueOf(dl.getUrlList().get(0));
                    }
                } catch (Throwable ignored) {}
            }

            synchronized (LOCK) {
                // If exists, remove and re-insert to update order (LRU)
                sEntries.remove(aid);
                if (sEntries.size() >= MAX_ENTRIES) {
                    // remove oldest
                    String firstKey = sEntries.keySet().iterator().next();
                    sEntries.remove(firstKey);
                }
                sEntries.put(aid, entry);
            }

            scheduleFlush();

            // Notify connected subsystems
            Localizer.onVideoSeen(aweme);
            Continuation.onVideoSeen(aweme);
            OfflineActions.onVideoSeen(aweme);

            // Notify ecosystem watching bridge
            Context ctx = Margy.context();
            if (ctx != null) {
                MiogramBridge.notifyWatching(ctx, entry.getShareUrl(), entry.desc, entry.getAuthorDisplay(), entry.coverUrl);
            }
        } catch (Throwable error) {
            Diary.note("history onVideoWatched: " + error);
        }
    }

    public static List<Entry> getList(String query) {
        ensureLoaded();
        List<Entry> results = new ArrayList<Entry>();
        synchronized (LOCK) {
            List<Entry> all = new ArrayList<Entry>(sEntries.values());
            String q = (query != null) ? query.toLowerCase(Locale.US).trim() : "";
            for (int i = all.size() - 1; i >= 0; i--) {
                Entry e = all.get(i);
                if (q.isEmpty()) {
                    results.add(e);
                } else {
                    boolean match = (e.desc != null && e.desc.toLowerCase(Locale.US).contains(q))
                            || (e.authorUniqueId != null && e.authorUniqueId.toLowerCase(Locale.US).contains(q))
                            || (e.authorNickname != null && e.authorNickname.toLowerCase(Locale.US).contains(q))
                            || (e.aid != null && e.aid.contains(q));
                    if (match) results.add(e);
                }
            }
        }
        return results;
    }

    public static int getCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return sEntries.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            sEntries.clear();
            sDirty = true;
        }
        flush();
    }

    // ------------------------------------------------------------- UI Dialog

    public static void showHistoryDialog(final Context context) {
        if (context == null) return;
        try {
            final Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            final Window window = dialog.getWindow();
            if (window != null) {
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.65f);
            }

            final int dp16 = dp(context, 16);
            final int dp12 = dp(context, 12);
            final int dp8 = dp(context, 8);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp16, dp16, dp16, dp16);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(skin.card);
            bg.setCornerRadius(dp(context, 16));
            root.setBackground(bg);

            // Title row
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = new TextView(context);
            title.setText("🕒 Історія переглядів (" + getCount() + ")");
            title.setTextColor(skin.text);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView clearBtn = new TextView(context);
            clearBtn.setText("Очистити");
            clearBtn.setTextColor(skin.muted());
            clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            clearBtn.setPadding(dp8, dp8, dp8, dp8);
            clearBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clear();
                    dialog.dismiss();
                    Toast.makeText(context, "Історію очищено", Toast.LENGTH_SHORT).show();
                }
            });
            header.addView(clearBtn);

            root.addView(header);

            // Search filter
            final EditText search = new EditText(context);
            search.setHint("🔍 Пошук відео чи автора...");
            search.setHintTextColor(skin.muted());
            search.setTextColor(skin.text);
            search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            search.setPadding(dp12, dp8, dp12, dp8);
            search.setSingleLine(true);
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setColor(skin.dark() ? 0x22FFFFFF : 0x11000000);
            searchBg.setCornerRadius(dp(context, 8));
            search.setBackground(searchBg);

            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            searchLp.topMargin = dp12;
            searchLp.bottomMargin = dp8;
            root.addView(search, searchLp);

            // Scroll container
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            final LinearLayout listContainer = new LinearLayout(context);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(listContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.65f));
            root.addView(scroll, scrollLp);

            final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault());

            final Runnable renderList = new Runnable() {
                @Override
                public void run() {
                    listContainer.removeAllViews();
                    String query = search.getText().toString();
                    List<Entry> items = getList(query);
                    if (items.isEmpty()) {
                        TextView empty = new TextView(context);
                        empty.setText("Немає записів у локальній історії.\nВсі переглянуті відео зберігаються тут миттєво.");
                        empty.setTextColor(skin.muted());
                        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(dp16, dp(context, 40), dp16, dp(context, 40));
                        listContainer.addView(empty);
                        return;
                    }

                    for (final Entry item : items) {
                        LinearLayout card = new LinearLayout(context);
                        card.setOrientation(LinearLayout.HORIZONTAL);
                        card.setPadding(dp8, dp8, dp8, dp8);
                        card.setGravity(Gravity.CENTER_VERTICAL);

                        GradientDrawable cardBg = new GradientDrawable();
                        cardBg.setColor(skin.dark() ? 0x11FFFFFF : 0x07000000);
                        cardBg.setCornerRadius(dp(context, 8));
                        card.setBackground(cardBg);

                        // Video cover / icon
                        final ImageView coverView = new ImageView(context);
                        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        GradientDrawable thumbBg = new GradientDrawable();
                        thumbBg.setColor(skin.dark() ? 0x33FFFFFF : 0x22000000);
                        thumbBg.setCornerRadius(dp(context, 6));
                        coverView.setBackground(thumbBg);

                        int thumbW = dp(context, 48);
                        int thumbH = dp(context, 64);
                        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(thumbW, thumbH);
                        thumbLp.rightMargin = dp12;
                        card.addView(coverView, thumbLp);

                        // Async load cover
                        if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
                            final String cUrl = item.coverUrl;
                            Net.away("history-thumb", new Runnable() {
                                @Override
                                public void run() {
                                    final byte[] b = Net.bytes(cUrl);
                                    if (b != null) {
                                        final Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (bmp != null) coverView.setImageBitmap(bmp);
                                            }
                                        });
                                    }
                                }
                            });
                        }

                        // Text details
                        LinearLayout textCol = new LinearLayout(context);
                        textCol.setOrientation(LinearLayout.VERTICAL);

                        TextView authorText = new TextView(context);
                        authorText.setText(item.getAuthorDisplay());
                        authorText.setTextColor(Accent.colour());
                        authorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        authorText.setTypeface(Typeface.DEFAULT_BOLD);
                        textCol.addView(authorText);

                        TextView descText = new TextView(context);
                        String caption = item.desc != null && !item.desc.isEmpty() ? item.desc : "Без опису";
                        if (caption.length() > 90) caption = caption.substring(0, 87) + "...";
                        descText.setText(caption);
                        descText.setTextColor(skin.text);
                        descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        textCol.addView(descText);

                        TextView timeText = new TextView(context);
                        timeText.setText(formatTime(item.timestamp, sdf));
                        timeText.setTextColor(skin.muted());
                        timeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        textCol.addView(timeText);

                        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                        // Card click: open actions
                        card.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showEntryActions(context, item, dialog);
                            }
                        });

                        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        itemLp.bottomMargin = dp8;
                        listContainer.addView(card, itemLp);
                    }
                }
            };

            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    renderList.run();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });

            renderList.run();

            dialog.setContentView(root);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(dp(context, 400),
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.95f));
                window.setAttributes(params);
            }
        } catch (Throwable error) {
            Diary.note("history dialog error: " + error);
        }
    }

    private static String formatTime(long timestamp, SimpleDateFormat sdf) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60 * 1000) return "Щойно";
        if (diff < 60 * 60 * 1000) return (diff / (60 * 1000)) + " хв тому";
        if (diff < 24 * 60 * 60 * 1000) return (diff / (60 * 60 * 1000)) + " год тому";
        return sdf.format(new Date(timestamp));
    }

    public static void showEntryActions(final Context context, final Entry item, final Dialog parentDialog) {
        if (context == null || item == null) return;
        final Skin skin = Skin.remembered(context);
        final Dialog actDialog = new Dialog(context);
        Window w = actDialog.getWindow();
        if (w != null) {
            actDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
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
        head.setText(item.getAuthorDisplay() + "\n" + (item.desc != null ? item.desc : ""));
        head.setTextColor(skin.text);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setPadding(0, 0, 0, dp(context, 12));
        root.addView(head);

        // Action 1: Open in TikTok
        addActionButton(root, context, skin, "▶ Відкрити відео", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actDialog.dismiss();
                if (parentDialog != null) parentDialog.dismiss();
                openVideo(context, item);
            }
        });

        // Action 2: Copy link
        addActionButton(root, context, skin, "🔗 Скопіювати посилання", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actDialog.dismiss();
                copyLink(context, item);
            }
        });

        // Action 3: Search Next Part / Continuation
        addActionButton(root, context, skin, "🔍 Знайти проду (Автопошук)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actDialog.dismiss();
                if (parentDialog != null) parentDialog.dismiss();
                Continuation.searchProda(context, item);
            }
        });

        // Action 4: Save to Localizer / Archive
        addActionButton(root, context, skin, "💾 Локалайзер: Заархівувати", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actDialog.dismiss();
                Localizer.archiveEntry(context, item);
            }
        });

        // Action 5: Share to Amegram Cloud
        addActionButton(root, context, skin, "☁️ Надіслати в Амэоблако (Amegram)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actDialog.dismiss();
                MiogramBridge.openTelegram(context, item.getShareUrl());
            }
        });

        actDialog.setContentView(root);
        actDialog.setCanceledOnTouchOutside(true);
        actDialog.show();
    }

    private static void addActionButton(LinearLayout root, Context ctx, Skin skin, String text, View.OnClickListener l) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(skin.text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(skin.dark() ? 0x11FFFFFF : 0x08000000);
        bg.setCornerRadius(dp(ctx, 8));
        btn.setBackground(bg);
        btn.setOnClickListener(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 8);
        root.addView(btn, lp);
    }

    public static void openVideo(Context context, Entry item) {
        if (context == null || item == null) return;
        try {
            // Try TikTok internal deep link first
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1233://aweme/detail/" + item.aid));
            intent.setPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error1) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getShareUrl()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable error2) {
                Toast.makeText(context, "Не вдалося відкрити: " + error2.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void copyLink(Context context, Entry item) {
        if (context == null || item == null) return;
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("TikTok", item.getShareUrl()));
                Toast.makeText(context, "Посилання скопійовано!", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics());
    }
}
