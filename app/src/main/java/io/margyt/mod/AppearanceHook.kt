package io.margyt.mod

import android.content.pm.ApplicationInfo
import android.content.res.XModuleResources
import android.graphics.drawable.Drawable
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Работает В ПРОЦЕССЕ ЛАУНЧЕРА, а не тиктока: перехватывает чтение названия и
 * иконки для целевого пакета. Так не нужно трогать сам APK и ломать подпись.
 */
object AppearanceHook {

    private fun isTarget(pkg: String?) = pkg != null && pkg in Config.TARGET_PACKAGES

    private fun moduleIcon(): Drawable? {
        val path = Main.modulePath ?: return null
        return runCatching {
            XModuleResources.createInstance(path, null)
                .getDrawable(R.drawable.ic_margyt, null)
        }.getOrNull()
    }

    fun install(cl: ClassLoader) {
        hookLabels(cl)
        hookIcons(cl)
    }

    // ---------- название ----------

    private fun hookLabels(cl: ClassLoader) {
        // PackageItemInfo.loadLabel — база для ApplicationInfo и ActivityInfo
        hookAll(cl, "android.content.pm.PackageItemInfo", "loadLabel") { param ->
            val pkg = XposedHelpers.getObjectField(param.thisObject, "packageName") as? String
            if (isTarget(pkg)) param.result = Config.APP_LABEL
        }
        hookAll(cl, "android.content.pm.ApplicationInfo", "loadLabel") { param ->
            if (isTarget((param.thisObject as ApplicationInfo).packageName)) {
                param.result = Config.APP_LABEL
            }
        }
        // Современные лаунчеры ходят через LauncherApps
        hookAll(cl, "android.content.pm.LauncherActivityInfo", "getLabel") { param ->
            if (isTarget(launcherInfoPackage(param.thisObject))) param.result = Config.APP_LABEL
        }
        hookAll(cl, "android.app.ApplicationPackageManager", "getApplicationLabel") { param ->
            val info = param.args.getOrNull(0) as? ApplicationInfo ?: return@hookAll
            if (isTarget(info.packageName)) param.result = Config.APP_LABEL
        }
    }

    // ---------- иконка ----------

    private fun hookIcons(cl: ClassLoader) {
        val replaceIcon: (XC_MethodHook.MethodHookParam, String?) -> Unit = { param, pkg ->
            if (isTarget(pkg)) moduleIcon()?.let { param.result = it }
        }

        listOf("loadIcon", "loadUnbadgedIcon").forEach { method ->
            hookAll(cl, "android.content.pm.PackageItemInfo", method) { param ->
                val pkg = XposedHelpers.getObjectField(param.thisObject, "packageName") as? String
                replaceIcon(param, pkg)
            }
            hookAll(cl, "android.content.pm.ApplicationInfo", method) { param ->
                replaceIcon(param, (param.thisObject as ApplicationInfo).packageName)
            }
        }

        listOf("getIcon", "getBadgedIcon").forEach { method ->
            hookAll(cl, "android.content.pm.LauncherActivityInfo", method) { param ->
                replaceIcon(param, launcherInfoPackage(param.thisObject))
            }
        }

        listOf("getApplicationIcon", "getActivityIcon").forEach { method ->
            hookAll(cl, "android.app.ApplicationPackageManager", method) { param ->
                val pkg = when (val arg = param.args.getOrNull(0)) {
                    is String -> arg
                    is ApplicationInfo -> arg.packageName
                    else -> runCatching {
                        XposedHelpers.callMethod(arg, "getPackageName") as? String
                    }.getOrNull()
                }
                replaceIcon(param, pkg)
            }
        }
    }

    // ---------- утилиты ----------

    private fun launcherInfoPackage(info: Any): String? = runCatching {
        val component = XposedHelpers.callMethod(info, "getComponentName")
        XposedHelpers.callMethod(component, "getPackageName") as? String
    }.getOrNull()

    private inline fun hookAll(
        cl: ClassLoader,
        className: String,
        methodName: String,
        crossinline after: (XC_MethodHook.MethodHookParam) -> Unit,
    ) {
        runCatching {
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass(className, cl),
                methodName,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching { after(param) }
                    }
                }
            )
        }
    }
}
