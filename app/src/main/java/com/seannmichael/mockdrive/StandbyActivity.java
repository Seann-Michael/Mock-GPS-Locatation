package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;

public class StandbyActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView status;
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 5000); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18); root.setPadding(p,p,p,p);
        text(root,"Mock Drive Device",28);
        text(root,"Standalone mode — this phone stores and runs its own campaigns.",15);
        status = text(root,"Loading…",16);
        button(root,"Campaigns").setOnClickListener(v -> startActivity(new Intent(this, CampaignActivity.class)));
        button(root,"Trip Planner").setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        button(root,"Settings").setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        button(root,"Start Local API").setOnClickListener(v -> {
            Intent i=new Intent(this,ApiService.class).setAction(ApiService.ACTION_START);
            if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
            refresh();
        });
        button(root,"Stop Active Simulation").setOnClickListener(v -> startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    @Override protected void onResume(){super.onResume();handler.post(refreshTask);}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);super.onPause();}

    private void refresh(){
        JSONArray campaigns=CampaignStore.all(this);
        JSONArray trips=TripStore.all(this);
        long next=Long.MAX_VALUE;String nextName="None";
        for(int i=0;i<campaigns.length();i++){
            JSONObject c=campaigns.optJSONObject(i);if(c==null||!c.optBoolean("enabled",true))continue;
            long t=c.optLong("startAtEpochMs",0);if(t>System.currentTimeMillis()&&t<next){next=t;nextName=c.optString("name","Campaign");}
        }
        String nextText=next==Long.MAX_VALUE?"None":nextName+" — "+DateFormat.getDateTimeInstance().format(next);
        status.setText("Status: Waiting / ready\nSaved campaigns: "+campaigns.length()+"\nQueued trips: "+trips.length()+"\nNext campaign: "+nextText+"\nLocal API port: 8765\nMock location app must remain selected in Developer Options.");
    }

    private TextView text(LinearLayout p,String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(8),0,dp(8));p.addView(v);return v;}
    private Button button(LinearLayout p,String s){Button b=new Button(this);b.setText(s);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
