package com.aicr.gateway.util;

import android.util.Log;

import com.aicr.gateway.auth.GatewayConfig;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.request.Method;

/**
 * Logging utility for the AICR API Gateway.
 * Provides static convenience methods that wrap Android's {@link Log} class
 * and respect the log-enabled setting from {@link GatewayConfig}.
 */
public class LogUtil {

    private static final String LOG_TAG = "AICR-Gateway";
    private static GatewayConfig config;

    /**
     * Initialises the logging utility with the application's gateway configuration.
     * Must be called before any logging methods are used.
     *
     * @param config the gateway configuration to read log settings from
     */
    public static void init(GatewayConfig config) {
        LogUtil.config = config;
    }

    // =========================================================================
    // Standard log levels
    // =========================================================================

    /**
     * Logs an informational message.
     *
     * @param tag     the log tag (typically the class name)
     * @param message the message to log
     */
    public static void i(String tag, String message) {
        if (isLogEnabled()) {
            Log.i(LOG_TAG, formatTag(tag) + message);
        }
    }

    /**
     * Logs an error message.
     *
     * @param tag     the log tag
     * @param message the error message
     */
    public static void e(String tag, String message) {
        if (isLogEnabled()) {
            Log.e(LOG_TAG, formatTag(tag) + message);
        }
    }

    /**
     * Logs an error message with an associated throwable.
     *
     * @param tag     the log tag
     * @param message the error message
     * @param tr      the throwable to log
     */
    public static void e(String tag, String message, Throwable tr) {
        if (isLogEnabled()) {
            Log.e(LOG_TAG, formatTag(tag) + message, tr);
        }
    }

    /**
     * Logs a debug message.
     *
     * @param tag     the log tag
     * @param message the debug message
     */
    public static void d(String tag, String message) {
        if (isLogEnabled()) {
            Log.d(LOG_TAG, formatTag(tag) + message);
        }
    }

    // =========================================================================
    // Request / Response logging helpers
    // =========================================================================

    /**
     * Logs details of an incoming HTTP request.
     *
     * @param tag     the log tag
     * @param session the NanoHTTPD session to log
     */
    public static void logRequest(String tag, IHTTPSession session) {
        if (!isLogEnabled()) {
            return;
        }
        Method method = session.getMethod();
        String uri = session.getUri();
        String remoteIp = session.getHeaders().get("remote-addr");
        Log.i(LOG_TAG, formatTag(tag) + ">> " + method + " " + uri
                + " from " + (remoteIp != null ? remoteIp : "unknown"));
    }

    /**
     * Logs an outgoing HTTP response body (truncated for readability).
     *
     * @param tag            the log tag
     * @param responseBody   the response body string
     */
    public static void logResponse(String tag, String responseBody) {
        if (!isLogEnabled()) {
            return;
        }
        // Truncate long responses to avoid logcat overflow
        String display = responseBody;
        if (display != null && display.length() > 1024) {
            display = display.substring(0, 1024) + "... (truncated)";
        }
        Log.d(LOG_TAG, formatTag(tag) + "<< " + display);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private static boolean isLogEnabled() {
        // If config has not been initialised, default to enabled
        return config == null || config.isLogEnabled();
    }

    private static String formatTag(String tag) {
        return "[" + tag + "] ";
    }
}
