package cat.narezany.tiktok;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

/**
 * The second lambda a settings row's state carries.
 *
 * LanguageVM passes a generated dispatcher there; what it is for is not obvious
 * and our row has no use for it. Passing null instead would work only if every
 * caller happens to null-check, so this does nothing and returns cleanly.
 */
public final class SettingsCellNoop implements Function2<Object, Object, Object> {

    @Override
    public Object invoke(Object a, Object b) {
        return Unit.LIZ;
    }
}
