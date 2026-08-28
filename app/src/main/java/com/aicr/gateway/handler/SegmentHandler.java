package com.aicr.gateway.handler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.aicr.gateway.hook.ServiceProxy;
import com.aicr.gateway.util.ImageUtil;
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

public class SegmentHandler extends BaseHandler {

    private static final String TAG = "SegmentHandler";
    private static final int MAX_IMAGE_SIZE = 2048;
    private final ServiceProxy serviceProxy;

    public SegmentHandler(ServiceProxy serviceProxy) { this.serviceProxy = serviceProxy; }

    @Override
    public Response handle(IHTTPSession session) {
        if (session.getMethod() != Method.POST) return buildErrorResponse(405, "Method not allowed.");
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String tempFilePath = files.get("image");
            if (tempFilePath == null) tempFilePath = files.get("file");
            if (tempFilePath == null) return buildErrorResponse(400, "Missing image file.");

            Bitmap bitmap = BitmapFactory.decodeFile(tempFilePath);
            if (bitmap == null) return buildErrorResponse(400, "Failed to decode image.");
            if (bitmap.getWidth() > MAX_IMAGE_SIZE || bitmap.getHeight() > MAX_IMAGE_SIZE) {
                bitmap = ImageUtil.compressBitmap(bitmap, MAX_IMAGE_SIZE);
            }

            String segmentType = session.getParms().get("type");
            if (segmentType == null || segmentType.isEmpty()) segmentType = "general";

            ServiceProxy.SegmentResult result = serviceProxy.performSegment(bitmap, segmentType);
            if (result == null) return buildErrorResponse(500, "Segment returned null.");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);
            JsonObject data = new JsonObject();
            JsonArray segmentsArray = new JsonArray();
            if (result.getSegments() != null) {
                for (ServiceProxy.Segment seg : result.getSegments()) {
                    JsonObject segObj = new JsonObject();
                    segObj.addProperty("type", seg.getType());
                    if (seg.getMask() != null) segObj.addProperty("mask", ImageUtil.bitmapToBase64(seg.getMask()));
                    segObj.addProperty("width", seg.getWidth());
                    segObj.addProperty("height", seg.getHeight());
                    segmentsArray.add(segObj);
                }
            }
            data.add("segments", segmentsArray);
            responseJson.add("data", data);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString());
        } catch (IOException e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
        catch (Exception e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
    }
}