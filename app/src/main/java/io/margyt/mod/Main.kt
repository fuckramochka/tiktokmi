package io.margyt.mod

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        /** Путь к APK самого модуля — нужен, чтобы достать свою иконку в чужом процессе. */
        @Volatile
        var modulePath: String? = null

        fun log(msg: String) = XposedBridge.log("[${Config.TAG}] $msg")
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lp: XC_LoadPackage.LoadPackageParam) {
        when {
            // внутри самого TikTok — подменяем регион
            lp.packageName in Config.TARGET_PACKAGES -> {
                log("region hooks -> ${lp.packageName}")
                runCatching { RegionHook.install(lp.classLoader) }
                    .onFailure { log("region hooks failed: $it") }
            }

            // в процессе лаунчера — подменяем название и иконку ярлыка
            else -> {
                runCatching { AppearanceHook.install(lp.classLoader) }
                    .onFailure { log("appearance hooks failed in ${lp.packageName}: $it") }
            }
        }
    }
}
