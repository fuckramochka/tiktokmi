package mi.tiktokmi;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TikTok Direct Messages Search (Поиск по сообщениям в ТТ).
 *
 * Provides full-text search across all viewed and intercepted Direct Messages in TikTok,
 * including recalled/deleted messages preserved by Ghost Mode.
 */
public final class ChatSearch {

    private ChatSearch() {}

    private static final String FILE_NAME = "tiktokmi_chat_search.json";
    private static final int MAX_SEARCH_MESSAGES = 2000;

    public static final class MessageItem {
        public String conversationId;
        public String sender;
        public String text;
        public boolean isDeleted;
        public long timestamp;
    }

    private static final List<MessageItem> sMessages = new ArrayList<MessageItem>();
    private static boolean sLoaded = false;
    private static volatile boolean sDirty = false;
    private static final Object LOCK = new Object();

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
                        MessageItem m = new MessageItem();
                        m.conversationId = obj.optString("conv_id");
                        m.sender = obj.optString("sender");
                        m.text = obj.optString("text");
                        m.isDeleted = obj.optBoolean("is_del");
                        m.timestamp = obj.optLong("time");
                        if (m.text != null && !m.text.isEmpty()) {
                            sMessages.add(m);
                        }
                    }
                }
            } catch (Throwable error) {
                Diary.note("chat search load: " + error);
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
                for (MessageItem m : sMessages) {
                    JSONObject obj = new JSONObject();
                    obj.put("conv_id", m.conversationId);
                    obj.put("sender", m.sender);
                    obj.put("text", m.text);
                    obj.put("is_del", m.isDeleted);
                    obj.put("time", m.timestamp);
                    arr.put(obj);
                }
                File file = new File(ctx.getFilesDir(), FILE_NAME);
                File tmp = new File(ctx.getFilesDir(), FILE_NAME + ".tmp");
                if (Net.save(tmp, arr.toString().getBytes("UTF-8"))) {
                    if (file.exists()) file.delete();
                    tmp.renameTo(file);
                    sDirty = false;
                }
            } catch (Throwable error) {
                Diary.note("chat search flush: " + error);
            }
        }
    }

    public static void indexMessage(String convId, String sender, String text, boolean isDeleted, long timestamp) {
        if (text == null || text.trim().isEmpty()) return;
        ensureLoaded();

        final String cleanText = text.trim();
        final long time = timestamp > 0 ? timestamp : System.currentTimeMillis();

        synchronized (LOCK) {
            // Avoid recent duplicate in same conversation
            int size = sMessages.size();
            if (size > 0) {
                MessageItem last = sMessages.get(size - 1);
                if (cleanText.equals(last.text) && Math.abs(time - last.timestamp) < 2000) {
                    return;
                }
            }

            MessageItem m = new MessageItem();
            m.conversationId = convId != null ? convId : "dm";
            m.sender = sender != null ? sender : "Співрозмовник";
            m.text = cleanText;
            m.isDeleted = isDeleted;
            m.timestamp = time;

            if (sMessages.size() >= MAX_SEARCH_MESSAGES) {
                sMessages.remove(0);
            }
            sMessages.add(m);
            sDirty = true;
        }

        Net.away("chat-search-flush", new Runnable() {
            @Override
            public void run() {
                flush();
            }
        });

        // Also notify Localizer
        Localizer.onChatMessage(convId, sender, text, isDeleted, time);
    }

    public static List<MessageItem> search(String query) {
        ensureLoaded();
        List<MessageItem> results = new ArrayList<MessageItem>();
        synchronized (LOCK) {
            String q = query != null ? query.toLowerCase(Locale.US).trim() : "";
            for (int i = sMessages.size() - 1; i >= 0; i--) {
                MessageItem m = sMessages.get(i);
                if (q.isEmpty() || m.text.toLowerCase(Locale.US).contains(q)
                        || (m.sender != null && m.sender.toLowerCase(Locale.US).contains(q))) {
                    results.add(m);
                }
            }
        }
        return results;
    }

    public static int getCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return sMessages.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            sMessages.clear();
            sDirty = true;
        }
        flush();
    }

    // ------------------------------------------------------------- UI Dialog

    public static void showChatSearchDialog(final Context context) {
        if (context == null) return;
        try {
            final Skin skin = Skin.remembered(context);
            final Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
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
            title.setText("💬 Пошук по повідомленнях (" + getCount() + ")");
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
                    Toast.makeText(context, "Індекс повідомлень очищено", Toast.LENGTH_SHORT).show();
                }
            });
            header.addView(clearBtn);

            root.addView(header);

            // Search input
            final EditText search = new EditText(context);
            search.setHint("🔍 Введіть слово чи фразу...");
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

            final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault());

            final Runnable renderList = new Runnable() {
                @Override
                public void run() {
                    listContainer.removeAllViews();
                    final String query = search.getText().toString().trim();
                    List<MessageItem> items = search(query);

                    if (items.isEmpty()) {
                        TextView empty = new TextView(context);
                        empty.setText(query.isEmpty()
                                ? "Немає збережених повідомлень.\nПовідомлення індексуються під час читання чатів."
                                : "Нічого не знайдено за запитом «" + query + "»");
                        empty.setTextColor(skin.muted());
                        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(dp16, dp(context, 40), dp16, dp(context, 40));
                        listContainer.addView(empty);
                        return;
                    }

                    for (final MessageItem m : items) {
                        LinearLayout card = new LinearLayout(context);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(dp12, dp12, dp12, dp12);

                        GradientDrawable cardBg = new GradientDrawable();
                        cardBg.setColor(skin.dark() ? 0x12FFFFFF : 0x08000000);
                        cardBg.setCornerRadius(dp(context, 8));
                        card.setBackground(cardBg);

                        // Header: sender + time
                        LinearLayout rowTop = new LinearLayout(context);
                        rowTop.setOrientation(LinearLayout.HORIZONTAL);

                        TextView senderView = new TextView(context);
                        senderView.setText(m.sender);
                        senderView.setTextColor(Accent.colour());
                        senderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                        senderView.setTypeface(Typeface.DEFAULT_BOLD);
                        rowTop.addView(senderView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                        if (m.isDeleted) {
                            TextView delTag = new TextView(context);
                            delTag.setText("👻 Видалено");
                            delTag.setTextColor(0xFFFF4D4D);
                            delTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                            delTag.setPadding(0, 0, dp8, 0);
                            rowTop.addView(delTag);
                        }

                        TextView timeView = new TextView(context);
                        timeView.setText(sdf.format(new Date(m.timestamp)));
                        timeView.setTextColor(skin.muted());
                        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                        rowTop.addView(timeView);

                        card.addView(rowTop);

                        // Body with highlight
                        TextView bodyView = new TextView(context);
                        bodyView.setTextColor(skin.text);
                        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        bodyView.setPadding(0, dp8, 0, 0);

                        if (!query.isEmpty() && m.text.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US))) {
                            SpannableStringBuilder span = new SpannableStringBuilder(m.text);
                            String lower = m.text.toLowerCase(Locale.US);
                            String qLower = query.toLowerCase(Locale.US);
                            int start = 0;
                            while ((start = lower.indexOf(qLower, start)) >= 0) {
                                span.setSpan(new ForegroundColorSpan(Accent.colour()),
                                        start, start + qLower.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                                start += qLower.length();
                            }
                            bodyView.setText(span);
                        } else {
                            bodyView.setText(m.text);
                        }
                        card.addView(bodyView);

                        // Click to copy or share
                        card.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) {
                                    cm.setPrimaryClip(ClipData.newPlainText("TikTok Message", m.text));
                                    Toast.makeText(context, "Текст повідомлення скопійовано", Toast.LENGTH_SHORT).show();
                                }
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
            Diary.note("chat search dialog error: " + error);
        }
    }

    private static int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics());
    }
}
