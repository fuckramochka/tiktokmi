package kotlin.jvm.functions;

/** Заглушка для компиляции; в dex не попадает. */
public interface Function2<P1, P2, R> {
    R invoke(P1 p1, P2 p2);
}
