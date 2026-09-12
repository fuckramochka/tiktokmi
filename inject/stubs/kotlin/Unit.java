package kotlin;

/**
 * For the compiler only. This never reaches the dex -- the real kotlin.Unit is
 * already inside TikTok, and our classes link against that one at runtime.
 *
 * The field is LIZ, not INSTANCE: kotlin.Unit went through the obfuscator in
 * this build and the singleton came out renamed. Writing INSTANCE compiles
 * quietly and fails on the device.
 */
public final class Unit {
    public static final Unit LIZ = new Unit();
    private Unit() {}
}
