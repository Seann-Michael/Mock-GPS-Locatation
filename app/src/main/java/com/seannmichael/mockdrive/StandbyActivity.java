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
    private final Handler handler=new Handler();private TextView status;
    private final Runnable refreshTask=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,5000);}};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Mock Drive",false);
        LinearLayout hero=UiKit.card(this,root);hero.addView(UiKit.text(this,"Device ready",25,true));hero.addView(UiKit.text(this,"Standalone location simulator",14,false));status=UiKit.text(this,"Loading…",15,false);hero.addView(status);
        LinearLayout quick=UiKit.card(this,root);quick.addView(UiKit.text(this,"Quick actions",20,true));
        Button mock=UiKit.button(this,"Set mock location");quick.addView(mock);mock.setOnClickListener(v->startActivity(new Intent(this,QuickMockActivity.class)));
        Button campaigns=UiKit.button(this,"Campaigns");quick.addView(campaigns);campaigns.setOnClickListener(v->startActivity(new Intent(this,CampaignActivity.class)));
        Button planner=UiKit.button(this,"Trip planner");quick.addView(planner);planner.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        Button settings=UiKit.button(this,"Settings");quick.addView(settings);settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        LinearLayout service=UiKit.card(this,root);service.addView(UiKit.text(this,"Device controls",20,true));
        Button api=UiKit.button(this,"Start local API");service.addView(api);api.setOnClickListener(v->{Intent i=new Intent(this,ApiService.class).setAction(ApiService.ACTION_START);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);refresh();});
        Button stop=UiKit.button(this,"Stop simulation and restore real GPS");service.addView(stop);stop.setOnClickListener(v->startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    @Override protected void onResume(){super.onResume();handler.post(refreshTask);}@Override protected void onPause(){handler.removeCallbacks(refreshTask);super.onPause();}
    private void refresh(){JSONArray campaigns=CampaignStore.all(this),trips=TripStore.all(this);long next=Long.MAX_VALUE;String nextName="None";for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c==null||!c.optBoolean("enabled",true))continue;long t=c.optLong("startAtEpochMs",0);if(t>System.currentTimeMillis()&&t<next){next=t;nextName=c.optString("name","Campaign");}}String nextText=next==Long.MAX_VALUE?"None":nextName+" — "+DateFormat.getDateTimeInstance().format(next);status.setText("Status: Waiting / ready\nSaved campaigns: "+campaigns.length()+"\nQueued trips: "+trips.length()+"\nNext campaign: "+nextText+"\nAPI port: 8765");}
}