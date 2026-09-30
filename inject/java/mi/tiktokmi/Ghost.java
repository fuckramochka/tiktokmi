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

    // ------------------------------------------------- Message Interception

    /**
     * Intercepts message text on its way to a TextView in Direct Messages.
     * Preserves deleted messages and renders them with a ghost marker.
     */
    public static CharSequence filterMessage(TextView view, CharSequence text) {
        if (!isDeletedMessagesEnabled() || text == null || view == null) return text;

        String raw = text.toString().trim();
        if (raw.isEmpty()) return text;

        if (isRecallNotice(raw)) {
            Object tag = view.getTag(GHOST_TAG);
            String saved = (tag instanceof String) ? (String) tag : null;
            if (saved != null && !isRecallNotice(saved)) {
                ChatSearch.indexMessage("dm", "Співрозмовник", saved, true, System.currentTimeMillis());
                SpannableStringBuilder builder = new SpannableStringBuilder();
                builder.append("👻 [Видалено]: ");
                int start = builder.length();
                builder.append(saved);
                // Subtle highlight for deleted message
                builder.setSpan(new ForegroundColorSpan(0xFFFF4D4D), 0, start,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                return builder;
            }
        } else {
            // Normal message: remember it in case it gets recalled in this view
            if (raw.length() > 0 && raw.length() < 1000) {
                view.setTag(GHOST_TAG, raw);
                ChatSearch.indexMessage("dm", "Співрозмовник", raw, false, System.currentTimeMillis());
            }
        }

        return text;
    }

    /**
     * Checks whether the string matches a recall or deletion notice.
     */
    private static boolean isRecallNotice(String text) {
        if (text == null || text.length() > 65) return false;
        String lower = text.toLowerCase(java.util.Locale.US);
        return lower.contains("recalled")
                || lower.contains("message recalled")
                || lower.contains("this message was deleted")
                || lower.contains("повідомлення видалено")
                || lower.contains("повідомлення відкликано")
                || lower.contains("сообщение удалено")
                || lower.contains("сообщение отозвано");
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
