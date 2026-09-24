package mi.tiktokmi.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The execution context handed to a TikTok MI plugin.
 *
 * Provides isolated storage, mod state, diary logging, and access
 * to the Android application context.
 */
public class MiPluginContext {

    private final Context context;
    private final String id;
    private final String name;
    private final String version;
    private final File folder;
    private final Diarist diarist;
    private final HostBridge bridge;

    /** Hook into the TikTok MI internal diary without exposing internals. */
    public interface Diarist {
        void note(String line);
    }

    /** Bridge to TikTok MI runtime configuration and theme state. */
    public interface HostBridge {
        int getAccentColour();
        boolean isGhostMode();
        boolean isDarkTheme();
    }

    public MiPluginContext(Context context, String id, String name, String version,
                           File folder, Diarist diarist) {
        this(context, id, name, version, folder, diarist, null);
    }

    public MiPluginContext(Context context, String id, String name, String version,
                           File folder, Diarist diarist, HostBridge bridge) {
        this.context = context;
        this.id = id;
        this.name = name;
        this.version = version;
        this.folder = folder;
        this.diarist = diarist;
        this.bridge = bridge;
    }

    /** The Android application context. Safe to retain across activity lifecycles. */
    public Context context() {
        return context;
    }

    /** The unique identifier of this plugin as defined in manifest.json. */
    public String id() {
        return id;
    }

    /** The friendly name of this plugin. */
    public String name() {
        return name;
    }

    /** The version string of this plugin. */
    public String version() {
        return version;
    }

    /** The directory containing unpacked plugin assets and files (read-only). */
    public File folder() {
        return folder;
    }

    /** Isolated SharedPreferences dedicated exclusively to this plugin. */
    public SharedPreferences prefs() {
        return context.getSharedPreferences("tiktokmi_plugin_" + id, Context.MODE_PRIVATE);
    }

    /** Write an entry to the TikTok MI user diary. */
    public void log(String line) {
        if (diarist != null) {
            diarist.note("[" + id + "] " + line);
        }
    }

    /** Write an exception to the TikTok MI user diary. */
    public void log(Throwable error) {
        if (diarist != null && error != null) {
            diarist.note("[" + id + "] error: " + error.getMessage());
        }
    }

    /** Current dynamic UI accent colour in ARGB format. */
    public int accentColour() {
        return bridge != null ? bridge.getAccentColour() : 0xFFFE2C55;
    }

    /** Whether TikTok MI Ghost Mode is enabled. */
    public boolean isGhostMode() {
        return bridge != null && bridge.isGhostMode();
    }

    /** Whether TikTok MI Dark Theme is currently active. */
    public boolean isDarkTheme() {
        return bridge != null && bridge.isDarkTheme();
    }
}
