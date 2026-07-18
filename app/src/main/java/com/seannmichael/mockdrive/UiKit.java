package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}

    public static final int BLUE = Color.rgb(20, 91, 218);
    public static final int BLUE_DARK = Color.rgb(10, 55, 145);
    public static final int BLUE_LIGHT = Color.rgb(231, 240, 255);
    public static final int PAGE = Color.rgb(245, 249, 255);
    public static final int INK = Color.rgb(18, 40, 73);
    public static final int MUTED = Color.rgb(91, 111, 139);
    public static final int BORDER = Color.rgb(214, 226, 244);

    // Semantic status colors (foreground / soft background pairs) used by pills and status rows.
    public static final int OK = Color.rgb(21, 128, 82), OK_BG = Color.rgb(224, 244, 233);
    public static final int WARN = Color.rgb(173, 118, 12), WARN_BG = Color.rgb(252, 244, 221);
    public static final int BAD = Color.rgb(191, 54, 54), BAD_BG = Color.rgb(252, 231, 231);

    public static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    public static LinearLayout page(Activity a){
        LinearLayout p=new LinearLayout(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(a,16),dp(a,10),dp(a,16),dp(a,24));
        p.setBackgroundColor(PAGE);
        p.setFocusable(true);
        p.setFocusableInTouchMode(true);
        return p;
    }

    public static void setStickyScreen(Activity a, LinearLayout content, String current){
        a.getWindow().setNavigationBarColor(Color.WHITE);
        if(Build.VERSION.SDK_INT>=23)a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout shell=new LinearLayout(a);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(PAGE);
        shell.setFocusable(true);
        shell.setFocusableInTouchMode(true);

        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        shell.addView(bottomNavView(a,current),new LinearLayout.LayoutParams(-1,dp(a,84)));

        shell.setOnApplyWindowInsetsListener((view,insets)->{
            int left=0,right=0,bottom=0;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets nav=insets.getInsets(WindowInsets.Type.navigationBars());
                left=nav.left;right=nav.right;bottom=nav.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();
                right=insets.getSystemWindowInsetRight();
                bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left,0,right,bottom);
            return insets;
        });
        a.setContentView(shell);
        shell.requestApplyInsets();
    }

    public static void topBar(Activity a,LinearLayout p,String title,boolean back){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,2),dp(a,4),dp(a,2),dp(a,10));
        if(back){Button b=ghostButton(a,"‹");b.setTextSize(30);b.setOnClickListener(v->a.finish());row.addView(b,new LinearLayout.LayoutParams(dp(a,48),dp(a,52)));}
        TextView t=text(a,title,27,true);t.setTextColor(BLUE_DARK);row.addView(t,new LinearLayout.LayoutParams(0,dp(a,58),1));p.addView(row);
    }

    public static LinearLayout hero(Activity a,LinearLayout p){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,20),dp(a,20),dp(a,20),dp(a,20));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{BLUE,BLUE_DARK});g.setCornerRadius(dp(a,26));c.setBackground(g);c.setElevation(dp(a,5));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,6),0,dp(a,12));p.addView(c,lp);return c;
    }

    public static LinearLayout card(Activity a,LinearLayout p){
        LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(a,18),dp(a,17),dp(a,18),dp(a,17));
        GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setCornerRadius(dp(a,22));g.setStroke(dp(a,1),Color.rgb(214,226,244));c.setBackground(g);c.setElevation(dp(a,3));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,8),0,dp(a,9));p.addView(c,lp);return c;
    }

    public static TextView text(Activity a,String s,int z,boolean bold){
        TextView v=new TextView(a);v.setText(s);v.setTextSize(z);v.setTextColor(INK);v.setPadding(0,dp(a,4),0,dp(a,5));v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return v;
    }

    public static TextView whiteText(Activity a,String s,int z,boolean bold){TextView v=text(a,s,z,bold);v.setTextColor(Color.WHITE);return v;}

    public static Button button(Activity a,String s){
        Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));b.setMinHeight(dp(a,56));
        GradientDrawable g=new GradientDrawable();g.setColor(BLUE);g.setCornerRadius(dp(a,18));b.setBackground(pressable(Color.argb(70,255,255,255),g));b.setElevation(dp(a,2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,56));lp.setMargins(0,dp(a,7),0,dp(a,7));b.setLayoutParams(lp);return b;
    }

    public static Button secondaryButton(Activity a,String s){
        Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(BLUE_DARK);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(a,52));
        GradientDrawable g=new GradientDrawable();g.setColor(BLUE_LIGHT);g.setCornerRadius(dp(a,17));g.setStroke(dp(a,1),Color.rgb(179,205,246));b.setBackground(pressable(Color.argb(45,20,91,218),g));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,52));lp.setMargins(0,dp(a,6),0,dp(a,6));b.setLayoutParams(lp);return b;
    }

    private static Button ghostButton(Activity a,String s){Button b=new Button(a);b.setText(s);b.setAllCaps(false);b.setTextColor(BLUE);b.setBackgroundColor(Color.TRANSPARENT);return b;}

    /** Wraps a shaped background with a touch ripple so buttons give tactile press feedback. */
    private static Drawable pressable(int rippleColor,GradientDrawable content){
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),content,content);
    }

    /** Small rounded status badge, e.g. "Active" / "Missing". Pass a foreground and soft background color. */
    public static TextView pill(Activity a,String text,int fg,int bg){
        TextView v=new TextView(a);v.setText(text);v.setTextSize(12);v.setTextColor(fg);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        v.setPadding(dp(a,11),dp(a,4),dp(a,11),dp(a,5));v.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable();g.setColor(bg);g.setCornerRadius(dp(a,20));v.setBackground(g);
        return v;
    }

    /** A thin divider line for separating rows inside a card. */
    public static void divider(Activity a,LinearLayout parent){
        View v=new View(a);v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(1,dp(a,1)));lp.setMargins(0,dp(a,10),0,dp(a,10));parent.addView(v,lp);
    }

    /** A label on the left with a status pill on the right, wrapped in one row. */
    public static void statusRow(Activity a,LinearLayout parent,String label,String value,int fg,int bg){
        LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(a,6),0,dp(a,6));
        TextView l=text(a,label,15,false);l.setTextColor(MUTED);l.setPadding(0,0,dp(a,10),0);
        row.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        TextView p=pill(a,value,fg,bg);row.addView(p,new LinearLayout.LayoutParams(-2,-2));
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }

    public static EditText field(Activity a,String hint,String value){
        EditText e=new EditText(a);styleInput(a,e,hint,value);return e;
    }

    public static AutoCompleteTextView autocompleteField(Activity a,String hint){
        AutoCompleteTextView e=new AutoCompleteTextView(a);styleInput(a,e,hint,"");e.setThreshold(2);e.setDropDownBackgroundDrawable(rounded(Color.WHITE,Color.rgb(185,205,234),dp(a,14),dp(a,1)));return e;
    }

    private static void styleInput(Activity a,EditText e,String hint,String value){
        e.setHint(hint);e.setText(value);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(Color.rgb(120,139,166));e.setPadding(dp(a,15),dp(a,13),dp(a,15),dp(a,13));
        e.setSingleLine(true);e.setFocusable(true);e.setFocusableInTouchMode(true);e.setClickable(true);e.setLongClickable(true);e.setTextIsSelectable(false);e.setShowSoftInputOnFocus(true);
        e.setBackground(rounded(Color.WHITE,Color.rgb(185,205,234),dp(a,16),dp(a,1)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,7),0,dp(a,9));e.setLayoutParams(lp);
        e.setOnFocusChangeListener((v,hasFocus)->{
            if(hasFocus){
                e.postDelayed(()->{
                    InputMethodManager keyboard=(InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(keyboard!=null)keyboard.showSoftInput(e,InputMethodManager.SHOW_IMPLICIT);
                },120);
            }
        });
    }

    private static GradientDrawable rounded(int fill,int stroke,int radius,int width){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(radius);g.setStroke(width,stroke);return g;}

    public static void bottomNav(Activity a,LinearLayout p,String current){p.addView(bottomNavView(a,current),new LinearLayout.LayoutParams(-1,dp(a,84)));}

    private static LinearLayout bottomNavView(Activity a,String current){
        LinearLayout wrap=new LinearLayout(a);wrap.setPadding(dp(a,12),dp(a,5),dp(a,12),dp(a,8));wrap.setBackgroundColor(PAGE);
        LinearLayout nav=new LinearLayout(a);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(a,5),dp(a,7),dp(a,5),dp(a,7));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(a,24));bg.setStroke(dp(a,1),Color.rgb(207,222,244));nav.setBackground(bg);nav.setElevation(dp(a,7));
        addNav(a,nav,"⌂\nHome",StandbyActivity.class,current,"Home");addNav(a,nav,"➤\nDrive",SimpleDriveActivity.class,current,"Drive");addNav(a,nav,"◎\nMock",QuickMockActivity.class,current,"Mock");addNav(a,nav,"◷\nSchedule",SchedulerActivity.class,current,"Schedule");addNav(a,nav,"⚙\nSettings",SettingsActivity.class,current,"Settings");
        wrap.addView(nav,new LinearLayout.LayoutParams(-1,-1));return wrap;
    }

    private static void addNav(Activity a,LinearLayout nav,String display,Class<?> target,String current,String key){
        boolean selected=key.equals(current);Button b=new Button(a);b.setText(display);b.setAllCaps(false);b.setTextSize(11);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setTextColor(selected?Color.WHITE:MUTED);
        GradientDrawable g=new GradientDrawable();g.setColor(selected?BLUE:Color.TRANSPARENT);g.setCornerRadius(dp(a,18));b.setBackground(g);
        b.setOnClickListener(v->{if(!selected){Intent i=new Intent(a,target);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(i);}});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a,61),1);lp.setMargins(dp(a,2),0,dp(a,2),0);nav.addView(b,lp);
    }
}