package com.aicr.gateway.handler;

import com.aicr.gateway.hook.ServiceProxy;
import com.aicr.gateway.util.LogUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NERHandler extends BaseHandler {

    private static final String TAG = "NERHandler";
    private static final Map<Integer, String> ENTITY_TYPE_MAP = new HashMap<>();
    static {
        ENTITY_TYPE_MAP.put(0, "UNKNOW"); ENTITY_TYPE_MAP.put(1, "LOCATION"); ENTITY_TYPE_MAP.put(2, "ID");
        ENTITY_TYPE_MAP.put(3, "TEL"); ENTITY_TYPE_MAP.put(4, "BCN"); ENTITY_TYPE_MAP.put(5, "TRAIN");
        ENTITY_TYPE_MAP.put(6, "FLN"); ENTITY_TYPE_MAP.put(7, "CAR"); ENTITY_TYPE_MAP.put(8, "ORDER"); ENTITY_TYPE_MAP.put(9, "ENO");
    }

    private final ServiceProxy serviceProxy;
    public NERHandler(ServiceProxy serviceProxy) { this.serviceProxy = serviceProxy; }

    public static String resolveEntityTypeName(int type) { return ENTITY_TYPE_MAP.getOrDefault(type, "UNKNOW"); }

    @Override
    public Response handle(IHTTPSession session) {
        if (session.getMethod() != Method.POST) return buildErrorResponse(405, "Method not allowed.");
        try {
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String body = bodyMap.get("postData");
            if (body == null) body = bodyMap.getOrDefault("_content", "");
            if (body == null || body.trim().isEmpty()) return buildErrorResponse(400, "Empty body.");

            JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();
            if (!requestJson.has("text")) return buildErrorResponse(400, "Missing 'text'.");
            String inputText = requestJson.get("text").getAsString();
            if (inputText.isEmpty()) return buildErrorResponse(400, "Empty 'text'.");

            ServiceProxy.NERResult result = serviceProxy.performNER(inputText);
            if (result == null) return buildErrorResponse(500, "NER returned null.");

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("code", 0);
            JsonObject data = new JsonObject();
            data.addProperty("statusCode", result.getStatusCode());
            JsonArray entitiesArray = new JsonArray();
            if (result.getEntities() != null) {
                for (ServiceProxy.Entity entity : result.getEntities()) {
                    JsonObject eObj = new JsonObject();
                    eObj.addProperty("str", entity.getText());
                    eObj.addProperty("type", entity.getType());
                    eObj.addProperty("typeName", resolveEntityTypeName(entity.getType()));
                    eObj.addProperty("start", entity.getStart());
                    eObj.addProperty("end", entity.getEnd());
                    entitiesArray.add(eObj);
                }
            }
            data.add("entities", entitiesArray);
            responseJson.add("data", data);
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString());
        } catch (IOException e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
        catch (Exception e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
    }

    public static class TokenizeHandler extends BaseHandler {
        private static final String TAG = "TokenizeHandler";
        private final ServiceProxy serviceProxy;
        public TokenizeHandler(ServiceProxy serviceProxy) { this.serviceProxy = serviceProxy; }

        @Override
        public Response handle(IHTTPSession session) {
            if (session.getMethod() != Method.POST) return buildErrorResponse(405, "Method not allowed.");
            try {
                Map<String, String> bodyMap = new HashMap<>();
                session.parseBody(bodyMap);
                String body = bodyMap.get("postData");
                if (body == null) body = bodyMap.getOrDefault("_content", "");
                if (body == null || body.trim().isEmpty()) return buildErrorResponse(400, "Empty body.");

                JsonObject requestJson = JsonParser.parseString(body).getAsJsonObject();
                if (!requestJson.has("text")) return buildErrorResponse(400, "Missing 'text'.");
                String inputText = requestJson.get("text").getAsString();
                if (inputText.isEmpty()) return buildErrorResponse(400, "Empty 'text'.");

                ServiceProxy.TokenizeResult result = serviceProxy.performTokenize(inputText);
                if (result == null) return buildErrorResponse(500, "Tokenize returned null.");

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("code", 0);
                JsonObject data = new JsonObject();
                data.addProperty("statusCode", result.getStatusCode());
                JsonArray tokensArray = new JsonArray();
                if (result.getTokens() != null) { for (String t : result.getTokens()) tokensArray.add(t); }
                data.add("tokens", tokensArray);
                responseJson.add("data", data);
                return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", responseJson.toString());
            } catch (IOException e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
            catch (Exception e) { return buildErrorResponse(500, "Error: " + e.getMessage()); }
        }
    }
}