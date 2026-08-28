package com.aicr.gateway.auth;

import com.aicr.gateway.util.LogUtil;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.util.Map;

public class ApiKeyAuth {

    private static final String TAG = "ApiKeyAuth";
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String QUERY_PARAM_API_KEY = "api_key";

    private final GatewayConfig config;

    public ApiKeyAuth(GatewayConfig config) { this.config = config; }

    public boolean authenticate(IHTTPSession session) {
        if (!config.isAuthEnabled()) { return true; }
        String configuredKey = config.getApiKey();
        if (configuredKey == null || configuredKey.isEmpty()) { return false; }
        Map<String, String> headers = session.getHeaders();
        String providedKey = headers.get(HEADER_API_KEY.toLowerCase());
        if (providedKey == null) { providedKey = headers.get(HEADER_API_KEY); }
        if (providedKey == null || providedKey.isEmpty()) {
            Map<String, String> params = session.getParms();
            providedKey = params.get(QUERY_PARAM_API_KEY);
        }
        if (providedKey == null || providedKey.isEmpty()) { return false; }
        return configuredKey.equals(providedKey);
    }

    public Response getUnauthorizedResponse() {
        String body = "{\"code\":-1,\"message\":\"Unauthorized\"}";
        return NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, "application/json", body);
    }
}