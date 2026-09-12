package com.example.remotecontroller;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends Activity {
    static final String SERVER_URL = "ws://192.168.1.10:8080";
    WebSocket ws;
    EditText code;
    TextView status;
    ImageView screen;
    Bitmap last;
    float downX, downY;
    long downAt;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        code = findViewById(R.id.code);
        status = findViewById(R.id.status);
        screen = findViewById(R.id.screen);

        findViewById(R.id.connect).setOnClickListener(v -> connect());
        findViewById(R.id.disconnect).setOnClickListener(v -> {
            sendControl("disconnect");
            if (ws != null) ws.close(1000, "stop");
        });
        findViewById(R.id.back).setOnClickListener(v -> sendControl("back"));
        findViewById(R.id.home).setOnClickListener(v -> sendControl("home"));
        findViewById(R.id.recents).setOnClickListener(v -> sendControl("recents"));
        screen.setOnTouchListener((v, e) -> touch(e));
    }

    boolean touch(MotionEvent e) {
        if (last == null) return true;
        float nx = e.getX() / Math.max(1f, screen.getWidth());
        float ny = e.getY() / Math.max(1f, screen.getHeight());
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = nx; downY = ny; downAt = System.currentTimeMillis(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float dx = nx - downX, dy = ny - downY;
            try {
                if (Math.hypot(dx, dy) < 0.04) {
                    sendControl(new JSONObject().put("action", "tap").put("x", downX).put("y", downY));
                } else {
                    sendControl(new JSONObject()
                            .put("action", "swipe")
                            .put("x1", downX).put("y1", downY)
                            .put("x2", nx).put("y2", ny)
                            .put("duration", Math.max(150, System.currentTimeMillis() - downAt)));
                }
            } catch (Exception ignored) {}
            return true;
        }
        return true;
    }

    void connect() {
        String deviceKey = code.getText().toString().trim();
        if (deviceKey.isEmpty()) {
            status.setText("Masukkan model perangkat / identifier");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        ws = client.newWebSocket(
                new Request.Builder().url(SERVER_URL).build(),
                new WebSocketListener() {
                    @Override public void onOpen(WebSocket s, Response r) {
                        status.post(() -> status.setText("Meminta persetujuan..."));
                        try {
                            s.send(new JSONObject()
                                    .put("type", "REQUEST_SESSION")
                                    .put("deviceKey", deviceKey)
                                    .put("controllerName", "Emotexware Controller")
                                    .toString());
                        } catch (Exception ignored) {}
                    }

                    @Override public void onMessage(WebSocket s, String text) {
                        try {
                            JSONObject j = new JSONObject(text);
                            String t = j.getString("type");
                            if (t.equals("SESSION_APPROVED")) {
                                status.post(() -> status.setText("Sesi disetujui"));
                            } else if (t.equals("WAITING_APPROVAL")) {
                                status.post(() -> status.setText("Menunggu persetujuan HP target..."));
                            } else if (t.equals("ERROR")) {
                                status.post(() -> status.setText(j.optString("message", "Terjadi kesalahan")));
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override public void onMessage(WebSocket s, ByteString bytes) {
                        byte[] b = bytes.toByteArray();
                        Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
                        if (bm != null) {
                            screen.post(() -> {
                                if (last != null && !last.isRecycled()) last.recycle();
                                last = bm;
                                screen.setImageBitmap(bm);
                            });
                        }
                    }

                    @Override public void onFailure(WebSocket s, Throwable t, Response r) {
                        status.post(() -> status.setText("Koneksi gagal: " + t.getMessage()));
                    }
                });
    }

    void sendControl(String action) {
        try { sendControl(new JSONObject().put("action", action)); }
        catch (Exception ignored) {}
    }

    void sendControl(JSONObject payload) {
        if (ws == null) return;
        try {
            ws.send(new JSONObject().put("type", "CONTROL").put("payload", payload).toString());
        } catch (Exception ignored) {}
    }
}
