package com.aicr.gateway.handler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.aicr.gateway.hook.ServiceProxy;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OCRHandler extends BaseHandler {

    private static final String TAG = "OCRHandler";
    private final ServiceProxy serviceProxy;

    public OCRHandler(ServiceProxy serviceProxy) { this.serviceProxy = serviceProxy; }

    @Override
    public Response handle(IHTTPSession session) {
        LogUtil.logRequest(TAG, session);
        if (session.getMethod() != Method.POST) {
            return buildErrorResponse(405, "Method not allowed. Use POST.");
        }
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tempFilePath = files.get("image");
            if (tempFilePath == null) tempFilePath = files.get("file");
            if (tempFilePath == null) return buildErrorResponse(400, "Missing image file.");

            Bitmap bitmap = BitmapFactory.decodeFile(tempFilePath);
            if (bitmap == null) return buildErrorResponse(400, "Failed to decode image.");

            ServiceProxy.OCRResult result = serviceProxy.performOCR(bitmap);
            if (result == null) return buildErrorResponse(500, "OCR service returned null.");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);
            JsonObject data = new JsonObject();
            data.addProperty("status", result.getStatusCode());
            JsonArray textsArray = new JsonArray();
            if (result.getTexts() != null) {
                for (ServiceProxy.TextBlock tb : result.getTexts()) {
                    JsonObject textObj = new JsonObject();
                    textObj.addProperty("content", tb.getContent());
                    JsonArray bbox = new JsonArray();
                    if (tb.getBoundingBox() != null) { for (int v : tb.getBoundingBox()) bbox.add(v); }
                    textObj.add("boundingBox", bbox);
                    textsArray.add(textObj);
                }
            }
            data.add("texts", textsArray);
            responseJson.add("data", data);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString());
        } catch (IOException e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
        catch (Exception e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
    }
}