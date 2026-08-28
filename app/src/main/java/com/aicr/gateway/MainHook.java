package com.aicr.gateway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.os.IBinder;
import com.aicr.gateway.hook.AICRHook;
import com.aicr.gateway.hook.ServiceProxy;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "AICR_Gateway";
    private static final String TARGET_PACKAGE = "com.xiaomi.aicr";
    private volatile boolean serverStarted = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": Loaded in package " + lpparam.packageName);
        hookCoreService(lpparam);
        startHttpServer();
    }

    private void hookCoreService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.xiaomi.aicr.AiCrCoreService", lpparam.classLoader, "onBind",
                android.content.Intent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.getThrowable() != null) return;
                        IBinder binder = (IBinder) param.getResult();
                        if (binder != null) {
                            XposedBridge.log(TAG + ": Captured binder: " + binder.getClass().getName());
                            ServiceProxy.getInstance().setCoreServiceBinder(binder);
                            AICRHook.hookSubServices(binder, lpparam.classLoader);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + ": Hooked AiCrCoreService.onBind()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook: " + t.getMessage());
        }
    }

    private void startHttpServer() {
        if (serverStarted) return;
        synchronized (this) {
            if (serverStarted) return;
            Thread serverThread = new Thread(() -> {
                try {
                    new com.aicr.gateway.server.ApiServer(8080).start();
                    XposedBridge.log(TAG + ": HTTP server started on port 8080");
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": Failed to start server: " + e.getMessage());
                }
            }, "AICR-Gateway-HTTP");
            serverThread.setDaemon(true);
            serverThread.start();
            serverStarted = true;
        }
    }
}