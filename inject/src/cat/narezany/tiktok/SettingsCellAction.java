package cat.narezany.tiktok;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Field;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * What happens when the MargyT row in TikTok's settings list is tapped.
 *
 * Rows are handed an event object -- LX/0Ayy in this build -- whose field LIZ
 * is the Context and LIZIZ a string key. Those names are obfuscated and move
 * between releases, so the Context is found by walking the fields and matching
 * on type rather than by casting: a miss then leaves the row inert instead of
 * crashing the app.
 */
public final class SettingsCellAction implements Function1<Object, Object> {

    @Override
    public Object invoke(Object event) {
        try {
            Context ctx = findContext(event);
            if (ctx != null) {
                Intent i = new Intent(ctx, MargyTSettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            }
        } catch (Throwable ignored) {
        }
        return Unit.LIZ;
    }

    /** The first public field on the event whose type is a Context. */
    private static Context findContext(Object event) {
        if (event == null) return null;
        for (Field f : event.getClass().getFields()) {
            if (!Context.class.isAssignableFrom(f.getType())) continue;
            try {
                Object v = f.get(event);
                if (v instanceof Context) return (Context) v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
