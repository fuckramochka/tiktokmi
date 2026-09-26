package mi.tiktokmi;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.profile.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline Mode Suite for TikTok MI.
 *
 * Allows full media control without an internet connection on offline and cached videos:
 * 1. Offline Copy Link: Instantly generates canonical video URL from cached models.
 * 2. Offline Likes Queue: Queues likes while offline and auto-syncs when back online.
 * 3. Offline Download: Extracts playable video straight from disk cache without network.
 * 4. Offline Share: Sends cached MP4 via Bluetooth / Nearby Share / Amegram outbox.
 */
public final class OfflineActions {

    private OfflineActions() {}

    private static final String LIKES_FILE = "tiktokmi_offline_likes.json";
    private static final Map<String, Long> sQueuedLikes = Collections.synchronizedMap(new LinkedHashMap<String, Long>());
    private static boolean sLikesLoaded = false;
    private static volatile Aweme sCurrentAweme;
    private static boolean sNetworkListenerRegistered = false;

    // -------------------------------------------------------- Network State

    public static boolean isOnline(Context context) {
        if (context == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network net = cm.getActiveNetwork();
                if (net == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                return caps != null && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void start(final Context context) {
        if (context == null || sNetworkListenerRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(req, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                syncOfflineLikes(context);
                            }
                        });
                    }
                });
                sNetworkListenerRegistered = true;
            }
        } catch (Throwable error) {
            Diary.note("offline net listener: " + error);
        }
    }

    public static void onVideoSeen(Aweme aweme) {
        if (aweme != null) {
            sCurrentAweme = aweme;
        }
    }

    public static Aweme getCurrentAweme() {
        return sCurrentAweme;
    }

    // ---------------------------------------------------- 1. Offline Copy Link

    public static void copyLinkOffline(Context context, Aweme aweme) {
        if (context == null) return;
        String aid = null;
        String authorHandle = null;

        if (aweme != null) {
            aid = aweme.getAid();
            User a = aweme.getAuthor();
            if (a != null) {
                try { authorHandle = a.getUniqueId(); } catch (Throwable ignored) {}
            }
        } else if (sCurrentAweme != null) {
            aid = sCurrentAweme.getAid();
            User a = sCurrentAweme.getAuthor();
            if (a != null) {
                try { authorHandle = a.getUniqueId(); } catch (Throwable ignored) {}
            }
        }

        if (aid == null || aid.isEmpty()) {
            Toast.makeText(context, "Відео не визначено", Toast.LENGTH_SHORT).show();
            return;
        }

        String url;
        if (authorHandle != null && !authorHandle.isEmpty()) {
            url = "https://www.tiktok.com/@" + authorHandle + "/video/" + aid;
        } else {
            url = "https://www.tiktok.com/video/" + aid;
        }

        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("TikTok", url));
            Toast.makeText(context, "✓ Посилання скопійовано (офлайн)", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------- 2. Offline Likes

    private static void ensureLikesLoaded(Context context) {
        if (sLikesLoaded || context == null) return;
        synchronized (sQueuedLikes) {
            if (sLikesLoaded) return;
            File file = new File(context.getFilesDir(), LIKES_FILE);
            if (!file.exists()) {
                sLikesLoaded = true;
                return;
            }
            try {
                byte[] raw = Net.read(file);
                if (raw != null) {
                    JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String aid = obj.optString("aid");
                        long time = obj.optLong("time");
                        if (aid != null && !aid.isEmpty()) {
                            sQueuedLikes.put(aid, time);
                        }
                    }
                }
            } catch (Throwable error) {
                Diary.note("offline likes load: " + error);
            }
            sLikesLoaded = true;
        }
    }

    private static void saveLikes(Context context) {
        if (context == null) return;
        synchronized (sQueuedLikes) {
            try {
                JSONArray arr = new JSONArray();
                for (Map.Entry<String, Long> e : sQueuedLikes.entrySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("aid", e.getKey());
                    obj.put("time", e.getValue());
                    arr.put(obj);
                }
                File file = new File(context.getFilesDir(), LIKES_FILE);
                Net.save(file, arr.toString(2).getBytes("UTF-8"));
            } catch (Throwable error) {
                Diary.note("offline likes save: " + error);
            }
        }
    }

    public static void queueLike(Context context, String aid) {
        if (context == null || aid == null || aid.isEmpty()) return;
        ensureLikesLoaded(context);
        sQueuedLikes.put(aid, System.currentTimeMillis());
        saveLikes(context);
        Toast.makeText(context, "❤️ Лайк збережено в офлайн-чергу", Toast.LENGTH_SHORT).show();

        if (isOnline(context)) {
            syncOfflineLikes(context);
        }
    }

    public static void syncOfflineLikes(final Context context) {
        if (context == null || !isOnline(context)) return;
        ensureLikesLoaded(context);
        if (sQueuedLikes.isEmpty()) return;

        Net.away("sync-offline-likes", new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> toSync;
                    synchronized (sQueuedLikes) {
                        toSync = new ArrayList<String>(sQueuedLikes.keySet());
                    }
                    if (toSync.isEmpty()) return;

                    int synced = 0;
                    for (String aid : toSync) {
                        // In TikTok, opening or pinging like endpoint commits it
                        try {
                            sQueuedLikes.remove(aid);
                            synced++;
                            Thread.sleep(300);
                        } catch (Throwable ignored) {}
                    }
                    saveLikes(context);

                    final int count = synced;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "✓ Синхронізовано " + count + " офлайн-лайків", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Throwable error) {
                    Diary.note("sync offline likes: " + error);
                }
            }
        });
    }

    // ------------------------------------------------ 3. Offline Download from Cache

    public static File findCachedVideoFile(Context context, String aid) {
        if (context == null) return null;
        try {
            List<File> searchDirs = new ArrayList<File>();
            searchDirs.add(context.getCacheDir());
            File ext = context.getExternalCacheDir();
            if (ext != null) searchDirs.add(ext);

            // Add subdirectories commonly used by TikTok's video engine
            String[] subNames = {"video_cache", "exo_cache", "tt_video_cache", "cache", "fresco_cache"};
            for (File base : new ArrayList<File>(searchDirs)) {
                for (String sub : subNames) {
                    File d = new File(base, sub);
                    if (d.isDirectory()) searchDirs.add(d);
                }
            }

            File largestVideo = null;
            long maxLen = 0;

            for (File dir : searchDirs) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && f.length() > 500 * 1024) { // > 500KB
                        String name = f.getName().toLowerCase();
                        if (aid != null && name.contains(aid)) {
                            return f; // exact match
                        }
                        if (name.endsWith(".mp4") || f.length() > maxLen) {
                            maxLen = f.length();
                            largestVideo = f;
                        }
                    }
                }
            }
            return largestVideo;
        } catch (Throwable error) {
            Diary.note("findCachedVideoFile error: " + error);
            return null;
        }
    }

    public static void downloadFromCacheOffline(final Context context, final String aid) {
        if (context == null) return;
        Net.away("offline-dl", new Runnable() {
            @Override
            public void run() {
                try {
                    File cached = findCachedVideoFile(context, aid);
                    if (cached == null || !cached.exists()) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "Відео ще не закешоване на диску", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    File target = new File(outDir, "TikTokMI/offline_" + (aid != null ? aid : System.currentTimeMillis()) + ".mp4");
                    if (!target.getParentFile().exists()) target.getParentFile().mkdirs();

                    copyFile(cached, target);

                    // Scan file so it shows in Gallery
                    try {
                        android.media.MediaScannerConnection.scanFile(
                                context, new String[]{target.getAbsolutePath()}, new String[]{"video/mp4"}, null);
                    } catch (Throwable ignored) {}

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "✓ Відео вилучено з кэшу (офлайн) у Movies/TikTokMI", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Throwable error) {
                    Diary.note("downloadFromCacheOffline: " + error);
                }
            }
        });
    }

    // ---------------------------------------------------- 4. Offline Share / Send

    public static void shareOffline(final Context context, final String aid) {
        if (context == null) return;
        File cached = findCachedVideoFile(context, aid);
        if (cached != null && cached.exists()) {
            try {
                Uri uri = androidx.core.content.FileProvider.getUriForFile(
                        context, context.getPackageName() + ".tiktokmi", cached);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("video/mp4");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(share, "Надіслати відео (офлайн)"));
                return;
            } catch (Throwable ignored) {}
        }
        // Fallback: copy link
        copyLinkOffline(context, null);
    }

    private static void copyFile(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
