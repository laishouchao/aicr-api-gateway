package com.aicr.gateway.handler;

import com.aicr.gateway.proxy.ServiceProxy;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * NER (Named Entity Recognition) request handler.
 * Handles POST requests with JSON body containing a text field,
 * calls AICR NerService via ServiceProxy, and returns JSON with entities list.
 *
 * Entity type mapping:
 *   0 = UNKNOW, 1 = LOCATION, 2 = ID, 3 = TEL, 4 = BCN,
 *   5 = TRAIN, 6 = FLN, 7 = CAR, 8 = ORDER, 9 = ENO
 *
 * Endpoint: /api/v1/ner
 * Response format:
 * {
 *   "code": 0,
 *   "data": {
 *     "statusCode": 0,
 *     "entities": [
 *       {"str": "...", "type": 1, "typeName": "LOCATION", "start": 0, "end": 5}
 *     ]
 *   }
 * }
 *
 * Also includes TokenizeHandler as an inner class for /api/v1/ner/tokenize.
 */
public class NERHandler {

    private static final String TAG = "NERHandler";
    private static final String ENDPOINT = "/api/v1/ner";

    /** Entity type name mapping. */
    private static final Map<Integer, String> ENTITY_TYPE_MAP = new HashMap<>();

    static {
        ENTITY_TYPE_MAP.put(0, "UNKNOW");
        ENTITY_TYPE_MAP.put(1, "LOCATION");
        ENTITY_TYPE_MAP.put(2, "ID");
        ENTITY_TYPE_MAP.put(3, "TEL");
        ENTITY_TYPE_MAP.put(4, "BCN");
        ENTITY_TYPE_MAP.put(5, "TRAIN");
        ENTITY_TYPE_MAP.put(6, "FLN");
        ENTITY_TYPE_MAP.put(7, "CAR");
        ENTITY_TYPE_MAP.put(8, "ORDER");
        ENTITY_TYPE_MAP.put(9, "ENO");
    }

    private final ServiceProxy serviceProxy;

    public NERHandler(ServiceProxy serviceProxy) {
        this.serviceProxy = serviceProxy;
    }

    /**
     * Returns the endpoint path this handler serves.
     */
    public String getEndpoint() {
        return ENDPOINT;
    }

    /**
     * Resolves an entity type integer code to its string name.
     */
    public static String resolveEntityTypeName(int type) {
        return ENTITY_TYPE_MAP.getOrDefault(type, "UNKNOW");
    }

    /**
     * Handles an incoming HTTP session request for NER.
     * Expects a POST with JSON body: {"text": "..."}
     *
     * @param session the NanoHTTPD session
     * @return a JSON response with NER results or an error
     */
    public Response handle(IHTTPSession session) {
        LogUtil.logRequest(TAG, session);

        if (session.getMethod() != Method.POST) {
            return buildErrorResponse(405, "Method not allowed. Use POST.");
        }

        try {
            // Read the request body
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);

            // NanoHTTPD stores POST body under "postData" key for application/json
            String body = bodyMap.get("postData");
            if (body == null) {
                body = bodyMap.getOrDefault("_content", "");
            }

            if (body == null || body.trim().isEmpty()) {
                return buildErrorResponse(400, "Empty request body. Provide JSON with 'text' field.");
            }

            // Parse JSON body
            JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();
            if (!requestJson.has("text")) {
                return buildErrorResponse(400, "Missing 'text' field in JSON body.");
            }

            String inputText = requestJson.get("text").getAsString();
            if (inputText.isEmpty()) {
                return buildErrorResponse(400, "The 'text' field must not be empty.");
            }

            LogUtil.d(TAG, "NER input text length: " + inputText.length());

            // Call ServiceProxy to perform NER
            ServiceProxy.NERResult result = serviceProxy.performNER(inputText);

            if (result == null) {
                return buildErrorResponse(500, "NER service returned null result.");
            }

            // Build JSON response
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);

            JsonObject data = new JsonObject();
            data.addProperty("statusCode", result.getStatusCode());

            JsonArray entitiesArray = new JsonArray();
            if (result.getEntities() != null) {
                for (ServiceProxy.Entity entity : result.getEntities()) {
                    JsonObject entityObj = new JsonObject();
                    entityObj.addProperty("str", entity.getText());
                    entityObj.addProperty("type", entity.getType());
                    entityObj.addProperty("typeName", resolveEntityTypeName(entity.getType()));
                    entityObj.addProperty("start", entity.getStart());
                    entityObj.addProperty("end", entity.getEnd());
                    entitiesArray.add(entityObj);
                }
            }
            data.add("entities", entitiesArray);
            responseJson.add("data", data);

            String responseStr = responseJson.toString();
            LogUtil.logResponse(TAG, responseStr);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseStr);

        } catch (IOException e) {
            LogUtil.e(TAG, "Error parsing request body", e);
            return buildErrorResponse(500, "Error parsing request: " + e.getMessage());
        } catch (Exception e) {
            LogUtil.e(TAG, "Error processing NER request", e);
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

    // =========================================================================
    // Inner class: TokenizeHandler
    // =========================================================================

    /**
     * Tokenize request handler (inner class).
     * Handles POST requests with JSON body containing a text field,
     * calls AICR NerService tokenize via ServiceProxy, and returns
     * JSON with a tokens list.
     *
     * Endpoint: /api/v1/ner/tokenize
     * Response format:
     * {
     *   "code": 0,
     *   "data": {
     *     "statusCode": 0,
     *     "tokens": ["token1", "token2", ...]
     *   }
     * }
     */
    public static class TokenizeHandler {

        private static final String TAG = "TokenizeHandler";
        private static final String ENDPOINT = "/api/v1/ner/tokenize";

        private final ServiceProxy serviceProxy;

        public TokenizeHandler(ServiceProxy serviceProxy) {
            this.serviceProxy = serviceProxy;
        }

        public String getEndpoint() {
            return ENDPOINT;
        }

        /**
         * Handles an incoming HTTP session request for tokenization.
         * Expects a POST with JSON body: {"text": "..."}
         *
         * @param session the NanoHTTPD session
         * @return a JSON response with tokenization results or an error
         */
        public Response handle(IHTTPSession session) {
            LogUtil.logRequest(TAG, session);

            if (session.getMethod() != Method.POST) {
                return buildErrorResponse(405, "Method not allowed. Use POST.");
            }

            try {
                Map<String, String> bodyMap = new HashMap<>();
                session.parseBody(bodyMap);

                String body = bodyMap.get("postData");
                if (body == null) {
                    body = bodyMap.getOrDefault("_content", "");
                }

                if (body == null || body.trim().isEmpty()) {
                    return buildErrorResponse(400, "Empty request body. Provide JSON with 'text' field.");
                }

                JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();
                if (!requestJson.has("text")) {
                    return buildErrorResponse(400, "Missing 'text' field in JSON body.");
                }

                String inputText = requestJson.get("text").getAsString();
                if (inputText.isEmpty()) {
                    return buildErrorResponse(400, "The 'text' field must not be empty.");
                }

                LogUtil.d(TAG, "Tokenize input text length: " + inputText.length());

                // Call ServiceProxy to perform tokenization
                ServiceProxy.TokenizeResult result = serviceProxy.performTokenize(inputText);

                if (result == null) {
                    return buildErrorResponse(500, "Tokenize service returned null result.");
                }

                // Build JSON response
                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("code", 0);

                JsonObject data = new JsonObject();
                data.addProperty("statusCode", result.getStatusCode());

                JsonArray tokensArray = new JsonArray();
                if (result.getTokens() != null) {
                    for (String token : result.getTokens()) {
                        tokensArray.add(token);
                    }
                }
                data.add("tokens", tokensArray);
                responseJson.add("data", data);

                String responseStr = responseJson.toString();
                LogUtil.logResponse(TAG, responseStr);
                return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseStr);

            } catch (IOException e) {
                LogUtil.e(TAG, "Error parsing request body", e);
                return buildErrorResponse(500, "Error parsing request: " + e.getMessage());
            } catch (Exception e) {
                LogUtil.e(TAG, "Error processing tokenize request", e);
                return buildErrorResponse(500, "Internal error: " + e.getMessage());
            }
        }

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
}
