package com.aicr.gateway.server;

import android.util.Log;

import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Main HTTP server for the AICR API Gateway.
 * Extends NanoHTTPD to provide lightweight HTTP serving capabilities.
 * Supports CORS headers for cross-origin requests and delegates
 * request handling to the {@link Router} based on URI path.
 */
public class ApiServer extends NanoHTTPD {

    private static final String TAG = "ApiServer";

    /** CORS allowed origins; use "*" to allow all origins */
    private static final String CORS_ALLOW_ORIGIN = "*";

    /** CORS allowed HTTP methods */
    private static final String CORS_ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";

    /** CORS allowed request headers */
    private static final String CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Requested-With";

    /** CORS max age for preflight cache (seconds) */
    private static final String CORS_MAX_AGE = "3600";

    /** The router responsible for dispatching requests to handlers */
    private final Router router;

    /**
     * Constructs a new ApiServer listening on the specified port.
     *
     * @param port the port number to listen on
     */
    public ApiServer(int port) {
        super(port);
        this.router = new Router();
        Log.i(TAG, "ApiServer created on port " + port);
    }

    /**
     * Serves incoming HTTP requests.
     * Adds CORS headers to every response and handles OPTIONS preflight
     * requests immediately. All other requests are routed through the
     * {@link Router} to the appropriate handler.
     *
     * @param session the incoming HTTP session
     * @return the HTTP response
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Received " + method + " request for " + uri);

        // Handle OPTIONS preflight requests for CORS
        if (Method.OPTIONS.equals(method)) {
            Response response = Response.newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "");
            addCorsHeaders(response);
            response.addHeader("Access-Control-Allow-Methods", CORS_ALLOW_METHODS);
            response.addHeader("Access-Control-Allow-Headers", CORS_ALLOW_HEADERS);
            response.addHeader("Access-Control-Max-Age", CORS_MAX_AGE);
            return response;
        }

        // Parse the request body for POST/PUT requests
        Map<String, String> bodyParams = new HashMap<>();
        if (Method.POST.equals(method) || Method.PUT.equals(method)) {
            try {
                session.parseBody(bodyParams);
            } catch (IOException | ResponseException e) {
                Log.e(TAG, "Error parsing request body", e);
                Response errorResponse = Response.newFixedLengthResponse(
                        Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing request body");
                addCorsHeaders(errorResponse);
                return errorResponse;
            }
        }

        // Collect query parameters
        Map<String, String> queryParams = session.getParms();

        // Delegate routing to the Router
        try {
            Response response = router.route(session, uri, method, queryParams, bodyParams);
            addCorsHeaders(response);
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Error handling request: " + uri, e);
            Response errorResponse = Response.newFixedLengthResponse(
                    Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal server error");
            addCorsHeaders(errorResponse);
            return errorResponse;
        }
    }

    /**
     * Adds standard CORS headers to the given response.
     *
     * @param response the response to add CORS headers to
     */
    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", CORS_ALLOW_ORIGIN);
        response.addHeader("Access-Control-Allow-Methods", CORS_ALLOW_METHODS);
        response.addHeader("Access-Control-Allow-Headers", CORS_ALLOW_HEADERS);
    }
}
