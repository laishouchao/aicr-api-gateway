package com.aicr.gateway.util;

import android.util.Log;
import com.aicr.gateway.auth.GatewayConfig;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;

public class LogUtil {

    private static final String LOG_TAG = "AICR-Gateway";
    private static GatewayConfig config;

    public static void init(GatewayConfig config) {
        LogUtil.config = config;
    }

    public static void i(String tag, String message) {
        if (isLogEnabled()) { Log.i(LOG_TAG, formatTag(tag) + message); }
    }

    public static void e(String tag, String message) {
        if (isLogEnabled()) { Log.e(LOG_TAG, formatTag(tag) + message); }
    }

    public static void e(String tag, String message, Throwable tr) {
        if (isLogEnabled()) { Log.e(LOG_TAG, formatTag(tag) + message, tr); }
    }

    public static void e(String tag, Throwable tr) {
        if (isLogEnabled()) { Log.e(LOG_TAG, formatTag(tag) + (tr != null ? tr.getMessage() : "null"), tr); }
    }

    public static void d(String tag, String message) {
        if (isLogEnabled()) { Log.d(LOG_TAG, formatTag(tag) + message); }
    }

    public static void logRequest(String tag, IHTTPSession session) {
        if (!isLogEnabled()) return;
        Method method = session.getMethod();
        String uri = session.getUri();
        String remoteIp = session.getHeaders().get("remote-addr");
        Log.i(LOG_TAG, formatTag(tag) + ">> " + method + " " + uri + " from " + (remoteIp != null ? remoteIp : "unknown"));
    }

    public static void logResponse(String tag, String responseBody) {
        if (!isLogEnabled()) return;
        String display = responseBody;
        if (display != null && display.length() > 1024) { display = display.substring(0, 1024) + "... (truncated)"; }
        Log.d(LOG_TAG, formatTag(tag) + "<< " + display);
    }

    private static boolean isLogEnabled() { return config == null || config.isLogEnabled(); }
    private static String formatTag(String tag) { return "[" + tag + "] "; }
}