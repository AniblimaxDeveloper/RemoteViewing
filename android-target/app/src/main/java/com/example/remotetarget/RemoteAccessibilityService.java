package com.example.remotetarget;

import android.accessibilityservice.*; import android.graphics.Path; import android.graphics.PointF; import android.view.accessibility.AccessibilityEvent; import org.json.JSONObject;

public class RemoteAccessibilityService extends AccessibilityService {
    static RemoteAccessibilityService instance;
    @Override public void onServiceConnected(){ instance=this; }
    @Override public void onDestroy(){ if(instance==this) instance=null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    public static void control(JSONObject j){ if(instance!=null) instance.handle(j); }
    void handle(JSONObject j){ try { String action=j.getString("action"); if(action.equals("back")) performGlobalAction(GLOBAL_ACTION_BACK); else if(action.equals("home")) performGlobalAction(GLOBAL_ACTION_HOME); else if(action.equals("recents")) performGlobalAction(GLOBAL_ACTION_RECENTS); else if(action.equals("tap")) tap((float)j.getDouble("x"),(float)j.getDouble("y")); else if(action.equals("swipe")) swipe((float)j.getDouble("x1"),(float)j.getDouble("y1"),(float)j.getDouble("x2"),(float)j.getDouble("y2"),(long)j.optLong("duration",350)); }catch(Exception ignored){} }
    void tap(float x,float y){ Path p=new Path(); p.moveTo(x,y); GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,60)).build(); dispatchGesture(g,null,null); }
    void swipe(float x1,float y1,float x2,float y2,long duration){ Path p=new Path(); p.moveTo(x1,y1); p.lineTo(x2,y2); GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(100,duration))).build(); dispatchGesture(g,null,null); }
}
