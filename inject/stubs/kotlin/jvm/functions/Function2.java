package kotlin.jvm.functions;

/** For the compiler only; never reaches the dex. */
public interface Function2<P1, P2, R> {
    R invoke(P1 p1, P2 p2);
}
