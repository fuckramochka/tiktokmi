package kotlin.jvm.functions;

/** Заглушка для компиляции; в dex не попадает. */
public interface Function1<P1, R> {
    R invoke(P1 p1);
}
