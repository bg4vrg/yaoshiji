package com.yaoshiji.app;

import android.content.*;

public final class ReminderSoundPrefs {
    private static final String PREF="reminder_sound";
    public static final int SHORT=0,SYSTEM=1,SILENT=2;
    public static int mode(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getInt("mode",SHORT);}
    public static void setMode(Context c,int mode){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putInt("mode",mode).apply();}
    public static String label(int m){return m==SYSTEM?"系统短通知音":m==SILENT?"静音（仅振动）":"药时记短提示音";}
}
