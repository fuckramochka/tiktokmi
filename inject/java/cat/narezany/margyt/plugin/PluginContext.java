package cat.narezany.margyt.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The mod, from a plugin's side.
 *
 * A plugin gets one of these before its first hook and keeps it. Everything a
 * plugin is likely to want that it should not have to find for itself: the
 * application context, somewhere to write, settings of its own that no other
 * plugin can collide with, and a line in the mod's diary.
 */
public final class PluginContext {

    private final Context context;
    private final String id;
    private final File folder;
    private final Diarist diarist;

    /** How the loader writes into the mod's diary without exporting it. */
    public interface Diarist {
        void note(String line);
    }

    public PluginContext(Context context, String id, File folder, Diarist diarist) {
        this.context = context;
        this.id = id;
        this.folder = folder;
        this.diarist = diarist;
    }

    /** The application context. Never an activity, so it is safe to keep. */
    public Context context() {
        return context;
    }

    /** The plugin's own id, as its manifest spells it. */
    public String id() {
        return id;
    }

    /** Where the plugin was unpacked: its own files are here, read-only. */
    public File folder() {
        return folder;
    }

    /** Settings of the plugin's own, in a file named after its id. */
    public SharedPreferences prefs() {
        return context.getSharedPreferences("margyt_plugin_" + id, Context.MODE_PRIVATE);
    }

    /**
     * A line in the mod's diary, which the person can read and copy out of the
     * settings screen. This is the plugin's way of saying anything at all: an
     * app repacked from a release has no log anyone is watching.
     */
    public void log(String line) {
        diarist.note(id + ": " + line);
    }
}
