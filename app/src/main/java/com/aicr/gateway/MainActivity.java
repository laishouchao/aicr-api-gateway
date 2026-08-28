package com.aicr.gateway;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.aicr.gateway.auth.GatewayConfig;
import com.aicr.gateway.server.HttpServerManager;
import com.aicr.gateway.util.LogUtil;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Main configuration activity
 */
public class MainActivity extends Activity {

    private Switch switchService;
    private EditText editPort;
    private Switch switchAuth;
    private EditText editApiKey;
    private Switch switchLog;
    private Button btnSave;
    private Button btnRestart;
    private TextView tvStatus;
    private TextView tvIpAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadConfig();
        updateStatus();
    }

    private void initViews() {
        switchService = findViewById(R.id.switch_service);
        editPort = findViewById(R.id.edit_port);
        switchAuth = findViewById(R.id.switch_auth);
        editApiKey = findViewById(R.id.edit_api_key);
        switchLog = findViewById(R.id.switch_log);
        btnSave = findViewById(R.id.btn_save);
        btnRestart = findViewById(R.id.btn_restart);
        tvStatus = findViewById(R.id.tv_status);
        tvIpAddress = findViewById(R.id.tv_ip_address);

        // Auth switch listener
        switchAuth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editApiKey.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Save button
        btnSave.setOnClickListener(v -> saveConfig());

        // Restart button
        btnRestart.setOnClickListener(v -> restartService());
    }

    private void loadConfig() {
        GatewayConfig config = new GatewayConfig(this);
        switchService.setChecked(config.isEnabled());
        editPort.setText(String.valueOf(config.getPort()));
        switchAuth.setChecked(config.isAuthEnabled());
        editApiKey.setText(config.getApiKey());
        switchLog.setChecked(config.isLogEnabled());

        editApiKey.setVisibility(config.isAuthEnabled() ? View.VISIBLE : View.GONE);
    }

    private void saveConfig() {
        try {
            int port = Integer.parseInt(editPort.getText().toString());
            if (port < 1024 || port > 65535) {
                Toast.makeText(this, "端口号必须在1024-65535之间", Toast.LENGTH_SHORT).show();
                return;
            }

            GatewayConfig config = new GatewayConfig(this);
            config.setEnabled(switchService.isChecked());
            config.setPort(port);
            config.setAuthEnabled(switchAuth.isChecked());
            config.setApiKey(editApiKey.getText().toString());
            config.setLogEnabled(switchLog.isChecked());

            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            updateStatus();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
        }
    }

    private void restartService() {
        HttpServerManager.getInstance().restart(this);
        Toast.makeText(this, "服务已重启", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean isRunning = HttpServerManager.getInstance().isRunning();
        tvStatus.setText("服务状态: " + (isRunning ? "● 运行中" : "○ 已停止"));
        tvStatus.setTextColor(isRunning ? 0xFF4CAF50 : 0xFFF44336);

        String ip = getLocalIpAddress();
        GatewayConfig config = new GatewayConfig(this);
        tvIpAddress.setText("访问地址: http://" + ip + ":" + config.getPort());
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            if (ipInt != 0) {
                return String.format("%d.%d.%d.%d",
                    (ipInt & 0xff), (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
            }
        } catch (Exception e) {
            LogUtil.e("Failed to get WiFi IP", e);
        }

        // Fallback to network interface
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.e("Failed to get network IP", e);
        }

        return "0.0.0.0";
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
