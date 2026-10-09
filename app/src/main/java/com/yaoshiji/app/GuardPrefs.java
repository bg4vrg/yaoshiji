package com.yaoshiji.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings for the optional persistent reminder guard. Stored in device-protected storage. */
public final class GuardPrefs {
    private static final String PREF="reminder_guard_v1";
    private static final String KEY="enabled";
    private static SharedPreferences sp(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static boolean enabled(Context c){return sp(c).getBoolean(KEY,true);}
    public static void setEnabled(Context c,boolean enabled){sp(c).edit().putBoolean(KEY,enabled).apply();}
}
