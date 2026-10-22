package cat.narezany.margyt;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A test build's look at its own memory: once the Java heap passes a mark, a
 * snapshot of it goes to Downloads/MargyT, to be read on a computer. Also the
 * main thread's profile, written there now and then. Never in a release build.
 */
final class HeapShot {

    private HeapShot() {}

    private static final long MARK = 280L * 1024 * 1024;
    private static boolean taken;
    private static long checked, written;

    /** From the lag watcher's thread, every look; does anything only now and then. */
    static void tick(java.util.function.Supplier<String> profiler) {
        if (!Probe.ON) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - checked < 5000) return;
        checked = now;
        try {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            String profile = now - written > 60000 ? profiler.get() : null;
            if (profile != null && profile.length() > 0) {
                written = now;
                String text = "heap " + (used >> 20) + " MB of " + (runtime.maxMemory() >> 20) + " MB, uptime " + (now / 1000) + " s\n" + profile;
                File temp = new File(Margy.context().getCacheDir(), "margyt-profile.txt");
                Net.save(temp, text.getBytes("UTF-8"));
                publish(temp, "margyt-profile.txt", "text/plain");
            }
            if (!taken && used > MARK) {
                taken = true;
                Diary.note("heap: " + (used >> 20) + " MB, taking a snapshot");
                File dump = new File(Margy.context().getCacheDir(), "margyt-heap.hprof");
                android.os.Debug.dumpHprofData(dump.getAbsolutePath());
                publish(dump, "margyt-heap-" + (used >> 20) + "mb.hprof", "application/octet-stream");
                dump.delete();
            }
        } catch (Throwable error) {
            Diary.note("heap shot: " + error);
        }
    }

    private static void publish(File file, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT < 29) return;
        Context c = Margy.context();
        android.content.ContentResolver resolver = c.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        // the same name again is replaced, not added beside
        resolver.delete(collection, MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{name});
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MargyT");
        Uri item = resolver.insert(collection, values);
        if (item == null) return;
        try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(item)) {
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
    }
}
