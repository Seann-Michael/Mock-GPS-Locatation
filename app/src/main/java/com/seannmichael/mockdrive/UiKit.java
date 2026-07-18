package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}

    public static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    public static LinearLayout page(Activity a){
        LinearLayout p=new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,104));
        p.setBackgroundColor(Color.rgb(244,246,250));
        return p;
    }

    public static void topBar(Activity a,LinearLayout p,String title,boolean back){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,0,0,dp(a,8));
        if(back){Button b=ghostButton(a,"←");b.setOnClickListener(v->a.finish());row.addView(b,new LinearLayout.LayoutParams(dp(a,52),dp(a,48)));}
        TextView t=text(a,title,26,true);row.addView(t,new LinearLayout.LayoutParams(0,dp(a,56),1));
        Button h=ghostButton(a,"Home");h.setOnClickListener(v->{Intent i=new Intent(a,StandbyActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(i);});row.addView(h,new LinearLayout.LayoutParams(dp(a,86),dp(a,48)));
        p.addView(row);
    }

    public static LinearLayout card(Activity a,LinearLayout p){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,16));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(a,22));g.setStroke(dp(a,1),Color.rgb(226,230,239));c.setBackground(g);c.setElevation(dp(a,2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,10),0,dp(a,8));p.addView(c,lp);return c;
    }

    public static TextView text(Activity a,String s,int z,boolean bold){
        TextView v=new TextView(a);v.setText(s);v.setTextSize(z);v.setTextColor(Color.rgb(25,29,38));v.setPadding(0,dp(a,5),0,dp(a,5));if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;
    }

    public static Button button(Activity a,String s){
        Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setMinHeight(dp(a,52));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(48,84,214));g.setCornerRadius(dp(a,16));b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,52));lp.setMargins(0,dp(a,6),0,dp(a,6));b.setLayoutParams(lp);return b;
    }

    public static Button secondaryButton(Activity a,String s){
        Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.rgb(38,52,95));b.setMinHeight(dp(a,50));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(233,237,250));g.setCornerRadius(dp(a,16));b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,50));lp.setMargins(0,dp(a,5),0,dp(a,5));b.setLayoutParams(lp);return b;
    }

    private static Button ghostButton(Activity a,String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.rgb(48,84,214));b.setBackgroundColor(Color.TRANSPARENT);return b;}

    public static EditText field(Activity a,String hint,String value){
        EditText e=new EditText(a);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setTextColor(Color.rgb(25,29,38));e.setHintTextColor(Color.rgb(120,126,142));e.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(249,250,253));g.setCornerRadius(dp(a,14));g.setStroke(dp(a,1),Color.rgb(202,209,224));e.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,7),0,dp(a,9));e.setLayoutParams(lp);return e;
    }

    public static void bottomNav(Activity a,LinearLayout p,String current){
        LinearLayout nav=new LinearLayout(a);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(0,dp(a,10),0,0);
        addNav(a,nav,"Home",StandbyActivity.class,current);
        addNav(a,nav,"Mock",QuickMockActivity.class,current);
        addNav(a,nav,"Campaigns",CampaignActivity.class,current);
        addNav(a,nav,"Schedule",SchedulerActivity.class,current);
        addNav(a,nav,"Settings",SettingsActivity.class,current);
        p.addView(nav,new LinearLayout.LayoutParams(-1,dp(a,70)));
    }

    private static void addNav(Activity a,LinearLayout nav,String label,Class<?> target,String current){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(label.equals(current)?Color.rgb(48,84,214):Color.rgb(92,98,114));b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v->{if(!label.equals(current)){Intent i=new Intent(a,target);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(i);}});
        nav.addView(b,new LinearLayout.LayoutParams(0,dp(a,58),1));
    }
}