package com.aicr.gateway;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * AICR API Gateway Application
 */
public class AICRGatewayApp extends Application {
    private static final String TAG = "AICR-Gateway";
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Log.i(TAG, "AICR API Gateway Application initialized");
    }

    public static Context getAppContext() {
        return context;
    }
}
