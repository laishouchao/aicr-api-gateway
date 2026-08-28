package com.aicr.gateway.handler;

import com.aicr.gateway.hook.ServiceProxy;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.lang.reflect.Method;

public class StatusHandler extends BaseHandler {

    private static final String TAG = "StatusHandler";
    private static final String SERVICE_NAME = "AICR API Gateway";
    private static final String SERVICE_VERSION = "1.0.0";
    private final ServiceProxy serviceProxy;

    public StatusHandler(ServiceProxy serviceProxy) { this.serviceProxy = serviceProxy; }

    @Override
    public Response handle(IHTTPSession session) {
        if (session.getMethod() != Method.GET) return buildErrorResponse(405, "Method not allowed. Use GET.");
        try {
            boolean connected = serviceProxy != null && serviceProxy.isConnected();
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);
            JsonObject data = new JsonObject();
            data.addProperty("service", SERVICE_NAME);
            data.addProperty("version", SERVICE_VERSION);
            data.addProperty("aicrConnected", connected);
            JsonArray capabilities = new JsonArray();
            capabilities.add("ocr"); capabilities.add("ner"); capabilities.add("segment");
            data.add("capabilities", capabilities);

            // Debug mode: list AIDL proxy methods
            String query = session.getQueryParameterString();
            if (query != null && query.contains("debug")) {
                JsonObject debug = new JsonObject();
                listMethods(debug, "visionProxy", serviceProxy.getVisionService());
                listMethods(debug, "nerProxy", serviceProxy.getNerService());
                listMethods(debug, "segmentProxy", serviceProxy.getSegmentService());
                data.add("debug", debug);
            }

            responseJson.add("data", data);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString());
        } catch (Exception e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
    }

    private void listMethods(JsonObject parent, String key, Object service) {
        JsonArray methods = new JsonArray();
        if (service != null) {
            Class<?> clazz = service.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Method m : clazz.getDeclaredMethods()) {
                    JsonObject mObj = new JsonObject();
                    mObj.addProperty("name", m.getName());
                    mObj.addProperty("class", m.getDeclaringClass().getSimpleName());
                    Class<?>[] params = m.getParameterTypes();
                    JsonArray pArr = new JsonArray();
                    for (Class<?> p : params) pArr.add(p.getSimpleName());
                    mObj.add("params", pArr);
                    mObj.addProperty("return", m.getReturnType().getSimpleName());
                    methods.add(mObj);
                }
                clazz = clazz.getSuperclass();
            }
        } else {
            JsonObject nullObj = new JsonObject();
            nullObj.addProperty("error", "service proxy is null");
            methods.add(nullObj);
        }
        parent.add(key, methods);
    }
}
