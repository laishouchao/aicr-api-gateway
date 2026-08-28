package com.aicr.gateway.handler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.aicr.gateway.proxy.ServiceProxy;
import com.aicr.gateway.util.ImageUtil;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Image segmentation request handler.
 * Handles POST requests with multipart/form-data containing an image file
 * and an optional "type" parameter, calls AICR SegmentService via ServiceProxy,
 * and returns JSON with base64-encoded mask data.
 *
 * Endpoint: /api/v1/segment
 * Response format:
 * {
 *   "code": 0,
 *   "data": {
 *     "segments": [
 *       {"type": "person", "mask": "base64...", "width": 1920, "height": 1080}
 *     ]
 *   }
 * }
 */
public class SegmentHandler {

    private static final String TAG = "SegmentHandler";
    private static final String ENDPOINT = "/api/v1/segment";
    private static final int MAX_IMAGE_SIZE = 2048;

    private final ServiceProxy serviceProxy;

    public SegmentHandler(ServiceProxy serviceProxy) {
        this.serviceProxy = serviceProxy;
    }

    /**
     * Returns the endpoint path this handler serves.
     */
    public String getEndpoint() {
        return ENDPOINT;
    }

    /**
     * Handles an incoming HTTP session request for image segmentation.
     * Expects a POST with multipart/form-data containing:
     *   - "image" or "file": the image file
     *   - "type" (optional): segmentation type hint (e.g., "person", "background")
     *
     * @param session the NanoHTTPD session
     * @return a JSON response with segmentation results or an error
     */
    public Response handle(IHTTPSession session) {
        LogUtil.logRequest(TAG, session);

        if (session.getMethod() != Method.POST) {
            return buildErrorResponse(405, "Method not allowed. Use POST.");
        }

        try {
            // Parse multipart/form-data body
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            // Retrieve the uploaded image file path
            String tempFilePath = files.get("image");
            if (tempFilePath == null) {
                tempFilePath = files.get("file");
            }

            if (tempFilePath == null) {
                return buildErrorResponse(400, "Missing image file in request body. Use field name 'image'.");
            }

            // Read and decode the image
            Bitmap bitmap = BitmapFactory.decodeFile(tempFilePath);
            if (bitmap == null) {
                return buildErrorResponse(400, "Failed to decode image. Ensure a valid image file is provided.");
            }

            // Optionally compress large images before processing
            if (bitmap.getWidth() > MAX_IMAGE_SIZE || bitmap.getHeight() > MAX_IMAGE_SIZE) {
                bitmap = ImageUtil.compressBitmap(bitmap, MAX_IMAGE_SIZE);
                LogUtil.d(TAG, "Image compressed to: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            }

            // Retrieve optional type parameter
            String segmentType = session.getParms().get("type");
            if (segmentType == null || segmentType.isEmpty()) {
                segmentType = "general";
            }

            LogUtil.d(TAG, "Segmentation request - image: " + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + ", type: " + segmentType);

            // Call ServiceProxy to perform segmentation
            ServiceProxy.SegmentResult result = serviceProxy.performSegment(bitmap, segmentType);

            if (result == null) {
                return buildErrorResponse(500, "Segmentation service returned null result.");
            }

            // Build JSON response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);

            JsonObject data = new JsonObject();
            JsonArray segmentsArray = new JsonArray();

            if (result.getSegments() != null) {
                for (ServiceProxy.Segment seg : result.getSegments()) {
                    JsonObject segObj = new JsonObject();
                    segObj.addProperty("type", seg.getType());
                    segObj.addProperty("mask", ImageUtil.bitmapToBase64(seg.getMask()));
                    segObj.addProperty("width", seg.getWidth());
                    segObj.addProperty("height", seg.getHeight());
                    segmentsArray.add(segObj);
                }
            }

            data.add("segments", segmentsArray);
            responseJson.add("data", data);

            String responseStr = responseJson.toString();
            LogUtil.logResponse(TAG, responseStr);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseStr);

        } catch (IOException e) {
            LogUtil.e(TAG, "Error parsing request body", e);
            return buildErrorResponse(500, "Error parsing request: " + e.getMessage());
        } catch (Exception e) {
            LogUtil.e(TAG, "Error processing segmentation request", e);
            return buildErrorResponse(500, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Builds a standardized error JSON response.
     */
    private Response buildErrorResponse(int httpCode, String message) {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("code", -1);
        errorJson.addProperty("message", message);

        Status status;
        switch (httpCode) {
            case 400:
                status = Status.BAD_REQUEST;
                break;
            case 405:
                status = Status.METHOD_NOT_ALLOWED;
                break;
            default:
                status = Status.INTERNAL_ERROR;
                break;
        }

        LogUtil.e(TAG, "Error response: " + message);
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", errorJson.toString());
    }
}
