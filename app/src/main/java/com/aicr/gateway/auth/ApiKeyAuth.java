package com.aicr.gateway.auth;

import com.aicr.gateway.util.LogUtil;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.util.Map;

/**
 * API key authentication handler.
 * Reads configuration from {@link GatewayConfig} and checks for a valid
 * API key via the X-API-Key header or api_key query parameter.
 *
 * If authentication is disabled in config, all requests are allowed through.
 */
public class ApiKeyAuth {

    private static final String TAG = "ApiKeyAuth";
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String QUERY_PARAM_API_KEY = "api_key";

    private final GatewayConfig config;

    /**
     * Creates an ApiKeyAuth instance with the given configuration.
     *
     * @param config the gateway configuration to read auth settings from
     */
    public ApiKeyAuth(GatewayConfig config) {
        this.config = config;
    }

    /**
     * Authenticates the given HTTP session.
     *
     * Authentication logic:
     *   1. If auth is disabled in config, returns true immediately.
     *   2. Otherwise, checks for the X-API-Key header.
     *   3. If header is not present, checks for the api_key query parameter.
     *   4. Compares the provided key against the configured API key.
     *
     * @param session the NanoHTTPD HTTP session to authenticate
     * @return true if authentication passes (or is disabled), false otherwise
     */
    public boolean authenticate(IHTTPSession session) {
        // If auth is disabled, allow all requests
        if (!config.isAuthEnabled()) {
            LogUtil.d(TAG, "Auth disabled, allowing request");
            return true;
        }

        String configuredKey = config.getApiKey();
        if (configuredKey == null || configuredKey.isEmpty()) {
            LogUtil.e(TAG, "Auth enabled but no API key configured");
            return false;
        }

        // Try the X-API-Key header first
        Map<String, String> headers = session.getHeaders();
        String providedKey = headers.get(HEADER_API_KEY.toLowerCase());

        // NanoHTTPD normalises header keys to lowercase
        if (providedKey == null) {
            providedKey = headers.get(HEADER_API_KEY);
        }

        // Fall back to query parameter
        if (providedKey == null || providedKey.isEmpty()) {
            Map<String, String> params = session.getParms();
            providedKey = params.get(QUERY_PARAM_API_KEY);
        }

        if (providedKey == null || providedKey.isEmpty()) {
            LogUtil.d(TAG, "No API key provided in request");
            return false;
        }

        boolean match = configuredKey.equals(providedKey);
        if (!match) {
            LogUtil.e(TAG, "API key mismatch");
        } else {
            LogUtil.d(TAG, "API key authenticated successfully");
        }

        return match;
    }

    /**
     * Returns a standard 401 Unauthorized JSON response.
     *
     * @return a Response with status 401 and a JSON error body
     */
    public Response getUnauthorizedResponse() {
        String body = "{\"code\":-1,\"message\":\"Unauthorized. Provide a valid API key via the "
                + HEADER_API_KEY + " header or " + QUERY_PARAM_API_KEY + " query parameter.\"}";

        LogUtil.d(TAG, "Returning 401 Unauthorized");
        return NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, "application/json", body);
    }
}
