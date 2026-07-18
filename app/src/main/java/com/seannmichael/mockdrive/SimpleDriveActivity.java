package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class SimpleDriveActivity extends Activity {
    private EditText startAddress,startLat,startLon,destinationAddress,destinationLat,destinationLon;
    private TextView status,speedValue;
    private SeekBar speedBar;
    private int selectedSpeed=35;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Drive",true);
        LinearLayout hero=UiKit.hero(this,root);
        hero.addView(UiKit.whiteText(this,"Mock navigation",27,true));
        hero.addView(UiKit.whiteText(this,"Choose point A and point B. You can change the simulated speed at any time while the trip is running.",15,false));

        LinearLayout startCard=UiKit.card(this,root);
        startCard.addView(UiKit.text(this,"A  Starting location",20,true));
        startCard.addView(UiKit.text(this,"Where the phone should appear before the drive starts",13,false));
        startAddress=UiKit.field(this,"Search starting address","");startCard.addView(startAddress);
        Button findStart=UiKit.secondaryButton(this,"Find starting address");startCard.addView(findStart);
        startLat=UiKit.field(this,"Latitude","41.181097");startCard.addView(startLat);
        startLon=UiKit.field(this,"Longitude","-81.974890");startCard.addView(startLon);
        findStart.setOnClickListener(v->geocode(startAddress.getText().toString(),startLat,startLon,"Start"));

        LinearLayout destinationCard=UiKit.card(this,root);
        destinationCard.addView(UiKit.text(this,"B  Destination",20,true));
        destinationCard.addView(UiKit.text(this,"Where Google Maps should navigate",13,false));
        destinationAddress=UiKit.field(this,"Search destination address","");destinationCard.addView(destinationAddress);
        Button findDestination=UiKit.secondaryButton(this,"Find destination address");destinationCard.addView(findDestination);
        destinationLat=UiKit.field(this,"Latitude","");destinationCard.addView(destinationLat);
        destinationLon=UiKit.field(this,"Longitude","");destinationCard.addView(destinationLon);
        findDestination.setOnClickListener(v->geocode(destinationAddress.getText().toString(),destinationLat,destinationLon,"Destination"));

        LinearLayout speedCard=UiKit.card(this,root);
        speedCard.addView(UiKit.text(this,"Travel speed",20,true));
        speedCard.addView(UiKit.text(this,"Move the slider, then tap Apply. It changes the active trip immediately.",13,false));
        speedValue=UiKit.text(this,"35 mph",30,true);speedValue.setTextColor(UiKit.BLUE_DARK);speedCard.addView(speedValue);
        speedBar=new SeekBar(this);speedBar.setMax(95);speedBar.setProgress(30);speedCard.addView(speedBar,new LinearLayout.LayoutParams(-1,-2));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){selectedSpeed=progress+5;speedValue.setText(selectedSpeed+" mph");}
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){}
        });
        Button applySpeed=UiKit.secondaryButton(this,"Apply speed now");speedCard.addView(applySpeed);
        applySpeed.setOnClickListener(v->{Intent i=new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_SET_SPEED).putExtra(MockLocationService.EXTRA_SPEED_MPH,(double)selectedSpeed);startService(i);status.setText("Speed updated to "+selectedSpeed+" mph.");});

        LinearLayout actionCard=UiKit.card(this,root);
        Button start=UiKit.button(this,"Start Google Maps navigation");actionCard.addView(start);
        Button pause=UiKit.secondaryButton(this,"Pause drive");actionCard.addView(pause);
        Button resume=UiKit.secondaryButton(this,"Resume drive");actionCard.addView(resume);
        Button stop=UiKit.secondaryButton(this,"Stop and restore real GPS");actionCard.addView(stop);
        status=UiKit.text(this,"Ready to begin",15,true);status.setTextColor(UiKit.BLUE_DARK);actionCard.addView(status);
        start.setOnClickListener(v->startNavigation());
        pause.setOnClickListener(v->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_PAUSE));status.setText("Drive paused.");});
        resume.setOnClickListener(v->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_RESUME));status.setText("Drive resumed at "+selectedSpeed+" mph.");});
        stop.setOnClickListener(v->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP));status.setText("Simulation stopped. Real GPS restored.");});

        UiKit.bottomNav(this,root,"Drive");ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }

    private void geocode(String query,EditText latField,EditText lonField,String label){
        if(query==null||query.trim().isEmpty()){toast("Enter an address");return;}status.setText("Finding "+label.toLowerCase()+"…");
        new Thread(()->{try{JSONObject p=RouteEngine.geocode(query.trim());runOnUiThread(()->{latField.setText(String.valueOf(p.optDouble("latitude")));lonField.setText(String.valueOf(p.optDouble("longitude")));status.setText(label+" found: "+p.optString("label"));});}catch(Exception e){runOnUiThread(()->status.setText(label+" lookup failed: "+e.getMessage()));}},"simple-geocode").start();
    }

    private void startNavigation(){
        try{
            double aLat=Double.parseDouble(startLat.getText().toString().trim()),aLon=Double.parseDouble(startLon.getText().toString().trim());
            double bLat=Double.parseDouble(destinationLat.getText().toString().trim()),bLon=Double.parseDouble(destinationLon.getText().toString().trim());
            Intent hold=new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT).putExtra(MockLocationService.EXTRA_LAT,aLat).putExtra(MockLocationService.EXTRA_LON,aLon);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(hold);else startService(hold);status.setText("Location A set. Preparing road route…");
            JSONArray waypoints=new JSONArray().put(new JSONObject().put("latitude",aLat).put("longitude",aLon).put("stopSeconds",0)).put(new JSONObject().put("latitude",bLat).put("longitude",bLon).put("stopSeconds",0));
            JSONObject trip=new JSONObject().put("name","Simple A to B drive").put("waypoints",waypoints).put("averageSpeedMph",selectedSpeed).put("speedVariationPercent",0).put("gpsUpdateIntervalMs",1000).put("speedProfile","fixed").put("randomStops",false).put("holdAtDestination",true).put("recurrence","none");
            JSONObject saved=TripStore.save(this,trip);
            new Handler().postDelayed(()->{try{String destination=bLat+","+bLon;String url="https://www.google.com/maps/dir/?api=1&origin="+aLat+","+aLon+"&destination="+Uri.encode(destination)+"&travelmode=driving&dir_action=navigate";Intent maps=new Intent(Intent.ACTION_VIEW,Uri.parse(url));maps.setPackage("com.google.android.apps.maps");try{startActivity(maps);}catch(Exception e){maps.setPackage(null);startActivity(maps);}TripScheduler.launch(this,saved);status.setText("Navigation active at "+selectedSpeed+" mph.");}catch(Exception e){status.setText("Could not start navigation: "+e.getMessage());}},1500);
        }catch(Exception e){toast("Enter valid start and destination values");}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
