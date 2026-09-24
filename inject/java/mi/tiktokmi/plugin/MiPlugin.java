package mi.tiktokmi.plugin;

import android.app.Activity;
import android.content.Context;
import android.widget.TextView;

/**
 * Base class for all TikTok MI plugins.
 *
 * Designed from the ground up specifically for TikTok MI.
 *
 * Each plugin runs in the host TikTok process with full access to
 * TikTok MI services and Android runtime APIs.
 *
 * Plugins override only the hooks they need. All methods have safe
 * default no-op implementations.
 */
public abstract class MiPlugin {

    /**
     * The TikTok MI Plugin API specification level.
     * Version 2 introduces rich interceptors: onFlag, onText, onDirectMessage,
     * and modern context accessors.
     */
    public static final int API = 2;

    private MiPluginContext context;

    /** Called by the TikTok MI loader immediately upon instantiation. */
    public final void attach(MiPluginContext context) {
        this.context = context;
    }

    /** Access plugin context, preferences, state, and logging. */
    public final MiPluginContext context() {
        return context;
    }

    /** Alias for context() maintained for backwards compatibility. */
    public MiPluginContext tiktokmi() {
        return context;
    }

    // ------------------------------------------------------------- Lifecycle

    /**
     * Triggered early during process bootstrap inside TikTok MI initialization provider,
     * before TikTok's main Application.onCreate runs.
     */
    public void onStart(Context context) {}

    /**
     * Triggered when the plugin is turned off in settings or when the process shuts down.
     */
    public void onStop() {}

    /** An Android Activity was created. */
    public void onActivityCreated(Activity activity) {}

    /** An Android Activity came to the foreground. */
    public void onActivityResumed(Activity activity) {}

    /** An Android Activity went into the background. */
    public void onActivityPaused(Activity activity) {}

    // ---------------------------------------------------------- Interceptors

    /**
     * Intercepts dynamic UI colours and themes.
     * Return colour unchanged or return your transformed ARGB color.
     */
    public int onColour(int colour) {
        return colour;
    }

    /**
     * Intercepts SIM/Network country and carrier queries.
     * key: "sim_country", "network_country", "sim_operator", "network_operator", etc.
     */
    public String onRegion(String key, String value) {
        return value;
    }

    /**
     * Intercepts TikTok AB experimentation flags and settings.
     * Return null to leave default behavior, or Boolean.TRUE / Boolean.FALSE to override.
     */
    public Boolean onFlag(String key, Boolean current) {
        return current;
    }

    /**
     * Intercepts text before it is displayed in any TextView.
     * Return original text or a custom transformed CharSequence.
     */
    public CharSequence onText(TextView view, CharSequence text) {
        return text;
    }

    /**
     * Intercepts direct messages in chats.
     * Return original message text or a modified CharSequence.
     */
    public CharSequence onDirectMessage(TextView view, CharSequence text) {
        return text;
    }
}
