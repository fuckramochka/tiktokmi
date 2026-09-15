package mi.tiktokmi.hello;

import android.content.Context;

import mi.tiktokmi.plugin.MargyPlugin;

/**
 * The smallest plugin that does something you can see.
 *
 * It counts the starts of the app in settings of its own and writes the count
 * into the mod's diary, which is where a plugin's output goes: a TikTok
 * repacked from a release has no log anyone is watching.
 *
 * Build it with
 *
 *     python3 -m tiktokmi.plugin examples/hello
 *
 * and install the .mtp through Settings and privacy -> TikTok MI -> Plugins.
 */
public final class Hello extends MargyPlugin {

    private static final String STARTS = "starts";

    @Override
    public void onStart(Context context) {
        int starts = tiktokmi().prefs().getInt(STARTS, 0) + 1;
        tiktokmi().prefs().edit().putInt(STARTS, starts).apply();
        tiktokmi().log("hello -- this is start number " + starts);
    }

    /**
     * Every colour the mod redirects comes through here. This one hands each
     * one straight back, which is what a plugin that does not care about
     * colours should do -- and it is on the drawing path, so it does it
     * without allocating anything.
     */
    @Override
    public int onColour(int colour) {
        return colour;
    }
}
