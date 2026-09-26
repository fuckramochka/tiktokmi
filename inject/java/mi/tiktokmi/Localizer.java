package mi.tiktokmi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.ss.android.ugc.aweme.base.model.UrlModel;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.Video;
import com.ss.android.ugc.aweme.profile.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TikTok MI Localizer / Total Account Vault.
 *
 * Protects users against account bans, video deletions, and censorship:
 * 1. Automatic & on-demand archiving of Favorites (Избранное) with clean MP4,
 *    updating metadata JSON (likes, stats, hashtags, sound) and thumbnails.
 * 2. Automatic archiving of Direct Messages (Чаты), including recalled/deleted messages.
 * 3. 1-Tap Cloud Sync to "Амэоблако" (Amegram / Telegram Saved Messages & Cloud).
 */
public final class Localizer {

    private Localizer() {}

    public static final String KEY_AUTO_FAVORITES = "localizer_auto_favorites";
    public static final String KEY_AUTO_CHATS = "localizer_auto_chats";

    private static final Set<String> sDownloadingAids = Collections.synchronizedSet(new HashSet<String>());

    public static boolean isAutoFavoritesEnabled() {
        try {
            Context ctx = Margy.context();
            if (ctx == null) return true;
            return ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_AUTO_FAVORITES, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setAutoFavoritesEnabled(boolean enabled) {
        try {
            Context ctx = Margy.context();
            if (ctx != null) {
                ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_AUTO_FAVORITES, enabled).apply();
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isAutoChatsEnabled() {
        try {
            Context ctx = Margy.context();
            if (ctx == null) return true;
            return ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_AUTO_CHATS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void setAutoChatsEnabled(boolean enabled) {
        try {
            Context ctx = Margy.context();
            if (ctx != null) {
                ctx.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_AUTO_CHATS, enabled).apply();
            }
        } catch (Throwable ignored) {}
    }

    // ----------------------------------------------------------- Directory

    public static File getArchiveDir(Context context) {
        File dir = null;
        try {
            File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            if (movies != null) {
                dir = new File(movies, "TikTokMI/Localizer");
            }
        } catch (Throwable ignored) {}

        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            if (context != null) {
                dir = new File(context.getExternalFilesDir(null), "Localizer");
            } else {
                dir = new File("/sdcard/Movies/TikTokMI/Localizer");
            }
        }
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getFavoritesDir(Context context) {
        File f = new File(getArchiveDir(context), "Favorites");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File getChatsDir(Context context) {
        File f = new File(getArchiveDir(context), "Chats");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ------------------------------------------------- Video & Favorites Archiving

    public static void onVideoSeen(Aweme aweme) {
        if (aweme == null) return;
        try {
            // Check if collected / favorited
            if (aweme.getCollectStatus() == 1 && isAutoFavoritesEnabled()) {
                Context ctx = Margy.context();
                if (ctx != null) {
                    archiveAweme(ctx, aweme);
                }
            }
        } catch (Throwable error) {
            Diary.note("localizer onVideoSeen: " + error);
        }
    }

    public static void archiveAweme(final Context context, final Aweme aweme) {
        if (context == null || aweme == null) return;
        final String aid = aweme.getAid();
        if (aid == null || aid.isEmpty()) return;

        if (sDownloadingAids.contains(aid)) return;

        Net.away("localizer-archive-" + aid, new Runnable() {
            @Override
            public void run() {
                try {
                    sDownloadingAids.add(aid);
                    String authorHandle = "user";
                    String authorName = "";
                    User author = aweme.getAuthor();
                    if (author != null) {
                        try { authorHandle = author.getUniqueId(); } catch (Throwable ignored) {}
                        try { authorName = author.getNickname(); } catch (Throwable ignored) {}
                    }
                    if (authorHandle == null || authorHandle.isEmpty()) authorHandle = "user";

                    String safeAuthor = authorHandle.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    File targetFolder = new File(getFavoritesDir(context), safeAuthor + "_" + aid);
                    if (!targetFolder.exists()) targetFolder.mkdirs();

                    File metaFile = new File(targetFolder, "metadata.json");
                    File videoFile = new File(targetFolder, "video.mp4");
                    File coverFile = new File(targetFolder, "cover.jpg");

                    // 1. Build and write / update metadata
                    JSONObject meta = new JSONObject();
                    if (metaFile.exists()) {
                        try {
                            byte[] old = Net.read(metaFile);
                            if (old != null) meta = new JSONObject(new String(old, "UTF-8"));
                        } catch (Throwable ignored) {}
                    }

                    meta.put("aid", aid);
                    meta.put("desc", aweme.getDesc());
                    meta.put("author_unique_id", authorHandle);
                    meta.put("author_nickname", authorName);
                    meta.put("create_time", aweme.getCreateTime());
                    meta.put("is_collected", aweme.getCollectStatus() == 1);
                    meta.put("share_url", aweme.getShareUrl());
                    meta.put("updated_at", System.currentTimeMillis());

                    Video video = aweme.getVideo();
                    if (video != null) {
                        meta.put("duration", video.getDuration());
                    }

                    Net.save(metaFile, meta.toString(2).getBytes("UTF-8"));

                    // 2. Download thumbnail if missing
                    if (!coverFile.exists() && video != null) {
                        UrlModel cover = video.getCover();
                        if (cover != null && cover.getUrlList() != null && !cover.getUrlList().isEmpty()) {
                            byte[] b = Net.bytes(String.valueOf(cover.getUrlList().get(0)));
                            if (b != null) Net.save(coverFile, b);
                        }
                    }

                    // 3. Download clean video if missing
                    if (!videoFile.exists() && video != null) {
                        UrlModel cleanUrl = video.getDownloadNoWatermarkAddr();
                        String urlToFetch = null;
                        if (cleanUrl != null && cleanUrl.getUrlList() != null && !cleanUrl.getUrlList().isEmpty()) {
                            urlToFetch = String.valueOf(cleanUrl.getUrlList().get(0));
                        }
                        if (urlToFetch == null) {
                            UrlModel playUrl = video.getPlayAddr();
                            if (playUrl != null && playUrl.getUrlList() != null && !playUrl.getUrlList().isEmpty()) {
                                urlToFetch = String.valueOf(playUrl.getUrlList().get(0));
                            }
                        }
                        if (urlToFetch == null) {
                            UrlModel dl = video.getDownloadAddr();
                            if (dl != null && dl.getUrlList() != null && !dl.getUrlList().isEmpty()) {
                                urlToFetch = String.valueOf(dl.getUrlList().get(0));
                            }
                        }

                        if (urlToFetch != null) {
                            boolean ok = Net.download(urlToFetch, videoFile, null);
                            if (ok) {
                                Diary.note("localizer: saved clean video " + aid);
                            }
                        }
                    }
                } catch (Throwable error) {
                    Diary.note("localizer archiveAweme error: " + error);
                } finally {
                    sDownloadingAids.remove(aid);
                }
            }
        });
    }

    public static void archiveEntry(final Context context, final WatchHistory.Entry entry) {
        if (context == null || entry == null || entry.aid == null) return;
        final String aid = entry.aid;
        if (sDownloadingAids.contains(aid)) return;

        Net.away("localizer-entry-" + aid, new Runnable() {
            @Override
            public void run() {
                try {
                    sDownloadingAids.add(aid);
                    String safeAuthor = entry.authorUniqueId != null ? entry.authorUniqueId.replaceAll("[^a-zA-Z0-9_.-]", "_") : "creator";
                    File targetFolder = new File(getFavoritesDir(context), safeAuthor + "_" + aid);
                    if (!targetFolder.exists()) targetFolder.mkdirs();

                    File metaFile = new File(targetFolder, "metadata.json");
                    File videoFile = new File(targetFolder, "video.mp4");
                    File coverFile = new File(targetFolder, "cover.jpg");

                    JSONObject meta = new JSONObject();
                    meta.put("aid", aid);
                    meta.put("desc", entry.desc);
                    meta.put("author_unique_id", entry.authorUniqueId);
                    meta.put("author_nickname", entry.authorNickname);
                    meta.put("watched_time", entry.timestamp);
                    meta.put("duration", entry.duration);
                    meta.put("share_url", entry.getShareUrl());
                    meta.put("updated_at", System.currentTimeMillis());

                    Net.save(metaFile, meta.toString(2).getBytes("UTF-8"));

                    if (!coverFile.exists() && entry.coverUrl != null && !entry.coverUrl.isEmpty()) {
                        byte[] b = Net.bytes(entry.coverUrl);
                        if (b != null) Net.save(coverFile, b);
                    }

                    if (!videoFile.exists()) {
                        String url = entry.downloadUrl != null ? entry.downloadUrl : entry.playUrl;
                        if (url != null && !url.isEmpty()) {
                            Net.download(url, videoFile, null);
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "✓ Відео заархівовано в Локалайзер", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Throwable error) {
                    Diary.note("localizer archiveEntry error: " + error);
                } finally {
                    sDownloadingAids.remove(aid);
                }
            }
        });
    }

    // ------------------------------------------------------ Chat Archiving

    public static void onChatMessage(String conversationId, String sender, String text, boolean isDeleted, long timestamp) {
        if (!isAutoChatsEnabled() || text == null || text.trim().isEmpty()) return;
        final String chatId = (conversationId != null && !conversationId.isEmpty()) ? conversationId : "default_chat";
        final String s = sender != null ? sender : "partner";
        final String body = text.trim();
        final boolean del = isDeleted;
        final long time = timestamp > 0 ? timestamp : System.currentTimeMillis();

        Net.away("localizer-chat", new Runnable() {
            @Override
            public void run() {
                try {
                    Context ctx = Margy.context();
                    if (ctx == null) return;
                    File chatDir = new File(getChatsDir(ctx), chatId.replaceAll("[^a-zA-Z0-9_.-]", "_"));
                    if (!chatDir.exists()) chatDir.mkdirs();

                    File file = new File(chatDir, "messages.json");
                    JSONArray arr = new JSONArray();
                    if (file.exists()) {
                        byte[] raw = Net.read(file);
                        if (raw != null) {
                            try { arr = new JSONArray(new String(raw, "UTF-8")); } catch (Throwable ignored) {}
                        }
                    }

                    // Check for duplicate recent message
                    int len = arr.length();
                    if (len > 0) {
                        JSONObject last = arr.getJSONObject(len - 1);
                        if (body.equals(last.optString("text")) && Math.abs(time - last.optLong("time")) < 2000) {
                            return; // duplicate
                        }
                    }

                    JSONObject msg = new JSONObject();
                    msg.put("sender", s);
                    msg.put("text", body);
                    msg.put("is_deleted", del);
                    msg.put("time", time);
                    arr.put(msg);

                    Net.save(file, arr.toString(2).getBytes("UTF-8"));
                } catch (Throwable error) {
                    Diary.note("localizer onChatMessage error: " + error);
                }
            }
        });
    }

    // ---------------------------------------------------- Амэоблако Sync

    public static void syncAllToAmeoCloud(final Context context) {
        if (context == null) return;
        Toast.makeText(context, "☁️ Підготовка вивантаження в Амэоблако...", Toast.LENGTH_SHORT).show();

        Net.away("localizer-ameocloud", new Runnable() {
            @Override
            public void run() {
                try {
                    File favDir = getFavoritesDir(context);
                    File[] folders = favDir.listFiles();
                    if (folders == null || folders.length == 0) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "В архіві немає відео для вивантаження", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    int count = 0;
                    for (File f : folders) {
                        if (f.isDirectory()) {
                            File vid = new File(f, "video.mp4");
                            File meta = new File(f, "metadata.json");
                            String caption = "TikTok MI Archive: " + f.getName();
                            if (meta.exists()) {
                                try {
                                    byte[] raw = Net.read(meta);
                                    if (raw != null) {
                                        JSONObject j = new JSONObject(new String(raw, "UTF-8"));
                                        caption = "🎬 @" + j.optString("author_unique_id") + "\n" + j.optString("desc") + "\n\n🔗 " + j.optString("share_url");
                                    }
                                } catch (Throwable ignored) {}
                            }

                            if (vid.exists() && vid.length() > 0) {
                                MiogramBridge.shareToSavedMessages(context, vid, "video/mp4", caption);
                                count++;
                                try { Thread.sleep(600); } catch (Throwable ignored) {}
                            }
                        }
                    }

                    final int finalCount = count;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "✓ Надіслано " + finalCount + " відео в Амэоблако!", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Throwable error) {
                    Diary.note("localizer ameocloud sync: " + error);
                }
            }
        });
    }

    public static int getArchivedVideoCount(Context context) {
        try {
            File favDir = getFavoritesDir(context);
            File[] folders = favDir.listFiles();
            return (folders != null) ? folders.length : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int getArchivedChatCount(Context context) {
        try {
            File chatDir = getChatsDir(context);
            File[] folders = chatDir.listFiles();
            return (folders != null) ? folders.length : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void openArchiveFolder(Context context) {
        if (context == null) return;
        try {
            File dir = getArchiveDir(context);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".tiktokmi", dir);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Відкрити архів"));
        } catch (Throwable error) {
            Toast.makeText(context, "Папка: " + getArchiveDir(context).getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }
}
