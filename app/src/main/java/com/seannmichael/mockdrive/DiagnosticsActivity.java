package com.seannmichael.mockdrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class DiagnosticsActivity extends BaseActivity {
    private static final int REQUEST_SAVE_ZIP=4102;
    private final Handler handler=new Handler();
    private TextView stateView,logView;
    private File pendingZip;
    private final Runnable refreshTask=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Diagnostics",true);

        LinearLayout hero=UiKit.hero(this,root);
        hero.addView(UiKit.whiteText(this,"Navigation diagnostics",27,true));
        hero.addView(UiKit.whiteText(this,"Run a trip until it stops, then save the ZIP and upload it for analysis.",15,false));

        LinearLayout live=UiKit.card(this,root);
        live.addView(UiKit.text(this,"Live service state",20,true));
        stateView=UiKit.text(this,"No diagnostic state has been recorded yet.",14,false);
        stateView.setTextIsSelectable(true);live.addView(stateView);
        Button refresh=UiKit.secondaryButton(this,"Refresh now");live.addView(refresh);refresh.setOnClickListener(v->refresh());

        LinearLayout logs=UiKit.card(this,root);
        logs.addView(UiKit.text(this,"Recent navigation events",20,true));
        logs.addView(UiKit.text(this,"This shows the newest entries. The exported ZIP contains the complete log.",13,false));
        logView=UiKit.text(this,"No navigation log yet.",12,false);logView.setTextIsSelectable(true);logs.addView(logView);

        LinearLayout export=UiKit.card(this,root);
        export.addView(UiKit.text(this,"Export diagnostics",20,true));
        export.addView(UiKit.text(this,"Creates a ZIP containing navigation.log, exceptions.log, trip.json, route.json, phone_info.json, and live_state.json.",13,false));
        Button save=UiKit.button(this,"Save diagnostics ZIP");export.addView(save);save.setOnClickListener(v->saveDiagnostics());

        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    @Override protected void onResume(){super.onResume();handler.post(refreshTask);}
    @Override protected void onPause(){handler.removeCallbacks(refreshTask);super.onPause();}

    private void refresh(){
        String raw=DiagnosticLogger.readLiveState(this);
        if(raw.isEmpty())stateView.setText("No diagnostic state has been recorded yet.");
        else{
            try{
                JSONObject s=new JSONObject(raw);
                stateView.setText("Phase: "+s.optString("phase","unknown")+
                        "\nService running: "+s.optBoolean("serviceRunning")+
                        "\nWorker alive: "+s.optBoolean("workerAlive")+
                        "\nGPS provider enabled: "+s.optBoolean("gpsProviderEnabled")+
                        "\nGPS injections: "+s.optLong("injectionCount")+
                        "\nLast injection age: "+s.optLong("lastInjectionAgeMs")+" ms"+
                        "\nRoute segment: "+s.optInt("segment")+" / "+s.optInt("totalSegments")+
                        "\nCoordinate: "+s.optDouble("latitude")+", "+s.optDouble("longitude")+
                        "\nConfigured speed: "+Math.round(s.optDouble("speedMph"))+" mph"+
                        "\nLast error: "+(s.optString("lastError").isEmpty()?"None":s.optString("lastError")));
            }catch(Exception e){stateView.setText(raw);}
        }
        String tail=DiagnosticLogger.readLogTail(this,12000);
        logView.setText(tail.isEmpty()?"No navigation log yet.":tail);
    }

    private void saveDiagnostics(){
        try{
            pendingZip=DiagnosticLogger.exportZip(this);
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("application/zip");
            save.putExtra(Intent.EXTRA_TITLE,"MockDrive-diagnostics.zip");
            startActivityForResult(save,REQUEST_SAVE_ZIP);
        }catch(Exception e){Toast.makeText(this,"Could not prepare diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_SAVE_ZIP||resultCode!=RESULT_OK||data==null||data.getData()==null||pendingZip==null)return;
        Uri destination=data.getData();
        try(FileInputStream in=new FileInputStream(pendingZip);OutputStream out=getContentResolver().openOutputStream(destination)){
            if(out==null)throw new Exception("Android could not open the selected destination");
            byte[] buffer=new byte[8192];int read;
            while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
            out.flush();
            Toast.makeText(this,"Diagnostics ZIP saved. Upload that file here.",Toast.LENGTH_LONG).show();
        }catch(Exception e){Toast.makeText(this,"Could not save diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        finally{pendingZip=null;}
    }
}
