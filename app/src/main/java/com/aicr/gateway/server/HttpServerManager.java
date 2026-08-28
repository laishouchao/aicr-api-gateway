package com.aicr.gateway.server;

import android.content.Context;
import android.util.Log;

import com.aicr.gateway.config.GatewayConfig;

/**
 * Singleton HTTP server manager that manages the lifecycle of the API server.
 * Provides start, stop, restart, and status query operations.
 * The server is started in a background thread to avoid blocking the main thread.
 */
public class HttpServerManager {

    private static final String TAG = "HttpServerManager";

    /** Singleton instance */
    private static volatile HttpServerManager instance;

    /** The underlying API server instance */
    private ApiServer apiServer;

    /** Flag indicating whether the server is currently running */
    private volatile boolean running;

    /** The background thread that hosts the server */
    private Thread serverThread;

    /** The port the server listens on */
    private int port;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private HttpServerManager() {
        this.running = false;
    }

    /**
     * Returns the singleton instance of HttpServerManager.
     * Uses double-checked locking for thread safety.
     *
     * @return the singleton HttpServerManager instance
     */
    public static HttpServerManager getInstance() {
        if (instance == null) {
            synchronized (HttpServerManager.class) {
                if (instance == null) {
                    instance = new HttpServerManager();
                }
            }
        }
        return instance;
    }

    /**
     * Starts the HTTP server on the port configured in GatewayConfig.
     * The server is launched in a background daemon thread so it does not
     * block the calling thread.
     *
     * @param context the Android application context used to read configuration
     */
    public synchronized void start(Context context) {
        if (running) {
            Log.w(TAG, "Server is already running on port " + port);
            return;
        }

        try {
            GatewayConfig config = GatewayConfig.getInstance(context);
            this.port = config.getServerPort();

            Log.i(TAG, "Starting HTTP server on port " + port + " ...");

            apiServer = new ApiServer(port);

            serverThread = new Thread(() -> {
                try {
                    apiServer.start();
                    Log.i(TAG, "HTTP server started successfully on port " + port);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start HTTP server", e);
                    running = false;
                }
            }, "AICR-HTTP-Server");

            serverThread.setDaemon(true);
            running = true;
            serverThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing HTTP server", e);
            running = false;
        }
    }

    /**
     * Stops the HTTP server if it is currently running.
     * Waits briefly for the background thread to finish.
     */
    public synchronized void stop() {
        if (!running) {
            Log.w(TAG, "Server is not running");
            return;
        }

        Log.i(TAG, "Stopping HTTP server on port " + port + " ...");

        try {
            if (apiServer != null) {
                apiServer.stop();
                apiServer = null;
            }

            if (serverThread != null) {
                serverThread.join(3000);
                serverThread = null;
            }

            running = false;
            Log.i(TAG, "HTTP server stopped successfully");
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while stopping server", e);
            Thread.currentThread().interrupt();
            running = false;
        } catch (Exception e) {
            Log.e(TAG, "Error stopping HTTP server", e);
            running = false;
        }
    }

    /**
     * Restarts the HTTP server by stopping it first and then starting it again
     * with the latest configuration.
     *
     * @param context the Android application context used to read configuration
     */
    public synchronized void restart(Context context) {
        Log.i(TAG, "Restarting HTTP server ...");
        stop();
        start(context);
    }

    /**
     * Returns whether the HTTP server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }
}
