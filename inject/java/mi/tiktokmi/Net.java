package mi.tiktokmi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetching things, off the main thread and without dragging in a library.
 *
 * The mod needs very little of this -- a json file it reads every few minutes,
 * a picture now and then -- and `HttpURLConnection` is already in every
 * Android. Anything larger would mean shipping a networking stack inside
 * somebody else's apk, which is a great deal of weight for two requests.
 */
public final class Net {

    private Net() {}

    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;
    private static final int LIMIT = 4 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

    /** Run something on a thread of the mod's own, named so it is findable. */
    public static void away(final String what, final Runnable work) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work.run();
                } catch (Throwable error) {
                    Diary.note(what + ": " + error);
                }
            }
        }, "tiktokmi-" + what);
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** The bytes at a url, or null. Never throws, never runs on the main thread. */
    public static byte[] bytes(String url) {
        HttpURLConnection connection = null;
        try {
            String current = url;
            for (int redirect = 0; redirect < 5; redirect++) {
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER
                        || code == 307
                        || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        connection.disconnect();
                        current = location;
                        continue;
                    }
                }
                if (code / 100 != 2) return null;
                break;
            }
            if (connection == null || connection.getResponseCode() / 100 != 2) return null;

            InputStream in = connection.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read, total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > LIMIT) return null;  // nothing the mod wants is this big
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static String text(String url) {
        byte[] raw = bytes(url);
        if (raw == null) return null;
        try {
            return new String(raw, "UTF-8");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Told how far along a download is, in whole percent. */
    public interface Along {
        void at(int percent, long got, long total);
    }

    /**
     * Fetch straight to a file, saying how it is going.
     * Retries up to 3 times with chunk resumption if interrupted.
     */
    public static boolean download(String url, File into, Along along) {
        if (url == null || url.isEmpty() || into == null) return false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            boolean success = downloadAttempt(url, into, along);
            if (success) return true;
            Diary.note("download attempt " + attempt + " failed, retrying in 1.5s...");
            try {
                Thread.sleep(1500);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean downloadAttempt(String url, File into, Along along) {
        HttpURLConnection connection = null;
        File part = new File(into.getAbsolutePath() + ".part");
        try {
            File parent = into.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();

            long existingBytes = part.exists() ? part.length() : 0;
            String current = url;
            boolean resumed = false;

            for (int redirect = 0; redirect < 5; redirect++) {
                connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                if (existingBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                }
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM
                        || code == HttpURLConnection.HTTP_MOVED_TEMP
                        || code == HttpURLConnection.HTTP_SEE_OTHER
                        || code == 307
                        || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        connection.disconnect();
                        current = location;
                        continue;
                    }
                }
                if (code == 416) {
                    Diary.note("download: server returned 416, resetting .part and restarting from 0");
                    connection.disconnect();
                    if (part.exists()) part.delete();
                    existingBytes = 0;
                    current = url;
                    continue;
                }
                if (code == 206) {
                    resumed = true;
                    break;
                } else if (code == 200) {
                    resumed = false;
                    existingBytes = 0;
                    break;
                } else if (code / 100 != 2) {
                    Diary.note("download: http error " + code + " for " + current);
                    return false;
                }
                break;
            }
            if (connection == null) return false;
            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 206) return false;

            long contentLength;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                contentLength = connection.getContentLengthLong();
            } else {
                contentLength = connection.getContentLength();
            }
            long total = resumed ? (existingBytes + contentLength) : contentLength;

            InputStream in = connection.getInputStream();
            OutputStream out = new FileOutputStream(part, resumed);
            try {
                byte[] buffer = new byte[65536];
                long got = existingBytes;
                int read, told = -1;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    got += read;
                    int percent = total > 0 ? (int) (got * 100 / total) : -1;
                    if (along != null && percent != told) {
                        told = percent;
                        along.at(percent, got, total);
                    }
                }
            } finally {
                in.close();
                out.close();
            }

            if (into.exists()) into.delete();
            boolean renamed = part.renameTo(into);
            if (!renamed) {
                copyFile(part, into);
                part.delete();
            }
            return into.isFile() && into.length() > 0;
        } catch (Throwable error) {
            Diary.note("download: " + error);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void copyFile(File src, File dst) {
        try {
            InputStream in = new java.io.FileInputStream(src);
            try {
                OutputStream out = new FileOutputStream(dst);
                try {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Write bytes where they will still be after a restart. */
    public static boolean save(File file, byte[] data) {
        if (data == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            OutputStream out = new FileOutputStream(file);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static byte[] read(File file) {
        try {
            if (!file.isFile()) return null;
            java.io.InputStream in = new java.io.FileInputStream(file);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
