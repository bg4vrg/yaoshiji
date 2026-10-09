package com.yaoshiji.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal mirror of future reminders in device-protected storage.
 * This is intentionally not the main medication database. It only contains
 * enough data to restore alarms before the first unlock after a reboot.
 */
public final class DirectBootStore {
    private static final String PREF = "direct_boot_alarm_mirror_v1";
    private static final String KEY = "alarms";
    private static final String DELIVERED = "delivered";

    public static class AlarmItem {
        public int requestCode;
        public long when;
        public long scheduleId;
        public int seq;
        public String name, dose, meal, date, time;
        public AlarmItem(int requestCode,long when,long scheduleId,int seq,String name,String dose,String meal,String date,String time){
            this.requestCode=requestCode;this.when=when;this.scheduleId=scheduleId;this.seq=seq;
            this.name=name;this.dose=dose;this.meal=meal;this.date=date;this.time=time;
        }
    }

    private static Context dp(Context c){ return c.createDeviceProtectedStorageContext(); }
    private static String b64(String s){
        if(s==null)s="";
        return android.util.Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);
    }
    private static String unb64(String s){
        try{return new String(android.util.Base64.decode(s,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE),StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
    private static String encode(AlarmItem a){
        return a.requestCode+"\t"+a.when+"\t"+a.scheduleId+"\t"+a.seq+"\t"+b64(a.name)+"\t"+b64(a.dose)+"\t"+b64(a.meal)+"\t"+b64(a.date)+"\t"+b64(a.time);
    }
    private static AlarmItem decode(String s){
        try{
            String[] p=s.split("\\t",-1); if(p.length<9)return null;
            return new AlarmItem(Integer.parseInt(p[0]),Long.parseLong(p[1]),Long.parseLong(p[2]),Integer.parseInt(p[3]),unb64(p[4]),unb64(p[5]),unb64(p[6]),unb64(p[7]),unb64(p[8]));
        }catch(Exception e){return null;}
    }
    public static void save(Context c,List<AlarmItem> items){
        Set<String> set=new HashSet<>(); for(AlarmItem a:items)set.add(encode(a));
        dp(c).getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putStringSet(KEY,set).commit();
    }
    public static boolean wasDelivered(Context c,int requestCode,long when){
        SharedPreferences sp=dp(c).getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long x=sp.getLong(DELIVERED+"_"+requestCode,0);
        return x==when;
    }
    public static void markDelivered(Context c,int requestCode,long when){
        dp(c).getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putLong(DELIVERED+"_"+requestCode,when).apply();
    }

    public static List<AlarmItem> load(Context c){
        SharedPreferences sp=dp(c).getSharedPreferences(PREF,Context.MODE_PRIVATE);
        Set<String> set=sp.getStringSet(KEY,Collections.emptySet());
        ArrayList<AlarmItem> out=new ArrayList<>(); for(String s:set){AlarmItem a=decode(s);if(a!=null)out.add(a);}
        out.sort(Comparator.comparingLong(x->x.when)); return out;
    }
}
