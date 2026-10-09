package com.yaoshiji.app;

import android.content.Context;

/** Opportunistic automatic backup: runs only while the app is open/resumed. */
public final class WebDavAutoBackup {
    private static volatile boolean running=false;
    public static void maybeBackup(Context c){
        if(running||!SecurePrefs.autoBackup(c)||!SecurePrefs.configured(c))return;
        long now=System.currentTimeMillis();if(now-SecurePrefs.lastBackup(c)<24L*60*60*1000)return;running=true;Context app=c.getApplicationContext();
        new Thread(()->{try{
            WebDavClient client=new WebDavClient(new WebDavClient.Config(SecurePrefs.url(app),SecurePrefs.user(app),SecurePrefs.password(app),SecurePrefs.dir(app)));
            byte[] data=BackupManager.createBackup(app);client.upload(BackupManager.defaultAutoName(),data);SecurePrefs.setLastBackup(app,System.currentTimeMillis());
            try{client.pruneAutoBackups();}catch(Exception ignored){}
        }catch(Exception ignored){}finally{running=false;}},"yaoshiji-webdav-auto").start();
    }
    private WebDavAutoBackup(){}
}
