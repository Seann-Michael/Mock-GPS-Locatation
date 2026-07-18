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

    public static final int BLUE = Color.rgb(20, 91, 218);
    public static final int BLUE_DARK = Color.rgb(10, 55, 145);
    public static final int BLUE_LIGHT = Color.rgb(231, 240, 255);
    public static final int PAGE = Color.rgb(245, 249, 255);
    public static final int INK = Color.rgb(18, 40, 73);
    public static final int MUTED = Color.rgb(91, 111, 139);

    public static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    public static LinearLayout page(Activity a){
        LinearLayout p=new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(a,16),dp(a,10),dp(a,16),dp(a,28));
        p.setBackgroundColor(PAGE);
        return p;
    }

    public static void topBar(Activity a,LinearLayout p,String title,boolean back){
        LinearLayout row=new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a,2),dp(a,4),dp(a,2),dp(a,10));
        if(back){
            Button b=ghostButton(a,"‹");
            b.setTextSize(30);
            b.setOnClickListener(v->a.finish());
            row.addView(b,new LinearLayout.LayoutParams(dp(a,48),dp(a,52)));
        }
        TextView t=text(a,title,27,true);
        t.setTextColor(BLUE_DARK);
        row.addView(t,new LinearLayout.LayoutParams(0,dp(a,58),1));
        p.addView(row);
    }

    public static LinearLayout hero(Activity a,LinearLayout p){
        LinearLayout c=new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a,20),dp(a,20),dp(a,20),dp(a,20));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{BLUE,BLUE_DARK});
        g.setCornerRadius(dp(a,26));
        c.setBackground(g);
        c.setElevation(dp(a,5));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(a,6),0,dp(a,12));
        p.addView(c,lp);
        return c;
    }

    public static LinearLayout card(Activity a,LinearLayout p){
        LinearLayout c=new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(a,22));
        g.setStroke(dp(a,1),Color.rgb(214,226,244));
        c.setBackground(g);
        c.setElevation(dp(a,3));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(a,8),0,dp(a,9));
        p.addView(c,lp);
        return c;
    }

    public static TextView text(Activity a,String s,int z,boolean bold){
        TextView v=new TextView(a);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(INK);
        v.setPadding(0,dp(a,4),0,dp(a,5));
        if(bold)v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        else v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        return v;
    }

    public static TextView whiteText(Activity a,String s,int z,boolean bold){
        TextView v=text(a,s,z,bold);
        v.setTextColor(Color.WHITE);
        return v;
    }

    public static Button button(Activity a,String s){
        Button b=new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        b.setMinHeight(dp(a,56));
        GradientDrawable g=new GradientDrawable();
        g.setColor(BLUE);
        g.setCornerRadius(dp(a,18));
        b.setBackground(g);
        b.setElevation(dp(a,2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,56));
        lp.setMargins(0,dp(a,7),0,dp(a,7));
        b.setLayoutParams(lp);
        return b;
    }

    public static Button secondaryButton(Activity a,String s){
        Button b=new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(BLUE_DARK);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(a,52));
        GradientDrawable g=new GradientDrawable();
        g.setColor(BLUE_LIGHT);
        g.setCornerRadius(dp(a,17));
        g.setStroke(dp(a,1),Color.rgb(179,205,246));
        b.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,52));
        lp.setMargins(0,dp(a,6),0,dp(a,6));
        b.setLayoutParams(lp);
        return b;
    }

    private static Button ghostButton(Activity a,String s){
        Button b=new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(BLUE);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    public static EditText field(Activity a,String hint,String value){
        EditText e=new EditText(a);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(16);
        e.setTextColor(INK);
        e.setHintTextColor(Color.rgb(120,139,166));
        e.setPadding(dp(a,15),dp(a,13),dp(a,15),dp(a,13));
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(a,16));
        g.setStroke(dp(a,1),Color.rgb(185,205,234));
        e.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(a,7),0,dp(a,9));
        e.setLayoutParams(lp);
        return e;
    }

    public static void bottomNav(Activity a,LinearLayout p,String current){
        LinearLayout nav=new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(a,5),dp(a,7),dp(a,5),dp(a,7));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(a,24));
        bg.setStroke(dp(a,1),Color.rgb(207,222,244));
        nav.setBackground(bg);
        nav.setElevation(dp(a,7));
        addNav(a,nav,"⌂\nHome",StandbyActivity.class,current,"Home");
        addNav(a,nav,"➤\nDrive",SimpleDriveActivity.class,current,"Drive");
        addNav(a,nav,"◎\nMock",QuickMockActivity.class,current,"Mock");
        addNav(a,nav,"◷\nSchedule",SchedulerActivity.class,current,"Schedule");
        addNav(a,nav,"⚙\nSettings",SettingsActivity.class,current,"Settings");
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,76));
        lp.setMargins(0,dp(a,16),0,dp(a,4));
        p.addView(nav,lp);
    }

    private static void addNav(Activity a,LinearLayout nav,String display,Class<?> target,String current,String key){
        boolean selected=key.equals(current);
        Button b=new Button(a);
        b.setText(display);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setTextColor(selected?Color.WHITE:MUTED);
        GradientDrawable g=new GradientDrawable();
        g.setColor(selected?BLUE:Color.TRANSPARENT);
        g.setCornerRadius(dp(a,18));
        b.setBackground(g);
        b.setOnClickListener(v->{
            if(!selected){
                Intent i=new Intent(a,target);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                a.startActivity(i);
            }
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a,61),1);
        lp.setMargins(dp(a,2),0,dp(a,2),0);
        nav.addView(b,lp);
    }
}
