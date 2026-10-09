package com.yaoshiji.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.time.*;
import java.util.*;

public final class AlarmScheduler {
    public static boolean canExact(Context c){
        if(Build.VERSION.SDK_INT<31)return true;
        return ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }
    public static void openExactSettings(Context c){
        if(Build.VERSION.SDK_INT>=31){
            try{Intent i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+c.getPackageName()));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}catch(Exception ignored){}
        }
    }

    public static void rescheduleAll(Context c){
        MedicationDb db=new MedicationDb(c);
        cancelAllKnown(c,db);
        ArrayList<DirectBootStore.AlarmItem> mirror=new ArrayList<>();
        LocalDate today=LocalDate.now();
        for(int day=0;day<46;day++){
            LocalDate d=today.plusDays(day);
            for(MedicationDb.Task t:db.tasksFor(d)){
                if(t.taken())continue; // Completed dose: never recreate its reminder.
                LocalDateTime ldt=d.atTime(LocalTime.parse(t.dose.time));
                long when=ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if(when<=System.currentTimeMillis())continue;
                int req=requestCode(t.dose.id,d);
                DirectBootStore.AlarmItem a=new DirectBootStore.AlarmItem(req,when,t.dose.id,t.dose.seq,t.medication.name,formatDose(t.dose),t.medication.mealTiming,d.toString(),t.dose.time);
                scheduleOne(c,a);
                mirror.add(a);
            }
        }
        // Keep a minimal future-alarm mirror in device-protected storage so a
        // reboot can restore reminders before the first PIN/password unlock.
        DirectBootStore.save(c,mirror);
    }

    /** True if this precise scheduled dose has already been recorded as taken. */
    public static boolean isDoseTaken(Context c,long scheduleId,String date){
        if(scheduleId<=0||date==null)return false;
        try{
            MedicationDb db=new MedicationDb(c);
            MedicationDb.Record r=db.record(scheduleId,LocalDate.parse(date));
            return r!=null && r.taken();
        }catch(Exception e){return false;}
    }

    public static void dismissDoseNotification(Context c,long scheduleId,LocalDate date){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(requestCode(scheduleId,date));
    }

    public static void rescheduleFromDirectBoot(Context c){
        long now=System.currentTimeMillis();
        for(DirectBootStore.AlarmItem a:DirectBootStore.load(c))if(a.when>now)scheduleOne(c,a);
    }

    private static void scheduleOne(Context c,DirectBootStore.AlarmItem a){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(c,AlarmReceiver.class);
        i.putExtra("name",a.name);i.putExtra("dose",a.dose);i.putExtra("meal",a.meal);i.putExtra("date",a.date);i.putExtra("time",a.time);i.putExtra("seq",a.seq);i.putExtra("scheduleId",a.scheduleId);i.putExtra("requestCode",a.requestCode);i.putExtra("when",a.when);i.putExtra("test",false);
        PendingIntent pi=PendingIntent.getBroadcast(c,a.requestCode,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()){
                // Alarm-clock class scheduling is intentionally used for medication
                // reminders: user configured, time critical, and expected to wake the device.
                PendingIntent show=PendingIntent.getActivity(c,a.requestCode,new Intent(c,MainActivity.class).putExtra("date",a.date),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(a.when,show),pi);
            }else if(Build.VERSION.SDK_INT<31){
                PendingIntent show=PendingIntent.getActivity(c,a.requestCode,new Intent(c,MainActivity.class).putExtra("date",a.date),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(a.when,show),pi);
            }else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,a.when,pi);
            else am.setExact(AlarmManager.RTC_WAKEUP,a.when,pi);
        }catch(Exception e){
            try{if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,a.when,pi);else am.set(AlarmManager.RTC_WAKEUP,a.when,pi);}catch(Exception ignored){}
        }
        // Five minutes before the dose, attempt to wake the reminder guard.
        // This is only a best-effort compatibility-layer assist; the actual dose alarm above remains authoritative.
        long warmAt=a.when-5*60*1000L;
        if(warmAt>System.currentTimeMillis()+5000){
            Intent w=new Intent(c,AlarmReceiver.class);w.putExtra("warmup",true);w.putExtra("requestCode",warmRequestCode(a.requestCode));
            PendingIntent wp=PendingIntent.getBroadcast(c,warmRequestCode(a.requestCode),w,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            try{if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,warmAt,wp);else am.setExact(AlarmManager.RTC_WAKEUP,warmAt,wp);}catch(Exception ignored){}
        }
    }

    public static void cancelAllKnown(Context c,MedicationDb db){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Cursor cur=db.getReadableDatabase().rawQuery("SELECT id FROM dose_schedule",null);
        LocalDate base=LocalDate.now().minusDays(2);
        while(cur.moveToNext()){
            long sid=cur.getLong(0);
            for(int x=0;x<95;x++){
                LocalDate d=base.plusDays(x);int req=requestCode(sid,d);
                Intent i=new Intent(c,AlarmReceiver.class);
                PendingIntent pi=PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(pi!=null){am.cancel(pi);pi.cancel();}
                Intent wi=new Intent(c,AlarmReceiver.class);PendingIntent wpi=PendingIntent.getBroadcast(c,warmRequestCode(req),wi,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(wpi!=null){am.cancel(wpi);wpi.cancel();}
            }
        }
        cur.close();
    }

    public static void scheduleTest(Context c,long when){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(c,AlarmReceiver.class);i.putExtra("test",true);i.putExtra("requestCode",900001);
        PendingIntent p=PendingIntent.getBroadcast(c,900001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            if((Build.VERSION.SDK_INT<31)||am.canScheduleExactAlarms()){
                PendingIntent show=PendingIntent.getActivity(c,900001,new Intent(c,ReminderCheckActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(when,show),p);
            }else if(Build.VERSION.SDK_INT>=23)am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);else am.set(AlarmManager.RTC_WAKEUP,when,p);
        }catch(Exception e){am.set(AlarmManager.RTC_WAKEUP,when,p);}
    }

    private static int requestCode(long id,LocalDate d){return Math.abs(Objects.hash(id,d.toString()));}
    private static int warmRequestCode(int actual){return actual ^ 0x3f3f3f3f;}
    public static String formatDose(MedicationDb.Dose d){double a=d.amount;return (a==(long)a?String.valueOf((long)a):String.valueOf(a))+d.unit;}
}
