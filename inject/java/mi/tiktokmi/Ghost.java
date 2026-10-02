package mi.tiktokmi;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghost Mode (Режим привида) for TikTok MI.
 *
 * 1. Stealth Profile Visits:
 *    Visit any user profile without leaving a trace in their profile view history
 *    or triggering a visitor notification.
 *
 * 2. Deleted Messages in Direct Messages:
 *    When a conversation partner deletes or recalls a message, TikTok replaces it
 *    with a recall notice. Ghost Mode preserves the original message text in memory
 *    and displays it with a ghost indicator: "👻 [Видалено]: <текст>".
 *
 * 3. Stealth Chat (No Read Receipts / No Typing Status):
 *    Read incoming messages without triggering "Seen" / "Read" receipts or "Typing..."
 *    indicators.
 */
public final class Ghost {

    private Ghost() {}

    public static final String KEY_ENABLED = "ghost_enabled";
    public static final String KEY_PROFILE = "ghost_stealth_profile";
    public static final String KEY_DELETED = "ghost_deleted_msgs";
    public static final String KEY_CHAT = "ghost_stealth_chat";

    /** Cache of recent message contents: view identity / text hash -> original text */
    private static final Map<Integer, String> messageCache =
            new LinkedHashMap<Integer, String>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                    return size() > 250;
                }
            };

    // ----------------------------------------------------------- Switches

    public static boolean isEnabled() {
        return bool(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean on) {
        put(KEY_ENABLED, on);
    }

    public static boolean isStealthProfileEnabled() {
        return isEnabled() && bool(KEY_PROFILE, true);
    }

    public static void setStealthProfileEnabled(boolean on) {
        put(KEY_PROFILE, on);
    }

    public static boolean isDeletedMessagesEnabled() {
        return isEnabled() && bool(KEY_DELETED, true);
    }

    public static void setDeletedMessagesEnabled(boolean on) {
        put(KEY_DELETED, on);
    }

    public static boolean isStealthChatEnabled() {
        return isEnabled() && bool(KEY_CHAT, true);
    }

    public static void setStealthChatEnabled(boolean on) {
        put(KEY_CHAT, on);
    }

    // ---------------------------------------------------- AB Flags Override

    /**
     * Flags overridden to suppress profile visit tracking and DM read/typing signals.
     */
    public static Boolean overrideFlag(String key) {
        if (!isEnabled()) return null;

        if (isStealthProfileEnabled()) {
            if ("enable_profile_visitor".equals(key)
                    || "profile_viewer_enable".equals(key)
                    || "profile_view_history_enable".equals(key)
                    || "profile_visitor_post_unread_enable".equals(key)
                    || "profile_viewer_authorization".equals(key)
                    || "authorized_profile_views".equals(key)
                    || "profile_view_service_enable".equals(key)
                    || "profile_visitor_service_enable".equals(key)
                    || "profile_visitor_floating_ball".equals(key)) {
                return Boolean.FALSE;
            }
        }

        if (isStealthChatEnabled()) {
            if ("im_read_receipt_enable".equals(key)
                    || "im_typing_status_enable".equals(key)) {
                return Boolean.FALSE;
            }
        }

        return null;
    }

    private static final int GHOST_TAG = 0x54544748; // "TTGH"
    private static final int GHOST_LISTEN_TAG = 0x5454474C; // "TTGL"
    private static final long GHOST_TTL = 10 * 60 * 1000L;

    // ------------------------------------------------- Message Interception

    /**
     * Intercepts message text on its way to a TextView in Direct Messages.
     * Preserves deleted messages and renders them with a ghost marker.
     */
    public static CharSequence filterMessage(TextView view, CharSequence text) {
        if (!isDeletedMessagesEnabled() || text == null || view == null) return text;

        String raw = text.toString().trim();
        if (raw.isEmpty()) return text;
        // Never feed badge/mention/system views into the DM index: only short
        // chat-like texts are eligible, long captions stay out.
        if (raw.length() > 1000) return text;

        if (isRecallNotice(raw)) {
            Saved saved = savedOf(view);
            if (saved != null && !isRecallNotice(saved.text)
                    && System.currentTimeMillis() - saved.at < GHOST_TTL) {
                ChatSearch.indexMessage("dm", "Співрозмовник", saved.text, true, System.currentTimeMillis());
                SpannableStringBuilder builder = new SpannableStringBuilder();
                builder.append("👻 [Видалено]: ");
                int start = builder.length();
                builder.append(saved.text);
                // Subtle highlight for deleted message
                builder.setSpan(new ForegroundColorSpan(0xFFFF4D4D), 0, start,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                return builder;
            }
        } else {
            // Normal message: remember it in case it gets recalled in this view
            remember(view, raw);
            if (looksLikeChat(raw)) {
                ChatSearch.indexMessage("dm", "Співрозмовник", raw, false, System.currentTimeMillis());
            }
        }

        return text;
    }

    private static final class Saved {
        final String text;
        final long at;

        Saved(String text, long at) {
            this.text = text;
            this.at = at;
        }
    }

    private static Saved savedOf(TextView view) {
        try {
            Object tag = view.getTag(GHOST_TAG);
            if (tag instanceof Saved) return (Saved) tag;
            // legacy plain-String tag from older builds
            if (tag instanceof String) {
                return new Saved((String) tag, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void remember(final TextView view, String raw) {
        try {
            view.setTag(GHOST_TAG, new Saved(raw, System.currentTimeMillis()));
            synchronized (messageCache) {
                messageCache.put(System.identityHashCode(view), raw);
            }
            // RecyclerView reuses views across chats: drop the saved text when
            // the view leaves the window so a recall in another chat cannot
            // pick up someone else's message.
            if (view.getTag(GHOST_LISTEN_TAG) == null) {
                view.setTag(GHOST_LISTEN_TAG, Boolean.TRUE);
                view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        try {
                            v.setTag(GHOST_TAG, null);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** Cheap guard so nicknames/buttons/captions do not flood the DM index. */
    private static boolean looksLikeChat(String raw) {
        if (raw.length() > 500) return false;
        if (raw.contains("\n\n\n")) return false;
        return true;
    }

    /**
     * Checks whether the string matches a recall or deletion notice.
     */
    private static boolean isRecallNotice(String text) {
        if (text == null || text.length() > 140) return false;
        String lower = text.toLowerCase(java.util.Locale.US);
        return lower.contains("recalled")
                || lower.contains("unsent")
                || lower.contains("message deleted")
                || lower.contains("this message was deleted")
                || lower.contains("message no longer available")
                || lower.contains("deleted this message")
                || lower.contains("повідомлення видалено")
                || lower.contains("повідомлення відкликано")
                || lower.contains("повідомлення видалене")
                || lower.contains("сообщение удалено")
                || lower.contains("сообщение отозвано")
                || lower.contains("видалив повідомлення")
                || lower.contains("видалила повідомлення");
    }

    // -------------------------------------------------------------- Storage

    private static boolean bool(String key, boolean fallback) {
        try {
            Context context = Margy.context();
            if (context == null) return fallback;
            SharedPreferences prefs = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
            return prefs.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void put(String key, boolean value) {
        try {
            Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(key, value).apply();
            }
        } catch (Throwable ignored) {
        }
    }
}
