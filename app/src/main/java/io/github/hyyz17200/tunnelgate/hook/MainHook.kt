package io.github.hyyz17200.tunnelgate.hook

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android" || lpparam.processName != "android") return

        XposedBridge.log("TunnelGate: loaded android/system_server")

        try {
            val clazz = XposedHelpers.findClass("com.android.server.SystemServer", lpparam.classLoader)
            val targets = clazz.declaredMethods.filter { it.name == "startOtherServices" }

            if (targets.isEmpty()) {
                XposedBridge.log("TunnelGate: no startOtherServices method found on ${clazz.name}")
                return
            }

            for (method in targets) {
                XposedBridge.log("TunnelGate: hooking ${describe(method)}")
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val context = XposedHelpers.getObjectField(param.thisObject, "mSystemContext") as? Context ?: return
                            NetPolicyEngine.init(context)
                        } catch (t: Throwable) {
                            XposedBridge.log("TunnelGate: init failed $t")
                        }
                    }
                })
            }
        } catch (t: Throwable) {
            XposedBridge.log("TunnelGate: hook failed $t")
        }
    }

    private fun describe(method: Method): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        return "${method.declaringClass.name}#${method.name}($params)"
    }
}
