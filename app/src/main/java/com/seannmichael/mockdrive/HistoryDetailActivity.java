package com.seannmichael.mockdrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

public class HistoryDetailActivity extends BaseActivity {
    private static final int SAVE_DIAGNOSTICS = 4401;
    private JSONObject record;
    private File pendingZip;

    @Override protected void onCreate(Bundle state){super.onCreate(state);render();}

    private void render(){
        String id=getIntent().getStringExtra("history_id");
        record=HistoryStore.get(this,id);
        LinearLayout root=UiKit.page(this);
        UiKit.topBar(this,root,"Simulation details",true);
        if(record==null){LinearLayout c=UiKit.card(this,root);c.addView(UiKit.text(this,"History record not found",20,true));UiKit.setStickyScreen(this,root,"History");return;}

        LinearLayout hero=UiKit.hero(this,root);
        hero.addView(UiKit.whiteText(this,record.optString("status","unknown").toUpperCase(),25,true));
        hero.addView(UiKit.whiteText(this,record.optString("startAddress")+" → "+record.optString("endAddress"),14,false));

        LinearLayout summary=UiKit.card(this,root);
        summary.addView(UiKit.text(this,"Trip summary",20,true));
        summary.addView(UiKit.text(this,"Start: "+format(record.optLong("startTime"))+"\nEnd: "+format(record.optLong("endTime"))+"\nDuration: "+duration(record.optLong("durationMs"))+"\nDistance: "+String.format("%.2f miles",record.optDouble("miles")),15,false));

        JSONObject trip=record.optJSONObject("trip");
        LinearLayout setup=UiKit.card(this,root);
        setup.addView(UiKit.text(this,"Simulation setup",20,true));
        setup.addView(UiKit.text(this,"Start address: "+record.optString("startAddress")+"\nEnding address: "+record.optString("endAddress")+"\nConfigured speed: "+Math.round(trip==null?0:trip.optDouble("averageSpeedMph"))+" mph\nStatus: "+record.optString("status")+"\nTrip ID: "+record.optString("tripId")+"\nHistory ID: "+record.optString("historyId"),15,false));

        LinearLayout diag=UiKit.card(this,root);
        diag.addView(UiKit.text(this,"Detailed diagnostics",20,true));
        diag.addView(UiKit.text(this,"The readable summary appears below. Use Save complete diagnostics ZIP for the raw route, every injected and Android-reported location, provider state, service events, and exception stack traces.",13,false));
        diag.addView(UiKit.text(this,record.optString("diagnostics","No diagnostic details were recorded."),12,false));

        LinearLayout actions=UiKit.card(this,root);
        actions.addView(UiKit.text(this,"Actions",20,true));
        Button save=UiKit.button(this,"Save complete diagnostics ZIP");
        actions.addView(save);
        save.setOnClickListener(v->saveDiagnostics());
        Button email=UiKit.secondaryButton(this,"Email diagnostic summary");
        actions.addView(email);
        email.setOnClickListener(v->shareEmail());
        Button clone=UiKit.secondaryButton(this,"Clone and run again");
        actions.addView(clone);
        clone.setOnClickListener(v->cloneAndRun());
        UiKit.setStickyScreen(this,root,"History");
    }

    private void saveDiagnostics(){
        try{
            pendingZip=SimulationDiagnostics.exportZip(this,record.optString("historyId"));
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("application/zip");
            save.putExtra(Intent.EXTRA_TITLE,pendingZip.getName());
            startActivityForResult(save,SAVE_DIAGNOSTICS);
        }catch(Exception e){Toast.makeText(this,"Could not prepare diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=SAVE_DIAGNOSTICS||resultCode!=RESULT_OK||data==null||data.getData()==null||pendingZip==null)return;
        try(FileInputStream input=new FileInputStream(pendingZip);OutputStream output=getContentResolver().openOutputStream(data.getData())){
            if(output==null)throw new Exception("Unable to open selected file");
            byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))>0)output.write(buffer,0,read);
            Toast.makeText(this,"Diagnostics ZIP saved",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Could not save diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void cloneAndRun(){
        try{
            JSONObject cloned=HistoryStore.cloneTrip(this,record.optString("historyId"));
            JSONArray w=cloned.getJSONArray("waypoints");
            JSONObject a=w.getJSONObject(0),b=w.getJSONObject(w.length()-1);
            double aLat=a.getDouble("latitude"),aLon=a.getDouble("longitude"),bLat=b.getDouble("latitude"),bLon=b.getDouble("longitude");
            Intent hold=new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT).putExtra(MockLocationService.EXTRA_LAT,aLat).putExtra(MockLocationService.EXTRA_LON,aLon);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(hold);else startService(hold);
            new Handler().postDelayed(()->{try{
                String url="https://www.google.com/maps/dir/?api=1&origin="+aLat+","+aLon+"&destination="+Uri.encode(bLat+","+bLon)+"&travelmode=driving&dir_action=navigate";
                Intent maps=new Intent(Intent.ACTION_VIEW,Uri.parse(url));maps.setPackage("com.google.android.apps.maps");
                try{startActivity(maps);}catch(Exception e){maps.setPackage(null);startActivity(maps);}
                TripScheduler.launch(this,cloned);
                Toast.makeText(this,"Cloned simulation started",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Could not reopen route: "+e.getMessage(),Toast.LENGTH_LONG).show();}},1500);
        }catch(Exception e){Toast.makeText(this,"Could not clone: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void shareEmail(){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_SUBJECT,"Mock Drive diagnostics - "+record.optString("status"));
        i.putExtra(Intent.EXTRA_TEXT,emailBody());
        try{startActivity(Intent.createChooser(i,"Email simulation diagnostics"));}
        catch(Exception e){Toast.makeText(this,"No email app is available",Toast.LENGTH_LONG).show();}
    }

    private String emailBody(){
        JSONObject t=record.optJSONObject("trip");
        return "Mock Drive simulation diagnostics\n\nStatus: "+record.optString("status")+"\nStart: "+format(record.optLong("startTime"))+"\nEnd: "+format(record.optLong("endTime"))+"\nDuration: "+duration(record.optLong("durationMs"))+"\nMiles: "+String.format("%.2f",record.optDouble("miles"))+"\nStart address: "+record.optString("startAddress")+"\nEnding address: "+record.optString("endAddress")+"\nSpeed: "+Math.round(t==null?0:t.optDouble("averageSpeedMph"))+" mph\nTrip ID: "+record.optString("tripId")+"\nHistory ID: "+record.optString("historyId")+"\n\nDiagnostics summary:\n"+record.optString("diagnostics")+"\n\nFor complete raw evidence, export the diagnostics ZIP from the simulation history page.";
    }

    private String format(long t){return t<=0?"Not recorded":DateFormat.getDateTimeInstance().format(new Date(t));}
    private String duration(long ms){long s=Math.max(0,ms/1000),h=s/3600,m=(s%3600)/60;return h>0?h+" hr "+m+" min":m+" min "+(s%60)+" sec";}
}