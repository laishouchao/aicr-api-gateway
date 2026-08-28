package com.aicr.gateway.server;

import android.util.Log;
import com.aicr.gateway.handler.NERHandler;
import com.aicr.gateway.handler.OCRHandler;
import com.aicr.gateway.handler.SegmentHandler;
import com.aicr.gateway.handler.StatusHandler;
import com.aicr.gateway.hook.ServiceProxy;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.util.HashMap;
import java.util.Map;

public class Router {

    private static final String TAG = "Router";

    @FunctionalInterface
    public interface RequestHandler { Response handle(IHTTPSession session); }

    private final Map<String, RequestHandler> routes;

    public Router(ServiceProxy serviceProxy) {
        this.routes = new HashMap<>();
        initRoutes(serviceProxy);
    }

    private void initRoutes(ServiceProxy serviceProxy) {
        OCRHandler ocrHandler = new OCRHandler(serviceProxy);
        NERHandler nerHandler = new NERHandler(serviceProxy);
        NERHandler.TokenizeHandler tokenizeHandler = new NERHandler.TokenizeHandler(serviceProxy);
        SegmentHandler segmentHandler = new SegmentHandler(serviceProxy);
        StatusHandler statusHandler = new StatusHandler(serviceProxy);

        routes.put("/api/v1/ocr", ocrHandler::handle);
        routes.put("/api/v1/ner", nerHandler::handle);
        routes.put("/api/v1/ner/tokenize", tokenizeHandler::handle);
        routes.put("/api/v1/segment", segmentHandler::handle);
        routes.put("/api/v1/status", statusHandler::handle);

        Log.i(TAG, "Routes initialized: " + routes.keySet());
    }

    public Response route(IHTTPSession session) {
        String uri = session.getUri();
        RequestHandler handler = routes.get(uri);
        if (handler == null) {
            return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not Found: " + uri);
        }
        try { return handler.handle(session); }
        catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "Internal error");
        }
    }
}