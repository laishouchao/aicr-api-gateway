package com.aicr.gateway.handler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.aicr.gateway.proxy.ServiceProxy;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR request handler.
 * Handles POST requests with multipart/form-data containing an image file,
 * calls AICR VisionService via ServiceProxy, and returns JSON with text content
 * and bounding boxes.
 *
 * Endpoint: /api/v1/ocr
 * Response format:
 * {
 *   "code": 0,
 *   "data": {
 *     "status": 0,
 *     "texts": [
 *       {"content": "...", "boundingBox": [...]}
 *     ]
 *   }
 * }
 */
public class OCRHandler {

    private static final String TAG = "OCRHandler";
    private static final String ENDPOINT = "/api/v1/ocr";

    private final ServiceProxy serviceProxy;

    public OCRHandler(ServiceProxy serviceProxy) {
        this.serviceProxy = serviceProxy;
    }

    /**
     * Returns the endpoint path this handler serves.
     */
    public String getEndpoint() {
        return ENDPOINT;
    }

    /**
     * Handles an incoming HTTP session request.
     * Expects a POST with multipart/form-data containing an image file field named "image".
     *
     * @param session the NanoHTTPD session
     * @return a JSON response with OCR results or an error
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

            // NanoHTTPD stores uploaded files in temp files; the key is the field name
            String tempFilePath = files.get("image");
            if (tempFilePath == null) {
                // Also check for "file" as an alternative field name
                tempFilePath = files.get("file");
            }

            if (tempFilePath == null) {
                return buildErrorResponse(400, "Missing image file in request body. Use field name 'image'.");
            }

            // Read the uploaded file into a Bitmap
            Bitmap bitmap = BitmapFactory.decodeFile(tempFilePath);
            if (bitmap == null) {
                return buildErrorResponse(400, "Failed to decode image. Ensure a valid image file is provided.");
            }

            LogUtil.d(TAG, "Image received: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            // Call ServiceProxy to perform OCR
            ServiceProxy.OCRResult result = serviceProxy.performOCR(bitmap);

            if (result == null) {
                return buildErrorResponse(500, "OCR service returned null result.");
            }

            // Build JSON response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);

            JsonObject data = new JsonObject();
            data.addProperty("status", result.getStatusCode());

            JsonArray textsArray = new JsonArray();
            if (result.getTexts() != null) {
                for (ServiceProxy.TextBlock textBlock : result.getTexts()) {
                    JsonObject textObj = new JsonObject();
                    textObj.addProperty("content", textBlock.getContent());

                    JsonArray bboxArray = new JsonArray();
                    if (textBlock.getBoundingBox() != null) {
                        for (int val : textBlock.getBoundingBox()) {
                            bboxArray.add(val);
                        }
                    }
                    textObj.add("boundingBox", bboxArray);
                    textsArray.add(textObj);
                }
            }
            data.add("texts", textsArray);
            responseJson.add("data", data);

            String responseStr = responseJson.toString();
            LogUtil.logResponse(TAG, responseStr);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseStr);

        } catch (IOException e) {
            LogUtil.e(TAG, "Error parsing request body", e);
            return buildErrorResponse(500, "Error parsing request: " + e.getMessage());
        } catch (Exception e) {
            LogUtil.e(TAG, "Error processing OCR request", e);
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
