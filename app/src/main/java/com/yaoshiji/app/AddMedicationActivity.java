package com.yaoshiji.app;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Two-step medication editor, intentionally close to the original mini-program flow. */
public class AddMedicationActivity extends Activity {
    MedicationDb db; LinearLayout root,stepHost,doseAmountBox,doseTimeBox,advancedBox,colorPicker,stockBox;
    EditText name,count,interval,stockQty,stockAlert; Spinner route,meal,stockUnit; Button startDateBtn,moreBtn;
    LocalDate startDate=LocalDate.now(); long editId=-1; int selectedColor=0,step=1; boolean stockEnabled=false;
    ArrayList<DoseDraft> drafts=new ArrayList<>();

    static class DoseDraft {String time="08:00",unit="粒";double amount=1;DoseDraft(){}DoseDraft(String t,double a,String u){time=t;amount=a;unit=u;}}

    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.prepareWindow(this);db=new MedicationDb(this);editId=getIntent().getLongExtra("editId",-1);if(editId>0)loadModel();else drafts.add(new DoseDraft());build();}

    void build(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10),Ui.dp(this,30));root.setBackgroundColor(Ui.BG);
        TextView title=Ui.text(this,editId>0?"修改方案":"添加方案",20,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        stepHost=new LinearLayout(this);stepHost.setOrientation(LinearLayout.VERTICAL);root.addView(stepHost);sc.addView(root);setContentView(sc);renderStep();
    }
    void renderStep(){stepHost.removeAllViews();if(step==1)renderStep1();else renderStep2();}
    TextView label(String s){TextView v=Ui.text(this,s,17,true);v.setPadding(Ui.dp(this,2),Ui.dp(this,6),0,Ui.dp(this,6));return v;}
    TextView small(String s){return Ui.text(this,s,14,false);}
    EditText input(String hint){return Ui.compactInput(this,hint);}

    void renderStep1(){
        LinearLayout med=Ui.card(this);med.addView(label("药物名称"));
        LinearLayout nr=new LinearLayout(this);nr.setGravity(Gravity.CENTER_VERTICAL);name=input("点击输入药物名称");if(editId>0){MedicationDb.Medication m=db.medication(editId);if(m!=null)name.setText(m.name);}nr.addView(name,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Button photo=Ui.button(this,"拍照识别",true);photo.setEnabled(false);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(Ui.dp(this,112),Ui.dp(this,46));pl.setMargins(Ui.dp(this,8),0,0,0);nr.addView(photo,pl);med.addView(nr);stepHost.addView(med);

        LinearLayout dose=Ui.card(this);dose.addView(label("药物用量"));
        LinearLayout countRow=new LinearLayout(this);countRow.setGravity(Gravity.CENTER_VERTICAL);countRow.addView(small("每日"),new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        Button minus=Ui.button(this,"−",false),plus=Ui.button(this,"+",false);count=input("");count.setInputType(InputType.TYPE_CLASS_NUMBER);count.setText(String.valueOf(drafts.size()));count.setGravity(Gravity.CENTER);minus.setOnClickListener(v->changeCount(-1));plus.setOnClickListener(v->changeCount(1));countRow.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));countRow.addView(count,new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,44)));countRow.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));TextView times=small("次");times.setGravity(Gravity.CENTER);countRow.addView(times,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,44)));dose.addView(countRow);
        doseAmountBox=new LinearLayout(this);doseAmountBox.setOrientation(LinearLayout.VERTICAL);dose.addView(doseAmountBox);stepHost.addView(dose);

        LinearLayout timeCard=Ui.card(this);timeCard.addView(label("用药时间"));doseTimeBox=new LinearLayout(this);doseTimeBox.setOrientation(LinearLayout.VERTICAL);timeCard.addView(doseTimeBox);stepHost.addView(timeCard);renderDoseEditors();

        Button next=Ui.button(this,"下一步",true);next.setOnClickListener(v->{captureStep1();cachedName=name.getText().toString().trim();if(cachedName.isEmpty()){name.setError("请输入药物名称");return;}step=2;renderStep();});LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,Ui.dp(this,56));np.setMargins(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,18));stepHost.addView(next,np);
    }

    void renderDoseEditors(){
        doseAmountBox.removeAllViews();doseTimeBox.removeAllViews();
        for(int i=0;i<drafts.size();i++){final int idx=i;DoseDraft d=drafts.get(i);
            LinearLayout ar=new LinearLayout(this);ar.setGravity(Gravity.CENTER_VERTICAL);TextView seq=small(drafts.size()==1?"每次":"第"+(i+1)+"次");ar.addView(seq,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));EditText amt=input("1");amt.setTag("amount"+i);amt.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amt.setText(fmt(d.amount));amt.setGravity(Gravity.CENTER);ar.addView(amt,new LinearLayout.LayoutParams(Ui.dp(this,96),Ui.dp(this,44)));Spinner unit=new Spinner(this);String[] units={"粒","片","袋","支","毫升","滴","喷","次"};unit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,units));for(int u=0;u<units.length;u++)if(units[u].equals(d.unit))unit.setSelection(u);unit.setTag("unit"+i);ar.addView(unit,new LinearLayout.LayoutParams(Ui.dp(this,120),Ui.dp(this,44)));doseAmountBox.addView(ar);
            LinearLayout tr=new LinearLayout(this);tr.setGravity(Gravity.CENTER_VERTICAL);tr.setPadding(0,Ui.dp(this,2),0,Ui.dp(this,2));tr.addView(small("第"+(i+1)+"次用药"),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Button tb=Ui.button(this,d.time,false);tb.setTag(d.time);tb.setOnClickListener(v->{LocalTime t=LocalTime.parse((String)v.getTag());new TimePickerDialog(this,(vv,h,m)->{String x=String.format(Locale.CHINA,"%02d:%02d",h,m);drafts.get(idx).time=x;((Button)v).setText(x);v.setTag(x);},t.getHour(),t.getMinute(),true).show();});tr.addView(tb,new LinearLayout.LayoutParams(Ui.dp(this,150),Ui.dp(this,44)));doseTimeBox.addView(tr);
        }
    }
    void captureStep1(){if(count==null)return;for(int i=0;i<drafts.size()&&i<doseAmountBox.getChildCount();i++){LinearLayout r=(LinearLayout)doseAmountBox.getChildAt(i);EditText a=(EditText)r.getChildAt(1);Spinner u=(Spinner)r.getChildAt(2);drafts.get(i).amount=parseDouble(a.getText().toString(),1);drafts.get(i).unit=String.valueOf(u.getSelectedItem());}}
    void changeCount(int delta){captureStep1();int n=Math.max(1,Math.min(8,drafts.size()+delta));while(drafts.size()<n){DoseDraft d=new DoseDraft();int i=drafts.size();d.time=String.format(Locale.CHINA,"%02d:00",Math.min(8+i*4,23));drafts.add(d);}while(drafts.size()>n)drafts.remove(drafts.size()-1);count.setText(String.valueOf(n));renderDoseEditors();}

    void renderStep2(){
        LinearLayout extra=Ui.card(this);extra.addView(label("补充信息"));route=spinnerRow(extra,"用药方式",new String[]{"内服","外用"});meal=spinnerRow(extra,"用药时机",new String[]{"饭前用药","随餐用药","饭后用药","睡前用药"});
        LinearLayout dr=new LinearLayout(this);dr.setGravity(Gravity.CENTER_VERTICAL);dr.addView(small("开始日期"),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));startDateBtn=Ui.button(this,editId>0?"今天起生效":startDate.toString(),false);startDateBtn.setOnClickListener(v->pickDate());dr.addView(startDateBtn,new LinearLayout.LayoutParams(Ui.dp(this,165),Ui.dp(this,44)));extra.addView(dr);
        addColorPicker(extra);
        moreBtn=Ui.button(this,"更多设置",false);moreBtn.setOnClickListener(v->{advancedBox.setVisibility(advancedBox.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);moreBtn.setText(advancedBox.getVisibility()==View.VISIBLE?"收起":"更多设置");});extra.addView(moreBtn,new LinearLayout.LayoutParams(-1,Ui.dp(this,46)));
        advancedBox=new LinearLayout(this);advancedBox.setOrientation(LinearLayout.VERTICAL);advancedBox.setVisibility(View.GONE);advancedBox.setPadding(0,Ui.dp(this,8),0,0);advancedBox.addView(small("用药频率"));LinearLayout fr=new LinearLayout(this);fr.setGravity(Gravity.CENTER_VERTICAL);fr.addView(small("每"),new LinearLayout.LayoutParams(Ui.dp(this,36),Ui.dp(this,44)));interval=input("1");interval.setInputType(InputType.TYPE_CLASS_NUMBER);interval.setText("1");interval.setGravity(Gravity.CENTER);fr.addView(interval,new LinearLayout.LayoutParams(Ui.dp(this,70),Ui.dp(this,44)));fr.addView(small("日吃一次"),new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));advancedBox.addView(fr);TextView help=Ui.text(this,"1=每天；2=每隔1天。按开始日期计算周期。",12,false);help.setTextColor(Ui.MUTED);advancedBox.addView(help);extra.addView(advancedBox);stepHost.addView(extra);

        LinearLayout stock=Ui.card(this);LinearLayout sh=new LinearLayout(this);sh.setGravity(Gravity.CENTER_VERTICAL);sh.addView(label("药量管理（可选）"),new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(stockEnabled);sh.addView(sw);stock.addView(sh);stockBox=new LinearLayout(this);stockBox.setOrientation(LinearLayout.VERTICAL);stock.addView(stockBox);sw.setOnCheckedChangeListener((b,on)->{stockEnabled=on;stockBox.setVisibility(on?View.VISIBLE:View.GONE);});buildStockBox();stockBox.setVisibility(stockEnabled?View.VISIBLE:View.GONE);stepHost.addView(stock);

        if(editId>0){MedicationDb.Medication m=db.medication(editId);if(m!=null){setSpinner(route,m.route);setSpinner(meal,m.mealTiming);selectedColor=m.colorIndex;refreshColorPicker();interval.setText(String.valueOf(m.intervalDays));if(m.intervalDays>1){advancedBox.setVisibility(View.VISIBLE);moreBtn.setText("收起");}MedicationDb.StockInfo si=db.stockInfoByMed(editId);if(si!=null){stockEnabled=si.enabled;sw.setChecked(si.enabled);if(stockQty!=null)stockQty.setText(fmt(si.currentQty));if(stockUnit!=null)setSpinner(stockUnit,si.unit);if(stockAlert!=null)stockAlert.setText(String.valueOf(si.alertDays));}}}

        LinearLayout actions=new LinearLayout(this);Button prev=Ui.button(this,"上一步",false),save=Ui.button(this,"保存",true);prev.setOnClickListener(v->{captureStep2();step=1;renderStep();});save.setOnClickListener(v->save());actions.addView(prev,new LinearLayout.LayoutParams(0,Ui.dp(this,54),1));LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(0,Ui.dp(this,54),1);sl.setMargins(Ui.dp(this,10),0,0,0);actions.addView(save,sl);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,18));actions.setLayoutParams(ap);stepHost.addView(actions);
    }

    Spinner spinnerRow(LinearLayout p,String title,String[] vals){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(small(title),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vals));r.addView(s,new LinearLayout.LayoutParams(Ui.dp(this,165),Ui.dp(this,48)));p.addView(r);return s;}
    void buildStockBox(){stockBox.removeAllViews();LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(small("当前剩余"),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));stockQty=input("例如 30");stockQty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);r.addView(stockQty,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,44)));stockUnit=new Spinner(this);String[] units={"粒","片","袋","支","毫升","滴","喷","次"};stockUnit.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,units));r.addView(stockUnit,new LinearLayout.LayoutParams(Ui.dp(this,110),Ui.dp(this,44)));stockBox.addView(r);LinearLayout a=new LinearLayout(this);a.setGravity(Gravity.CENTER_VERTICAL);a.addView(small("提前提醒"),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));stockAlert=input("7");stockAlert.setText("7");stockAlert.setInputType(InputType.TYPE_CLASS_NUMBER);stockAlert.setGravity(Gravity.CENTER);a.addView(stockAlert,new LinearLayout.LayoutParams(Ui.dp(this,75),Ui.dp(this,44)));a.addView(small("天"),new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));stockBox.addView(a);TextView h=Ui.text(this,"打卡后自动扣减；撤销打卡会自动回补。可在“我的用药”里补充库存。",12,false);h.setTextColor(Ui.MUTED);stockBox.addView(h);}
    void captureStep2(){if(interval==null)return;}
    void pickDate(){DatePickerDialog d=new DatePickerDialog(this,(v,y,m,day)->{startDate=LocalDate.of(y,m+1,day);startDateBtn.setText(startDate.toString());},startDate.getYear(),startDate.getMonthValue()-1,startDate.getDayOfMonth());d.show();}

    void addColorPicker(LinearLayout p){p.addView(small("方案颜色（用于我的用药卡片）"));colorPicker=new LinearLayout(this);colorPicker.setGravity(Gravity.CENTER_VERTICAL);colorPicker.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,5));p.addView(colorPicker,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));refreshColorPicker();}
    void refreshColorPicker(){if(colorPicker==null)return;colorPicker.removeAllViews();for(int i=0;i<Ui.PASTELS.length;i++){final int idx=i;Button b=new Button(this);b.setText(selectedColor==i?"✓":"");b.setTextSize(17);b.setTextColor(Ui.TEXT);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(Ui.borderedBg(Ui.PASTELS[i],selectedColor==i?Ui.BLUE:Color.WHITE,selectedColor==i?2:1,12,this));b.setOnClickListener(v->{selectedColor=idx;refreshColorPicker();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,38),1);if(i>0)lp.setMargins(Ui.dp(this,6),0,0,0);colorPicker.addView(b,lp);}}

    void save(){captureStep1();String n=nameText();if(n.isEmpty()){step=1;renderStep();name.setError("请输入药物名称");return;}ArrayList<MedicationDb.DoseInput> ds=new ArrayList<>();for(DoseDraft d:drafts)ds.add(new MedicationDb.DoseInput(d.time,d.amount,d.unit));int inter=Math.max(1,parseInt(interval==null?"1":interval.getText().toString(),1));long id;if(editId>0)id=db.editAsNewVersion(editId,n,String.valueOf(route.getSelectedItem()),String.valueOf(meal.getSelectedItem()),inter,ds,selectedColor);else id=db.addMedication(n,String.valueOf(route.getSelectedItem()),String.valueOf(meal.getSelectedItem()),startDate.toString(),inter,ds,null,selectedColor);if(stockEnabled){double q=parseDouble(stockQty.getText().toString(),0);String u=String.valueOf(stockUnit.getSelectedItem());int ad=Math.max(1,parseInt(stockAlert.getText().toString(),7));db.configureStock(id,true,q,u,ad);}AlarmScheduler.rescheduleAll(this);ReminderGuardService.startIfEnabled(this);WebDavAutoBackup.maybeBackup(this);finish();}

    String cachedName="";String nameText(){if(name!=null)cachedName=name.getText().toString().trim();return cachedName;}
    void loadModel(){MedicationDb.Medication m=db.medication(editId);if(m==null){drafts.add(new DoseDraft());return;}cachedName=m.name;selectedColor=m.colorIndex;drafts.clear();for(MedicationDb.Dose d:m.doses)drafts.add(new DoseDraft(d.time,d.amount,d.unit));MedicationDb.StockInfo si=db.stockInfoByMed(editId);stockEnabled=si!=null&&si.enabled;}
    void setSpinner(Spinner s,String val){for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(val)){s.setSelection(i);return;}}
    int parseInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}} double parseDouble(String s,double d){try{return Double.parseDouble(s.trim());}catch(Exception e){return d;}}String fmt(double x){return x==(long)x?String.valueOf((long)x):String.valueOf(x);}
}
