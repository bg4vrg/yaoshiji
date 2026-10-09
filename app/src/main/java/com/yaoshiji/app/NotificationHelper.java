package com.yaoshiji.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class NotificationHelper {
    // New channel ID forces Android/HarmonyOS to apply the V1.2 alarm-channel settings instead of reusing V1.1 settings.
    public static final String CHANNEL_SHORT="medication_alarm_short_v1";
    public static final String CHANNEL_SYSTEM="medication_alarm_system_v1";
    public static final String CHANNEL_SILENT="medication_alarm_silent_v1";

    public static void ensure(Context c){
        if(Build.VERSION.SDK_INT>=26){NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);createChannel(c,n,CHANNEL_SHORT,"用药提醒 · 短提示音",ReminderSoundPrefs.SHORT);createChannel(c,n,CHANNEL_SYSTEM,"用药提醒 · 系统通知音",ReminderSoundPrefs.SYSTEM);createChannel(c,n,CHANNEL_SILENT,"用药提醒 · 静音",ReminderSoundPrefs.SILENT);}
    }
    private static void createChannel(Context c,NotificationManager n,String id,String name,int mode){NotificationChannel ch=new NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH);ch.setDescription("按计划时间发出高优先级用药提醒");ch.enableVibration(true);ch.setVibrationPattern(new long[]{0,320,150,420});ch.enableLights(true);ch.setLightColor(Color.BLUE);ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);try{AudioAttributes a=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();if(mode==ReminderSoundPrefs.SILENT)ch.setSound(null,null);else if(mode==ReminderSoundPrefs.SYSTEM)ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,a);else ch.setSound(Uri.parse("android.resource://"+c.getPackageName()+"/"+com.yaoshiji.app.R.raw.reminder_short),a);}catch(Exception ignored){}n.createNotificationChannel(ch);}
    private static String channel(Context c){int m=ReminderSoundPrefs.mode(c);return m==ReminderSoundPrefs.SYSTEM?CHANNEL_SYSTEM:m==ReminderSoundPrefs.SILENT?CHANNEL_SILENT:CHANNEL_SHORT;}

    private static PendingIntent mainPi(Context c,int id,String date){
        Intent i=new Intent(c,MainActivity.class);if(date!=null)i.putExtra("date",date);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    public static void postMedication(Context c,int id,String name,String dose,String meal,String date,String time,int seq){
        ensure(c);String dateText=formatDate(date);String title="该吃药了 · "+safe(time);
        String compact=safe(name)+(dose==null||dose.isEmpty()?"":" · "+dose)+(meal==null||meal.isEmpty()?"":" · "+meal);
        String big="用药方案："+safe(name)+"\n计划时间："+dateText+" "+safe(time)+"（第"+seq+"次）\n用量："+safe(dose)+(meal==null||meal.isEmpty()?"":"\n用药时机："+meal);
        Intent alert=new Intent(c,ReminderAlertActivity.class);alert.putExtra("name",name);alert.putExtra("dose",dose);alert.putExtra("meal",meal);alert.putExtra("date",date);alert.putExtra("time",time);alert.putExtra("seq",seq);alert.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent full=PendingIntent.getActivity(c,id+300000,alert,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent content=mainPi(c,id,date);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel(c)):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(compact).setSubText(dateText+" · 第"+seq+"次")
         .setStyle(new Notification.BigTextStyle().bigText(big)).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
         .setAutoCancel(true).setContentIntent(content).setFullScreenIntent(full,true).setPriority(Notification.PRIORITY_MAX)
         .setVibrate(new long[]{0,320,150,420}).addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_view,"打开打卡",content).build());
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,b.build());
    }

    public static void postTest(Context c,int id){
        ensure(c);String now=java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.CHINA));
        Intent alert=new Intent(c,ReminderAlertActivity.class).putExtra("test",true).putExtra("time",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))).putExtra("date",LocalDate.now().toString());
        PendingIntent full=PendingIntent.getActivity(c,id+300000,alert.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent content=mainPi(c,id,null);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel(c)):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("药时记测试提醒").setContentText(now+" · 基础提醒链路正常")
         .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(content).setFullScreenIntent(full,true).setPriority(Notification.PRIORITY_MAX).setVibrate(new long[]{0,320,150,420});
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id,b.build());
    }

    public static void maybePostLowStock(Context c,MedicationDb db,MedicationDb.Medication m){
        try{MedicationDb.StockProjection p=db.stockProjection(m);if(p==null||p.daysLeft>p.alertDays)return;String today=LocalDate.now().toString();android.content.SharedPreferences sp=c.getSharedPreferences("stock_alert",Context.MODE_PRIVATE);String key="last_"+m.groupUuid;if(today.equals(sp.getString(key,"")))return;ensure(c);String title="药量不足提醒 · "+m.name;String text="剩余约 "+fmtQty(p.current)+p.unit+"，预计可用到 "+p.runOutDate+"，建议尽快补充。";Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,channel(c)):new Notification.Builder(c);PendingIntent pi=mainPi(c,700000+(int)(m.id%100000),null);b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(pi).setPriority(Notification.PRIORITY_HIGH);((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(700000+(int)(m.id%100000),b.build());sp.edit().putString(key,today).apply();}catch(Exception ignored){}
    }
    private static String fmtQty(double x){return x==(long)x?String.valueOf((long)x):String.valueOf(x);}
    private static String safe(String s){return s==null?"":s;}
    private static String formatDate(String date){try{LocalDate d=LocalDate.parse(date);String[] w={"一","二","三","四","五","六","日"};return d+" 周"+w[d.getDayOfWeek().getValue()-1];}catch(Exception e){return date==null?"":date;}}
}
