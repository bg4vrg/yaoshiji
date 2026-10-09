package com.yaoshiji.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class GuardRestartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(!GuardPrefs.enabled(c))return;
        try{
            Intent s=new Intent(c,ReminderGuardService.class);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);
        }catch(Exception ignored){}
    }
}
