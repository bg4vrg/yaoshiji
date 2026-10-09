package com.yaoshiji.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

public class ReminderAlertActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);Ui.prepareWindow(this);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        draw();
    }
    void draw(){
        boolean test=getIntent().getBooleanExtra("test",false);String name=getIntent().getStringExtra("name"),dose=getIntent().getStringExtra("dose"),meal=getIntent().getStringExtra("meal"),date=getIntent().getStringExtra("date"),time=getIntent().getStringExtra("time");int seq=getIntent().getIntExtra("seq",1);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(Ui.dp(this,24),Ui.dp(this,34),Ui.dp(this,24),Ui.dp(this,34));root.setBackgroundColor(Ui.BG);
        TextView bell=Ui.text(this,"⏰",54,false);bell.setGravity(Gravity.CENTER);root.addView(bell,new LinearLayout.LayoutParams(-1,Ui.dp(this,82)));
        TextView title=Ui.text(this,test?"测试提醒":"该吃药了",28,true);title.setGravity(Gravity.CENTER);title.setTextColor(Ui.BLUE);root.addView(title);
        LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22));
        if(test){card.addView(center("如果能看到这个页面，弹窗提醒链路已触发。",18,true));}
        else{
            card.addView(center(name==null?"用药方案":name,24,true));
            card.addView(center((date==null?"":date)+"  "+(time==null?"":time)+"  第"+seq+"次",17,false));
            card.addView(center("用量："+(dose==null?"":dose),20,true));
            if(meal!=null&&!meal.isEmpty())card.addView(center(meal,17,false));
        }
        root.addView(card,new LinearLayout.LayoutParams(-1,-2));
        Button open=Ui.button(this,test?"知道了":"打开药时记打卡",true);open.setOnClickListener(v->{if(test){finish();return;}Intent i=new Intent(this,MainActivity.class);if(date!=null)i.putExtra("date",date);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(i);finish();});LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,Ui.dp(this,58));op.setMargins(0,Ui.dp(this,18),0,0);root.addView(open,op);
        Button later=Ui.button(this,"暂时关闭",false);later.setOnClickListener(v->finish());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,50));lp.setMargins(0,Ui.dp(this,10),0,0);root.addView(later,lp);
        setContentView(root);
    }
    TextView center(String s,int sp,boolean bold){TextView t=Ui.text(this,s,sp,bold);t.setGravity(Gravity.CENTER);t.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,7));return t;}
}
