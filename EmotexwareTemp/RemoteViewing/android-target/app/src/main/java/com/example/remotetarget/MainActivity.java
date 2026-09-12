package com.example.remotetarget;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String SERVER_URL = "ws://192.168.1.10:8080";
    static final int REQ_CAPTURE = 7001;

    TextView codeView, status;
    RemoteSocket socket;
    String deviceKey;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        codeView = findViewById(R.id.code);
        status = findViewById(R.id.status);

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "Unknown" : Build.MODEL.trim();
        deviceKey = (manufacturer + " " + model).trim();
        codeView.setText(deviceKey);

        socket = RemoteSocket.get();
        socket.connect(SERVER_URL, this::handleMessage);
        try {
            socket.send(new JSONObject()
                    .put("type", "REGISTER_TARGET")
                    .put("deviceKey", deviceKey)
                    .put("model", model));
        } catch (Exception ignored) {}

        findViewById(R.id.accessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.share).setOnClickListener(v -> requestCapture());
        findViewById(R.id.stop).setOnClickListener(v ->
                stopService(new Intent(this, ScreenCaptureService.class)));
    }

    void requestCapture() {
        MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int r, int c, Intent data) {
        super.onActivityResult(r, c, data);
        if (r == REQ_CAPTURE && c == RESULT_OK && data != null) {
            Intent i = new Intent(this, ScreenCaptureService.class);
            i.putExtra("resultCode", c);
            i.putExtra("data", data);
            startForegroundServiceCompat(i);
            status.setText("Berbagi layar aktif");
        }
    }

    void startForegroundServiceCompat(Intent i) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    void handleMessage(JSONObject j) {
        runOnUiThread(() -> {
            try {
                String t = j.getString("type");
                if (t.equals("CONTROL")) {
                    RemoteAccessibilityService.control(j.getJSONObject("payload"));
                } else if (t.equals("ACCESS_REQUEST")) {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Permintaan Remote Support")
                            .setMessage("Controller meminta akses ke " + deviceKey + ".\n\nKamu dapat menolak kapan saja.")
                            .setNegativeButton("Tolak", (d, w) -> {
                                try { socket.send(new JSONObject().put("type", "REJECT_SESSION")); }
                                catch (Exception ignored) {}
                            })
                            .setPositiveButton("Izinkan", (d, w) -> {
                                try { socket.send(new JSONObject().put("type", "APPROVE_SESSION")); }
                                catch (Exception ignored) {}
                                requestCapture();
                            })
                            .show();
                } else if (t.equals("SESSION_ENDED")) {
                    status.setText("Sesi berakhir");
                    stopService(new Intent(this, ScreenCaptureService.class));
                } else if (t.equals("ERROR")) {
                    status.setText(j.optString("message", "Server error"));
                }
            } catch (Exception ignored) {}
        });
    }
}
