package com.aicr.gateway.server;

import android.content.Context;
import android.util.Log;
import com.aicr.gateway.auth.GatewayConfig;

public class HttpServerManager {

    private static final String TAG = "HttpServerManager";
    private static volatile HttpServerManager instance;
    private ApiServer apiServer;
    private volatile boolean running;
    private Thread serverThread;
    private int port;

    private HttpServerManager() { this.running = false; }

    public static HttpServerManager getInstance() {
        if (instance == null) {
            synchronized (HttpServerManager.class) {
                if (instance == null) { instance = new HttpServerManager(); }
            }
        }
        return instance;
    }

    public synchronized void start(Context context) {
        if (running) { Log.w(TAG, "Already running on port " + port); return; }
        try {
            GatewayConfig config = GatewayConfig.getInstance(context);
            this.port = config.getServerPort();
            apiServer = new ApiServer(port);
            serverThread = new Thread(() -> {
                try { apiServer.start(); Log.i(TAG, "Started on port " + port); }
                catch (Exception e) { Log.e(TAG, "Failed to start", e); running = false; }
            }, "AICR-HTTP-Server");
            serverThread.setDaemon(true);
            running = true;
            serverThread.start();
        } catch (Exception e) { Log.e(TAG, "Error initializing", e); running = false; }
    }

    public synchronized void stop() {
        if (!running) { return; }
        try {
            if (apiServer != null) { apiServer.stop(); apiServer = null; }
            if (serverThread != null) { serverThread.join(3000); serverThread = null; }
            running = false;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); running = false; }
        catch (Exception e) { running = false; }
    }

    public synchronized void restart(Context context) { stop(); start(context); }
    public boolean isRunning() { return running; }
}