package mi.tiktokmi.plugin;

import android.content.Context;
import java.io.File;

/**
 * Compatibility adapter extending MiPluginContext for legacy plugins.
 */
public class PluginContext extends MiPluginContext {

    public PluginContext(Context context, String id, File folder, final Diarist diarist) {
        super(context, id, id, "1.0", folder, diarist);
    }
}
