package com.aicr.gateway.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuration manager for the AICR API Gateway.
 * Uses Android SharedPreferences to persist gateway settings.
 *
 * Keys and defaults:
 *   - service_enabled (boolean, default: true)
 *   - server_port     (int,     default: 8080)
 *   - auth_enabled    (boolean, default: false)
 *   - api_key         (String,  default: "")
 *   - log_enabled     (boolean, default: true)
 */
public class GatewayConfig {

    private static final String PREFS_NAME = "aicr_gateway_config";

    // Preference keys
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_SERVER_PORT     = "server_port";
    private static final String KEY_AUTH_ENABLED    = "auth_enabled";
    private static final String KEY_API_KEY         = "api_key";
    private static final String KEY_LOG_ENABLED     = "log_enabled";

    // Default values
    private static final boolean DEFAULT_SERVICE_ENABLED = true;
    private static final int     DEFAULT_SERVER_PORT     = 8080;
    private static final boolean DEFAULT_AUTH_ENABLED    = false;
    private static final String  DEFAULT_API_KEY         = "";
    private static final boolean DEFAULT_LOG_ENABLED     = true;

    private final SharedPreferences prefs;

    /**
     * Creates a GatewayConfig backed by the given application context.
     */
    public GatewayConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =========================================================================
    // Getters
    // =========================================================================

    /**
     * Returns whether the gateway service is enabled.
     */
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED);
    }

    /**
     * Returns the server port number.
     */
    public int getPort() {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
    }

    /**
     * Returns whether API key authentication is enabled.
     */
    public boolean isAuthEnabled() {
        return prefs.getBoolean(KEY_AUTH_ENABLED, DEFAULT_AUTH_ENABLED);
    }

    /**
     * Returns the configured API key.
     */
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY);
    }

    /**
     * Returns whether request/response logging is enabled.
     */
    public boolean isLogEnabled() {
        return prefs.getBoolean(KEY_LOG_ENABLED, DEFAULT_LOG_ENABLED);
    }

    // =========================================================================
    // Setters
    // =========================================================================

    /**
     * Enables or disables the gateway service.
     */
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply();
    }

    /**
     * Sets the server port number.
     */
    public void setPort(int port) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    /**
     * Enables or disables API key authentication.
     */
    public void setAuthEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTH_ENABLED, enabled).apply();
    }

    /**
     * Sets the API key used for authentication.
     */
    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey != null ? apiKey : "").apply();
    }

    /**
     * Enables or disables request/response logging.
     */
    public void setLogEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOG_ENABLED, enabled).apply();
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Resets all configuration values to their defaults.
     */
    public void resetToDefaults() {
        prefs.edit()
                .putBoolean(KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED)
                .putInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
                .putBoolean(KEY_AUTH_ENABLED, DEFAULT_AUTH_ENABLED)
                .putString(KEY_API_KEY, DEFAULT_API_KEY)
                .putBoolean(KEY_LOG_ENABLED, DEFAULT_LOG_ENABLED)
                .apply();
    }

    /**
     * Returns a human-readable summary of the current configuration.
     */
    @Override
    public String toString() {
        return "GatewayConfig{" +
                "enabled=" + isEnabled() +
                ", port=" + getPort() +
                ", authEnabled=" + isAuthEnabled() +
                ", apiKey='" + (getApiKey().isEmpty() ? "(none)" : "***") + '\'' +
                ", logEnabled=" + isLogEnabled() +
                '}';
    }
}
