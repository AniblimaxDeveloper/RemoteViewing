package com.example.remotetarget;

import android.app.*; import android.content.*; import android.media.projection.MediaProjectionManager; import android.net.Uri; import android.os.*; import android.provider.Settings; import android.widget.*; import org.json.JSONObject; import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends Activity {
    static final String SERVER_URL = "ws://192.168.1.10:8080"; // GANTI ke alamat server Anda
    static String CODE; TextView codeView,status; RemoteSocket socket; final int REQ_CAPTURE=7001;
    @Override public void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main);
        codeView=findViewById(R.id.code); status=findViewById(R.id.status); CODE=String.format("%06d", ThreadLocalRandom.current().nextInt(0,1000000)); codeView.setText(CODE);
        socket=RemoteSocket.get(); socket.connect(SERVER_URL, this::handleMessage);
        try { JSONObject j=new JSONObject().put("type","REGISTER_TARGET").put("code",CODE); socket.send(j); }catch(Exception ignored){}
        findViewById(R.id.accessibility).setOnClickListener(v->{ startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });
        findViewById(R.id.share).setOnClickListener(v->requestCapture());
        findViewById(R.id.stop).setOnClickListener(v->stopService(new Intent(this,ScreenCaptureService.class)));
    }
    void requestCapture(){ MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE); startActivityForResult(m.createScreenCaptureIntent(),REQ_CAPTURE); }
    @Override protected void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(r==REQ_CAPTURE && c==RESULT_OK && data!=null){ Intent i=new Intent(this,ScreenCaptureService.class); i.putExtra("resultCode",c); i.putExtra("data",data); startForegroundServiceCompat(i); status.setText("Berbagi layar aktif"); } }
    void startForegroundServiceCompat(Intent i){ if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }
    void handleMessage(JSONObject j){ runOnUiThread(()->{ try { String t=j.getString("type"); if(t.equals("CONTROL")){ RemoteAccessibilityService.control(j.getJSONObject("payload")); } else if(t.equals("ACCESS_REQUEST")){ new AlertDialog.Builder(this).setTitle("Permintaan Remote Support").setMessage("Controller: "+j.optString("controllerName","Unknown")+"\n\nIzinkan melihat dan mengontrol layar?").setNegativeButton("Tolak",(d,w)->{try{socket.send(new JSONObject().put("type","REJECT_SESSION"));}catch(Exception ignored){}}).setPositiveButton("Izinkan",(d,w)->{try{socket.send(new JSONObject().put("type","APPROVE_SESSION"));}catch(Exception ignored){ } requestCapture();}).show(); } else if(t.equals("SESSION_ENDED")){ status.setText("Sesi berakhir"); stopService(new Intent(this,ScreenCaptureService.class)); } }catch(Exception ignored){} }); }
}
