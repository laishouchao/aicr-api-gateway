package com.aicr.gateway.server;

import android.util.Log;
import com.aicr.gateway.hook.ServiceProxy;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import fi.iki.elonen.NanoHTTPD.ResponseException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ApiServer extends NanoHTTPD {

    private static final String TAG = "ApiServer";
    private static final String CORS_ALLOW_ORIGIN = "*";
    private static final String CORS_ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String CORS_ALLOW_HEADERS = "Content-Type, Authorization, X-Requested-With";
    private static final String CORS_MAX_AGE = "3600";

    private final Router router;

    public ApiServer(int port) {
        super(port);
        this.router = new Router(ServiceProxy.getInstance());
        Log.i(TAG, "ApiServer created on port " + port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        if (Method.OPTIONS.equals(method)) {
            Response response = newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "");
            addCorsHeaders(response);
            response.addHeader("Access-Control-Allow-Methods", CORS_ALLOW_METHODS);
            response.addHeader("Access-Control-Allow-Headers", CORS_ALLOW_HEADERS);
            response.addHeader("Access-Control-Max-Age", CORS_MAX_AGE);
            return response;
        }

        Map<String, String> bodyParams = new HashMap<>();
        if (Method.POST.equals(method) || Method.PUT.equals(method)) {
            try { session.parseBody(bodyParams); }
            catch (IOException | ResponseException e) {
                Response errorResponse = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error parsing body");
                addCorsHeaders(errorResponse);
                return errorResponse;
            }
        }

        try {
            Response response = router.route(session);
            addCorsHeaders(response);
            return response;
        } catch (Exception e) {
            Response errorResponse = newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal server error");
            addCorsHeaders(errorResponse);
            return errorResponse;
        }
    }

    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", CORS_ALLOW_ORIGIN);
        response.addHeader("Access-Control-Allow-Methods", CORS_ALLOW_METHODS);
        response.addHeader("Access-Control-Allow-Headers", CORS_ALLOW_HEADERS);
    }
}