package com.aicr.gateway.auth;

import android.content.Context;
import android.content.SharedPreferences;

public class GatewayConfig {

    private static final String PREFS_NAME = "aicr_gateway_config";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_AUTH_ENABLED = "auth_enabled";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_LOG_ENABLED = "log_enabled";
    private static final boolean DEFAULT_SERVICE_ENABLED = true;
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final boolean DEFAULT_AUTH_ENABLED = false;
    private static final String DEFAULT_API_KEY = "";
    private static final boolean DEFAULT_LOG_ENABLED = true;

    private final SharedPreferences prefs;
    private static volatile GatewayConfig instance;

    public GatewayConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static GatewayConfig getInstance(Context context) {
        if (instance == null) {
            synchronized (GatewayConfig.class) {
                if (instance == null) instance = new GatewayConfig(context.getApplicationContext());
            }
        }
        return instance;
    }

    public boolean isEnabled() { return prefs.getBoolean(KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED); }
    public int getPort() { return prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT); }
    public int getServerPort() { return getPort(); }
    public boolean isAuthEnabled() { return prefs.getBoolean(KEY_AUTH_ENABLED, DEFAULT_AUTH_ENABLED); }
    public String getApiKey() { return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY); }
    public boolean isLogEnabled() { return prefs.getBoolean(KEY_LOG_ENABLED, DEFAULT_LOG_ENABLED); }

    public void setEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply(); }
    public void setPort(int port) { prefs.edit().putInt(KEY_SERVER_PORT, port).apply(); }
    public void setAuthEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_AUTH_ENABLED, enabled).apply(); }
    public void setApiKey(String apiKey) { prefs.edit().putString(KEY_API_KEY, apiKey != null ? apiKey : "").apply(); }
    public void setLogEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_LOG_ENABLED, enabled).apply(); }

    public void resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED)
            .putInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
            .putBoolean(KEY_AUTH_ENABLED, DEFAULT_AUTH_ENABLED)
            .putString(KEY_API_KEY, DEFAULT_API_KEY)
            .putBoolean(KEY_LOG_ENABLED, DEFAULT_LOG_ENABLED)
            .apply();
    }
}