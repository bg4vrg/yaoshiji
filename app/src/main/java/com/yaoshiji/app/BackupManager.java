package com.yaoshiji.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Versioned, provider-neutral backup package. It intentionally excludes WebDAV credentials. */
public final class BackupManager {
    public static final int FORMAT_VERSION=2;
    public static final String APP_VERSION="1.3.2-beta";
    private static final String[] TABLES={"medication","dose_schedule","dose_record","stock_account","stock_txn"};

    public static byte[] createBackup(Context c) throws Exception{
        JSONObject root=new JSONObject();root.put("formatVersion",FORMAT_VERSION);root.put("appVersion",APP_VERSION);root.put("createdAt",System.currentTimeMillis());
        MedicationDb helper=new MedicationDb(c);SQLiteDatabase db=helper.getReadableDatabase();
        JSONObject tables=new JSONObject();for(String t:TABLES)tables.put(t,readTable(db,t));root.put("tables",tables);
        JSONObject settings=new JSONObject();settings.put("guardEnabled",GuardPrefs.enabled(c));settings.put("webdavAutoBackup",SecurePrefs.autoBackup(c));settings.put("reminderSoundMode",ReminderSoundPrefs.mode(c));root.put("settings",settings);helper.close();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out);
        ZipEntry manifest=new ZipEntry("manifest.json");zip.putNextEntry(manifest);JSONObject m=new JSONObject();m.put("formatVersion",FORMAT_VERSION);m.put("createdAt",root.getLong("createdAt"));m.put("appVersion",APP_VERSION);zip.write(m.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        ZipEntry data=new ZipEntry("data.json");zip.putNextEntry(data);zip.write(root.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.finish();zip.close();return out.toByteArray();
    }

    private static JSONArray readTable(SQLiteDatabase db,String table) throws Exception{
        JSONArray arr=new JSONArray();Cursor cur=db.rawQuery("SELECT * FROM "+table,null);
        try{while(cur.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<cur.getColumnCount();i++){String n=cur.getColumnName(i);switch(cur.getType(i)){case Cursor.FIELD_TYPE_NULL:o.put(n,JSONObject.NULL);break;case Cursor.FIELD_TYPE_INTEGER:o.put(n,cur.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:o.put(n,cur.getDouble(i));break;case Cursor.FIELD_TYPE_BLOB:o.put(n,Base64.getEncoder().encodeToString(cur.getBlob(i)));break;default:o.put(n,cur.getString(i));}}arr.put(o);}}finally{cur.close();}return arr;
    }

    public static File saveLocalSafetyCopy(Context c,byte[] bytes,String prefix) throws Exception{
        File dir=new File(c.getFilesDir(),"backups");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create backup directory");
        String ts=timestamp();File f=new File(dir,prefix+"_"+ts+".zip");try(FileOutputStream os=new FileOutputStream(f)){os.write(bytes);}return f;
    }

    public static void restore(Context c,byte[] zipBytes) throws Exception{
        byte[] json=null;try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(zipBytes))){ZipEntry e;while((e=zin.getNextEntry())!=null){if("data.json".equals(e.getName())){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=zin.read(buf))>0)b.write(buf,0,n);json=b.toByteArray();break;}}}
        if(json==null)throw new IOException("Backup data.json not found");JSONObject root=new JSONObject(new String(json,StandardCharsets.UTF_8));int fv=root.optInt("formatVersion",0);if(fv<1||fv>FORMAT_VERSION)throw new IOException("Unsupported backup format");JSONObject tables=root.getJSONObject("tables");
        MedicationDb helper=new MedicationDb(c);SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            db.delete("stock_txn",null,null);db.delete("stock_account",null,null);db.delete("dose_record",null,null);db.delete("dose_schedule",null,null);db.delete("medication",null,null);
            insertRows(db,"medication",tables.getJSONArray("medication"));insertRows(db,"dose_schedule",tables.getJSONArray("dose_schedule"));insertRows(db,"dose_record",tables.getJSONArray("dose_record"));if(tables.has("stock_account"))insertRows(db,"stock_account",tables.getJSONArray("stock_account"));if(tables.has("stock_txn"))insertRows(db,"stock_txn",tables.getJSONArray("stock_txn"));db.setTransactionSuccessful();
        }finally{db.endTransaction();helper.close();}
        JSONObject settings=root.optJSONObject("settings");if(settings!=null&&settings.has("guardEnabled"))GuardPrefs.setEnabled(c,settings.optBoolean("guardEnabled",true));if(settings!=null&&settings.has("reminderSoundMode"))ReminderSoundPrefs.setMode(c,settings.optInt("reminderSoundMode",ReminderSoundPrefs.SHORT));
        AlarmScheduler.rescheduleAll(c);if(GuardPrefs.enabled(c))ReminderGuardService.startIfEnabled(c);else ReminderGuardService.stop(c);
    }

    private static void insertRows(SQLiteDatabase db,String table,JSONArray rows) throws Exception{
        for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);ContentValues v=new ContentValues();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();Object x=o.get(k);if(x==JSONObject.NULL)v.putNull(k);else if(x instanceof Integer||x instanceof Long)v.put(k,((Number)x).longValue());else if(x instanceof Float||x instanceof Double)v.put(k,((Number)x).doubleValue());else if(x instanceof Boolean)v.put(k,((Boolean)x)?1:0);else v.put(k,String.valueOf(x));}db.insertOrThrow(table,null,v);}
    }

    private static String timestamp(){return new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());}
    public static String defaultManualName(){return "yaoshiji_manual_"+timestamp()+".zip";}
    public static String defaultAutoName(){return "yaoshiji_auto_"+timestamp()+".zip";}
    /** Kept for older call sites; manual backups must never be automatically pruned. */
    public static String defaultRemoteName(){return defaultManualName();}
}
