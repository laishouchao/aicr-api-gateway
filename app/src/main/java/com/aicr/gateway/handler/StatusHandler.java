package com.aicr.gateway.handler;

import com.aicr.gateway.proxy.ServiceProxy;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

/**
 * Status handler that reports service health, version, and capabilities.
 * Handles GET requests and returns a JSON response with service metadata.
 *
 * Endpoint: /api/v1/status
 * Response format:
 * {
 *   "code": 0,
 *   "data": {
 *     "service": "AICR API Gateway",
 *     "version": "1.0.0",
 *     "aicrConnected": true,
 *     "capabilities": ["ocr", "ner", "segment"]
 *   }
 * }
 */
public class StatusHandler {

    private static final String TAG = "StatusHandler";
    private static final String ENDPOINT = "/api/v1/status";
    private static final String SERVICE_NAME = "AICR API Gateway";
    private static final String SERVICE_VERSION = "1.0.0";

    private final ServiceProxy serviceProxy;

    public StatusHandler(ServiceProxy serviceProxy) {
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
     * Only GET requests are accepted.
     *
     * @param session the NanoHTTPD session
     * @return a JSON response with service status information
     */
    public Response handle(IHTTPSession session) {
        LogUtil.logRequest(TAG, session);

        if (session.getMethod() != Method.GET) {
            return buildErrorResponse(405, "Method not allowed. Use GET.");
        }

        try {
            boolean connected = serviceProxy != null && serviceProxy.isConnected();

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);

            JsonObject data = new JsonObject();
            data.addProperty("service", SERVICE_NAME);
            data.addProperty("version", SERVICE_VERSION);
            data.addProperty("aicrConnected", connected);

            JsonArray capabilities = new JsonArray();
            capabilities.add("ocr");
            capabilities.add("ner");
            capabilities.add("segment");
            data.add("capabilities", capabilities);

            responseJson.add("data", data);

            String responseStr = responseJson.toString();
            LogUtil.logResponse(TAG, responseStr);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseStr);

        } catch (Exception e) {
            LogUtil.e(TAG, "Error building status response", e);
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
