package io.github.nexalloy

import android.app.Application
import android.content.Context
import android.util.Log
import app.morphe.extension.shared.ResourceType
import app.morphe.extension.shared.ResourceUtils
import app.morphe.extension.shared.Utils
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.IXposedHookZygoteInit.StartupParam
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.nexalloy.common.UpdateChecker
import io.github.nexalloy.morphe.ResourceFinder
import io.github.nexalloy.morphe.resourceMappings

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    lateinit var startupParam: StartupParam
    lateinit var lpparam: LoadPackageParam
    lateinit var app: Application
    var targetPackageName: String? = null

    fun shouldHook(packageName: String): Boolean {
        // यूट्यूब को छोड़कर किसी अन्य ऐप को हुक न करें (YouTube Only Optimization)
        if (packageName != "com.google.android.youtube") return false
        
        if (!patchesByPackage.containsKey(packageName)) return false
        if (targetPackageName == null) targetPackageName = packageName
        return targetPackageName == packageName
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (!lpparam.isFirstApplication) return
        if (!shouldHook(lpparam.packageName)) return
        this.lpparam = lpparam

        inContext(lpparam) { app ->
            this.app = app
            if (isReVancedPatched(lpparam)) {
                Utils.showToastLong("NexAlloy module does not work with patched app")
                return@inContext
            }

            resourceMappings = object : ResourceFinder {
                override operator fun get(type: String, name: String): Int {
                    val id = ResourceUtils.getIdentifier(ResourceType.fromValue(type), name)
                    if (id == 0) throw Exception("Could not find resource type: $type name: $name")
                    return id
                }
            }

            val patches = patchesByPackage[lpparam.packageName] ?: return@inContext
            PatchExecutor(app, lpparam).applyPatches(patches)
        }
    }

    private fun isReVancedPatched(lpparam: LoadPackageParam): Boolean {
        return runCatching {
            lpparam.classLoader.loadClass("app.morphe.extension.shared.Utils")
        }.isSuccess || runCatching {
            lpparam.classLoader.loadClass("app.morphe.extension.shared.utils.Utils")
        }.isSuccess || runCatching {
            lpparam.classLoader.loadClass("app.revanced.integrations.shared.Utils")
        }.isSuccess || runCatching {
            lpparam.classLoader.loadClass("app.revanced.integrations.shared.utils.Utils")
        }.isSuccess
    }

    override fun initZygote(startupParam: StartupParam) {
        this.startupParam = startupParam
        XposedInit = startupParam
    }
}

fun inContext(lpparam: LoadPackageParam, f: (Application) -> Unit) {
    val appClazz = XposedHelpers.findClass(lpparam.appInfo.className, lpparam.classLoader)
    XposedBridge.hookMethod(appClazz.getMethod("onCreate"), object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val app = param.thisObject as Application
            Utils.setContext(app)
            f(app)
            if (XposedInit.modulePath.startsWith("/data/app/")) {
                val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "prefs")
                if (!prefs.file.canRead() || !prefs.getBoolean("disable_auto_check_update", false)) {
                    UpdateChecker().hookNewActivity()
                }
            }
        }
    })
}

// ==========================================
// इन-बिल्ट कैशे मैनेजर (DexKit Caching Object)
// ==========================================
object DexKitCache {
    private const val TAG = "NexAlloyCache"
    private const val PREFS_NAME = "nexalloy_dexkit_cache"
    private const val APP_VERSION_KEY = "target_app_version"

    // कैशे से पहले से सेव की गई क्लास/मेथड का नाम निकालना
    fun getCachedTarget(context: Context, key: String, currentAppVersion: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersion = prefs.getString(APP_VERSION_KEY, null)
        
        // यदि यूट्यूब का वर्जन बदल गया है, तो कैशे को साफ करें ताकि नए वर्जन में कोई गड़बड़ी न हो
        if (cachedVersion != currentAppVersion) {
            Log.d(TAG, "YouTube version changed from $cachedVersion to $currentAppVersion. Clearing cache.")
            prefs.edit().clear().putString(APP_VERSION_KEY, currentAppVersion).apply()
            return null
        }
        
        return prefs.getString(key, null)
    }

    // पहली बार मिलने वाले स्कैन रिजल्ट को यूट्यूब के प्राइवेट स्टोरेज में सेव करना
    fun saveTarget(context: Context, key: String, value: String, currentAppVersion: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(APP_VERSION_KEY, currentAppVersion)
            .putString(key, value)
            .apply()
        Log.d(TAG, "Successfully cached: $key -> $value")
    }
}
