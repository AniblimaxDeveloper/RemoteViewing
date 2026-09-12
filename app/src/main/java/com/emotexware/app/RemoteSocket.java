package com.emotexware.app;

import android.util.Log;
import org.json.JSONObject;
import okhttp3.*;
import okio.ByteString;

public final class RemoteSocket {
    private static final String TAG = "EmotexwareWS";
    private static final RemoteSocket INSTANCE = new RemoteSocket();
    private WebSocket socket;
    private Listener listener;
    private String url;

    public interface Listener {
        void onText(JSONObject json);
        void onBinary(ByteString bytes);
        void onFailure(Throwable t);
    }

    public static RemoteSocket get(){ return INSTANCE; }

    public synchronized void connect(String url, Listener listener){
        this.listener = listener;
        this.url = url;
        if(socket != null) socket.cancel();
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
        Request request = new Request.Builder().url(url).build();
        socket = client.newWebSocket(request, new WebSocketListener(){
            @Override public void onMessage(WebSocket ws, String text){
                try { if(RemoteSocket.this.listener != null) RemoteSocket.this.listener.onText(new JSONObject(text)); }
                catch(Exception e){ Log.e(TAG, "Invalid JSON", e); }
            }
            @Override public void onMessage(WebSocket ws, ByteString bytes){
                if(RemoteSocket.this.listener != null) RemoteSocket.this.listener.onBinary(bytes);
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r){
                if(RemoteSocket.this.listener != null) RemoteSocket.this.listener.onFailure(t);
            }
        });
    }

    public boolean send(JSONObject json){ return socket != null && socket.send(json.toString()); }
    public boolean send(ByteString bytes){ return socket != null && socket.send(bytes); }
    public void close(){ if(socket != null) socket.close(1000, "closed"); }
}
