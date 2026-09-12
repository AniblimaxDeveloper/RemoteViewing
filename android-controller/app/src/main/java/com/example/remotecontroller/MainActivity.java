package com.example.remotecontroller;

import android.app.*; import android.graphics.*; import android.os.*; import android.view.*; import android.widget.*; import java.nio.ByteBuffer; import okhttp3.*; import okio.ByteString; import org.json.JSONObject;

public class MainActivity extends Activity {
    static final String SERVER_URL="ws://192.168.1.10:8080"; // GANTI
    WebSocket ws; EditText code; TextView status; ImageView screen; float downX,downY; long downAt; Bitmap last;
    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);code=findViewById(R.id.code);status=findViewById(R.id.status);screen=findViewById(R.id.screen);
        findViewById(R.id.connect).setOnClickListener(v->connect());
        findViewById(R.id.disconnect).setOnClickListener(v->{sendControl("disconnect"); if(ws!=null)ws.close(1000,"stop");});
        findViewById(R.id.back).setOnClickListener(v->sendControl("back")); findViewById(R.id.home).setOnClickListener(v->sendControl("home")); findViewById(R.id.recents).setOnClickListener(v->sendControl("recents"));
        screen.setOnTouchListener((v,e)->touch(e)); }
    boolean touch(MotionEvent e){ if(last==null)return true; float nx=e.getX()/Math.max(1f,screen.getWidth()), ny=e.getY()/Math.max(1f,screen.getHeight()); if(e.getAction()==MotionEvent.ACTION_DOWN){downX=nx;downY=ny;downAt=System.currentTimeMillis();return true;} if(e.getAction()==MotionEvent.ACTION_UP){float dx=nx-downX,dy=ny-downY; if(Math.hypot(dx,dy)<0.04) sendPoint("tap",downX,downY); else {try{sendControl(new JSONObject().put("action","swipe").put("x1",downX).put("y1",downY).put("x2",nx).put("y2",ny).put("duration",Math.max(150,System.currentTimeMillis()-downAt)));}catch(Exception ignored){}} return true;}return true; }
    void connect(){String c=code.getText().toString().trim(); if(!c.matches("\\d{6}")){status.setText("Masukkan Device ID 6 digit");return;} OkHttpClient client=new OkHttpClient(); ws=client.newWebSocket(new Request.Builder().url(SERVER_URL).build(),new WebSocketListener(){
        @Override public void onOpen(WebSocket s,Response r){status.post(()->status.setText("Meminta akses...")); try{s.send(new JSONObject().put("type","REQUEST_SESSION").put("code",c).put("controllerName","Remote Controller").toString());}catch(Exception ignored){}}
        @Override public void onMessage(WebSocket s,String text){try{JSONObject j=new JSONObject(text);String t=j.getString("type"); status.post(()->status.setText(t));}catch(Exception ignored){}}
        @Override public void onMessage(WebSocket s,ByteString bytes){byte[] b=bytes.toByteArray(); Bitmap bm=BitmapFactory.decodeByteArray(b,0,b.length); if(bm!=null)screen.post(()->{if(last!=null)last.recycle();last=bm;screen.setImageBitmap(bm);});}
        @Override public void onFailure(WebSocket s,Throwable t,Response r){status.post(()->status.setText("Error: "+t.getMessage()));}
    }); }
    void sendPoint(String action,float x,float y){try{sendControl(new JSONObject().put("action",action).put("x",x).put("y",y));}catch(Exception ignored){}}
    void sendControl(String action){try{sendControl(new JSONObject().put("action",action));}catch(Exception ignored){}}
    void sendControl(JSONObject j){
        if(ws==null)return;
        try{
            JSONObject msg=new JSONObject();
            msg.put("type","CONTROL");
            msg.put("payload",j);
            ws.send(msg.toString());
        }catch(Exception e){
            status.post(()->status.setText("Gagal mengirim kontrol"));
        }
    }
}
