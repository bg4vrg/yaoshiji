package com.yaoshiji.app;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class ReminderCheckActivity extends Activity {
    LinearLayout root;
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.prepareWindow(this);draw();}
    @Override protected void onResume(){super.onResume();if(root!=null)draw();}

    void draw(){
        ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,28));root.setBackgroundColor(Ui.BG);
        TextView t=Ui.text(this,"提醒检查与后台保护",20,true);t.setGravity(Gravity.CENTER);root.addView(t,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        boolean notify=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        item("通知权限",notify?"正常":"未授权",()->{if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);else openNotificationSettings();});
        boolean exact=AlarmScheduler.canExact(this);item("精确 / 闹钟级提醒",exact?"正常":"未授权，可能无法准时",()->AlarmScheduler.openExactSettings(this));
        boolean full=canFullScreen();item("弹窗提醒",full?"系统允许 Full-screen Intent":"系统可能限制；仍保留高优先级锁屏/横幅通知",this::openFullScreenSettings);
        item("通知样式","建议开启锁屏通知、横幅、亮屏、声音和振动",this::openNotificationSettings);
        item("提醒铃声",ReminderSoundPrefs.label(ReminderSoundPrefs.mode(this)),this::chooseSound);

        LinearLayout guard=Ui.card(this);LinearLayout gr=new LinearLayout(this);gr.setGravity(Gravity.CENTER_VERTICAL);LinearLayout gt=new LinearLayout(this);gt.setOrientation(LinearLayout.VERTICAL);gt.addView(Ui.text(this,"提醒守护（系统回收兜底）",16,true));TextView gd=Ui.text(this,"常驻前台服务 + 到点前5分钟预唤醒 + 服务内部兜底计时。可用于系统回收场景，但实测无法抵抗卓易通“上滑清除任务”。",12,false);gd.setTextColor(Ui.MUTED);gt.addView(gd);gr.addView(gt,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(GuardPrefs.enabled(this));sw.setOnCheckedChangeListener((b,v)->{GuardPrefs.setEnabled(this,v);if(v)ReminderGuardService.startIfEnabled(this);else ReminderGuardService.stop(this);});gr.addView(sw);guard.addView(gr);root.addView(guard);

        item("卓易通实测结论","V9已确认：普通锁屏提醒正常；上滑清除任务后，守护通知也会同时消失，后续无提醒。该行为视为卓易通承载层限制。",()->{});
        item("重启说明","卓易通重启后可能不会自动初始化 Android 兼容环境。当前建议：手机重启后至少打开药时记一次，应用会立即重建全部提醒。",()->{});

        Button test=Ui.button(this,"1 分钟后测试重要提醒",true);test.setOnClickListener(v->{AlarmScheduler.scheduleTest(this,System.currentTimeMillis()+60000);Toast.makeText(this,"已安排 1 分钟测试。建议锁屏观察。",Toast.LENGTH_LONG).show();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(this,54));p.setMargins(0,Ui.dp(this,10),0,0);root.addView(test,p);
        Button notif=Ui.button(this,"打开药时记通知设置",false);notif.setOnClickListener(v->openNotificationSettings());LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));np.setMargins(0,Ui.dp(this,7),0,0);root.addView(notif,np);
        Button app=Ui.button(this,"打开应用详情",false);app.setOnClickListener(v->openAppSettings());LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));ap.setMargins(0,Ui.dp(this,7),0,0);root.addView(app,ap);
        TextView note=Ui.text(this,"说明：Mate 60 Pro+ / HarmonyOS 7 / 卓易通对后台与开机行为与原生 Android 不完全一致。本页不再提供“电池优化”入口，因为实测该系统入口在卓易通下提示不支持。",12,false);note.setTextColor(Ui.MUTED);note.setPadding(Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4),0);root.addView(note);
        sc.addView(root);setContentView(sc);
    }

    void chooseSound(){String[] a={"药时记短提示音（推荐，约2秒）","系统短通知音","静音（仅振动）"};int cur=ReminderSoundPrefs.mode(this);new AlertDialog.Builder(this).setTitle("选择提醒铃声").setSingleChoiceItems(a,cur,(d,w)->{ReminderSoundPrefs.setMode(this,w);NotificationHelper.ensure(this);d.dismiss();draw();Toast.makeText(this,"已切换。Android 通知声道会按新设置使用。",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消",null).show();}

    void item(String a,String b,Runnable r){LinearLayout c=Ui.card(this);TextView x=Ui.text(this,a+"\n"+b,15,true);x.setPadding(Ui.dp(this,4),Ui.dp(this,5),Ui.dp(this,4),Ui.dp(this,5));x.setOnClickListener(v->r.run());c.addView(x);root.addView(c);}
    boolean canFullScreen(){if(Build.VERSION.SDK_INT<34)return true;try{return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).canUseFullScreenIntent();}catch(Exception e){return false;}}
    void openFullScreenSettings(){if(Build.VERSION.SDK_INT>=34){try{Intent i=new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()));startActivity(i);return;}catch(Exception ignored){}}openNotificationSettings();}
    void openNotificationSettings(){try{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}catch(Exception e){openAppSettings();}}
    void openAppSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}}
}
