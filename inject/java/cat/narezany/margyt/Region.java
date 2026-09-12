package cat.narezany.margyt;

import android.telephony.TelephonyManager;

/**
 * Where the rewritten call sites land.
 *
 * Every
 *     invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
 * in TikTok's bytecode becomes
 *     invoke-static  {v0}, Lcat/narezany/margyt/Region;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
 *
 * Same instruction format, same register count, same return type: the receiver
 * simply becomes the first argument, so nothing around the call has to be
 * renumbered. Each method here takes that receiver and hands it straight back
 * the real answer whenever the mod is off -- switching MargyT off has to leave
 * the app exactly as it was, down to the exception the real call would have
 * thrown.
 */
public final class Region {

    private Region() {}

    public static String getSimCountryIso(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getSimCountryIso();
        return Margy.current()[Margy.ISO];
    }

    public static String getNetworkCountryIso(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getNetworkCountryIso();
        return Margy.current()[Margy.ISO];
    }

    public static String getSimOperator(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getSimOperator();
        return Margy.current()[Margy.MCCMNC];
    }

    public static String getNetworkOperator(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getNetworkOperator();
        return Margy.current()[Margy.MCCMNC];
    }

    public static String getSimOperatorName(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getSimOperatorName();
        return Margy.current()[Margy.CARRIER];
    }

    public static String getNetworkOperatorName(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? "" : tm.getNetworkOperatorName();
        return Margy.current()[Margy.CARRIER];
    }

    /**
     * A card that is present and ready. Without this, a phone with no SIM keeps
     * telling the app so, and half of what reads the country never asks.
     */
    public static int getSimState(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? TelephonyManager.SIM_STATE_UNKNOWN : tm.getSimState();
        return TelephonyManager.SIM_STATE_READY;
    }

    public static int getSimState(TelephonyManager tm, int slot) {
        if (!Margy.active()) {
            return tm == null ? TelephonyManager.SIM_STATE_UNKNOWN : tm.getSimState(slot);
        }
        return slot == 0 ? TelephonyManager.SIM_STATE_READY : TelephonyManager.SIM_STATE_UNKNOWN;
    }

    public static boolean hasIccCard(TelephonyManager tm) {
        if (!Margy.active()) return tm != null && tm.hasIccCard();
        return true;
    }

    /** Roaming would tell the app the SIM's country and the network's disagree. */
    public static boolean isNetworkRoaming(TelephonyManager tm) {
        if (!Margy.active()) return tm != null && tm.isNetworkRoaming();
        return false;
    }

    /**
     * The carrier id is a number in Google's own carrier list, and there is no
     * honest way to pick one for a carrier we are only claiming to be on.
     * Unknown is what a phone says when the list has no answer either.
     */
    public static int getSimCarrierId(TelephonyManager tm) {
        if (!Margy.active()) return tm == null ? -1 : tm.getSimCarrierId();
        return TelephonyManager.UNKNOWN_CARRIER_ID;
    }
}
