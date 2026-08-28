package com.aicr.gateway.hook;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AICRHook performs deeper introspection on the AICR core service
 * to locate sub-service plugin instances (Vision, NER, Segment)
 * after MainHook has captured the core service binder or instance.
 */
public class AICRHook {

    private static final String TAG = "AICR_Gateway";

    // Corrected class path based on dumpsys package output
    private static final String CORE_SERVICE_CLASS = "com.xiaomi.aicr.access.AiCrCoreService";

    /**
     * Called after MainHook captures the IBinder from AiCrCoreService.onBind().
     * Attempts to hook sub-service plugin initialization methods.
     *
     * @param binder      the IBinder returned by AiCrCoreService.onBind()
     * @param classLoader the class loader of the AICR application
     */
    public static void hookSubServices(IBinder binder, ClassLoader classLoader) {
        XposedBridge.log(TAG + ": hookSubServices() invoked, binder=" + binder.getClass().getName());

        // Log the binder's interface descriptor
        try {
            Method getDescriptor = binder.getClass().getMethod("getInterfaceDescriptor");
            String descriptor = (String) getDescriptor.invoke(binder);
            XposedBridge.log(TAG + ": Binder interface descriptor: " + descriptor);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Could not get interface descriptor: " + t.getMessage());
        }

        // Discover AIDL stubs by scanning the classloader
        discoverServiceInterfaces(classLoader);

        // Scan for plugin classes
        scanPluginClasses(classLoader);
    }

    /**
     * Scans the classloader for AIDL Stub classes that might be sub-service interfaces.
     * Looks for classes matching known patterns (IVisionService, INerService, etc.).
     */
    private static void discoverServiceInterfaces(ClassLoader classLoader) {
        // Try common AIDL interface names
        String[] possibleNames = {
            "com.xiaomi.aicr.service.IVisionService$Stub",
            "com.xiaomi.aicr.service.INerService$Stub",
            "com.xiaomi.aicr.service.IImageSegmentService$Stub",
            "com.xiaomi.aicr.IVisionService$Stub",
            "com.xiaomi.aicr.INerService$Stub",
            "com.xiaomi.aicr.IImageSegmentService$Stub",
            "com.xiaomi.aicr.access.IVisionService$Stub",
            "com.xiaomi.aicr.access.INerService$Stub",
            "com.xiaomi.aicr.access.IImageSegmentService$Stub",
            "com.xiaomi.aicr.service.ICoreService$Stub",
            "com.xiaomi.aicr.ICoreService$Stub",
            "com.xiaomi.aicr.access.ICoreService$Stub",
            "com.xiaomi.aicr.service.IAiCrService$Stub",
            "com.xiaomi.aicr.IAiCrService$Stub",
            "com.xiaomi.aicr.access.IAiCrService$Stub",
        };

        for (String name : possibleNames) {
            try {
                Class<?> clazz = Class.forName(name, false, classLoader);
                XposedBridge.log(TAG + ": Found AIDL stub: " + name);
            } catch (ClassNotFoundException ignored) {
                // Expected - most won't exist
            }
        }
    }

    /**
     * Scans the classloader for known plugin class patterns.
     */
    private static void scanPluginClasses(ClassLoader classLoader) {
        String[] possiblePlugins = {
            "com.xiaomi.aicr.plugin.VisionPlugIn",
            "com.xiaomi.aicr.plugin.VisionPlugin",
            "com.xiaomi.aicr.plugin.SegmentPlugin",
            "com.xiaomi.aicr.plugin.SegmentPlugIn",
            "com.xiaomi.aicr.plugin.NerPlugin",
            "com.xiaomi.aicr.plugin.NerPlugIn",
            "com.xiaomi.aicr.plugin.OCRPlugin",
            "com.xiaomi.aicr.plugin.OCRPlugIn",
            "com.xiaomi.aicr.access.VisionPlugIn",
            "com.xiaomi.aicr.access.SegmentPlugin",
            "com.xiaomi.aicr.access.NerPlugin",
        };

        for (String name : possiblePlugins) {
            try {
                Class<?> clazz = Class.forName(name, false, classLoader);
                XposedBridge.log(TAG + ": Found plugin class: " + name);
            } catch (ClassNotFoundException ignored) {
                // Expected
            }
        }
    }
}
