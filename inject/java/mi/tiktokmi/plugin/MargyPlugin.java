package mi.tiktokmi.plugin;

/**
 * Backward-compatibility adapter extending MiPlugin.
 *
 * All legacy plugins extending MargyPlugin run seamlessly on the
 * redesigned TikTok MI plugin engine.
 * New plugins should extend MiPlugin directly.
 */
public abstract class MargyPlugin extends MiPlugin {

    public static final int API = MiPlugin.API;

    private PluginContext legacyContext;

    @Override
    public PluginContext tiktokmi() {
        if (legacyContext == null && context() != null) {
            legacyContext = new PluginContext(
                    context().context(),
                    context().id(),
                    context().folder(),
                    new PluginContext.Diarist() {
                        @Override
                        public void note(String line) {
                            context().log(line);
                        }
                    }
            );
        }
        return legacyContext;
    }
}
