package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}
    public static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    public static LinearLayout page(Activity a){LinearLayout p=new LinearLayout(a);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,24));p.setBackgroundColor(Color.rgb(246,247,251));return p;}
    public static void topBar(Activity a,LinearLayout p,String title,boolean back){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);if(back){Button b=button(a,"←");b.setOnClickListener(v->a.finish());row.addView(b,new LinearLayout.LayoutParams(dp(a,56),dp(a,48)));}TextView t=text(a,title,24,true);row.addView(t,new LinearLayout.LayoutParams(0,dp(a,56),1));Button h=button(a,"Home");h.setOnClickListener(v->{Intent i=new Intent(a,StandbyActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(i);});row.addView(h,new LinearLayout.LayoutParams(dp(a,82),dp(a,48)));p.addView(row);}
    public static LinearLayout card(Activity a,LinearLayout p){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,16),dp(a,14),dp(a,16),dp(a,14));GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(a,18));g.setStroke(dp(a,1),Color.rgb(226,229,238));c.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,10),0,dp(a,6));p.addView(c,lp);return c;}
    public static TextView text(Activity a,String s,int z,boolean bold){TextView v=new TextView(a);v.setText(s);v.setTextSize(z);v.setTextColor(Color.rgb(28,31,40));v.setPadding(0,dp(a,5),0,dp(a,5));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    public static Button button(Activity a,String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(a,50));return b;}
    public static EditText field(Activity a,String hint,String value){EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(249,250,253));g.setCornerRadius(dp(a,12));g.setStroke(dp(a,1),Color.rgb(205,210,223));e.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,6),0,dp(a,8));e.setLayoutParams(lp);return e;}
}