package mi.tiktokmi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import mi.tiktokmi.plugin.MargyPlugin;
import mi.tiktokmi.plugin.PluginContext;

import dalvik.system.DexClassLoader;

/**
 * Other people's code, running inside this one.
 *
 * A plugin is a zip -- `.mtp` -- holding a manifest, a dex and, if it likes, an
 * icon. Installing one unpacks it into the app's own files; switching it on
 * loads its dex with `DexClassLoader` and calls the hooks in `MargyPlugin`.
 *
 * Margy's plugins are Python, and these are not, for a reason that is about
 * repacking rather than taste: Margy is a fork and builds its own apk, so a
 * Python runtime goes in at build time. TikTok MI edits an apk somebody else
 * built, and a Python runtime would mean carrying CPython's native libraries
 * and its standard library into a 340 MB archive whose alignment is not ours to
 * decide. `DexClassLoader` is an ordinary Android API that costs nothing and
 * needs no root, so the format is the same shape -- manifest, icon, metadata,
 * a list with switches -- with a dex where the `main.py` would be.
 *
 * There is no sandbox. A plugin runs with everything TikTok has: its files, its
 * network, its session. The manifest says who wrote it; nothing here checks
 * that it is true. This is written the same way in the documentation, in the
 * settings screen and here, because it is the whole risk in one sentence.
 */
public final class Plugins {

    private Plugins() {}

    public static final String FOLDER = "plugins";
    public static final String MANIFEST = "manifest.json";
    public static final String DEX = "classes.dex";
    public static final String ICON = "icon.png";
    public static final String EXTENSION = ".mtp";

    private static final String PREFS = "tiktokmi_plugins";

    /** What a manifest says, and what the loader made of it. */
    public static final class Info {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final String entry;
        public final int minApi;
        public final File folder;

        /** Why it is not running, or null when it is fine. */
        public String trouble;

        private Bitmap icon;
        private boolean iconRead;

        Info(String id, String name, String version, String author, String description,
             String entry, int minApi, File folder) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.entry = entry;
            this.minApi = minApi;
            this.folder = folder;
        }

        /** The icon, read once and kept; null when the plugin ships without one. */
        public Bitmap icon() {
            if (!iconRead) {
                iconRead = true;
                File file = new File(folder, ICON);
                if (file.isFile()) {
                    icon = BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }
            return icon;
        }
    }

    /** One installed plugin, with the instance when it is running. */
    private static final class Live {
        final Info info;
        MargyPlugin instance;

        Live(Info info) {
            this.info = info;
        }
    }

    private static final List<Live> installed = new ArrayList<>();
    private static volatile MargyPlugin[] running = new MargyPlugin[0];
    private static boolean read;

    // -------------------------------------------------------------- reading

    private static File home(Context context) {
        File folder = new File(context.getFilesDir(), FOLDER);
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Everything installed, whether it runs or not. */
    public static synchronized List<Info> list() {
        Context context = Margy.context();
        if (context == null) return Collections.emptyList();
        if (!read) scan(context);
        List<Info> out = new ArrayList<>();
        for (Live live : installed) out.add(live.info);
        return out;
    }

    public static boolean isEnabled(String id) {
        Context context = Margy.context();
        if (context == null) return false;
        return prefs(context).getBoolean(id, false);
    }

    /**
     * Switch a plugin on or off. It starts at once; stopping it unloads
     * nothing, because a class loaded into a process stays there -- the hooks
     * simply stop being called, and the next start of the app is clean.
     */
    public static synchronized void setEnabled(String id, boolean enabled) {
        Context context = Margy.context();
        if (context == null) return;
        prefs(context).edit().putBoolean(id, enabled).apply();
        if (!read) scan(context);
        for (Live live : installed) {
            if (!live.info.id.equals(id)) continue;
            if (enabled && live.instance == null) {
                start(context, live);
            } else if (!enabled && live.instance != null) {
                MargyPlugin instance = live.instance;
                live.instance = null;
                try {
                    instance.onStop();
                } catch (Throwable error) {
                    Diary.note("plugin " + id + " threw on stop: " + error);
                }
            }
            break;
        }
        publish();
    }

    private static void scan(Context context) {
        read = true;
        installed.clear();
        File[] folders = home(context).listFiles();
        if (folders == null) return;
        for (File folder : folders) {
            if (!folder.isDirectory()) continue;
            Info info = readManifest(folder);
            if (info != null) installed.add(new Live(info));
        }
    }

    private static Info readManifest(File folder) {
        File manifest = new File(folder, MANIFEST);
        if (!manifest.isFile()) return null;
        try {
            JSONObject json = new JSONObject(new String(readAll(new FileInputStream(manifest)),
                    "UTF-8"));
            String id = json.optString("id", folder.getName());
            Info info = new Info(
                    id,
                    localised(json, "name", id),
                    json.optString("version", "?"),
                    json.optString("author", "?"),
                    localised(json, "description", ""),
                    json.optString("entry", ""),
                    json.optInt("min_api", 1),
                    folder);
            if (info.entry.isEmpty()) info.trouble = "manifest has no entry class";
            if (info.minApi > MargyPlugin.API) {
                info.trouble = "wants TikTok MI plugin api " + info.minApi
                        + ", this one is " + MargyPlugin.API;
            }
            return info;
        } catch (Throwable error) {
            Diary.note("plugin in " + folder.getName() + " has a bad manifest: " + error);
            return null;
        }
    }

    /** `name_ru` before `name`, so a plugin can speak the phone's language. */
    private static String localised(JSONObject json, String field, String fallback) {
        String language = Locale.getDefault().getLanguage();
        String translated = json.optString(field + "_" + language, "");
        if (!translated.isEmpty()) return translated;
        return json.optString(field, fallback);
    }

    // ------------------------------------------------------------- starting

    /**
     * Load and start everything that is switched on.
     *
     * Called from the mod's start-up provider, before TikTok's own onCreate.
     */
    public static synchronized void startAll(Context context) {
        if (!read) scan(context);
        int started = 0;
        for (Live live : installed) {
            if (!isEnabled(live.info.id) || live.info.trouble != null) continue;
            if (start(context, live)) started++;
        }
        publish();
        if (!installed.isEmpty()) {
            Diary.note("plugins: " + started + " of " + installed.size() + " running");
        }
    }

    private static boolean start(Context context, Live live) {
        Info info = live.info;
        try {
            File dex = new File(info.folder, DEX);
            if (!dex.isFile()) {
                info.trouble = "no " + DEX + " in the plugin";
                return false;
            }
            // Android 14 refuses to load a dex anyone could still write to
            if (dex.canWrite()) dex.setReadOnly();

            ClassLoader loader = new DexClassLoader(
                    dex.getAbsolutePath(),
                    context.getCodeCacheDir().getAbsolutePath(),
                    null,
                    Plugins.class.getClassLoader());

            Class<?> type = loader.loadClass(info.entry);
            Object made = type.getDeclaredConstructor().newInstance();
            if (!(made instanceof MargyPlugin)) {
                info.trouble = info.entry + " does not extend MargyPlugin";
                return false;
            }
            MargyPlugin plugin = (MargyPlugin) made;
            plugin.attach(new PluginContext(context.getApplicationContext(), info.id,
                    info.folder, new PluginContext.Diarist() {
                @Override
                public void note(String line) {
                    Diary.note(line);
                }
            }));
            plugin.onStart(context);
            live.instance = plugin;
            info.trouble = null;
            return true;
        } catch (Throwable error) {
            info.trouble = String.valueOf(error);
            Diary.note("plugin " + info.id + " would not start: " + error);
            return false;
        }
    }

    /** The running set, as an array the hot hooks can walk without locking. */
    private static void publish() {
        List<MargyPlugin> live = new ArrayList<>();
        for (Live one : installed) {
            if (one.instance != null) live.add(one.instance);
        }
        running = live.toArray(new MargyPlugin[0]);
    }

    /**
     * A plugin that throws is dropped rather than asked again.
     *
     * The alternative is the same exception on every frame, which is not a
     * plugin misbehaving any more, it is TikTok not working.
     */
    private static synchronized void drop(MargyPlugin plugin, Throwable error) {
        for (Live live : installed) {
            if (live.instance != plugin) continue;
            Diary.note("plugin " + live.info.id + " threw, dropped: " + error);
            live.info.trouble = String.valueOf(error);
            live.instance = null;
            break;
        }
        publish();
    }

    // ---------------------------------------------------------- the sending

    public static void onActivityCreated(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityCreated(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    public static void onActivityResumed(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityResumed(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    public static void onActivityPaused(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityPaused(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    /** In the drawing path: the empty case has to cost nothing. */
    public static int colour(int colour) {
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return colour;
        for (MargyPlugin plugin : plugins) {
            try {
                colour = plugin.onColour(colour);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return colour;
    }

    public static String region(String key, String value) {
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return value;
        for (MargyPlugin plugin : plugins) {
            try {
                value = plugin.onRegion(key, value);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return value;
    }

    // ------------------------------------------------------- installing one

    /**
     * Unpack a `.mtp` the person picked, keyed by the id in its manifest.
     *
     * Installing over an existing id replaces it, which is how a plugin is
     * updated. Returns the id, or throws with something worth showing.
     */
    public static synchronized String install(Context context, Uri source) throws Exception {
        File staging = new File(context.getCacheDir(), "mtp-" + System.currentTimeMillis());
        try {
            unpack(context, source, staging);

            Info info = readManifest(staging);
            if (info == null) throw new Exception("no readable " + MANIFEST + " in the plugin");
            if (!new File(staging, DEX).isFile()) throw new Exception("no " + DEX + " in the plugin");
            if (info.minApi > MargyPlugin.API) {
                throw new Exception("the plugin wants api " + info.minApi
                        + " and this TikTok MI has " + MargyPlugin.API);
            }

            File home = new File(home(context), safe(info.id));
            remove(home);
            if (!staging.renameTo(home)) {
                copyFolder(staging, home);
                remove(staging);
            }
            read = false;
            Diary.note("plugin installed: " + info.id + " " + info.version);
            return info.id;
        } finally {
            remove(staging);
        }
    }

    /** Take a plugin off the phone. It stops being called at once. */
    public static synchronized void uninstall(Context context, String id) {
        setEnabled(id, false);
        prefs(context).edit().remove(id).apply();
        remove(new File(home(context), safe(id)));
        read = false;
    }

    private static void unpack(Context context, Uri source, File into) throws Exception {
        into.mkdirs();
        InputStream raw = context.getContentResolver().openInputStream(source);
        if (raw == null) throw new Exception("cannot read the file that was picked");
        ZipInputStream zip = new ZipInputStream(raw);
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // a zip may name any path it likes, including one that climbs
                // out of the folder it is being written into
                File out = new File(into, safe(new File(entry.getName()).getName()));
                if (!out.getCanonicalPath().startsWith(into.getCanonicalPath())) continue;
                OutputStream sink = new FileOutputStream(out);
                try {
                    copy(zip, sink);
                } finally {
                    sink.close();
                }
            }
        } finally {
            zip.close();
        }
    }

    /** A file name that cannot be a path, an id that cannot be a folder. */
    private static String safe(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'
                    ? c : '_');
        }
        String cleaned = out.toString();
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")
                ? "plugin" : cleaned;
    }

    private static void copyFolder(File from, File to) throws Exception {
        to.mkdirs();
        File[] files = from.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) continue;
            InputStream in = new FileInputStream(file);
            OutputStream out = new FileOutputStream(new File(to, file.getName()));
            try {
                copy(in, out);
            } finally {
                in.close();
                out.close();
            }
        }
    }

    private static void remove(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) remove(child);
        }
        file.delete();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
