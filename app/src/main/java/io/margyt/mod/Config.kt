package io.margyt.mod

object Config {

    /**
     * Packages the region hooks apply to.
     * cat.narezany.tiktok is our repack; the other two are kept so the module
     * can be tried against an untouched TikTok.
     */
    val TARGET_PACKAGES = setOf(
        "cat.narezany.tiktok",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
    )

    /** The name under the icon on the home screen. */
    const val APP_LABEL = "MargyT"

    // ---- Region: Netherlands ----
    // Other NL carriers, if KPN does not suit:
    //   Vodafone NL = 20404, Odido/T-Mobile NL = 20416, Lebara NL = 20409
    const val ISO_COUNTRY = "nl"
    const val MCC_MNC = "20408"
    const val CARRIER_NAME = "KPN"
    const val TIMEZONE = "Europe/Amsterdam"

    /** Country only; the interface language is left alone. */
    const val SPOOF_LOCALE_REGION = true

    /** Off by default: moving the clock moves the timestamps on posts. */
    const val SPOOF_TIMEZONE = false

    /** Properties read through android.os.SystemProperties. */
    val SYSTEM_PROPS: Map<String, String> = mapOf(
        "gsm.sim.operator.iso-country" to ISO_COUNTRY,
        "gsm.operator.iso-country" to ISO_COUNTRY,
        "gsm.sim.operator.numeric" to MCC_MNC,
        "gsm.operator.numeric" to MCC_MNC,
        "gsm.sim.operator.alpha" to CARRIER_NAME,
        "gsm.operator.alpha" to CARRIER_NAME,
        "persist.sys.country" to ISO_COUNTRY.uppercase(),
        "ro.csc.countryiso_code" to ISO_COUNTRY.uppercase(),
    )

    const val TAG = "MargyT"
}
