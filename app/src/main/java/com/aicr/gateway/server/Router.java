package com.aicr.gateway.server;

import android.util.Log;

import com.aicr.gateway.handler.BaseHandler;
import com.aicr.gateway.handler.NERHandler;
import com.aicr.gateway.handler.OCRHandler;
import com.aicr.gateway.handler.SegmentHandler;
import com.aicr.gateway.handler.StatusHandler;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Request router that maps incoming URI paths to the appropriate handlers.
 * Supports GET and POST HTTP methods and returns a 404 response for unknown routes.
 *
 * <p>Registered routes:
 * <ul>
 *   <li>{@code /api/v1/ocr}         -> {@link OCRHandler}</li>
 *   <li>{@code /api/v1/ner}         -> {@link NERHandler}</li>
 *   <li>{@code /api/v1/ner/tokenize} -> {@link NERHandler.TokenizeHandler}</li>
 *   <li>{@code /api/v1/segment}      -> {@link SegmentHandler}</li>
 *   <li>{@code /api/v1/status}       -> {@link StatusHandler}</li>
 * </ul>
 */
public class Router {

    private static final String TAG = "Router";

    /** Map of URI paths to their corresponding request handlers */
    private final Map<String, BaseHandler> routes;

    /** Set of supported HTTP methods */
    private final Set<Method> supportedMethods;

    /**
     * Constructs a new Router and initializes all route mappings.
     */
    public Router() {
        this.routes = new HashMap<>();
        this.supportedMethods = new HashSet<>(Arrays.asList(Method.GET, Method.POST));
        initRoutes();
    }

    /**
     * Initializes the route-to-handler mappings.
     */
    private void initRoutes() {
        routes.put("/api/v1/ocr", new OCRHandler());
        routes.put("/api/v1/ner", new NERHandler());
        routes.put("/api/v1/ner/tokenize", new NERHandler.TokenizeHandler());
        routes.put("/api/v1/segment", new SegmentHandler());
        routes.put("/api/v1/status", new StatusHandler());

        Log.i(TAG, "Routes initialized: " + routes.keySet());
    }

    /**
     * Routes the incoming request to the appropriate handler based on the URI path.
     * If the HTTP method is not supported (not GET or POST), a 405 Method Not Allowed
     * response is returned. If no handler is registered for the given path, a 404
     * Not Found response is returned.
     *
     * @param session      the incoming HTTP session
     * @param uri          the request URI path
     * @param method       the HTTP method (GET, POST, etc.)
     * @param queryParams  the query parameters from the URL
     * @param bodyParams   the parsed body parameters (for POST/PUT requests)
     * @return the HTTP response produced by the matched handler, or an error response
     */
    public Response route(IHTTPSession session, String uri, Method method,
                          Map<String, String> queryParams, Map<String, String> bodyParams) {

        Log.d(TAG, "Routing " + method + " " + uri);

        // Check if the HTTP method is supported
        if (!supportedMethods.contains(method)) {
            Log.w(TAG, "Unsupported method: " + method + " for path: " + uri);
            return Response.newFixedLengthResponse(
                    Status.METHOD_NOT_ALLOWED, "text/plain",
                    "Method " + method + " is not allowed. Supported methods: GET, POST");
        }

        // Look up the handler for this path
        BaseHandler handler = routes.get(uri);

        if (handler == null) {
            Log.w(TAG, "No handler found for path: " + uri);
            return notFoundResponse(uri);
        }

        // Delegate to the handler
        try {
            return handler.handle(session, method, queryParams, bodyParams);
        } catch (Exception e) {
            Log.e(TAG, "Handler error for path: " + uri, e);
            return Response.newFixedLengthResponse(
                    Status.INTERNAL_ERROR, "text/plain",
                    "Internal server error while handling " + uri);
        }
    }

    /**
     * Creates a 404 Not Found response with a list of available routes.
     *
     * @param uri the requested URI that was not found
     * @return a 404 response
     */
    private Response notFoundResponse(String uri) {
        StringBuilder body = new StringBuilder();
        body.append("404 Not Found\n");
        body.append("No handler registered for path: ").append(uri).append("\n\n");
        body.append("Available routes:\n");
        for (String path : routes.keySet()) {
            body.append("  - ").append(path).append("\n");
        }
        return Response.newFixedLengthResponse(
                Status.NOT_FOUND, "text/plain", body.toString());
    }
}
