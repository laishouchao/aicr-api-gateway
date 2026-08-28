package com.aicr.gateway.hook;

import android.graphics.Bitmap;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.XposedBridge;

public class ServiceProxy {

    private static final String TAG = "AICR_Gateway";

    // AIDL stub class name candidates
    private static final String[] VISION_STUB_NAMES = {
        "com.xiaomi.aicr.service.IVisionService$Stub",
        "com.xiaomi.aicr.IVisionService$Stub",
        "com.xiaomi.aicr.access.IVisionService$Stub",
    };
    private static final String[] NER_STUB_NAMES = {
        "com.xiaomi.aicr.service.INerService$Stub",
        "com.xiaomi.aicr.INerService$Stub",
        "com.xiaomi.aicr.access.INerService$Stub",
    };
    private static final String[] SEGMENT_STUB_NAMES = {
        "com.xiaomi.aicr.service.IImageSegmentService$Stub",
        "com.xiaomi.aicr.IImageSegmentService$Stub",
        "com.xiaomi.aicr.access.IImageSegmentService$Stub",
    };

    private static volatile ServiceProxy instance;
    public static ServiceProxy getInstance() {
        if (instance == null) { synchronized (ServiceProxy.class) { if (instance == null) instance = new ServiceProxy(); } }
        return instance;
    }
    private ServiceProxy() {}

    private volatile IBinder coreServiceBinder;
    private volatile Object coreServiceInstance;
    private volatile ClassLoader appClassLoader;
    private volatile Object visionService;
    private volatile Object nerService;
    private volatile Object segmentService;

    // Direct engine references discovered from service fields
    private volatile Object visionEngine;
    private volatile Object nerEngine;
    private volatile Object segmentEngine;

    /**
     * Set the core service binder (from onBind or self-bind).
     */
    public void setCoreServiceBinder(IBinder binder, ClassLoader classLoader) {
        this.coreServiceBinder = binder;
        this.appClassLoader = classLoader;
        this.visionService = null;
        this.nerService = null;
        this.segmentService = null;
        XposedBridge.log(TAG + ": Core service binder set: " + (binder != null ? binder.getClass().getName() : "null"));

        // Try to discover AIDL interface descriptors
        if (binder != null) {
            discoverInterfaces(binder, classLoader);
        }
    }

    /**
     * Set the core service instance (from onCreate hook or ActivityThread scan).
     * This enables direct field access to AI engines.
     */
    public void setCoreServiceInstance(Object service, ClassLoader classLoader) {
        this.coreServiceInstance = service;
        if (this.appClassLoader == null) this.appClassLoader = classLoader;
        XposedBridge.log(TAG + ": Core service instance set: " + (service != null ? service.getClass().getName() : "null"));

        if (service != null) {
            scanServiceFields(service);
        }
    }

    public IBinder getCoreServiceBinder() { return coreServiceBinder; }
    public boolean isConnected() { return coreServiceBinder != null || visionEngine != null || nerEngine != null || segmentEngine != null; }

    public static void reset() {
        synchronized (ServiceProxy.class) { instance = null; }
    }

    // ---- Interface Discovery ----

    private void discoverInterfaces(IBinder binder, ClassLoader classLoader) {
        // Get interface descriptor
        try {
            Method descMethod = IBinder.class.getMethod("getInterfaceDescriptor");
            String descriptor = (String) descMethod.invoke(binder);
            XposedBridge.log(TAG + ": Binder descriptor: " + descriptor);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Cannot get descriptor: " + t.getMessage());
        }

        // Try to create service proxies with known stub names
        if (visionService == null) visionService = tryCreateProxy(VISION_STUB_NAMES, binder, classLoader);
        if (nerService == null) nerService = tryCreateProxy(NER_STUB_NAMES, binder, classLoader);
        if (segmentService == null) segmentService = tryCreateProxy(SEGMENT_STUB_NAMES, binder, classLoader);
    }

    private Object tryCreateProxy(String[] classNames, IBinder binder, ClassLoader classLoader) {
        for (String name : classNames) {
            try {
                Class<?> stubClass = Class.forName(name, false, classLoader);
                Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
                Object proxy = asInterface.invoke(null, binder);
                if (proxy != null) {
                    XposedBridge.log(TAG + ": Created proxy: " + name);
                    return proxy;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Proxy creation error for " + name + ": " + t.getMessage());
            }
        }
        return null;
    }

    // ---- Service Field Scanning ----

    private void scanServiceFields(Object service) {
        XposedBridge.log(TAG + ": Scanning service fields for AI engines...");
        Class<?> clazz = service.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(service);
                    if (value == null) continue;

                    String fieldName = field.getName();
                    String typeName = field.getType().getName();

                    XposedBridge.log(TAG + ": Field [" + fieldName + "] type=" + typeName);

                    classifyAndStore(fieldName, typeName, value);

                    // If it's a Map, scan values
                    if (value instanceof Map) {
                        for (Object v : ((Map<?, ?>) value).values()) {
                            if (v != null) classifyAndStore(fieldName, v.getClass().getName(), v);
                        }
                    }
                    // If it's an array or List, scan items
                    if (value instanceof List) {
                        for (Object v : (List<?>) value) {
                            if (v != null) classifyAndStore(fieldName, v.getClass().getName(), v);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }

        XposedBridge.log(TAG + ": Scan complete - vision=" + (visionEngine != null)
                + " ner=" + (nerEngine != null) + " segment=" + (segmentEngine != null));
    }

    private void classifyAndStore(String fieldName, String typeName, Object value) {
        String combined = (fieldName + ":" + typeName).toLowerCase();
        if (visionEngine == null && (combined.contains("vision") || combined.contains("ocr") || combined.contains("recogni"))) {
            visionEngine = value;
            XposedBridge.log(TAG + ": -> Vision engine found: [" + fieldName + "] " + typeName);
        }
        if (nerEngine == null && (combined.contains("ner") || combined.contains("nlp") || combined.contains("entity") || combined.contains("tokeni"))) {
            nerEngine = value;
            XposedBridge.log(TAG + ": -> NER engine found: [" + fieldName + "] " + typeName);
        }
        if (segmentEngine == null && (combined.contains("segment") || combined.contains("seg") || combined.contains("matting"))) {
            segmentEngine = value;
            XposedBridge.log(TAG + ": -> Segment engine found: [" + fieldName + "] " + typeName);
        }
    }

    // ---- Reflection Utilities ----

    private Method findMethod(Object obj, String methodName, Class<?>... paramTypes) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try { Method m = clazz.getDeclaredMethod(methodName, paramTypes); m.setAccessible(true); return m; }
            catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private Object invokeMethod(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = findMethod(obj, name);
                if (m != null && m.getParameterTypes().length == 0) {
                    return m.invoke(obj);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Object invokeMethod(Object obj, Class<?>[] paramTypes, Object[] args, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = findMethod(obj, name, paramTypes);
                if (m != null) return m.invoke(obj, args);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    // ---- Public API Methods ----

    public OCRResult performOCR(Bitmap bitmap) {
        // Try AIDL proxy first
        Object svc = getVisionService();
        if (svc != null) {
            try {
                Method method = findMethod(svc, "ocr", Bitmap.class);
                if (method == null) method = findMethod(svc, "performOCR", Bitmap.class);
                if (method == null) method = findMethod(svc, "recognizeText", Bitmap.class);
                if (method != null) {
                    Object result = method.invoke(svc, bitmap);
                    OCRResult r = convertOCRResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": AIDL OCR failed: " + t.getMessage()); }
        }

        // Fallback: direct engine access
        if (visionEngine != null) {
            try {
                Method method = findMethod(visionEngine, "ocr", Bitmap.class);
                if (method == null) method = findMethod(visionEngine, "performOCR", Bitmap.class);
                if (method == null) method = findMethod(visionEngine, "recognizeText", Bitmap.class);
                if (method == null) method = findMethod(visionEngine, "doOCR", Bitmap.class);
                if (method == null) method = findMethod(visionEngine, "process", Bitmap.class);
                if (method != null) {
                    Object result = method.invoke(visionEngine, bitmap);
                    OCRResult r = convertOCRResult(result);
                    if (r != null) return r;
                }
                // Try no-arg methods (engines that process last-set image)
                Object r = invokeMethod(visionEngine, "ocr", "performOCR", "recognizeText", "getResult");
                if (r != null) return convertOCRResult(r);
            } catch (Throwable t) { XposedBridge.log(TAG + ": Direct OCR failed: " + t.getMessage()); }
        }
        return null;
    }

    public NERResult performNER(String text) {
        Object svc = getNerService();
        if (svc != null) {
            try {
                Method method = findMethod(svc, "ner", String.class);
                if (method == null) method = findMethod(svc, "performNER", String.class);
                if (method == null) method = findMethod(svc, "analyze", String.class);
                if (method != null) {
                    Object result = method.invoke(svc, text);
                    NERResult r = convertNERResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": AIDL NER failed: " + t.getMessage()); }
        }
        if (nerEngine != null) {
            try {
                Method method = findMethod(nerEngine, "ner", String.class);
                if (method == null) method = findMethod(nerEngine, "performNER", String.class);
                if (method == null) method = findMethod(nerEngine, "analyze", String.class);
                if (method == null) method = findMethod(nerEngine, "doNER", String.class);
                if (method != null) {
                    Object result = method.invoke(nerEngine, text);
                    NERResult r = convertNERResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": Direct NER failed: " + t.getMessage()); }
        }
        return null;
    }

    public TokenizeResult performTokenize(String text) {
        Object svc = getNerService();
        if (svc != null) {
            try {
                Method method = findMethod(svc, "tokenize", String.class);
                if (method == null) method = findMethod(svc, "performTokenize", String.class);
                if (method != null) {
                    Object result = method.invoke(svc, text);
                    TokenizeResult r = convertTokenizeResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": AIDL tokenize failed: " + t.getMessage()); }
        }
        if (nerEngine != null) {
            try {
                Method method = findMethod(nerEngine, "tokenize", String.class);
                if (method == null) method = findMethod(nerEngine, "performTokenize", String.class);
                if (method == null) method = findMethod(nerEngine, "doTokenize", String.class);
                if (method != null) {
                    Object result = method.invoke(nerEngine, text);
                    TokenizeResult r = convertTokenizeResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": Direct tokenize failed: " + t.getMessage()); }
        }
        return null;
    }

    public SegmentResult performSegment(Bitmap bitmap, String type) {
        Object svc = getSegmentService();
        if (svc != null) {
            try {
                Method method = findMethod(svc, "segment", Bitmap.class, String.class);
                if (method == null) method = findMethod(svc, "performSegment", Bitmap.class, String.class);
                if (method != null) {
                    Object result = method.invoke(svc, bitmap, type);
                    SegmentResult r = convertSegmentResult(result);
                    if (r != null) return r;
                }
                // Try single-param version
                method = findMethod(svc, "segment", Bitmap.class);
                if (method == null) method = findMethod(svc, "performSegment", Bitmap.class);
                if (method != null) {
                    Object result = method.invoke(svc, bitmap);
                    SegmentResult r = convertSegmentResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": AIDL segment failed: " + t.getMessage()); }
        }
        if (segmentEngine != null) {
            try {
                Method method = findMethod(segmentEngine, "segment", Bitmap.class, String.class);
                if (method == null) method = findMethod(segmentEngine, "performSegment", Bitmap.class, String.class);
                if (method != null) {
                    Object result = method.invoke(segmentEngine, bitmap, type);
                    SegmentResult r = convertSegmentResult(result);
                    if (r != null) return r;
                }
                method = findMethod(segmentEngine, "segment", Bitmap.class);
                if (method == null) method = findMethod(segmentEngine, "performSegment", Bitmap.class);
                if (method == null) method = findMethod(segmentEngine, "doSegment", Bitmap.class);
                if (method != null) {
                    Object result = method.invoke(segmentEngine, bitmap);
                    SegmentResult r = convertSegmentResult(result);
                    if (r != null) return r;
                }
            } catch (Throwable t) { XposedBridge.log(TAG + ": Direct segment failed: " + t.getMessage()); }
        }
        return null;
    }

    // ---- Service Proxy Getters ----

    public Object getVisionService() {
        if (visionService == null && coreServiceBinder != null && appClassLoader != null) {
            synchronized (this) { if (visionService == null) visionService = tryCreateProxy(VISION_STUB_NAMES, coreServiceBinder, appClassLoader); }
        }
        return visionService;
    }

    public Object getNerService() {
        if (nerService == null && coreServiceBinder != null && appClassLoader != null) {
            synchronized (this) { if (nerService == null) nerService = tryCreateProxy(NER_STUB_NAMES, coreServiceBinder, appClassLoader); }
        }
        return nerService;
    }

    public Object getSegmentService() {
        if (segmentService == null && coreServiceBinder != null && appClassLoader != null) {
            synchronized (this) { if (segmentService == null) segmentService = tryCreateProxy(SEGMENT_STUB_NAMES, coreServiceBinder, appClassLoader); }
        }
        return segmentService;
    }

    // ---- Result Converters ----

    private OCRResult convertOCRResult(Object result) {
        if (result == null) return null;
        try {
            OCRResult r = new OCRResult();
            Method m = findMethod(result, "getStatusCode"); if (m != null) r.setStatusCode((int) m.invoke(result));
            m = findMethod(result, "getTexts"); if (m == null) m = findMethod(result, "getTextBlocks");
            if (m != null) {
                Object textList = m.invoke(result);
                if (textList instanceof List) {
                    List<TextBlock> blocks = new ArrayList<>();
                    for (Object item : (List<?>) textList) {
                        TextBlock block = new TextBlock();
                        Method gm = findMethod(item, "getContent"); if (gm == null) gm = findMethod(item, "getText");
                        if (gm != null) block.setContent((String) gm.invoke(item));
                        gm = findMethod(item, "getBoundingBox");
                        if (gm != null) { Object bbox = gm.invoke(item); if (bbox instanceof int[]) block.setBoundingBox((int[]) bbox); }
                        blocks.add(block);
                    }
                    r.setTexts(blocks);
                }
            }
            return r;
        } catch (Throwable e) { return null; }
    }

    private NERResult convertNERResult(Object result) {
        if (result == null) return null;
        try {
            NERResult r = new NERResult();
            Method m = findMethod(result, "getStatusCode"); if (m != null) r.setStatusCode((int) m.invoke(result));
            m = findMethod(result, "getEntities");
            if (m != null) {
                Object entityList = m.invoke(result);
                if (entityList instanceof List) {
                    List<Entity> entities = new ArrayList<>();
                    for (Object item : (List<?>) entityList) {
                        Entity e = new Entity();
                        Method gm = findMethod(item, "getText"); if (gm == null) gm = findMethod(item, "getStr");
                        if (gm != null) e.setText((String) gm.invoke(item));
                        gm = findMethod(item, "getType"); if (gm != null) e.setType((int) gm.invoke(item));
                        gm = findMethod(item, "getStart"); if (gm != null) e.setStart((int) gm.invoke(item));
                        gm = findMethod(item, "getEnd"); if (gm != null) e.setEnd((int) gm.invoke(item));
                        entities.add(e);
                    }
                    r.setEntities(entities);
                }
            }
            return r;
        } catch (Throwable e) { return null; }
    }

    private TokenizeResult convertTokenizeResult(Object result) {
        if (result == null) return null;
        try {
            TokenizeResult r = new TokenizeResult();
            Method m = findMethod(result, "getStatusCode"); if (m != null) r.setStatusCode((int) m.invoke(result));
            m = findMethod(result, "getTokens");
            if (m != null) {
                Object tokenList = m.invoke(result);
                if (tokenList instanceof List) {
                    List<String> tokens = new ArrayList<>();
                    for (Object item : (List<?>) tokenList) tokens.add(String.valueOf(item));
                    r.setTokens(tokens);
                }
            }
            return r;
        } catch (Throwable e) { return null; }
    }

    private SegmentResult convertSegmentResult(Object result) {
        if (result == null) return null;
        try {
            SegmentResult r = new SegmentResult();
            Method m = findMethod(result, "getSegments");
            if (m != null) {
                Object segList = m.invoke(result);
                if (segList instanceof List) {
                    List<Segment> segments = new ArrayList<>();
                    for (Object item : (List<?>) segList) {
                        Segment s = new Segment();
                        Method gm = findMethod(item, "getType"); if (gm != null) s.setType((String) gm.invoke(item));
                        gm = findMethod(item, "getMask"); if (gm != null) { Object mask = gm.invoke(item); if (mask instanceof Bitmap) s.setMask((Bitmap) mask); }
                        gm = findMethod(item, "getWidth"); if (gm != null) s.setWidth((int) gm.invoke(item));
                        gm = findMethod(item, "getHeight"); if (gm != null) s.setHeight((int) gm.invoke(item));
                        segments.add(s);
                    }
                    r.setSegments(segments);
                }
            }
            return r;
        } catch (Throwable e) { return null; }
    }

    // ---- Data Classes ----

    public static class TextBlock {
        private String content; private int[] boundingBox;
        public TextBlock() {} public TextBlock(String c, int[] bb) { content = c; boundingBox = bb; }
        public String getContent() { return content; } public void setContent(String c) { content = c; }
        public int[] getBoundingBox() { return boundingBox; } public void setBoundingBox(int[] bb) { boundingBox = bb; }
    }

    public static class OCRResult {
        private int statusCode; private List<TextBlock> texts = new ArrayList<>();
        public int getStatusCode() { return statusCode; } public void setStatusCode(int s) { statusCode = s; }
        public List<TextBlock> getTexts() { return texts; } public void setTexts(List<TextBlock> t) { texts = t; }
    }

    public static class Entity {
        private String text; private int type; private int start; private int end;
        public Entity() {} public Entity(String t, int ty, int s, int e) { text = t; type = ty; start = s; end = e; }
        public String getText() { return text; } public void setText(String t) { text = t; }
        public int getType() { return type; } public void setType(int t) { type = t; }
        public int getStart() { return start; } public void setStart(int s) { start = s; }
        public int getEnd() { return end; } public void setEnd(int e) { end = e; }
    }

    public static class NERResult {
        private int statusCode; private List<Entity> entities = new ArrayList<>();
        public int getStatusCode() { return statusCode; } public void setStatusCode(int s) { statusCode = s; }
        public List<Entity> getEntities() { return entities; } public void setEntities(List<Entity> e) { entities = e; }
    }

    public static class TokenizeResult {
        private int statusCode; private List<String> tokens = new ArrayList<>();
        public int getStatusCode() { return statusCode; } public void setStatusCode(int s) { statusCode = s; }
        public List<String> getTokens() { return tokens; } public void setTokens(List<String> t) { tokens = t; }
    }

    public static class Segment {
        private String type; private Bitmap mask; private int width; private int height;
        public Segment() {} public Segment(String t, Bitmap m, int w, int h) { type = t; mask = m; width = w; height = h; }
        public String getType() { return type; } public void setType(String t) { type = t; }
        public Bitmap getMask() { return mask; } public void setMask(Bitmap m) { mask = m; }
        public int getWidth() { return width; } public void setWidth(int w) { width = w; }
        public int getHeight() { return height; } public void setHeight(int h) { height = h; }
    }

    public static class SegmentResult {
        private List<Segment> segments = new ArrayList<>();
        public List<Segment> getSegments() { return segments; } public void setSegments(List<Segment> s) { segments = s; }
    }
}
