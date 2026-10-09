package com.yaoshiji.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class WebDavActivity extends Activity {
    private static final int REQ_EXPORT_LOCAL=4101;
    private static final int REQ_IMPORT_LOCAL=4102;
    EditText url,user,pass,dir;CheckBox auto;TextView status,last;
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.prepareWindow(this);draw();}

    void draw(){
        ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,32));root.setBackgroundColor(Ui.BG);
        TextView title=Ui.text(this,"数据备份与恢复",20,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));

        LinearLayout local=Ui.card(this);local.addView(Ui.text(this,"本地备份",18,true));TextView lintro=Ui.text(this,"将完整备份 ZIP 保存到你选择的位置（下载、文档、网盘同步目录等）。恢复时直接选择 ZIP 文件；无需存储权限。",13,false);lintro.setTextColor(Ui.MUTED);local.addView(lintro);
        Button export=Ui.button(this,"导出本地备份 ZIP",true);export.setOnClickListener(v->chooseLocalExport());LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));ep.topMargin=Ui.dp(this,8);local.addView(export,ep);
        Button importB=Ui.button(this,"从本地备份恢复",false);importB.setOnClickListener(v->chooseLocalImport());LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));ip.topMargin=Ui.dp(this,8);local.addView(importB,ip);root.addView(local);

        LinearLayout c=Ui.card(this);c.addView(Ui.text(this,"WebDAV 备份",18,true));TextView intro=Ui.text(this,"采用“备份/恢复”而不是多端双向同步。自动备份执行 7-4-12 分层保留；手动备份永不自动删除。备份包不包含 WebDAV 密码，建议使用 HTTPS。",13,false);intro.setTextColor(Ui.MUTED);c.addView(intro);
        url=field("WebDAV 根地址，例如 https://dav.example.com/dav");user=field("用户名");pass=field("密码");pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);dir=field("远端目录，例如 /YaoShiJi/");
        url.setText(SecurePrefs.url(this));user.setText(SecurePrefs.user(this));pass.setText(SecurePrefs.password(this));dir.setText(SecurePrefs.dir(this));
        addLabeled(c,"服务器",url);addLabeled(c,"用户名",user);addLabeled(c,"密码",pass);addLabeled(c,"备份目录",dir);
        auto=new CheckBox(this);auto.setText("应用打开时每天自动备份一次");auto.setTextSize(14);auto.setTextColor(Ui.TEXT);auto.setChecked(SecurePrefs.autoBackup(this));c.addView(auto);
        TextView retention=Ui.text(this,"自动保留：近7天每天1份 + 近4周每周1份 + 近12个月每月1份（约最多23份）。手动备份不参与自动清理。",12,false);retention.setTextColor(Ui.MUTED);c.addView(retention);
        last=Ui.text(this,lastBackupText(),12,false);last.setTextColor(Ui.MUTED);c.addView(last);root.addView(c);

        LinearLayout actions=Ui.card(this);Button save=Ui.button(this,"保存设置并测试连接",true);save.setOnClickListener(v->{saveSettings();run("正在测试连接...",()->client().test(),false);});actions.addView(save,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        Button backup=Ui.button(this,"立即手动备份到 WebDAV",false);backup.setOnClickListener(v->{saveSettings();run("正在创建并上传手动备份...",()->{byte[] data=BackupManager.createBackup(this);String n=BackupManager.defaultManualName();WebDavClient wc=client();wc.upload(n,data);SecurePrefs.setLastBackup(this,System.currentTimeMillis());return "手动备份成功："+n;},false);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));bp.topMargin=Ui.dp(this,8);actions.addView(backup,bp);
        Button restore=Ui.button(this,"查看云端备份 / 恢复",false);restore.setOnClickListener(v->{saveSettings();listAndRestore();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));rp.topMargin=Ui.dp(this,8);actions.addView(restore,rp);
        Button prune=Ui.button(this,"整理自动备份（7-4-12）",false);prune.setOnClickListener(v->{saveSettings();run("正在整理自动备份...",()->client().pruneAutoBackups().summary(),false);});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));pp.topMargin=Ui.dp(this,8);actions.addView(prune,pp);
        Button legacy=Ui.button(this,"整理旧版备份（保留最近7份）",false);legacy.setOnClickListener(v->confirmLegacyCleanup());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,48));lp.topMargin=Ui.dp(this,8);actions.addView(legacy,lp);
        root.addView(actions);

        LinearLayout info=Ui.card(this);info.addView(Ui.text(this,"恢复与保留说明",16,true));TextView note=Ui.text(this,"恢复会替换当前本机用药数据。执行恢复前，药时记会先在应用私有目录生成一份安全副本，再恢复所选数据并重新注册提醒。V1.2.1 起，自动 WebDAV 备份使用 yaoshiji_auto_ 前缀并自动执行 7-4-12 清理；手动云端备份使用 yaoshiji_manual_ 前缀并永久保留，除非你自行删除。旧版 yaoshiji_backup_ 无法区分手动与自动，因此不会自动删除。",13,false);note.setTextColor(Ui.MUTED);info.addView(note);root.addView(info);
        status=Ui.text(this,"",13,false);status.setTextColor(Ui.BLUE);status.setPadding(Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6),0);root.addView(status);
        sc.addView(root);setContentView(sc);
    }

    EditText field(String hint){EditText e=Ui.compactInput(this,hint);e.setSingleLine(true);return e;}
    void addLabeled(LinearLayout p,String label,EditText e){TextView l=Ui.text(this,label,13,true);l.setPadding(Ui.dp(this,2),Ui.dp(this,8),0,Ui.dp(this,3));p.addView(l);p.addView(e,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));}
    void saveSettings(){String d=dir.getText().toString().trim();if(d.isEmpty())d="/YaoShiJi/";SecurePrefs.saveBasic(this,url.getText().toString().trim(),user.getText().toString().trim(),d,auto.isChecked());SecurePrefs.setPassword(this,pass.getText().toString());last.setText(lastBackupText());}
    WebDavClient client(){return new WebDavClient(new WebDavClient.Config(SecurePrefs.url(this),SecurePrefs.user(this),SecurePrefs.password(this),SecurePrefs.dir(this)));}
    String lastBackupText(){long x=SecurePrefs.lastBackup(this);return x<=0?"最近成功云端备份：暂无":"最近成功云端备份："+new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(x));}

    interface Work{String go() throws Exception;}
    void run(String busy,Work work,boolean silent){status.setText(busy);new Thread(()->{try{String result=work.go();runOnUiThread(()->{status.setText(result);last.setText(lastBackupText());if(!silent)Toast.makeText(this,result,Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{String m="操作失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());status.setText(m);Toast.makeText(this,m,Toast.LENGTH_LONG).show();});}},"yaoshiji-backup").start();}

    void chooseLocalExport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,BackupManager.defaultManualName());startActivityForResult(i,REQ_EXPORT_LOCAL);}
    void chooseLocalImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");startActivityForResult(i,REQ_IMPORT_LOCAL);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(requestCode==REQ_EXPORT_LOCAL)exportLocal(uri);else if(requestCode==REQ_IMPORT_LOCAL)confirmLocalRestore(uri);}

    void exportLocal(Uri uri){status.setText("正在导出本地备份...");new Thread(()->{try{byte[] bytes=BackupManager.createBackup(this);try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法打开目标文件");out.write(bytes);out.flush();}runOnUiThread(()->{status.setText("本地备份已导出");Toast.makeText(this,"本地备份成功",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->status.setText("本地备份失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}},"yaoshiji-local-export").start();}
    void confirmLocalRestore(Uri uri){new AlertDialog.Builder(this).setTitle("恢复本地备份？").setMessage("当前本机用药数据会被替换；恢复前会自动生成安全副本。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->restoreLocal(uri)).show();}
    void restoreLocal(Uri uri){status.setText("正在读取并恢复本地备份...");new Thread(()->{try{byte[] current=BackupManager.createBackup(this);BackupManager.saveLocalSafetyCopy(this,current,"pre_restore");byte[] data=readAll(uri);BackupManager.restore(this,data);runOnUiThread(()->{status.setText("本地恢复完成；提醒已重新注册");Toast.makeText(this,"恢复完成",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->status.setText("本地恢复失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}},"yaoshiji-local-restore").start();}
    byte[] readAll(Uri uri) throws Exception{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法打开备份文件");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}}

    void listAndRestore(){status.setText("正在读取云端备份列表...");new Thread(()->{try{List<String> names=client().listBackups();runOnUiThread(()->{if(names.isEmpty()){status.setText("云端目录中没有药时记备份");return;}new AlertDialog.Builder(this).setTitle("选择要恢复的备份").setItems(names.toArray(new String[0]),(d,w)->confirmRestore(names.get(w))).setNegativeButton("取消",null).show();});}catch(Exception e){runOnUiThread(()->status.setText("读取失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}},"yaoshiji-webdav-list").start();}
    void confirmRestore(String name){new AlertDialog.Builder(this).setTitle("恢复云端备份？").setMessage(name+"\n\n当前本机用药数据会被替换；恢复前将自动生成本地安全副本。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->restore(name)).show();}
    void restore(String name){status.setText("正在下载并恢复...");new Thread(()->{try{byte[] current=BackupManager.createBackup(this);BackupManager.saveLocalSafetyCopy(this,current,"pre_restore");byte[] cloud=client().download(name);BackupManager.restore(this,cloud);runOnUiThread(()->{status.setText("恢复完成："+name+"；提醒已重新注册");Toast.makeText(this,"恢复完成",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->status.setText("恢复失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}},"yaoshiji-webdav-restore").start();}

    void confirmLegacyCleanup(){saveSettings();new AlertDialog.Builder(this).setTitle("整理旧版备份？").setMessage("旧版 yaoshiji_backup_ 文件无法区分手动备份与自动备份。此操作只保留最新 7 份旧版备份，其余会从 WebDAV 删除。建议确认旧版备份无重要手工快照后再执行。").setNegativeButton("取消",null).setPositiveButton("保留最近7份并整理",(d,w)->run("正在整理旧版备份...",()->{int n=client().pruneLegacyKeepLatest(7);return "旧版备份整理完成，删除 "+n+" 份";},false)).show();}
}
