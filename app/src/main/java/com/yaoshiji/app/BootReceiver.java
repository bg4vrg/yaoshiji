package com.yaoshiji.app;

import android.content.*;
import android.os.UserManager;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String a=i.getAction();
        if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)){
            AlarmScheduler.rescheduleFromDirectBoot(c);
            ReminderGuardService.startIfEnabled(c);
            return;
        }
        UserManager um=(UserManager)c.getSystemService(Context.USER_SERVICE);
        if(um==null||um.isUserUnlocked())AlarmScheduler.rescheduleAll(c); else AlarmScheduler.rescheduleFromDirectBoot(c);
        ReminderGuardService.startIfEnabled(c);
    }
}
