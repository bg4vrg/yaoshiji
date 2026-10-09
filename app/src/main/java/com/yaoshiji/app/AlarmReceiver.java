package com.yaoshiji.app;

import android.content.*;
import android.os.UserManager;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i.getBooleanExtra("warmup",false)){
            ReminderGuardService.startIfEnabled(c);
            return;
        }
        boolean test=i.getBooleanExtra("test",false);
        if(test){
            NotificationHelper.postTest(c,900001);
        }else{
            String name=i.getStringExtra("name"),dose=i.getStringExtra("dose"),meal=i.getStringExtra("meal"),date=i.getStringExtra("date"),time=i.getStringExtra("time");
            int seq=i.getIntExtra("seq",1),id=i.getIntExtra("requestCode",Math.abs((String.valueOf(name)+System.currentTimeMillis()/60000).hashCode()));
            long when=i.getLongExtra("when",0);
            UserManager unlock=(UserManager)c.getSystemService(Context.USER_SERVICE);
            boolean available=unlock==null||unlock.isUserUnlocked();
            long scheduleId=i.getLongExtra("scheduleId",0);
            // Defense in depth against an alarm already in the broadcast queue.
            if(!available || !AlarmScheduler.isDoseTaken(c,scheduleId,date)){
                NotificationHelper.postMedication(c,id,name,dose,meal,date,time,seq);
                ReminderGuardService.startIfEnabled(c);
            }
            if(when>0)DirectBootStore.markDelivered(c,id,when);
        }
        UserManager um=(UserManager)c.getSystemService(Context.USER_SERVICE);
        if(um==null||um.isUserUnlocked())AlarmScheduler.rescheduleAll(c);
    }
}
