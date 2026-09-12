package com.emotexware.app;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.provider.Settings;
import android.view.MotionEvent;
import android.widget.*;
import org.json.JSONObject;
import java.util.UUID;
import okio.ByteString;

public class MainActivity extends Activity {
    // Change this to your server address.
    private static final String SERVER_URL = "ws://192.168.1.10:8080";
    private static final int REQ_CAPTURE = 7001;

    LinearLayout controllerPanel, targetPanel;
    EditText modelInput;
    TextView controllerStatus, modelView, targetStatus;
    ImageView screen;
    RemoteSocket socket;
    Bitmap last;
    String model, installId;
    float downX,downY; long downAt;
    boolean controllerMode = true;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        controllerPanel=findViewById(R.id.controllerPanel);
        targetPanel=findViewById(R.id.targetPanel);
        modelInput=findViewById(R.id.modelInput);
        controllerStatus=findViewById(R.id.controllerStatus);
        modelView=findViewById(R.id.modelView);
        targetStatus=findViewById(R.id.targetStatus);
        screen=findViewById(R.id.screen);
        model=((Build.MANUFACTURER==null?"":Build.MANUFACTURER.trim())+" "+(Build.MODEL==null?"Unknown":Build.MODEL.trim())).trim();
        installId=getSharedPreferences("emotex",MODE_PRIVATE).getString("install_id",null);
        if(installId==null){ installId=UUID.randomUUID().toString(); getSharedPreferences("emotex",MODE_PRIVATE).edit().putString("install_id",installId).apply(); }
        modelView.setText(model);

        findViewById(R.id.modeController).setOnClickListener(v->setMode(true));
        findViewById(R.id.modeTarget).setOnClickListener(v->setMode(false));
        findViewById(R.id.connect).setOnClickListener(v->connectController());
        findViewById(R.id.disconnect).setOnClickListener(v->{sendControl(actionObj("disconnect")); closeSession();});
        findViewById(R.id.back).setOnClickListener(v->sendControl(actionObj("back")));
        findViewById(R.id.home).setOnClickListener(v->sendControl(actionObj("home")));
        findViewById(R.id.recents).setOnClickListener(v->sendControl(actionObj("recents")));
        findViewById(R.id.accessibility).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.share).setOnClickListener(v->requestCapture());
        findViewById(R.id.stop).setOnClickListener(v->{ stopService(new Intent(this,ScreenCaptureService.class)); targetStatus.setText("Sesi dihentikan"); });
        screen.setOnTouchListener((v,e)->touch(e));
    }

    private void setMode(boolean controller){
        controllerMode=controller;
        controllerPanel.setVisibility(controller?android.view.View.VISIBLE:android.view.View.GONE);
        targetPanel.setVisibility(controller?android.view.View.GONE:android.view.View.VISIBLE);
        if(controller && socket!=null){ socket.close(); socket=null; }
        if(!controller) connectTarget();
    }

    private void connectTarget(){
        socket=RemoteSocket.get();
        socket.connect(SERVER_URL,new RemoteSocket.Listener(){
            @Override public void onText(JSONObject j){ handleTarget(j); }
            @Override public void onBinary(ByteString b){}
            @Override public void onFailure(Throwable t){ runOnUiThread(()->targetStatus.setText("Koneksi gagal")); }
        });
        new Handler().postDelayed(()->{
            try{ socket.send(new JSONObject().put("type","REGISTER_TARGET").put("deviceKey",model).put("model",model).put("installId",installId)); }
            catch(Exception ignored){}
        },500);
    }

    private void connectController(){
        String wanted=modelInput.getText().toString().trim();
        if(wanted.isEmpty()){ controllerStatus.setText("Masukkan model, misalnya OPPO CPH2477"); return; }
        socket=RemoteSocket.get();
        socket.connect(SERVER_URL,new RemoteSocket.Listener(){
            @Override public void onText(JSONObject j){
                runOnUiThread(()->{
                    try{
                        String t=j.getString("type");
                        if(t.equals("WAITING_APPROVAL")) controllerStatus.setText("Menunggu persetujuan perangkat...");
                        else if(t.equals("SESSION_APPROVED")) controllerStatus.setText("Terhubung");
                        else if(t.equals("SESSION_REJECTED")) controllerStatus.setText("Permintaan ditolak");
                        else if(t.equals("ERROR")) controllerStatus.setText(j.optString("message","Error"));
                    }catch(Exception ignored){}
                });
            }
            @Override public void onBinary(ByteString b){
                Bitmap bm=BitmapFactory.decodeByteArray(b.toByteArray(),0,b.size());
                if(bm!=null) screen.post(()->{ if(last!=null&&!last.isRecycled())last.recycle(); last=bm; screen.setImageBitmap(bm); });
            }
            @Override public void onFailure(Throwable t){ controllerStatus.post(()->controllerStatus.setText("Koneksi gagal")); }
        });
        new Handler().postDelayed(()->{
            try{ socket.send(new JSONObject().put("type","REQUEST_SESSION").put("deviceKey",wanted).put("controllerName","Emotexware")); }
            catch(Exception ignored){}
        },500);
    }

    private void handleTarget(JSONObject j){
        runOnUiThread(()->{
            try{
                String t=j.getString("type");
                if(t.equals("REGISTERED")) targetStatus.setText("Online • siap menerima permintaan");
                else if(t.equals("ACCESS_REQUEST")){
                    new AlertDialog.Builder(this)
                        .setTitle("Permintaan Remote Support")
                        .setMessage("Perangkat lain ingin melihat/mengendalikan perangkat ini ("+model+").")
                        .setNegativeButton("Tolak",(d,w)->sendType("REJECT_SESSION"))
                        .setPositiveButton("Izinkan",(d,w)->{ sendType("APPROVE_SESSION"); requestCapture(); })
                        .setOnCancelListener(d->sendType("REJECT_SESSION"))
                        .show();
                } else if(t.equals("CONTROL")) RemoteAccessibilityService.control(j.optJSONObject("payload"));
                else if(t.equals("SESSION_ENDED")) { stopService(new Intent(this,ScreenCaptureService.class)); targetStatus.setText("Sesi berakhir"); }
            }catch(Exception ignored){}
        });
    }

    private void requestCapture(){
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),REQ_CAPTURE);
    }
    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==REQ_CAPTURE && c==RESULT_OK && data!=null){
            Intent i=new Intent(this,ScreenCaptureService.class); i.putExtra("resultCode",c); i.putExtra("data",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            targetStatus.setText("Berbagi layar aktif");
        }
    }

    private JSONObject actionObj(String action){ try{return new JSONObject().put("action",action);}catch(Exception e){return new JSONObject();} }
    private void sendControl(JSONObject p){ if(socket!=null) try{socket.send(new JSONObject().put("type","CONTROL").put("payload",p));}catch(Exception ignored){} }
    private void sendType(String type){ if(socket!=null) try{socket.send(new JSONObject().put("type",type));}catch(Exception ignored){} }
    private void closeSession(){ if(socket!=null)socket.close(); socket=null; controllerStatus.setText("Disconnected"); }

    private boolean touch(MotionEvent e){
        if(last==null || !controllerMode) return true;
        float nx=e.getX()/Math.max(1f,screen.getWidth());
        float ny=e.getY()/Math.max(1f,screen.getHeight());
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=nx;downY=ny;downAt=System.currentTimeMillis();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            float dx=nx-downX,dy=ny-downY;
            try{
                if(Math.hypot(dx,dy)<0.04) sendControl(new JSONObject().put("action","tap").put("x",downX).put("y",downY));
                else sendControl(new JSONObject().put("action","swipe").put("x1",downX).put("y1",downY).put("x2",nx).put("y2",ny).put("duration",Math.max(150,System.currentTimeMillis()-downAt)));
            }catch(Exception ignored){}
            return true;
        }
        return true;
    }
}
