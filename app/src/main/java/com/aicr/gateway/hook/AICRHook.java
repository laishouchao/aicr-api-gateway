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
 * AICRHook is responsible for hooking into the AICR core service classes
 * and extracting sub-service plugin instances via reflection.
 *
 * After MainHook captures the IBinder from AiCrCoreService.onBind(), this
 * class performs deeper introspection to locate VisionPlugIn and SegmentPlugin
 * instances that are held by the core service, enabling the gateway to
 * interact with them directly when needed.
 */
public class AICRHook {

    private static final String TAG = "AICR_Gateway";

    // Known class names within the AICR application
    private static final String CORE_SERVICE_CLASS = "com.xiaomi.aicr.AiCrCoreService";
    private static final String VISION_PLUGIN_CLASS = "com.xiaomi.aicr.plugin.VisionPlugIn";
    private static final String SEGMENT_PLUGIN_CLASS = "com.xiaomi.aicr.plugin.SegmentPlugin";

    /**
     * Hooks additional sub-service methods after the core binder has been
     * captured by MainHook. This method is called once per binder capture
     * and sets up interception of VisionPlugIn and SegmentPlugin instances.
     *
     * @param binder      the IBinder returned by AiCrCoreService.onBind()
     * @param classLoader the class loader of the AICR application
     */
    public static void hookSubServices(IBinder binder, ClassLoader classLoader) {
        XposedBridge.log(TAG + ": hookSubServices() invoked");

        try {
            // Attempt to resolve plugin instances from the service binder
            hookVisionPlugin(classLoader);
            hookSegmentPlugin(classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error in hookSubServices: " + t.getMessage());
        }
    }

    /**
     * Uses reflection to locate and hook the VisionPlugIn instance within
     * the AICR core service. The VisionPlugIn provides vision-related
     * capabilities such as object detection, OCR, and image classification.
     *
     * @param classLoader the class loader of the AICR application
     */
    private static void hookVisionPlugin(ClassLoader classLoader) {
        try {
            Class<?> visionPluginClass = Class.forName(VISION_PLUGIN_CLASS, false, classLoader);
            XposedBridge.log(TAG + ": Resolved VisionPlugIn class: " + visionPluginClass.getName());

            // Hook the VisionPlugIn's initialize method if it exists
            try {
                XposedHelpers.findAndHookMethod(
                        visionPluginClass,
                        "initialize",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (param.getThrowable() != null) {
                                    XposedBridge.log(TAG + ": VisionPlugIn.initialize() failed: "
                                            + param.getThrowable().getMessage());
                                    return;
                                }

                                Object pluginInstance = param.thisObject;
                                XposedBridge.log(TAG + ": VisionPlugIn initialized: "
                                        + pluginInstance.getClass().getName());

                                // Store the plugin reference in ServiceProxy for later use
                                storePluginReference("visionPlugin", pluginInstance);
                            }
                        }
                );
                XposedBridge.log(TAG + ": Hooked VisionPlugIn.initialize()");
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + ": VisionPlugIn.initialize() not found, "
                        + "attempting field extraction instead");
                extractPluginFromFields(visionPluginClass, classLoader);
            }

        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + ": VisionPlugIn class not found: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking VisionPlugIn: " + t.getMessage());
        }
    }

    /**
     * Uses reflection to locate and hook the SegmentPlugin instance within
     * the AICR core service. The SegmentPlugin provides image segmentation
     * capabilities such as background removal and subject isolation.
     *
     * @param classLoader the class loader of the AICR application
     */
    private static void hookSegmentPlugin(ClassLoader classLoader) {
        try {
            Class<?> segmentPluginClass = Class.forName(SEGMENT_PLUGIN_CLASS, false, classLoader);
            XposedBridge.log(TAG + ": Resolved SegmentPlugin class: " + segmentPluginClass.getName());

            // Hook the SegmentPlugin's initialize method if it exists
            try {
                XposedHelpers.findAndHookMethod(
                        segmentPluginClass,
                        "initialize",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                if (param.getThrowable() != null) {
                                    XposedBridge.log(TAG + ": SegmentPlugin.initialize() failed: "
                                            + param.getThrowable().getMessage());
                                    return;
                                }

                                Object pluginInstance = param.thisObject;
                                XposedBridge.log(TAG + ": SegmentPlugin initialized: "
                                        + pluginInstance.getClass().getName());

                                // Store the plugin reference for later use
                                storePluginReference("segmentPlugin", pluginInstance);
                            }
                        }
                );
                XposedBridge.log(TAG + ": Hooked SegmentPlugin.initialize()");
            } catch (NoSuchMethodError e) {
                XposedBridge.log(TAG + ": SegmentPlugin.initialize() not found, "
                        + "attempting field extraction instead");
                extractPluginFromFields(segmentPluginClass, classLoader);
            }

        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + ": SegmentPlugin class not found: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking SegmentPlugin: " + t.getMessage());
        }
    }

    /**
     * Attempts to extract plugin instances by scanning the fields of the
     * AiCrCoreService class for references to known plugin types.
     * This is a fallback when the plugin classes do not expose a direct
     * initialize() method that can be hooked.
     *
     * @param pluginClass the plugin class to search for in service fields
     * @param classLoader the class loader of the AICR application
     */
    private static void extractPluginFromFields(Class<?> pluginClass, ClassLoader classLoader) {
        try {
            Class<?> coreServiceClass = Class.forName(CORE_SERVICE_CLASS, false, classLoader);

            for (Field field : coreServiceClass.getDeclaredFields()) {
                if (pluginClass.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    XposedBridge.log(TAG + ": Found plugin field: " + field.getName()
                            + " of type " + field.getType().getName());

                    // We cannot access a live instance without an object reference,
                    // but we log the discovery for the hook that will capture the instance
                    // when onBind() is intercepted.
                    XposedBridge.log(TAG + ": Plugin field " + field.getName()
                            + " will be resolved when service instance is available");
                }
            }
        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + ": Core service class not found: " + e.getMessage());
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error scanning fields: " + t.getMessage());
        }
    }

    /**
     * Stores a plugin reference by name for later retrieval.
     * This is used to cache plugin instances after they have been
     * initialized or extracted from the service.
     *
     * @param name     a human-readable name for the plugin
     * @param instance the plugin object instance
     */
    private static void storePluginReference(String name, Object instance) {
        // Store in a static map or delegate to ServiceProxy as needed.
        // For now, we log the capture; integration with ServiceProxy
        // can be extended when the full plugin interface is mapped.
        XposedBridge.log(TAG + ": Stored plugin reference [" + name + "]: "
                + (instance != null ? instance.getClass().getName() : "null"));
    }
}
