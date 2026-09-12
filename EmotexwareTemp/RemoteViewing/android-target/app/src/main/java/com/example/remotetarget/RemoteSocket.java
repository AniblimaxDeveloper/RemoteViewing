package com.example.remotetarget;

import android.util.Log;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;

public final class RemoteSocket {
    private static final String TAG = "RemoteSocket";
    private static final RemoteSocket INSTANCE = new RemoteSocket();
    private WebSocket socket;
    private Listener listener;
    private String url;

    public interface Listener { void onText(JSONObject json); }
    public static RemoteSocket get(){ return INSTANCE; }
    public void connect(String url, Listener listener){
        this.listener = listener; this.url = url;
        if(socket != null) socket.cancel();
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(url).build();
        socket = client.newWebSocket(request, new WebSocketListener(){
            @Override public void onMessage(WebSocket ws, String text){ try { if(RemoteSocket.this.listener!=null) RemoteSocket.this.listener.onText(new JSONObject(text)); } catch(Exception e){ Log.e(TAG,"JSON",e); } }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r){ Log.e(TAG,"WS",t); }
        });
    }
    public void send(JSONObject json){ if(socket!=null) socket.send(json.toString()); }
    public void send(ByteString bytes){ if(socket!=null) socket.send(bytes); }
    public void close(){ if(socket!=null) socket.close(1000,"closed"); }
}
