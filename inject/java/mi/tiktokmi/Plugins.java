package mi.tiktokmi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.TextView;

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

import dalvik.system.DexClassLoader;
import mi.tiktokmi.plugin.MiPlugin;
import mi.tiktokmi.plugin.MiPluginContext;

/**
 * TikTok MI Plugin Engine.
 *
 * Designed and engineered specifically for TikTok MI.
 *
 * Supports modern TikTok MI Plugins (.mip) and legacy (.mtp) packages.
 * Plugins run directly in the TikTok process, dynamically loaded via DexClassLoader.
 *
 * Features:
 * - Thread-safe, lock-free dispatch for high-frequency rendering and text hooks.
 * - Fault isolation: plugins that throw are automatically deactivated with diagnostic
 *   reports logged to Diary, preserving host app stability.
 * - Multi-language manifest support (Ukrainian, Russian, English).
 * - Safe sandbox-free unpacked directory management with Zip Slip protection.
 */
public final class Plugins {

    private Plugins() {}

    public static final String DIR_PLUGINS = "plugins";
    public static final String FILE_MANIFEST = "manifest.json";
    public static final String FILE_DEX = "classes.dex";
    public static final String FILE_ICON = "icon.png";
    public static final String EXT_MIP = ".mip";
    public static final String EXT_MTP = ".mtp";

    private static final String PREF_PLUGINS = "tiktokmi_plugins";

    /**
     * Metadata describing an installed plugin package.
     */
    public static final class Info {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final String entry;
        public final int minApi;
        public final File folder;

        public String trouble;

        private Bitmap icon;
        private boolean iconLoaded;

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

        public Bitmap icon() {
            if (!iconLoaded) {
                iconLoaded = true;
                File iconFile = new File(folder, FILE_ICON);
                if (iconFile.isFile()) {
                    icon = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                }
            }
            return icon;
        }
    }

    private static final class RegisteredPlugin {
        final Info info;
        MiPlugin instance;

        RegisteredPlugin(Info info) {
            this.info = info;
        }
    }

    private static final List<RegisteredPlugin> registry = new ArrayList<>();
    private static volatile MiPlugin[] activePlugins = new MiPlugin[0];
    private static boolean scanned;

    // ------------------------------------------------------------- Storage & Preferences

    private static File storageDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_PLUGINS);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static SharedPreferences pluginPreferences(Context context) {
        return context.getSharedPreferences(PREF_PLUGINS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- Discovery & State

    public static synchronized List<Info> list() {
        Context context = Margy.context();
        if (context == null) return Collections.emptyList();
        if (!scanned) scan(context);
        List<Info> result = new ArrayList<>(registry.size());
        for (RegisteredPlugin item : registry) {
            result.add(item.info);
        }
        return result;
    }

    public static boolean isEnabled(String id) {
        Context context = Margy.context();
        if (context == null) return false;
        return pluginPreferences(context).getBoolean(id, false);
    }

    public static synchronized void setEnabled(String id, boolean enabled) {
        Context context = Margy.context();
        if (context == null) return;
        pluginPreferences(context).edit().putBoolean(id, enabled).apply();
        if (!scanned) scan(context);

        for (RegisteredPlugin item : registry) {
            if (!item.info.id.equals(id)) continue;
            if (enabled && item.instance == null) {
                activatePlugin(context, item);
            } else if (!enabled && item.instance != null) {
                MiPlugin instance = item.instance;
                item.instance = null;
                try {
                    instance.onStop();
                } catch (Throwable error) {
                    Diary.note("plugin " + id + " error during onStop: " + error);
                }
            }
            break;
        }
        refreshActiveArray();
    }

    private static void scan(Context context) {
        scanned = true;
        registry.clear();
        File root = storageDir(context);
        File[] folders = root.listFiles();
        if (folders == null) return;

        for (File dir : folders) {
            if (!dir.isDirectory()) continue;
            Info info = parseManifest(dir);
            if (info != null) {
                registry.add(new RegisteredPlugin(info));
            }
        }
    }

    private static Info parseManifest(File folder) {
        File manifestFile = new File(folder, FILE_MANIFEST);
        if (!manifestFile.isFile()) return null;

        try {
            byte[] bytes = readStreamFully(new FileInputStream(manifestFile));
            JSONObject json = new JSONObject(new String(bytes, "UTF-8"));
            String id = json.optString("id", folder.getName());

            Info info = new Info(
                    id,
                    localizedText(json, "name", id),
                    json.optString("version", "1.0"),
                    json.optString("author", "Unknown"),
                    localizedText(json, "description", ""),
                    json.optString("entry", ""),
                    json.optInt("min_api", 1),
                    folder
            );

            if (info.entry.isEmpty()) {
                info.trouble = "Manifest does not specify an entry class";
            } else if (info.minApi > MiPlugin.API) {
                info.trouble = "Requires API " + info.minApi + ", but TikTok MI provides " + MiPlugin.API;
            }
            return info;
        } catch (Throwable error) {
            Diary.note("plugin [" + folder.getName() + "] manifest error: " + error);
            return null;
        }
    }

    private static String localizedText(JSONObject json, String baseKey, String fallback) {
        String lang = Locale.getDefault().getLanguage();
        String candidate = json.optString(baseKey + "_" + lang, "");
        if (!candidate.isEmpty()) return candidate;
        return json.optString(baseKey, fallback);
    }

    // ------------------------------------------------------------- Lifecycle & Loading

    public static synchronized void startAll(Context context) {
        if (!scanned) scan(context);
        int started = 0;
        for (RegisteredPlugin item : registry) {
            if (!isEnabled(item.info.id) || item.info.trouble != null) continue;
            if (activatePlugin(context, item)) {
                started++;
            }
        }
        refreshActiveArray();
        if (!registry.isEmpty()) {
            Diary.note("plugins: " + started + " of " + registry.size() + " active");
        }
    }

    private static boolean activatePlugin(Context context, RegisteredPlugin item) {
        Info info = item.info;
        try {
            File dexFile = new File(info.folder, FILE_DEX);
            if (!dexFile.isFile()) {
                info.trouble = "Missing classes.dex in plugin bundle";
                return false;
            }
            if (dexFile.canWrite()) {
                dexFile.setReadOnly();
            }

            ClassLoader parentLoader = Plugins.class.getClassLoader();
            DexClassLoader classLoader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    context.getCodeCacheDir().getAbsolutePath(),
                    null,
                    parentLoader
            );

            Class<?> clazz = classLoader.loadClass(info.entry);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (!(instance instanceof MiPlugin)) {
                info.trouble = info.entry + " does not extend MiPlugin";
                return false;
            }

            MiPlugin plugin = (MiPlugin) instance;
            MiPluginContext ctx = new MiPluginContext(
                    context.getApplicationContext(),
                    info.id,
                    info.name,
                    info.version,
                    info.folder,
                    new MiPluginContext.Diarist() {
                        @Override
                        public void note(String line) {
                            Diary.note(line);
                        }
                    },
                    new MiPluginContext.HostBridge() {
                        @Override
                        public int getAccentColour() {
                            return Accent.colour();
                        }

                        @Override
                        public boolean isGhostMode() {
                            return Ghost.isEnabled();
                        }

                        @Override
                        public boolean isDarkTheme() {
                            return Themes.isDark();
                        }
                    }
            );
            plugin.attach(ctx);
            plugin.onStart(context);

            item.instance = plugin;
            info.trouble = null;
            return true;
        } catch (Throwable error) {
            info.trouble = error.getMessage() != null ? error.getMessage() : String.valueOf(error);
            Diary.note("plugin [" + info.id + "] failed to start: " + error);
            return false;
        }
    }

    private static void refreshActiveArray() {
        List<MiPlugin> list = new ArrayList<>();
        for (RegisteredPlugin item : registry) {
            if (item.instance != null) {
                list.add(item.instance);
            }
        }
        activePlugins = list.toArray(new MiPlugin[0]);
    }

    private static synchronized void isolateFailure(MiPlugin plugin, Throwable error) {
        for (RegisteredPlugin item : registry) {
            if (item.instance == plugin) {
                Diary.note("plugin [" + item.info.id + "] faulted and was isolated: " + error);
                item.info.trouble = "Fault: " + error;
                item.instance = null;
                break;
            }
        }
        refreshActiveArray();
    }

    // ------------------------------------------------------------- Dispatch Hooks

    public static void onActivityCreated(Activity activity) {
        MiPlugin[] list = activePlugins;
        for (MiPlugin plugin : list) {
            try {
                plugin.onActivityCreated(activity);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
    }

    public static void onActivityResumed(Activity activity) {
        MiPlugin[] list = activePlugins;
        for (MiPlugin plugin : list) {
            try {
                plugin.onActivityResumed(activity);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
    }

    public static void onActivityPaused(Activity activity) {
        MiPlugin[] list = activePlugins;
        for (MiPlugin plugin : list) {
            try {
                plugin.onActivityPaused(activity);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
    }

    public static int colour(int colour) {
        MiPlugin[] list = activePlugins;
        if (list.length == 0) return colour;
        for (MiPlugin plugin : list) {
            try {
                colour = plugin.onColour(colour);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
        return colour;
    }

    public static String region(String key, String value) {
        MiPlugin[] list = activePlugins;
        if (list.length == 0) return value;
        for (MiPlugin plugin : list) {
            try {
                value = plugin.onRegion(key, value);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
        return value;
    }

    /**
     * Intercepts AB experiments and configuration flags.
     */
    public static Boolean flag(String key) {
        MiPlugin[] list = activePlugins;
        if (list.length == 0) return null;
        for (MiPlugin plugin : list) {
            try {
                Boolean override = plugin.onFlag(key, null);
                if (override != null) return override;
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
        return null;
    }

    /**
     * Intercepts text rendered in TextViews across the application.
     */
    public static CharSequence text(TextView view, CharSequence text) {
        MiPlugin[] list = activePlugins;
        if (list.length == 0 || text == null) return text;
        for (MiPlugin plugin : list) {
            try {
                text = plugin.onText(view, text);
                text = plugin.onDirectMessage(view, text);
            } catch (Throwable error) {
                isolateFailure(plugin, error);
            }
        }
        return text;
    }

    // ------------------------------------------------------------- Installation & Removal

    public static synchronized String install(Context context, Uri source) throws Exception {
        File tempDir = new File(context.getCacheDir(), "mplugin-" + System.currentTimeMillis());
        try {
            unpackZip(context, source, tempDir);

            Info info = parseManifest(tempDir);
            if (info == null) {
                throw new Exception("Bundle does not contain a valid " + FILE_MANIFEST);
            }
            if (!new File(tempDir, FILE_DEX).isFile()) {
                throw new Exception("Bundle does not contain " + FILE_DEX);
            }
            if (info.minApi > MiPlugin.API) {
                throw new Exception("Plugin requires API " + info.minApi + " (TikTok MI has " + MiPlugin.API + ")");
            }

            File targetDir = new File(storageDir(context), sanitizeId(info.id));
            deleteRecursively(targetDir);
            if (!tempDir.renameTo(targetDir)) {
                copyRecursively(tempDir, targetDir);
                deleteRecursively(tempDir);
            }

            scanned = false;
            Diary.note("plugin installed: " + info.id + " v" + info.version);
            return info.id;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public static synchronized void uninstall(Context context, String id) {
        setEnabled(id, false);
        pluginPreferences(context).edit().remove(id).apply();
        deleteRecursively(new File(storageDir(context), sanitizeId(id)));
        scanned = false;
        Diary.note("plugin uninstalled: " + id);
    }

    private static void unpackZip(Context context, Uri uri, File destination) throws Exception {
        destination.mkdirs();
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new Exception("Unable to open source archive");

        ZipInputStream zip = new ZipInputStream(input);
        try {
            ZipEntry entry;
            String canonicalDest = destination.getCanonicalPath();
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String safeName = sanitizeId(new File(entry.getName()).getName());
                File target = new File(destination, safeName);
                if (!target.getCanonicalPath().startsWith(canonicalDest)) {
                    continue; // Guard against Zip Slip path traversal
                }
                FileOutputStream out = new FileOutputStream(target);
                try {
                    copyStream(zip, out);
                } finally {
                    out.close();
                }
            }
        } finally {
            zip.close();
        }
    }

    private static String sanitizeId(String raw) {
        if (raw == null) return "plugin";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String clean = sb.toString();
        if (clean.isEmpty() || ".".equals(clean) || "..".equals(clean)) {
            return "plugin";
        }
        return clean;
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
    }

    private static byte[] readStreamFully(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copyStream(in, out);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void copyRecursively(File from, File to) throws Exception {
        to.mkdirs();
        File[] list = from.listFiles();
        if (list == null) return;
        for (File child : list) {
            if (child.isDirectory()) continue;
            FileInputStream in = new FileInputStream(child);
            FileOutputStream out = new FileOutputStream(new File(to, child.getName()));
            try {
                copyStream(in, out);
            } finally {
                in.close();
                out.close();
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
