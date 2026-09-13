package cat.narezany.margyt.plugin;

import android.app.Activity;
import android.content.Context;

/**
 * What a MargyT plugin extends.
 *
 * Everything here does nothing by default, so a plugin overrides the one or two
 * hooks it cares about and ignores the rest. New hooks can be added to this
 * class without breaking a plugin that was written before they existed, which
 * is the whole reason it is a class rather than an interface.
 *
 * A plugin's code is loaded into TikTok's own process, with this mod's classes
 * as its parent, so it can call anything in `cat.narezany.margyt` and anything
 * the app itself can reach. There is no sandbox and there cannot be one: read
 * what you install.
 *
 * Every hook is called with whatever thread the app happens to be on -- the
 * activity ones on the main thread, `onStart` before the application's own
 * onCreate. Anything slow belongs on a thread of the plugin's own. A hook that
 * throws is caught, written to the diary and never called again in that
 * process; it does not take TikTok down.
 *
 * See docs/plugins.md.
 */
public abstract class MargyPlugin {

    /**
     * The mod's API version, raised whenever a hook changes shape.
     *
     * A plugin declares the oldest one it works with in its manifest, as
     * `min_api`, and the loader refuses it rather than calling a hook that no
     * longer means what the plugin thought.
     */
    public static final int API = 1;

    private PluginContext margyt;

    /** Set by the loader before anything else is called. */
    public final void attach(PluginContext context) {
        this.margyt = context;
    }

    /** The mod, from the plugin's side: settings, the diary, the app context. */
    public final PluginContext margyt() {
        return margyt;
    }

    // ------------------------------------------------------------ the hooks

    /**
     * The process has started and the mod is up.
     *
     * This runs inside the mod's start-up provider, before TikTok's own
     * Application.onCreate: early enough to get in front of most things, and
     * early enough that most of the app does not exist yet.
     */
    public void onStart(Context context) {}

    /** An activity was created. */
    public void onActivityCreated(Activity activity) {}

    /** An activity came to the front. Called for every screen, so keep it cheap. */
    public void onActivityResumed(Activity activity) {}

    /** An activity went away. */
    public void onActivityPaused(Activity activity) {}

    /**
     * A colour is on its way to the screen.
     *
     * Every colour the mod redirects -- the constants in the bytecode, whatever
     * comes back from the framework -- passes through here after the accent has
     * had its say. Return `colour` to leave it alone. This sits in the drawing
     * path of half the app: no allocation, no lookups, no logging.
     */
    public int onColour(int colour) {
        return colour;
    }

    /**
     * An answer about where the phone is, on its way back to TikTok.
     *
     * `key` is one of `sim_country`, `network_country`, `sim_operator`,
     * `network_operator`, `sim_operator_name`, `network_operator_name`.
     * `value` is what the mod was about to answer -- the phone's own answer
     * when the region switch is off, the chosen country's when it is on.
     * Return `value` to leave it alone.
     */
    public String onRegion(String key, String value) {
        return value;
    }

    /** The plugin was switched off, or the app is going down. */
    public void onStop() {}
}
