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

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;

public class DiagnosticsActivity extends BaseActivity {
    private final Handler handler=new Handler();
    private TextView stateView,logView;
    private final Runnable refreshTask=new Runnable(){@Override public void run(){refresh();handler.postDelayed(this,1000);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Diagnostics",true);

        LinearLayout hero=UiKit.hero(this,root);
        hero.addView(UiKit.whiteText(this,"Navigation diagnostics",27,true));
        hero.addView(UiKit.whiteText(this,"Run a trip until it stops, then export the ZIP and share it for analysis.",15,false));

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
        export.addView(UiKit.text(this,"Export and share",20,true));
        export.addView(UiKit.text(this,"Creates a ZIP containing navigation.log, exceptions.log, trip.json, route.json, phone_info.json, and live_state.json.",13,false));
        Button share=UiKit.button(this,"Export diagnostics ZIP");export.addView(share);share.setOnClickListener(v->shareDiagnostics());

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

    private void shareDiagnostics(){
        try{
            File zip=DiagnosticLogger.exportZip(this);
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",zip);
            Intent share=new Intent(Intent.ACTION_SEND);
            share.setType("application/zip");share.putExtra(Intent.EXTRA_STREAM,uri);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Share Mock Drive diagnostics"));
        }catch(Exception e){Toast.makeText(this,"Could not export diagnostics: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
