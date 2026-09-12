package kotlin;

/**
 * Заглушка ТОЛЬКО для компиляции. В dex не попадает — настоящий kotlin.Unit
 * уже лежит в самом TikTok, и наши классы слинкуются с ним в рантайме.
 *
 * Поле named LIZ, а не INSTANCE: в этой сборке TikTok kotlin.Unit прогнан
 * через обфускатор, и синглтон там называется LIZ. Промах по имени
 * компилируется молча, а падает уже на устройстве.
 */
public final class Unit {
    public static final Unit LIZ = new Unit();
    private Unit() {}
}
