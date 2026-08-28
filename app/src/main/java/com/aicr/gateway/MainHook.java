package com.aicr.gateway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.os.IBinder;

import com.aicr.gateway.hook.AICRHook;
import com.aicr.gateway.hook.ServiceProxy;

/**
 * LSPosed entry point for the AICR API Gateway module.
 *
 * This class hooks into the AICR application (com.xiaomi.aicr) at load time,
 * intercepts the core service binder via AiCrCoreService.onBind(), and starts
 * an HTTP server that exposes AICR capabilities through a REST API.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "AICR_Gateway";
    private static final String TARGET_PACKAGE = "com.xiaomi.aicr";

    private volatile boolean serverStarted = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Only hook into the AICR application process
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Loaded in package " + lpparam.packageName);

        // Hook AiCrCoreService.onBind() to intercept the service binder
        hookCoreService(lpparam);

        // Start the HTTP server in a separate daemon thread
        startHttpServer();
    }

    /**
     * Hooks AiCrCoreService.onBind(Intent) to capture the IBinder returned
     * by the core AICR service. The binder is stored in ServiceProxy so that
     * downstream service consumers (IVisionService, INerService, IImageSegmentService)
     * can be derived from it via reflection.
     */
    private void hookCoreService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String coreServiceClass = "com.xiaomi.aicr.AiCrCoreService";

            XposedHelpers.findAndHookMethod(
                    coreServiceClass,
                    lpparam.classLoader,
                    "onBind",
                    android.content.Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + ": AiCrCoreService.onBind() called");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getThrowable() != null) {
                                XposedBridge.log(TAG + ": AiCrCoreService.onBind() threw: "
                                        + param.getThrowable().getMessage());
                                return;
                            }

                            IBinder binder = (IBinder) param.getResult();
                            if (binder != null) {
                                XposedBridge.log(TAG + ": Captured core service binder: "
                                        + binder.getClass().getName());

                                // Store the binder in the singleton ServiceProxy
                                ServiceProxy.getInstance().setCoreServiceBinder(binder);

                                // Run additional hooking logic for sub-services
                                AICRHook.hookSubServices(binder, lpparam.classLoader);
                            } else {
                                XposedBridge.log(TAG + ": AiCrCoreService.onBind() returned null binder");
                            }
                        }
                    }
            );

            XposedBridge.log(TAG + ": Successfully hooked AiCrCoreService.onBind()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook AiCrCoreService: " + t.getMessage());
        }
    }

    /**
     * Starts the HTTP server on a separate daemon thread so it does not
     * block the main application thread or prevent process shutdown.
     */
    private void startHttpServer() {
        if (serverStarted) {
            return;
        }

        synchronized (this) {
            if (serverStarted) {
                return;
            }

            Thread serverThread = new Thread(() -> {
                try {
                    XposedBridge.log(TAG + ": Starting HTTP server thread");

                    // HttpServer is expected to bind to a local port (e.g., 8080)
                    // and serve REST endpoints that proxy requests to AICR services.
                    com.aicr.gateway.server.HttpServer server =
                            new com.aicr.gateway.server.HttpServer(8080);
                    server.start();

                    XposedBridge.log(TAG + ": HTTP server started on port 8080");
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": Failed to start HTTP server: " + e.getMessage());
                }
            }, "AICR-Gateway-HTTP");

            serverThread.setDaemon(true);
            serverThread.start();

            serverStarted = true;
            XposedBridge.log(TAG + ": HTTP server thread launched");
        }
    }
}
