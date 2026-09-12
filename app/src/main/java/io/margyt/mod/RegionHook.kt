package io.margyt.mod

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Locale
import java.util.TimeZone

/**
 * Подмена страны SIM на уровне фреймворка: приложение видит NL-оператора,
 * NL-пропертя и NL-регион в Locale.
 */
object RegionHook {

    fun install(cl: ClassLoader) {
        hookTelephony(cl)
        hookSystemProperties(cl)
        applyDefaults()          // на случай, если что-то читается до Application.attach
        hookApplicationAttach(cl)
    }

    private fun hookTelephony(cl: ClassLoader) {
        val tm = XposedHelpers.findClass("android.telephony.TelephonyManager", cl)

        val constants = mapOf(
            "getSimCountryIso" to Config.ISO_COUNTRY,
            "getNetworkCountryIso" to Config.ISO_COUNTRY,
            "getSimOperator" to Config.MCC_MNC,
            "getNetworkOperator" to Config.MCC_MNC,
            "getSimOperatorName" to Config.CARRIER_NAME,
            "getNetworkOperatorName" to Config.CARRIER_NAME,
        )
        // hookAllMethods накрывает и скрытые перегрузки с subId
        constants.forEach { (name, value) ->
            XposedBridge.hookAllMethods(tm, name, XC_MethodReplacement.returnConstant(value))
        }

        // Без этого на устройстве без SIM приложение уйдёт в ветку "SIM нет"
        // и всё вышеперечисленное просто не прочитает.
        XposedBridge.hookAllMethods(
            tm, "getSimState",
            XC_MethodReplacement.returnConstant(TelephonyManager.SIM_STATE_READY)
        )
        XposedBridge.hookAllMethods(tm, "hasIccCard", XC_MethodReplacement.returnConstant(true))
        XposedBridge.hookAllMethods(tm, "isNetworkRoaming", XC_MethodReplacement.returnConstant(false))
        XposedBridge.hookAllMethods(
            tm, "getPhoneType",
            XC_MethodReplacement.returnConstant(TelephonyManager.PHONE_TYPE_GSM)
        )
    }

    private fun hookSystemProperties(cl: ClassLoader) {
        val sp = XposedHelpers.findClass("android.os.SystemProperties", cl)
        XposedBridge.hookAllMethods(sp, "get", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args.getOrNull(0) as? String ?: return
                Config.SYSTEM_PROPS[key]?.let { param.result = it }
            }
        })
    }

    private fun hookApplicationAttach(cl: ClassLoader) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java, "attach", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) = applyDefaults()
                }
            )
        }
    }

    private fun applyDefaults() {
        if (Config.SPOOF_LOCALE_REGION) {
            runCatching {
                val current = Locale.getDefault()
                if (!current.country.equals(Config.ISO_COUNTRY, ignoreCase = true)) {
                    // язык оставляем как есть — меняем только страну,
                    // иначе интерфейс уедет на нидерландский
                    Locale.setDefault(
                        Locale.Builder()
                            .setLanguage(current.language.ifEmpty { "en" })
                            .setRegion(Config.ISO_COUNTRY.uppercase())
                            .build()
                    )
                }
            }
        }
        if (Config.SPOOF_TIMEZONE) {
            runCatching { TimeZone.setDefault(TimeZone.getTimeZone(Config.TIMEZONE)) }
        }
    }
}
