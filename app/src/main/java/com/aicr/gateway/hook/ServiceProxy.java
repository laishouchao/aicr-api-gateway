package com.aicr.gateway.hook;

import android.graphics.Bitmap;
import android.os.IBinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XposedBridge;

public class ServiceProxy {

    private static final String TAG = "AICR_Gateway";
    private static final String VISION_STUB_CLASS = "com.xiaomi.aicr.service.IVisionService$Stub";
    private static final String NER_STUB_CLASS = "com.xiaomi.aicr.service.INerService$Stub";
    private static final String SEGMENT_STUB_CLASS = "com.xiaomi.aicr.service.IImageSegmentService$Stub";
    private static final String AS_INTERFACE_METHOD = "asInterface";

    private static volatile ServiceProxy instance;
    public static ServiceProxy getInstance() {
        if (instance == null) { synchronized (ServiceProxy.class) { if (instance == null) instance = new ServiceProxy(); } }
        return instance;
    }
    private ServiceProxy() {}

    private volatile IBinder coreServiceBinder;
    private volatile Object visionService;
    private volatile Object nerService;
    private volatile Object segmentService;

    public void setCoreServiceBinder(IBinder binder) {
        this.coreServiceBinder = binder;
        this.visionService = null;
        this.nerService = null;
        this.segmentService = null;
        XposedBridge.log(TAG + ": Core service binder set: " + (binder != null ? binder.getClass().getName() : "null"));
    }

    public IBinder getCoreServiceBinder() { return coreServiceBinder; }

    public Object getVisionService() {
        if (visionService == null && coreServiceBinder != null) {
            synchronized (this) { if (visionService == null) visionService = createServiceProxy(VISION_STUB_CLASS, coreServiceBinder); }
        }
        return visionService;
    }

    public Object getNerService() {
        if (nerService == null && coreServiceBinder != null) {
            synchronized (this) { if (nerService == null) nerService = createServiceProxy(NER_STUB_CLASS, coreServiceBinder); }
        }
        return nerService;
    }

    public Object getSegmentService() {
        if (segmentService == null && coreServiceBinder != null) {
            synchronized (this) { if (segmentService == null) segmentService = createServiceProxy(SEGMENT_STUB_CLASS, coreServiceBinder); }
        }
        return segmentService;
    }

    private Object createServiceProxy(String stubClassName, IBinder binder) {
        try {
            Class<?> stubClass = Class.forName(stubClassName);
            Method asInterface = stubClass.getMethod(AS_INTERFACE_METHOD, IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to create proxy for " + stubClassName + ": " + e.getMessage());
            return null;
        }
    }

    private Method findMethod(Object obj, String methodName, Class<?>... paramTypes) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try { Method m = clazz.getDeclaredMethod(methodName, paramTypes); m.setAccessible(true); return m; }
            catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    public boolean isConnected() { return coreServiceBinder != null; }
    public static void reset() { synchronized (ServiceProxy.class) { instance = null; } }

    public OCRResult performOCR(Bitmap bitmap) {
        Object svc = getVisionService();
        if (svc == null) return null;
        try {
            Method method = findMethod(svc, "ocr", Bitmap.class);
            if (method == null) method = findMethod(svc, "performOCR", Bitmap.class);
            if (method == null) return null;
            Object result = method.invoke(svc, bitmap);
            return convertOCRResult(result);
        } catch (Exception e) { XposedBridge.log(TAG + ": performOCR failed: " + e.getMessage()); return null; }
    }

    public NERResult performNER(String text) {
        Object svc = getNerService();
        if (svc == null) return null;
        try {
            Method method = findMethod(svc, "ner", String.class);
            if (method == null) method = findMethod(svc, "performNER", String.class);
            if (method == null) return null;
            Object result = method.invoke(svc, text);
            return convertNERResult(result);
        } catch (Exception e) { XposedBridge.log(TAG + ": performNER failed: " + e.getMessage()); return null; }
    }

    public TokenizeResult performTokenize(String text) {
        Object svc = getNerService();
        if (svc == null) return null;
        try {
            Method method = findMethod(svc, "tokenize", String.class);
            if (method == null) method = findMethod(svc, "performTokenize", String.class);
            if (method == null) return null;
            Object result = method.invoke(svc, text);
            return convertTokenizeResult(result);
        } catch (Exception e) { XposedBridge.log(TAG + ": performTokenize failed: " + e.getMessage()); return null; }
    }

    public SegmentResult performSegment(Bitmap bitmap, String type) {
        Object svc = getSegmentService();
        if (svc == null) return null;
        try {
            Method method = findMethod(svc, "segment", Bitmap.class, String.class);
            if (method == null) method = findMethod(svc, "performSegment", Bitmap.class, String.class);
            if (method == null) { method = findMethod(svc, "segment", Bitmap.class); if (method == null) method = findMethod(svc, "performSegment", Bitmap.class); }
            if (method == null) return null;
            Object result = method.getParameterTypes().length == 2 ? method.invoke(svc, bitmap, type) : method.invoke(svc, bitmap);
            return convertSegmentResult(result);
        } catch (Exception e) { XposedBridge.log(TAG + ": performSegment failed: " + e.getMessage()); return null; }
    }

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
        } catch (Exception e) { return null; }
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
        } catch (Exception e) { return null; }
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
        } catch (Exception e) { return null; }
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
        } catch (Exception e) { return null; }
    }

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