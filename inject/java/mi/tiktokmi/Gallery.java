package mi.tiktokmi;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.OutputStream;

/**
 * Putting a file where the gallery will find it.
 *
 * Two ways, because Android changed its mind in the middle: on anything recent
 * a row in MediaStore, which needs no permission because the app is writing
 * its own entry, and on older ones a file in Pictures plus a word to the
 * scanner. TikTok itself holds the storage permission an older Android wants,
 * and the mod runs inside it.
 */
public final class Gallery {

    private Gallery() {}

    public static final String FOLDER = "TikTok MI";
    public static final String KEY_FOLDER = "save_folder";

    private static volatile String cachedFolder;

    /**
     * Where the mod's own saves go, inside Pictures. Renaming it does not
     * move what is already saved; it only decides where the next file lands.
     */
    public static String folder() {
        String known = cachedFolder;
        if (known != null) return known;
        String folder = FOLDER;
        try {
            android.content.Context context = Margy.context();
            if (context != null) {
                String read = context.getSharedPreferences(
                        Margy.PREFS, android.content.Context.MODE_PRIVATE)
                        .getString(KEY_FOLDER, FOLDER);
                folder = cleanName(read);
            }
        } catch (Throwable ignored) {
        }
        cachedFolder = folder;
        return folder;
    }

    public static void setFolder(String name) {
        String folder = cleanName(name);
        cachedFolder = folder;
        try {
            android.content.Context context = Margy.context();
            if (context != null) {
                context.getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE)
                        .edit().putString(KEY_FOLDER, folder).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Letters, digits, spaces and a few marks; anything else never happened. */
    static String cleanName(String name) {
        if (name == null) return FOLDER;
        String trimmed = name.trim();
        if (trimmed.length() == 0) return FOLDER;
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < trimmed.length() && kept.length() < 40; i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-'
                    || c == '(' || c == ')') {
                kept.append(c);
            }
        }
        String folder = kept.toString().trim();
        return folder.length() == 0 ? FOLDER : folder;
    }

    /** Returns where it landed, or null. Never throws. */
    public static String save(Context context, byte[] data, String name, String mime) {
        if (data == null || data.length == 0) return null;
        try {
            return Build.VERSION.SDK_INT >= 29
                    ? modern(context, data, name, mime)
                    : older(data, name);
        } catch (Throwable error) {
            Diary.note("gallery: " + error);
            return null;
        }
    }

    private static String modern(Context context, byte[] data, String name, String mime)
            throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + folder());
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = mime.startsWith("video")
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri item = resolver.insert(collection, values);
        if (item == null) return null;

        OutputStream out = resolver.openOutputStream(item);
        if (out == null) return null;
        try {
            out.write(data);
        } finally {
            out.close();
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(item, values, null, null);
        return Environment.DIRECTORY_PICTURES + "/" + folder() + "/" + name;
    }

    private static String older(byte[] data, String name) throws Exception {
        File folder = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), folder());
        if (!folder.isDirectory()) folder.mkdirs();
        File file = new File(folder, name);
        if (!Net.save(file, data)) return null;
        try {
            android.media.MediaScannerConnection.scanFile(
                    Margy.context(), new String[]{file.getAbsolutePath()}, null, null);
        } catch (Throwable ignored) {
        }
        return file.getAbsolutePath();
    }

    /** A name nothing else will take, ending in the right extension. */
    public static String name(String prefix, String url, String fallback) {
        String extension = fallback;
        try {
            String path = url;
            int question = path.indexOf('?');
            if (question > 0) path = path.substring(0, question);
            int dot = path.lastIndexOf('.');
            if (dot > 0 && path.length() - dot <= 5) extension = path.substring(dot + 1);
        } catch (Throwable ignored) {
        }
        return prefix + "_" + System.currentTimeMillis() + "." + extension;
    }
}
