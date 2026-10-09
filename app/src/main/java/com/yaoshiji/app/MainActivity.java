package com.yaoshiji.app;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    MedicationDb db; LinearLayout content; int tab=0; LocalDate selected=LocalDate.now(); YearMonth month=YearMonth.now(); Button[] navButtons=new Button[4];

    @Override public void onCreate(Bundle b){
        super.onCreate(b);Ui.prepareWindow(this);db=new MedicationDb(this);NotificationHelper.ensure(this);requestNotify();consumeIntent(getIntent());AlarmScheduler.rescheduleAll(this);ReminderGuardService.startIfEnabled(this);buildShell();
        if(android.os.SystemClock.elapsedRealtime()<15*60*1000L)Toast.makeText(this,"设备近期重启：药时记已重新注册提醒。卓易通环境下请每次重启后至少打开一次药时记。",Toast.LENGTH_LONG).show();
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);consumeIntent(i);if(content!=null)showTab(0);}
    @Override protected void onResume(){super.onResume();if(content!=null)showTab(tab);WebDavAutoBackup.maybeBackup(this);}

    void consumeIntent(Intent i){
        if(i==null)return;String d=i.getStringExtra("date");if(d!=null){try{selected=LocalDate.parse(d);month=YearMonth.from(selected);tab=0;}catch(Exception ignored){}}
    }
    void requestNotify(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);}

    void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.WHITE);nav.setPadding(0,Ui.dp(this,2),0,0);
        String[] names={"吃药打卡","我的用药","用药记录","我的"};
        for(int i=0;i<4;i++){final int x=i;Button b=Ui.button(this,names[i],false);b.setBackgroundColor(Color.WHITE);b.setTextSize(13);b.setOnClickListener(v->{tab=x;showTab(x);});navButtons[i]=b;nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1));}
        root.addView(nav);setContentView(root);showTab(tab);
    }
    void updateNav(){for(int i=0;i<navButtons.length;i++){Button b=navButtons[i];if(b==null)continue;boolean on=i==tab;b.setTextColor(on?Ui.BLUE:Ui.MUTED);b.setTypeface(b.getTypeface(),on?1:0);}}
    void clear(String title){content.removeAllViews();updateNav();TextView t=Ui.text(this,title,20,true);t.setGravity(Gravity.CENTER);t.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,6));content.addView(t,new LinearLayout.LayoutParams(-1,Ui.dp(this,44)));}
    void showTab(int x){tab=x;updateNav();if(x==0)today();else if(x==1)meds();else if(x==2)history();else me();}

    void statsCard(){
        LinearLayout c=Ui.card(this);c.setPadding(Ui.dp(this,13),Ui.dp(this,10),Ui.dp(this,13),Ui.dp(this,10));c.setBackground(Ui.borderedBg(Color.WHITE,Color.rgb(226,233,246),1,18,this));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(Ui.text(this,"服药状态",16,true),new LinearLayout.LayoutParams(0,-2,1));MedicationDb.MonthStats ms=db.monthStats(YearMonth.now());if(ms.interrupted>0){TextView x=Ui.text(this,"待补签 "+ms.interrupted+" 次 ›",12,true);x.setTextColor(Color.rgb(220,78,78));x.setOnClickListener(v->showInterrupted(ms));head.addView(x);}c.addView(head);
        List<MedicationDb.Medication> meds=db.activeMedications();
        if(meds.isEmpty())c.addView(Ui.text(this,"暂无进行中的用药方案",13,false));
        for(MedicationDb.Medication m:meds){MedicationDb.MedicationProgress p=db.medicationProgress(m);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,5));String unit=m.intervalDays>1?"个用药日":"天";TextView line=Ui.text(this,"您已连续服用 "+m.name+" "+p.streakDays+unit,14,true);row.addView(line);String last=p.lastInterrupted==null?"上一次中断：暂无":"上一次中断："+p.lastInterrupted.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"));TextView sub=Ui.text(this,last,12,false);sub.setTextColor(Ui.MUTED);row.addView(sub);if(p.stock!=null){String stock="库存："+formatAmount(p.stock.current)+p.stock.unit+(p.stock.runOutDate==null?"":" · 预计可用至 "+p.stock.runOutDate);TextView sv=Ui.text(this,stock,12,true);sv.setTextColor(p.stock.daysLeft<=p.stock.alertDays?Color.rgb(220,82,64):Color.rgb(42,150,105));row.addView(sv);}c.addView(row);}
        content.addView(c);
    }
    void showInterrupted(MedicationDb.MonthStats s){
        if(s.items.isEmpty()){toast("本月暂无待补签记录");return;}String[] labels=new String[s.items.size()];for(int i=0;i<s.items.size();i++){MedicationDb.InterruptedItem x=s.items.get(i);labels[i]=x.date+" "+x.time+"  "+x.medication+"  "+formatAmount(x.amount)+x.unit;}
        new AlertDialog.Builder(this).setTitle("待确认服用（"+s.items.size()+"次）").setItems(labels,(d,which)->confirmSupplement(s.items.get(which))).setNegativeButton("关闭",null).show();
    }
    void confirmSupplement(MedicationDb.InterruptedItem x){
        String message = x.date + " " + x.time
                + "\n" + x.medication + " · " + formatAmount(x.amount) + x.unit
                + "\n\n补签表示：这次药实际上已经服用，只是当时忘记打卡。补签不会被标记为迟到。";
        new AlertDialog.Builder(this)
                .setTitle("补签这次服药？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认补签", (d, w) -> {
                    MedicationDb.Task t = db.findTask(x.medicationId, x.scheduleId, x.date);
                    if (t != null) {
                        db.supplement(t);
                        WebDavAutoBackup.maybeBackup(this);
                        today();
                        toast("已补签");
                    }
                })
                .show();
    }
    String formatAmount(double x){return x==(long)x?String.valueOf((long)x):String.valueOf(x);}
    LinearLayout statCell(String label,int value,int color){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER);TextView n=Ui.text(this,String.valueOf(value),20,true);n.setTextColor(color);n.setGravity(Gravity.CENTER);n.setIncludeFontPadding(false);TextView t=Ui.text(this,label,11,false);t.setTextColor(Ui.MUTED);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);l.addView(n);l.addView(t);return l;}

    void today(){
        clear("药时记");statsCard();calendar(true);List<MedicationDb.Task> tasks=db.tasksFor(selected);
        if(tasks.isEmpty()){LinearLayout c=Ui.card(this);c.addView(Ui.text(this,selected+"  当天没有用药任务",16,true));content.addView(c);return;}
        for(MedicationDb.Task task:tasks){
            LinearLayout c=Ui.card(this);c.setBackground(Ui.bg(Ui.PASTELS[Math.floorMod(task.medication.colorIndex,Ui.PASTELS.length)],20,this));
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);txt.addView(Ui.text(this,task.dose.time,18,true));txt.addView(Ui.text(this,task.medication.name,18,true));txt.addView(Ui.text(this,AlarmScheduler.formatDose(task.dose)+" · "+task.medication.mealTiming,14,false));row.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
            Button b=Ui.button(this,task.taken()?"已打卡":(selected.isBefore(LocalDate.now())?"补打卡":"立即打卡"),!task.taken());b.setOnClickListener(v->{if(task.taken())confirmUndo(task);else checkIn(task);});row.addView(b,new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,52)));c.addView(row);
            Button note=Ui.button(this,"写备注",false);note.setOnClickListener(v->note(task));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));np.topMargin=Ui.dp(this,9);c.addView(note,np);content.addView(c);
        }
    }
    void checkIn(MedicationDb.Task t){
        if(t.date.isAfter(LocalDate.now())){toast("未来任务暂不允许提前打卡");return;}
        if(t.date.isBefore(LocalDate.now())){
            TimePickerDialog d=new TimePickerDialog(this,(v,h,m)->{
                long actual=t.date.atTime(h,m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                confirmTimeWindowThenCheckIn(t,Math.min(actual,System.currentTimeMillis()));
            },LocalTime.now().getHour(),LocalTime.now().getMinute(),true);
            d.setTitle("实际服药时间");d.show();
        }else{
            confirmTimeWindowThenCheckIn(t,System.currentTimeMillis());
        }
    }
    void confirmTimeWindowThenCheckIn(MedicationDb.Task t,long actualMillis){
        long scheduled=t.date.atTime(LocalTime.parse(t.dose.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diff=Math.abs(actualMillis-scheduled);
        if(diff<=60*60*1000L){performCheckIn(t,actualMillis);return;}
        String actualText=Instant.ofEpochMilli(actualMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        String message="计划服药时间："+t.dose.time
                +"\n当前/实际时间："+actualText
                +"\n\n当前时间与设定服药时间相差超过 1 小时。若您确认今天已经服用该药，可继续打卡；确认后仍会计入今日服药与连续服用天数。";
        new AlertDialog.Builder(this)
                .setTitle("当前不在设定服药时间范围")
                .setMessage(message)
                .setNegativeButton("取消",null)
                .setPositiveButton("仍然打卡",(d,w)->performCheckIn(t,actualMillis))
                .show();
    }
    void performCheckIn(MedicationDb.Task t,long actualMillis){
        db.checkIn(t,actualMillis);
        AlarmScheduler.dismissDoseNotification(this,t.dose.id,t.date);
        AlarmScheduler.rescheduleAll(this);
        NotificationHelper.maybePostLowStock(this,db,t.medication);
        WebDavAutoBackup.maybeBackup(this);
        today();
        toast("打卡成功");
    }
    void confirmUndo(MedicationDb.Task t){new AlertDialog.Builder(this).setTitle("是否撤回打卡？").setMessage("撤回后该次用药会恢复为未完成状态。计划时间仍保留。").setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{db.undo(t);AlarmScheduler.rescheduleAll(this);WebDavAutoBackup.maybeBackup(this);today();}).show();}
    void note(MedicationDb.Task t){EditText e=new EditText(this);MedicationDb.Record r=db.record(t.dose.id,t.date);if(r!=null&&r.note!=null)e.setText(r.note);new AlertDialog.Builder(this).setTitle("用药备注").setView(e).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{db.saveNote(t,e.getText().toString().trim());WebDavAutoBackup.maybeBackup(this);toast("备注已保存");}).show();}

    void calendar(boolean interactive){
        LinearLayout card=Ui.card(this);LinearLayout head=new LinearLayout(this);Button prev=Ui.button(this,"‹",false),next=Ui.button(this,"›",false);TextView title=Ui.text(this,month.getYear()+"年"+String.format(Locale.CHINA,"%02d",month.getMonthValue())+"月",22,true);title.setGravity(Gravity.CENTER);prev.setOnClickListener(v->{month=month.minusMonths(1);showTab(tab);});next.setOnClickListener(v->{month=month.plusMonths(1);showTab(tab);});head.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,44)));head.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));head.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,50),Ui.dp(this,44)));card.addView(head);
        GridLayout g=new GridLayout(this);g.setColumnCount(7);String[] wk={"日","一","二","三","四","五","六"};for(String s:wk){TextView q=Ui.text(this,s,13,true);q.setGravity(Gravity.CENTER);g.addView(q,new ViewGroup.LayoutParams(Ui.dp(this,47),Ui.dp(this,34)));}
        LocalDate first=month.atDay(1);int start=first.getDayOfWeek().getValue()%7;for(int i=0;i<start;i++){TextView q=Ui.text(this,"",14,false);g.addView(q,new ViewGroup.LayoutParams(Ui.dp(this,47),Ui.dp(this,40)));}
        for(int d=1;d<=month.lengthOfMonth();d++){LocalDate date=month.atDay(d);MedicationDb.DayState state=db.dayState(date);String mark=state==MedicationDb.DayState.COMPLETE?"✓":state==MedicationDb.DayState.PARTIAL?"•":state==MedicationDb.DayState.INTERRUPTED?"!":"";TextView q=Ui.text(this,mark.isEmpty()?String.valueOf(d):(d+"\n"+mark),12,date.equals(selected));q.setGravity(Gravity.CENTER);q.setLineSpacing(0,0.86f);if(state==MedicationDb.DayState.COMPLETE)q.setTextColor(Color.rgb(42,176,124));else if(state==MedicationDb.DayState.PARTIAL)q.setTextColor(Color.rgb(230,153,45));else if(state==MedicationDb.DayState.INTERRUPTED)q.setTextColor(Color.rgb(220,78,78));else if(state==MedicationDb.DayState.PLANNED)q.setTextColor(Color.rgb(92,177,145));if(date.equals(selected)){q.setTextColor(Color.WHITE);q.setBackground(Ui.bg(Ui.BLUE,23,this));}if(interactive)q.setOnClickListener(v->{selected=date;showTab(tab);});g.addView(q,new ViewGroup.LayoutParams(Ui.dp(this,47),Ui.dp(this,44)));}
        card.addView(g);content.addView(card);
    }

    void meds(){
        clear("用药方案");List<MedicationDb.Medication> ms=db.activeMedications();if(ms.isEmpty()){LinearLayout c=Ui.card(this);c.addView(Ui.text(this,"暂无进行中的用药方案",17,true));content.addView(c);}
        for(MedicationDb.Medication m:ms){
            LinearLayout c=Ui.card(this);c.setBackground(Ui.borderedBg(Ui.PASTELS[Math.floorMod(m.colorIndex,Ui.PASTELS.length)],Color.argb(35,0,0,0),1,20,this));
            c.addView(Ui.text(this,m.name,20,true));c.addView(Ui.text(this,"每日"+m.doses.size()+"次 · "+m.mealTiming+" · "+m.route,15,false));c.addView(Ui.text(this,m.startDate+"开始"+(m.intervalDays>1?" · 每"+m.intervalDays+"日一次":""),14,false));
            MedicationDb.StockProjection sp=db.stockProjection(m);if(sp!=null){String stock="剩余约 "+formatAmount(sp.current)+sp.unit+(sp.runOutDate!=null?" · 预计用到 "+sp.runOutDate:"");TextView sv=Ui.text(this,stock,14,true);sv.setTextColor(sp.daysLeft<=sp.alertDays?Color.rgb(220,82,64):Color.rgb(42,150,105));c.addView(sv);}
            LinearLayout actions=new LinearLayout(this);Button edit=Ui.button(this,"修改",false),stock=Ui.button(this,"补充库存",false),end=Ui.button(this,"结束用药",false);edit.setOnClickListener(v->{Intent i=new Intent(this,AddMedicationActivity.class);i.putExtra("editId",m.id);startActivity(i);});stock.setVisibility(sp==null?View.GONE:View.VISIBLE);stock.setOnClickListener(v->addStockDialog(m));end.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("结束用药？").setMessage("已有记录不会删除，未来不再提醒。").setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{db.endMedication(m.id);AlarmScheduler.rescheduleAll(this);WebDavAutoBackup.maybeBackup(this);meds();}).show());actions.addView(edit,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));if(sp!=null)actions.addView(stock,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));actions.addView(end,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));c.addView(actions);content.addView(c);
        }
        Button add=Ui.button(this,"添加用药方案",true);add.setOnClickListener(v->startActivity(new Intent(this,AddMedicationActivity.class)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));lp.setMargins(Ui.dp(this,24),Ui.dp(this,18),Ui.dp(this,24),Ui.dp(this,28));content.addView(add,lp);
    }
    void addStockDialog(MedicationDb.Medication m){EditText e=Ui.compactInput(this,"例如 30");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);MedicationDb.StockInfo si=db.stockInfoByMed(m.id);String unit=si==null?"":si.unit;new AlertDialog.Builder(this).setTitle("补充库存 · "+m.name).setMessage("请输入本次新增的"+unit+"数").setView(e).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{try{double q=Double.parseDouble(e.getText().toString().trim());if(q>0){db.addStock(m.id,q,"手动补充");WebDavAutoBackup.maybeBackup(this);meds();}}catch(Exception ex){toast("数量格式不正确");}}).show();}

    void history(){
        clear("用药记录");calendar(true);List<MedicationDb.Task> ts=db.tasksFor(selected);LinearLayout c=Ui.card(this);c.addView(Ui.text(this,selected.toString(),18,false));boolean any=false;
        for(MedicationDb.Task t:ts){if(t.taken()){any=true;String actual=java.time.Instant.ofEpochMilli(t.record.takenAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));String status="SUPPLEMENTED".equals(t.record.status)?"补签":"已服用";TextView v=Ui.text(this,t.medication.name+"   计划 "+t.dose.time+" / 实际 "+actual+"   "+status,15,true);c.addView(v);if(t.record.note!=null&&!t.record.note.isEmpty())c.addView(Ui.text(this,"备注："+t.record.note,14,false));}}
        if(!any)c.addView(Ui.text(this,"没有打卡记录",18,true));content.addView(c);
    }

    void me(){
        clear("我的");LinearLayout profile=Ui.card(this);TextView icon=Ui.text(this,"◉",54,false);icon.setGravity(Gravity.CENTER);profile.addView(icon);TextView local=Ui.text(this,"本地用户 · 无需登录",17,true);local.setGravity(Gravity.CENTER);profile.addView(local);content.addView(profile);
        String[] names={"常见问题","个人信息","问题反馈","提醒检查与后台保护","数据备份与恢复","支持作者"};
        for(String s:names){LinearLayout c=Ui.card(this);TextView v=Ui.text(this,s+"   ›",16,true);v.setOnClickListener(x->{if(s.equals("常见问题"))startActivity(new Intent(this,FaqActivity.class));else if(s.startsWith("提醒检查"))startActivity(new Intent(this,ReminderCheckActivity.class));else if(s.startsWith("数据备份"))startActivity(new Intent(this,WebDavActivity.class));else if(s.equals("个人信息"))new AlertDialog.Builder(this).setTitle("个人信息").setMessage("药时记 V1.2 不使用账号体系。核心用药数据保存在本机；如启用 WebDAV，仅上传用户主动配置的备份包，WebDAV 密码不会写入备份包。").setPositiveButton("知道了",null).show();else if(s.equals("问题反馈"))new AlertDialog.Builder(this).setTitle("问题反馈").setMessage("反馈时请记录：应用版本、Mate 60 Pro+ / HarmonyOS 版本、卓易通版本、提醒时间，以及是否锁屏/清后台/重启/守护模式开启。").setPositiveButton("知道了",null).show();else toast("暂不接入支付或赞助功能");});c.addView(v);content.addView(c);}
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
