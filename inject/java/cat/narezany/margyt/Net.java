package cat.narezany.margyt;

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

    private static final int TIMEOUT = 15000;
    private static final int LIMIT = 4 * 1024 * 1024;

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
        }, "margyt-" + what);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** The bytes at a url, or null. Never throws, never runs on the main thread. */
    public static byte[] bytes(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "MargyT");
            if (connection.getResponseCode() / 100 != 2) return null;

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
