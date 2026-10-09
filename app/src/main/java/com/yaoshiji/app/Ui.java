package com.yaoshiji.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

public final class Ui {
    public static final int BLUE=Color.rgb(22,119,255), BG=Color.rgb(240,244,252), TEXT=Color.rgb(30,32,36), MUTED=Color.rgb(105,110,120), FIELD=Color.rgb(247,249,253);
    public static final int[] PASTELS={Color.rgb(232,242,255),Color.rgb(232,250,241),Color.rgb(255,245,220),Color.rgb(250,233,241),Color.rgb(239,235,255),Color.rgb(236,242,244)};
    public static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    public static GradientDrawable bg(int color,float radiusDp, Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,(int)radiusDp));return g;}
    public static GradientDrawable borderedBg(int color,int strokeColor,int strokeDp,float radiusDp,Context c){GradientDrawable g=bg(color,radiusDp,c);g.setStroke(dp(c,strokeDp),strokeColor);return g;}
    public static TextView text(Context c,String s,int sp,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(sp);v.setTextColor(TEXT);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));if(bold)v.setTypeface(v.getTypeface(),1);return v;}
    public static Button button(Context c,String s,boolean primary){Button b=new Button(c);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:TEXT);b.setBackground(bg(primary?BLUE:Color.WHITE,14,c));b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(c,10),0,dp(c,10),0);return b;}
    public static LinearLayout card(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(c,13),dp(c,10),dp(c,13),dp(c,10));l.setBackground(bg(Color.WHITE,20,c));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(dp(c,10),dp(c,4),dp(c,10),dp(c,4));l.setLayoutParams(lp);return l;}
    public static EditText compactInput(Context c,String hint){EditText e=new EditText(c);e.setHint(hint);e.setTextSize(15);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(150,155,165));e.setSingleLine(true);e.setGravity(Gravity.CENTER_VERTICAL);e.setPadding(dp(c,12),0,dp(c,12),0);e.setBackground(borderedBg(FIELD,Color.rgb(226,231,240),1,12,c));return e;}
    public static void prepareWindow(Activity a){Window w=a.getWindow();w.setStatusBarColor(Color.WHITE);w.setNavigationBarColor(Color.WHITE);int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if(Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;w.getDecorView().setSystemUiVisibility(flags);}
    public static Space space(Context c,int h){Space s=new Space(c);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h)));return s;}
}
