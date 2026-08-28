package com.aicr.gateway;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import com.aicr.gateway.hook.AICRHook;
import com.aicr.gateway.hook.ServiceProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "AICR_Gateway";
    private static final String TARGET_PACKAGE = "com.xiaomi.aicr";
    private static final String CORE_SERVICE_CLASS = "com.xiaomi.aicr.access.AiCrCoreService";
    private volatile boolean serverStarted = false;
    private ClassLoader appClassLoader;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": Loaded in package " + lpparam.packageName);
        this.appClassLoader = lpparam.classLoader;

        // Strategy 1: Hook AiCrCoreService lifecycle
        hookCoreService(lpparam);

        // Strategy 2: Hook Application to get context and self-bind
        hookApplication(lpparam);

        // Strategy 3: Scan ActivityThread for already-running services
        scanExistingServices();

        // Start HTTP server
        startHttpServer();
    }

    private void hookCoreService(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook onCreate to capture the service instance
        try {
            XposedHelpers.findAndHookMethod(
                CORE_SERVICE_CLASS, lpparam.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object service = param.thisObject;
                        XposedBridge.log(TAG + ": AiCrCoreService.onCreate() captured");
                        ServiceProxy.getInstance().setCoreServiceInstance(service, appClassLoader);
                    }
                }
            );
            XposedBridge.log(TAG + ": Hooked AiCrCoreService.onCreate()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook onCreate: " + t.getMessage());
        }

        // Hook onBind to capture the binder
        try {
            XposedHelpers.findAndHookMethod(
                CORE_SERVICE_CLASS, lpparam.classLoader, "onBind",
                Intent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.getThrowable() != null) return;
                        IBinder binder = (IBinder) param.getResult();
                        if (binder != null) {
                            XposedBridge.log(TAG + ": Captured binder from onBind: " + binder.getClass().getName());
                            ServiceProxy.getInstance().setCoreServiceBinder(binder, appClassLoader);
                            AICRHook.hookSubServices(binder, appClassLoader);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + ": Hooked AiCrCoreService.onBind()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook onBind: " + t.getMessage());
        }
    }

    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = (Context) param.thisObject;
                        XposedBridge.log(TAG + ": Application.onCreate() - attempting self-bind to service");
                        selfBindToService(ctx);

                        // Also try to scan ActivityThread again (services may have started by now)
                        scanExistingServices();
                    }
                }
            );
            XposedBridge.log(TAG + ": Hooked Application.onCreate()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook Application.onCreate(): " + t.getMessage());
        }
    }

    private void selfBindToService(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(TARGET_PACKAGE, CORE_SERVICE_CLASS));
            boolean bound = context.bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    XposedBridge.log(TAG + ": Self-bound to AiCrCoreService, binder: " + binder.getClass().getName());
                    ServiceProxy.getInstance().setCoreServiceBinder(binder, appClassLoader);
                    AICRHook.hookSubServices(binder, appClassLoader);
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    XposedBridge.log(TAG + ": Service disconnected");
                    ServiceProxy.reset();
                }
            }, Context.BIND_AUTO_CREATE);
            XposedBridge.log(TAG + ": bindService() returned: " + bound);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Self-bind failed: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void scanExistingServices() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAT = atClass.getDeclaredMethod("currentActivityThread");
            currentAT.setAccessible(true);
            Object activityThread = currentAT.invoke(null);
            if (activityThread == null) return;

            Field mServicesField = atClass.getDeclaredField("mServices");
            mServicesField.setAccessible(true);
            Object mServicesObj = mServicesField.get(activityThread);
            if (!(mServicesObj instanceof Map)) return;

            Map<?, ?> services = (Map<?, ?>) mServicesObj;
            for (Object svc : services.values()) {
                String name = svc.getClass().getName();
                if (name.contains("AiCrCore") || name.contains("access.AiCr")) {
                    XposedBridge.log(TAG + ": Found existing service instance: " + name);
                    ServiceProxy.getInstance().setCoreServiceInstance(svc, appClassLoader);
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Service scan failed: " + t.getMessage());
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
