package com.aicr.gateway.hook;

import android.graphics.Bitmap;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XposedBridge;

/**
 * ServiceProxy communicates with AICR AI services via AIDL interfaces.
 * 
 * Actual AIDL methods (discovered via jadx decompilation):
 * - IVisionService: setImage(Bitmap), doOCRDetect(int), doOCRRecognize(int)
 * - INerService: extract(String) -> List<Entity>, cut(String) -> List<String>
 * - IImageSegmentService: segment(Bitmap, VisionAttribute) -> SegmentResult
 */
public class ServiceProxy {

    private static final String TAG = "AICR_Gateway";

    private static final String VISION_STUB = "com.xiaomi.aicr.plugin.IVisionService$Stub";
    private static final String NER_STUB = "com.xiaomi.aicr.plugin.INerService$Stub";
    private static final String SEGMENT_STUB = "com.xiaomi.aicr.plugin.IImageSegmentService$Stub";

    private static volatile ServiceProxy instance;
    public static ServiceProxy getInstance() {
        if (instance == null) { synchronized (ServiceProxy.class) { if (instance == null) instance = new ServiceProxy(); } }
        return instance;
    }
    private ServiceProxy() {}

    private volatile IBinder coreServiceBinder;
    private volatile ClassLoader appClassLoader;
    private volatile Object visionService;
    private volatile Object nerService;
    private volatile Object segmentService;

    public void setCoreServiceBinder(IBinder binder, ClassLoader classLoader) {
        this.coreServiceBinder = binder;
        this.appClassLoader = classLoader;
        this.visionService = null;
        this.nerService = null;
        this.segmentService = null;
        XposedBridge.log(TAG + ": Core service binder set: " + (binder != null ? binder.getClass().getName() : "null"));
        if (binder != null) createAllProxies(binder, classLoader);
    }

    public void setCoreServiceInstance(Object service, ClassLoader classLoader) {
        if (this.appClassLoader == null) this.appClassLoader = classLoader;
        XposedBridge.log(TAG + ": Core service instance: " + (service != null ? service.getClass().getName() : "null"));
    }

    public IBinder getCoreServiceBinder() { return coreServiceBinder; }
    public boolean isConnected() { return coreServiceBinder != null; }
    public Object getVisionService() { return visionService; }
    public Object getNerService() { return nerService; }
    public Object getSegmentService() { return segmentService; }
    public static void reset() { synchronized (ServiceProxy.class) { instance = null; } }

    private void createAllProxies(IBinder binder, ClassLoader cl) {
        visionService = createProxy(VISION_STUB, binder, cl);
        nerService = createProxy(NER_STUB, binder, cl);
        segmentService = createProxy(SEGMENT_STUB, binder, cl);
        XposedBridge.log(TAG + ": Proxies - vision=" + (visionService != null) 
            + " ner=" + (nerService != null) + " segment=" + (segmentService != null));
    }

    private Object createProxy(String stubClassName, IBinder binder, ClassLoader cl) {
        try {
            Class<?> stubClass = Class.forName(stubClassName, false, cl);
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object proxy = asInterface.invoke(null, binder);
            if (proxy != null) XposedBridge.log(TAG + ": Created proxy: " + stubClassName);
            return proxy;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Proxy failed [" + stubClassName + "]: " + t.getMessage());
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

    // ==================== OCR ====================
    
    public OCRResult performOCR(Bitmap bitmap) {
        if (visionService == null) {
            XposedBridge.log(TAG + ": OCR: visionService is null");
            return null;
        }
        try {
            // Step 1: setImage(Bitmap) -> int
            Method setImage = findMethod(visionService, "setImage", Bitmap.class);
            if (setImage == null) {
                XposedBridge.log(TAG + ": OCR: setImage method not found");
                return null;
            }
            int setResult = (int) setImage.invoke(visionService, bitmap);
            XposedBridge.log(TAG + ": OCR: setImage returned " + setResult);

            // Step 2: doOCRRecognize(int) -> OCRResult
            Method doOCRRecognize = findMethod(visionService, "doOCRRecognize", int.class);
            if (doOCRRecognize == null) {
                XposedBridge.log(TAG + ": OCR: doOCRRecognize method not found");
                return null;
            }
            Object ocrResult = doOCRRecognize.invoke(visionService, 0);
            if (ocrResult == null) {
                XposedBridge.log(TAG + ": OCR: doOCRRecognize returned null");
                return null;
            }

            // Convert AICR OCRResult to our OCRResult
            return convertAICROCRResult(ocrResult);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": OCR error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    private OCRResult convertAICROCRResult(Object ocrResult) {
        try {
            OCRResult result = new OCRResult();
            result.setStatusCode(0);

            // Get total_text field
            try {
                Field totalTextField = ocrResult.getClass().getField("total_text");
                String totalText = (String) totalTextField.get(ocrResult);
                XposedBridge.log(TAG + ": OCR total_text: " + (totalText != null ? totalText.substring(0, Math.min(50, totalText.length())) : "null"));
            } catch (Throwable ignored) {}

            // Get paragraphs -> lines -> line_text
            Method getParagraphs = findMethod(ocrResult, "getParagraphs");
            if (getParagraphs == null) {
                // Try field access
                Field paragraphsField = ocrResult.getClass().getField("paragraphs");
                paragraphsField.setAccessible(true);
                Object paragraphs = paragraphsField.get(ocrResult);
                if (paragraphs instanceof Object[]) {
                    for (Object para : (Object[]) paragraphs) {
                        extractLinesFromParagraph(para, result);
                    }
                }
            } else {
                Object paragraphs = getParagraphs.invoke(ocrResult);
                if (paragraphs instanceof Object[]) {
                    for (Object para : (Object[]) paragraphs) {
                        extractLinesFromParagraph(para, result);
                    }
                }
            }
            return result;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": OCR convert error: " + t.getMessage());
            return null;
        }
    }

    private void extractLinesFromParagraph(Object para, OCRResult result) {
        try {
            Field linesField = para.getClass().getField("lines");
            linesField.setAccessible(true);
            Object lines = linesField.get(para);
            if (lines instanceof Object[]) {
                for (Object line : (Object[]) lines) {
                    TextBlock block = new TextBlock();
                    Field textField = line.getClass().getField("line_text");
                    textField.setAccessible(true);
                    block.setContent((String) textField.get(line));
                    // Get location -> box
                    try {
                        Field locField = line.getClass().getField("location");
                        locField.setAccessible(true);
                        Object loc = locField.get(line);
                        if (loc != null) {
                            Field boxField = loc.getClass().getField("box");
                            boxField.setAccessible(true);
                            float[] box = (float[]) boxField.get(loc);
                            if (box != null) {
                                int[] bbox = new int[box.length];
                                for (int i = 0; i < box.length; i++) bbox[i] = (int) box[i];
                                block.setBoundingBox(bbox);
                            }
                        }
                    } catch (Throwable ignored) {}
                    result.getTexts().add(block);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": OCR line extract error: " + t.getMessage());
        }
    }

    // ==================== NER ====================

    public NERResult performNER(String text) {
        if (nerService == null) {
            XposedBridge.log(TAG + ": NER: nerService is null");
            return null;
        }
        try {
            // extract(String) -> List<Entity>
            Method extract = findMethod(nerService, "extract", String.class);
            if (extract == null) {
                XposedBridge.log(TAG + ": NER: extract method not found");
                return null;
            }
            Object entityList = extract.invoke(nerService, text);
            if (entityList == null) {
                XposedBridge.log(TAG + ": NER: extract returned null");
                return null;
            }

            NERResult result = new NERResult();
            result.setStatusCode(0);
            if (entityList instanceof List) {
                for (Object entity : (List<?>) entityList) {
                    Entity e = new Entity();
                    // Entity fields: str, mType, start, end
                    try {
                        Field strField = entity.getClass().getField("str");
                        strField.setAccessible(true);
                        e.setText((String) strField.get(entity));
                        Field typeField = entity.getClass().getField("mType");
                        typeField.setAccessible(true);
                        e.setType((int) typeField.get(entity));
                        Field startField = entity.getClass().getField("start");
                        startField.setAccessible(true);
                        e.setStart((int) startField.get(entity));
                        Field endField = entity.getClass().getField("end");
                        endField.setAccessible(true);
                        e.setEnd((int) endField.get(entity));
                    } catch (Throwable ignored) {}
                    result.getEntities().add(e);
                }
            }
            XposedBridge.log(TAG + ": NER: found " + result.getEntities().size() + " entities");
            return result;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": NER error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    // ==================== Tokenize ====================

    public TokenizeResult performTokenize(String text) {
        if (nerService == null) {
            XposedBridge.log(TAG + ": Tokenize: nerService is null");
            return null;
        }
        try {
            // cut(String) -> List<String>
            Method cut = findMethod(nerService, "cut", String.class);
            if (cut == null) {
                XposedBridge.log(TAG + ": Tokenize: cut method not found");
                return null;
            }
            Object tokenList = cut.invoke(nerService, text);
            if (tokenList == null) {
                XposedBridge.log(TAG + ": Tokenize: cut returned null");
                return null;
            }

            TokenizeResult result = new TokenizeResult();
            result.setStatusCode(0);
            if (tokenList instanceof List) {
                for (Object token : (List<?>) tokenList) {
                    result.getTokens().add(String.valueOf(token));
                }
            }
            XposedBridge.log(TAG + ": Tokenize: found " + result.getTokens().size() + " tokens");
            return result;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Tokenize error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    // ==================== Segment ====================

    public SegmentResult performSegment(Bitmap bitmap, String type) {
        if (segmentService == null) {
            XposedBridge.log(TAG + ": Segment: segmentService is null");
            return null;
        }
        try {
            // segment(Bitmap, VisionAttribute) -> SegmentResult
            // VisionAttribute is optional, pass null
            Class<?> vaClass = Class.forName("com.xiaomi.aicr.vision.VisionAttribute", false, appClassLoader);
            Method segment = findMethod(segmentService, "segment", Bitmap.class, vaClass);
            if (segment == null) {
                XposedBridge.log(TAG + ": Segment: segment method not found");
                return null;
            }
            Object segResult = segment.invoke(segmentService, bitmap, null);
            if (segResult == null) {
                XposedBridge.log(TAG + ": Segment: segment returned null");
                return null;
            }

            SegmentResult result = new SegmentResult();
            // Get bitmaps from result
            try {
                Method getBitmaps = findMethod(segResult, "getBitmaps");
                if (getBitmaps != null) {
                    Object bitmaps = getBitmaps.invoke(segResult);
                    if (bitmaps instanceof List) {
                        for (Object bmp : (List<?>) bitmaps) {
                            Segment s = new Segment();
                            s.setType(type != null ? type : "foreground");
                            if (bmp instanceof Bitmap) s.setMask((Bitmap) bmp);
                            result.getSegments().add(s);
                        }
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Segment convert error: " + t.getMessage());
            }
            XposedBridge.log(TAG + ": Segment: found " + result.getSegments().size() + " segments");
            return result;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Segment error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    // ==================== Data Classes ====================

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
