package com.yaoshiji.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import java.util.*;

/**
 * Experimental foreground guard for compatibility-layer devices.
 * It keeps a visible service alive and independently checks the device-protected alarm mirror.
 * This is a fallback in addition to AlarmManager; it does not replace system alarms.
 */
public class ReminderGuardService extends Service {
    private static final String CHANNEL="reminder_guard_v1";
    private static final int NOTIFICATION_ID=880011;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){@Override public void run(){checkDueAndRefresh();handler.postDelayed(this,15000);}};

    public static void startIfEnabled(Context c){
        if(!GuardPrefs.enabled(c))return;
        try{Intent i=new Intent(c,ReminderGuardService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}catch(Exception ignored){}
    }
    public static void stop(Context c){try{c.stopService(new Intent(c,ReminderGuardService.class));}catch(Exception ignored){}}

    @Override public void onCreate(){super.onCreate();ensureChannel();startForeground(NOTIFICATION_ID,buildGuardNotification("正在保护用药提醒"));handler.post(tick);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){checkDueAndRefresh();return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacks(tick);if(GuardPrefs.enabled(this))scheduleRestart(30000);super.onDestroy();}
    @Override public void onTaskRemoved(Intent rootIntent){if(GuardPrefs.enabled(this))scheduleRestart(15000);super.onTaskRemoved(rootIntent);}

    private void scheduleRestart(long delay){
        try{
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            Intent i=new Intent(this,GuardRestartReceiver.class);
            PendingIntent p=PendingIntent.getBroadcast(this,880012,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            long at=System.currentTimeMillis()+delay;
            if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);else am.setExact(AlarmManager.RTC_WAKEUP,at,p);
        }catch(Exception ignored){}
    }

    private void checkDueAndRefresh(){
        long now=System.currentTimeMillis();
        List<DirectBootStore.AlarmItem> items=DirectBootStore.load(this);
        DirectBootStore.AlarmItem next=null;
        UserManager um=(UserManager)getSystemService(USER_SERVICE);
        boolean unlocked=um==null||um.isUserUnlocked();
        for(DirectBootStore.AlarmItem a:items){
            if(unlocked && AlarmScheduler.isDoseTaken(this,a.scheduleId,a.date))continue;
            if(a.when>=now && (next==null||a.when<next.when))next=a;
            if(a.when<=now && a.when>=now-120000 && !DirectBootStore.wasDelivered(this,a.requestCode,a.when)){
                NotificationHelper.postMedication(this,a.requestCode,a.name,a.dose,a.meal,a.date,a.time,a.seq);
                DirectBootStore.markDelivered(this,a.requestCode,a.when);
            }
        }
        String text="暂无未来用药提醒";
        if(next!=null){
            long min=Math.max(0,(next.when-now+59999)/60000);
            text="下一次 "+next.date+" "+next.time+" · "+next.name+(min<=60?" · 约"+min+"分钟后":"");
        }
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID,buildGuardNotification(text));
    }

    private void ensureChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch=new NotificationChannel(CHANNEL,"提醒守护服务",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持药时记提醒守护运行；关闭可能降低卓易通环境下的提醒可靠性");
            ch.setShowBadge(false);ch.enableLights(false);ch.enableVibration(false);ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            n.createNotificationChannel(ch);
        }
    }
    private Notification buildGuardNotification(String text){
        Intent open=new Intent(this,ReminderCheckActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,NOTIFICATION_ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("药时记提醒守护已开启").setContentText(text)
         .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
         .setPriority(Notification.PRIORITY_MIN).setColor(Color.rgb(22,119,255));
        return b.build();
    }
}
