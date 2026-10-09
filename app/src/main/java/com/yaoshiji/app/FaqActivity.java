package com.yaoshiji.app;

import android.app.*;import android.os.*;import android.view.*;import android.widget.*;
public class FaqActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);Ui.prepareWindow(this);ScrollView sc=new ScrollView(this);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,28));r.setBackgroundColor(Ui.BG);TextView title=Ui.text(this,"常见问题",21,true);title.setGravity(Gravity.CENTER);r.addView(title);item(r,"两天吃一次药，如何设置？","在添加或修改药物的第二步点击“更多设置”，把用药频率设置为“每 2 日吃一次”。药时记按开始日期作为周期基准计算。 ");item(r,"有没有铃声提醒？闹钟提醒？","有。药时记使用系统通知/闹钟能力提醒。请在“我的 > 提醒检查”确认通知权限和精确提醒权限，并使用 1 分钟测试提醒验证。 ");item(r,"不小心误打卡，如何取消？","在打卡页面再次点击“已打卡”，确认“撤回打卡”即可。撤回后恢复为未完成，历史统计同步更新。 ");item(r,"为什么没有提醒了？","优先检查：通知权限、精确提醒权限、系统省电/后台限制、卓易通运行环境。修改权限后建议使用测试提醒验证。 ");item(r,"关于药时记","药时记是面向个人使用的本地优先用药提醒与记录工具。V1.0 不使用账号、不上传用药数据到开发者服务器。 ");sc.addView(r);setContentView(sc);}
 void item(LinearLayout r,String q,String a){LinearLayout c=Ui.card(this);c.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));TextView v=Ui.text(this,"？  "+q+"   ›",17,true);v.setOnClickListener(x->new AlertDialog.Builder(this).setTitle(q).setMessage(a).setPositiveButton("知道了",null).show());c.addView(v);r.addView(c);}
}
