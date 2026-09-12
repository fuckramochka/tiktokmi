package kotlin.jvm.functions;

/** For the compiler only; never reaches the dex. */
public interface Function1<P1, R> {
    R invoke(P1 p1);
}
