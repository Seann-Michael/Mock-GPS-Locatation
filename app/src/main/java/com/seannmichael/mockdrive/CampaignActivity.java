package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class CampaignActivity extends Activity {
    private EditText name,originLat,originLon,businessName,placeId,destLat,destLon,stops,speed;
    private Spinner recurrence;private TextView status,list;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Campaigns",true);
        LinearLayout intro=UiKit.card(this,root);intro.addView(UiKit.text(this,"Create a reusable simulated drive",21,true));intro.addView(UiKit.text(this,"Set the origin, verified destination, stops, speed and recurrence.",14,false));
        LinearLayout form=UiKit.card(this,root);
        name=field(form,"Campaign name","Medina Junk Removal");originLat=field(form,"Starting latitude","41.1432");originLon=field(form,"Starting longitude","-81.8552");businessName=field(form,"Verified destination business","1st Choice Junk Removal");placeId=field(form,"Google Place ID","");destLat=field(form,"Destination latitude","");destLon=field(form,"Destination longitude","");stops=field(form,"Intermediate stops: lat,lon,stopSeconds"," ");stops.setMinLines(3);speed=field(form,"Average/max speed mph","40");
        recurrence=new Spinner(this);recurrence.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"None","Daily","Weekly","Monthly"}));form.addView(recurrence);
        CheckBox random=new CheckBox(this);random.setText("Random traffic stops");random.setChecked(true);form.addView(random);CheckBox hold=new CheckBox(this);hold.setText("Hold at destination");hold.setChecked(true);form.addView(hold);
        Button save=UiKit.button(this,"Save campaign");form.addView(save);save.setOnClickListener(v->{try{JSONObject c=build(random.isChecked(),hold.isChecked());CampaignStore.save(this,c);status.setText("Campaign saved");refresh();}catch(Exception e){toast(e.getMessage());}});
        Button start=UiKit.button(this,"Save and start now");form.addView(start);start.setOnClickListener(v->{try{JSONObject c=CampaignStore.save(this,build(random.isChecked(),hold.isChecked()));CampaignLauncher.start(this,c,true);status.setText("Campaign started; Google Maps opened.");refresh();}catch(Exception e){toast(e.getMessage());}});
        LinearLayout saved=UiKit.card(this,root);saved.addView(UiKit.text(this,"Saved campaigns",20,true));Button refresh=UiKit.button(this,"Refresh list");saved.addView(refresh);refresh.setOnClickListener(v->refresh());status=UiKit.text(this,"",14,true);saved.addView(status);list=UiKit.text(this,"",12,false);list.setTextIsSelectable(true);saved.addView(list);refresh();ScrollView s=new ScrollView(this);s.addView(root);setContentView(s);
    }
    private JSONObject build(boolean random,boolean hold)throws Exception{JSONObject origin=new JSONObject().put("latitude",Double.parseDouble(originLat.getText().toString().trim())).put("longitude",Double.parseDouble(originLon.getText().toString().trim()));JSONObject destination=new JSONObject().put("businessName",businessName.getText().toString().trim()).put("placeId",placeId.getText().toString().trim()).put("latitude",Double.parseDouble(destLat.getText().toString().trim())).put("longitude",Double.parseDouble(destLon.getText().toString().trim()));JSONArray stopArray=new JSONArray();String raw=stops.getText().toString().trim();if(!raw.isEmpty())for(String line:raw.split("\\n")){String[] x=line.split(",");stopArray.put(new JSONObject().put("latitude",Double.parseDouble(x[0].trim())).put("longitude",Double.parseDouble(x[1].trim())).put("stopSeconds",x.length>2?Integer.parseInt(x[2].trim()):0));}int r=recurrence.getSelectedItemPosition();String repeat=r==1?"daily":r==2?"weekly":r==3?"monthly":"none";return new JSONObject().put("name",name.getText().toString().trim()).put("origin",origin).put("destination",destination).put("stops",stopArray).put("averageSpeedMph",Double.parseDouble(speed.getText().toString().trim())).put("speedVariationPercent",8).put("gpsUpdateIntervalMs",1000).put("speedProfile","road_aware").put("randomStops",random).put("holdAtDestination",hold).put("recurrence",repeat).put("enabled",true);}
    private void refresh(){try{list.setText(CampaignStore.all(this).toString(2));}catch(Exception e){list.setText(CampaignStore.all(this).toString());}}
    private EditText field(LinearLayout p,String h,String v){EditText e=UiKit.field(this,h,v);p.addView(e);return e;}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}