package com.aicr.gateway.handler;

import com.google.gson.JsonObject;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public abstract class BaseHandler {

    public abstract Response handle(IHTTPSession session);

    protected Response buildErrorResponse(int httpCode, String message) {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("code", -1);
        errorJson.addProperty("message", message);

        Status status;
        switch (httpCode) {
            case 400: status = Status.BAD_REQUEST; break;
            case 401: status = Status.UNAUTHORIZED; break;
            case 404: status = Status.NOT_FOUND; break;
            case 405: status = Status.METHOD_NOT_ALLOWED; break;
            default: status = Status.INTERNAL_ERROR; break;
        }

        return NanoHTTPD.newFixedLengthResponse(status, "application/json", errorJson.toString());
    }
}