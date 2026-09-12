package cat.narezany.tiktok;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Field;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Обработчик нажатия на ячейку «Настройки MargyT» в списке настроек TikTok.
 *
 * Ячейки получают событие объектом класса LX/0Ayy, у которого поле LIZ — это
 * Context, а LIZIZ — строковый ключ. Имена обфусцированы и меняются от версии
 * к версии, поэтому Context достаётся рефлексией с перебором кандидатов, а не
 * приведением к типу: промах по имени тогда не роняет приложение, а просто
 * оставляет ячейку неактивной.
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

    /** Первое попавшееся поле типа Context среди публичных полей события. */
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
