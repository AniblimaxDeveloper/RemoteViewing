package com.emotexware.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import java.io.ByteArrayOutputStream;
import okio.ByteString;

public class ScreenCaptureService extends Service {
    static final int NOTIFICATION_ID = 42;
    MediaProjection projection;
    VirtualDisplay display;
    ImageReader reader;
    HandlerThread thread;
    Handler handler;

    @Override public void onCreate(){
        super.onCreate();
        thread = new HandlerThread("emotex-capture"); thread.start();
        handler = new Handler(thread.getLooper());
        createNotification();
    }

    private void createNotification(){
        NotificationChannel ch = new NotificationChannel("emotex-remote","Emotexware Remote",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this,"emotex-remote")
                .setContentTitle("Emotexware: screen sharing active")
                .setContentText("Layar sedang dibagikan dengan persetujuan pemilik perangkat.")
                .setSmallIcon(android.R.drawable.ic_menu_view).build();
        if(Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID,n);
    }

    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent == null) return START_NOT_STICKY;
        Intent data = intent.getParcelableExtra("data");
        int result = intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);
        if(data != null && projection == null){
            MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(result,data);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            int density = getResources().getDisplayMetrics().densityDpi;
            reader = ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            display = projection.createVirtualDisplay("Emotexware",w,h,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
            reader.setOnImageAvailableListener(r -> sendFrame(r,w,h),handler);
        }
        return START_NOT_STICKY;
    }

    private void sendFrame(ImageReader r,int w,int h){
        Image img = null;
        try{
            img = r.acquireLatestImage();
            if(img == null) return;
            Image.Plane p = img.getPlanes()[0];
            int ps = p.getPixelStride(), rs = p.getRowStride();
            Bitmap bm = Bitmap.createBitmap(rs/ps,h,Bitmap.Config.ARGB_8888);
            bm.copyPixelsFromBuffer(p.getBuffer());
            if(bm.getWidth()!=w) bm = Bitmap.createBitmap(bm,0,0,w,h);
            if(w > 720){ int nh = (int)(h*(720f/w)); bm = Bitmap.createScaledBitmap(bm,720,nh,true); }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG,55,out);
            RemoteSocket.get().send(ByteString.of(out.toByteArray()));
            bm.recycle();
        } catch(Exception ignored){}
        finally{ if(img!=null) img.close(); }
    }

    @Override public void onDestroy(){
        if(display!=null) display.release();
        if(reader!=null) reader.close();
        if(projection!=null) projection.stop();
        if(thread!=null) thread.quitSafely();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){ return null; }
}
