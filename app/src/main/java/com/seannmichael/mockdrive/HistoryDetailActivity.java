package com.seannmichael.mockdrive;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public class HistoryDetailActivity extends BaseActivity {
    private JSONObject record;
    @Override protected void onCreate(Bundle state){super.onCreate(state);render();}

    private void render(){
        String id=getIntent().getStringExtra("history_id");record=HistoryStore.get(this,id);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Simulation details",true);
        if(record==null){LinearLayout c=UiKit.card(this,root);c.addView(UiKit.text(this,"History record not found",20,true));UiKit.setStickyScreen(this,root,"History");return;}
        LinearLayout hero=UiKit.hero(this,root);hero.addView(UiKit.whiteText(this,record.optString("status","unknown").toUpperCase(),25,true));hero.addView(UiKit.whiteText(this,record.optString("startAddress")+" → "+record.optString("endAddress"),14,false));

        LinearLayout summary=UiKit.card(this,root);summary.addView(UiKit.text(this,"Trip summary",20,true));
        summary.addView(UiKit.text(this,"Start: "+format(record.optLong("startTime"))+"\nEnd: "+format(record.optLong("endTime"))+"\nDuration: "+duration(record.optLong("durationMs"))+"\nDistance: "+String.format("%.2f miles",record.optDouble("miles")),15,false));

        JSONObject trip=record.optJSONObject("trip");
        LinearLayout setup=UiKit.card(this,root);setup.addView(UiKit.text(this,"Simulation setup",20,true));
        setup.addView(UiKit.text(this,"Start address: "+record.optString("startAddress")+"\nEnding address: "+record.optString("endAddress")+"\nConfigured speed: "+Math.round(trip==null?0:trip.optDouble("averageSpeedMph"))+" mph\nStatus: "+record.optString("status"),15,false));

        LinearLayout diag=UiKit.card(this,root);diag.addView(UiKit.text(this,"Diagnostics",20,true));diag.addView(UiKit.text(this,record.optString("diagnostics","No diagnostic details were recorded."),13,false));

        LinearLayout actions=UiKit.card(this,root);actions.addView(UiKit.text(this,"Actions",20,true));
        Button clone=UiKit.button(this,"Clone and run again");actions.addView(clone);clone.setOnClickListener(v->cloneAndRun());
        Button email=UiKit.secondaryButton(this,"Email diagnostic details");actions.addView(email);email.setOnClickListener(v->shareEmail());
        UiKit.setStickyScreen(this,root,"History");
    }

    private void cloneAndRun(){try{JSONObject cloned=HistoryStore.cloneTrip(this,record.optString("historyId"));TripScheduler.launch(this,cloned);Toast.makeText(this,"Cloned simulation started",Toast.LENGTH_LONG).show();startActivity(new Intent(this,SimpleDriveActivity.class));}catch(Exception e){Toast.makeText(this,"Could not clone: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void shareEmail(){
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("message/rfc822");i.putExtra(Intent.EXTRA_SUBJECT,"Mock Drive diagnostics - "+record.optString("status"));i.putExtra(Intent.EXTRA_TEXT,emailBody());
        try{startActivity(Intent.createChooser(i,"Email simulation diagnostics"));}catch(Exception e){Toast.makeText(this,"No email app is available",Toast.LENGTH_LONG).show();}
    }
    private String emailBody(){JSONObject t=record.optJSONObject("trip");return "Mock Drive simulation diagnostics\n\nStatus: "+record.optString("status")+"\nStart: "+format(record.optLong("startTime"))+"\nEnd: "+format(record.optLong("endTime"))+"\nDuration: "+duration(record.optLong("durationMs"))+"\nMiles: "+String.format("%.2f",record.optDouble("miles"))+"\nStart address: "+record.optString("startAddress")+"\nEnding address: "+record.optString("endAddress")+"\nSpeed: "+Math.round(t==null?0:t.optDouble("averageSpeedMph"))+" mph\n\nDiagnostics:\n"+record.optString("diagnostics");}
    private String format(long t){return t<=0?"Not recorded":DateFormat.getDateTimeInstance().format(new Date(t));}
    private String duration(long ms){long s=Math.max(0,ms/1000),h=s/3600,m=(s%3600)/60;return h>0?h+" hr "+m+" min":m+" min "+(s%60)+" sec";}
}
