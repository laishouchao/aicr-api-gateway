package com.aicr.gateway.hook;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/**
 * ServiceProxy is a singleton that holds references to the AICR service binders
 * and provides typed accessors for each sub-service interface.
 *
 * After MainHook captures the core service binder via AiCrCoreService.onBind(),
 * it calls {@link #setCoreServiceBinder(IBinder)} to register it here. Subsequent
 * calls to {@link #getVisionService()}, {@link #getNerService()}, and
 * {@link #getSegmentService()} use reflection to derive the concrete proxy objects
 * from the stored binder.
 *
 * Expected AICR service interfaces (resolved via reflection):
 *   - IVisionService          : com.xiaomi.aicr.service.IVisionService
 *   - INerService             : com.xiaomi.aicr.service.INerService
 *   - IImageSegmentService    : com.xiaomi.aicr.service.IImageSegmentService
 */
public class ServiceProxy {

    private static final String TAG = "AICR_Gateway";

    // Reflection constants for AIDL stub classes
    private static final String VISION_STUB_CLASS  = "com.xiaomi.aicr.service.IVisionService$Stub";
    private static final String NER_STUB_CLASS     = "com.xiaomi.aicr.service.INerService$Stub";
    private static final String SEGMENT_STUB_CLASS = "com.xiaomi.aicr.service.IImageSegmentService$Stub";

    // AsProxy method name on AIDL Stub classes
    private static final String AS_INTERFACE_METHOD = "asInterface";

    // ---- Singleton ----

    private static volatile ServiceProxy instance;

    /**
     * Returns the singleton instance of ServiceProxy.
     * Uses double-checked locking for thread safety.
     */
    public static ServiceProxy getInstance() {
        if (instance == null) {
            synchronized (ServiceProxy.class) {
                if (instance == null) {
                    instance = new ServiceProxy();
                }
            }
        }
        return instance;
    }

    private ServiceProxy() {
        // Private constructor for singleton
    }

    // ---- Binder storage ----

    private volatile IBinder coreServiceBinder;

    // Cached service proxy objects (lazily created)
    private volatile Object visionService;
    private volatile Object nerService;
    private volatile Object segmentService;

    /**
     * Sets the core service binder captured from AiCrCoreService.onBind().
     * When a new binder is set, all cached service proxies are invalidated
     * so they will be re-derived on the next access.
     *
     * @param binder the IBinder returned by AiCrCoreService.onBind()
     */
    public void setCoreServiceBinder(IBinder binder) {
        this.coreServiceBinder = binder;

        // Invalidate cached proxies
        this.visionService = null;
        this.nerService = null;
        this.segmentService = null;

        XposedBridge.log(TAG + ": Core service binder set: "
                + (binder != null ? binder.getClass().getName() : "null"));
    }

    /**
     * Returns the raw core service binder, or null if not yet captured.
     */
    public IBinder getCoreServiceBinder() {
        return coreServiceBinder;
    }

    // ---- Service accessors ----

    /**
     * Returns the IVisionService proxy derived from the core binder.
     *
     * @return the vision service proxy object, or null if unavailable
     */
    public Object getVisionService() {
        if (visionService == null && coreServiceBinder != null) {
            synchronized (this) {
                if (visionService == null) {
                    visionService = createServiceProxy(VISION_STUB_CLASS, coreServiceBinder);
                }
            }
        }
        return visionService;
    }

    /**
     * Returns the INerService proxy derived from the core binder.
     *
     * @return the NER service proxy object, or null if unavailable
     */
    public Object getNerService() {
        if (nerService == null && coreServiceBinder != null) {
            synchronized (this) {
                if (nerService == null) {
                    nerService = createServiceProxy(NER_STUB_CLASS, coreServiceBinder);
                }
            }
        }
        return nerService;
    }

    /**
     * Returns the IImageSegmentService proxy derived from the core binder.
     *
     * @return the segment service proxy object, or null if unavailable
     */
    public Object getSegmentService() {
        if (segmentService == null && coreServiceBinder != null) {
            synchronized (this) {
                if (segmentService == null) {
                    segmentService = createServiceProxy(SEGMENT_STUB_CLASS, coreServiceBinder);
                }
            }
        }
        return segmentService;
    }

    // ---- Reflection helpers ----

    /**
     * Uses reflection to call {@code Stub.asInterface(IBinder)} on the given
     * AIDL stub class, producing a service proxy from the raw binder.
     *
     * @param stubClassName fully-qualified name of the AIDL Stub inner class
     * @param binder        the IBinder to convert into a service proxy
     * @return the service proxy object, or null on failure
     */
    private Object createServiceProxy(String stubClassName, IBinder binder) {
        try {
            Class<?> stubClass = Class.forName(stubClassName);
            Method asInterface = stubClass.getMethod(AS_INTERFACE_METHOD, IBinder.class);
            Object proxy = asInterface.invoke(null, binder);

            XposedBridge.log(TAG + ": Created proxy for " + stubClassName);
            return proxy;
        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + ": Stub class not found: " + stubClassName + " - " + e.getMessage());
        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": asInterface method not found on " + stubClassName + " - " + e.getMessage());
        } catch (InvocationTargetException e) {
            XposedBridge.log(TAG + ": asInterface invocation failed for " + stubClassName
                    + " - " + e.getCause());
        } catch (IllegalAccessException e) {
            XposedBridge.log(TAG + ": asInterface not accessible on " + stubClassName + " - " + e.getMessage());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Unexpected error creating proxy for " + stubClassName
                    + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks whether a core service binder has been registered.
     *
     * @return true if the binder is available
     */
    public boolean isBound() {
        return coreServiceBinder != null;
    }

    /**
     * Resets the singleton instance. Primarily intended for testing.
     */
    public static void reset() {
        synchronized (ServiceProxy.class) {
            instance = null;
        }
    }
}
