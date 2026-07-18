package com.seannmichael.mockdrive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;

public class StandbyActivity extends Activity {
    private final Handler handler=new Handler();
    private TextView deviceStatus,nextStatus;
    private final Runnable refreshTask=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,5000);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        requestPermissionsIfNeeded();
        LinearLayout root=UiKit.page(this);
        UiKit.topBar(this,root,"Mock Drive",false);

        LinearLayout hero=UiKit.card(this,root);
        hero.addView(UiKit.text(this,"Device ready",24,true));
        hero.addView(UiKit.text(this,"This phone stores and runs its campaigns independently.",14,false));
        deviceStatus=UiKit.text(this,"Loading status…",15,true);hero.addView(deviceStatus);

        LinearLayout quick=UiKit.card(this,root);
        quick.addView(UiKit.text(this,"Quick actions",19,true));
        Button mock=UiKit.button(this,"Set mock location");quick.addView(mock);mock.setOnClickListener(v->startActivity(new Intent(this,QuickMockActivity.class)));
        Button campaign=UiKit.secondaryButton(this,"Create or run campaign");quick.addView(campaign);campaign.setOnClickListener(v->startActivity(new Intent(this,CampaignActivity.class)));

        LinearLayout upcoming=UiKit.card(this,root);
        upcoming.addView(UiKit.text(this,"Next scheduled campaign",19,true));
        nextStatus=UiKit.text(this,"None scheduled",15,false);upcoming.addView(nextStatus);
        Button schedule=UiKit.secondaryButton(this,"View scheduler");upcoming.addView(schedule);schedule.setOnClickListener(v->startActivity(new Intent(this,SchedulerActivity.class)));

        LinearLayout controls=UiKit.card(this,root);
        controls.addView(UiKit.text(this,"Device controls",19,true));
        Button api=UiKit.secondaryButton(this,"Start local API");controls.addView(api);api.setOnClickListener(v->{Intent i=new Intent(this,ApiService.class).setAction(ApiService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);refresh();});
        Button stop=UiKit.secondaryButton(this,"Emergency stop and restore GPS");controls.addView(stop);stop.setOnClickListener(v->startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));

        UiKit.bottomNav(this,root,"Home");
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    @Override protected void onResume(){super.onResume();handler.post(refreshTask);}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);super.onPause();}

    private void requestPermissionsIfNeeded(){
        if(Build.VERSION.SDK_INT<23)return;
        ArrayList<String> permissions=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!permissions.isEmpty())requestPermissions(permissions.toArray(new String[0]),100);
    }

    private void refresh(){
        JSONArray campaigns=CampaignStore.all(this);JSONArray trips=TripStore.all(this);
        long next=Long.MAX_VALUE;String nextName="None";
        for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c==null||!c.optBoolean("enabled",true))continue;long t=c.optLong("startAtEpochMs",0);if(t>System.currentTimeMillis()&&t<next){next=t;nextName=c.optString("name","Campaign");}}
        boolean locationGranted=Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        deviceStatus.setText("Location permission: "+(locationGranted?"Granted":"Required")+"\nSaved campaigns: "+campaigns.length()+"   •   Queued trips: "+trips.length()+"\nLocal API: port 8765   •   Select Mock Drive in Developer Options");
        nextStatus.setText(next==Long.MAX_VALUE?"No campaign is currently scheduled.":nextName+"\n"+DateFormat.getDateTimeInstance().format(next));
    }
}