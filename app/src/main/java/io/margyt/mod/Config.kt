package io.margyt.mod

object Config {

    /**
     * Пакеты, в которых работают хуки региона.
     * cat.narezany.tiktok — наш репак; остальные два оставлены,
     * чтобы модуль можно было проверить на нетронутом TikTok.
     */
    val TARGET_PACKAGES = setOf(
        "cat.narezany.tiktok",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
    )

    /** Название под иконкой на рабочем столе. */
    const val APP_LABEL = "MargyT"

    // ---- Регион: Нидерланды ----
    // Другие NL-операторы, если KPN не устроит:
    //   Vodafone NL = 20404, Odido/T-Mobile NL = 20416, Lebara NL = 20409
    const val ISO_COUNTRY = "nl"
    const val MCC_MNC = "20408"
    const val CARRIER_NAME = "KPN"
    const val TIMEZONE = "Europe/Amsterdam"

    /** Менять только страну в Locale, язык интерфейса не трогать. */
    const val SPOOF_LOCALE_REGION = true

    /** Подменять таймзону внутри приложения. По умолчанию выкл — палится редко, но ломает время постов. */
    const val SPOOF_TIMEZONE = false

    /** Системные пропертя, которые читают через android.os.SystemProperties. */
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
