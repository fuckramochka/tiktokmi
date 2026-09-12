package io.margyt.mod

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Main : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        /** Path to the module's own apk, needed to load our icon in another process. */
        @Volatile
        var modulePath: String? = null

        fun log(msg: String) = XposedBridge.log("[${Config.TAG}] $msg")
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lp: XC_LoadPackage.LoadPackageParam) {
        when {
            // inside TikTok itself: spoof the region
            lp.packageName in Config.TARGET_PACKAGES -> {
                log("region hooks -> ${lp.packageName}")
                runCatching { RegionHook.install(lp.classLoader) }
                    .onFailure { log("region hooks failed: $it") }
            }

            // inside the launcher: swap the label and the icon
            else -> {
                runCatching { AppearanceHook.install(lp.classLoader) }
                    .onFailure { log("appearance hooks failed in ${lp.packageName}: $it") }
            }
        }
    }
}
