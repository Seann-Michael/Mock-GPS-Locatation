package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class CampaignActivity extends Activity {
    private EditText name,originLat,originLon,businessName,placeId,destLat,destLon,stops,speed;
    private Spinner recurrence;
    private TextView status,list;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int p=dp(16);root.setPadding(p,p,p,p);
        label(root,"Campaigns",28);label(root,"Create reusable Google Maps navigation and mock-drive scenarios.",14);
        name=field(root,"Campaign name","Medina Junk Removal");
        originLat=field(root,"Starting latitude","41.1432");originLon=field(root,"Starting longitude","-81.8552");
        businessName=field(root,"Verified destination business","1st Choice Junk Removal");placeId=field(root,"Google Place ID","");
        destLat=field(root,"Destination latitude","");destLon=field(root,"Destination longitude","");
        stops=field(root,"Intermediate stops: lat,lon,stopSeconds (one per line)","");stops.setMinLines(3);
        speed=field(root,"Average/max speed mph","40");
        recurrence=new Spinner(this);recurrence.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"None","Daily","Weekly","Monthly"}));root.addView(recurrence);
        CheckBox random=new CheckBox(this);random.setText("Random traffic stops");random.setChecked(true);root.addView(random);
        CheckBox hold=new CheckBox(this);hold.setText("Hold at destination");hold.setChecked(true);root.addView(hold);
        button(root,"Save Campaign").setOnClickListener(v->{try{JSONObject c=build(random.isChecked(),hold.isChecked());CampaignStore.save(this,c);status.setText("Campaign saved: "+c.getString("id"));refresh();}catch(Exception e){toast(e.getMessage());}});
        button(root,"Save and Start Now").setOnClickListener(v->{try{JSONObject c=CampaignStore.save(this,build(random.isChecked(),hold.isChecked()));CampaignLauncher.start(this,c,true);status.setText("Campaign started; Google Maps opened and GPS simulation launched.");refresh();}catch(Exception e){toast(e.getMessage());}});
        button(root,"Refresh Campaign List").setOnClickListener(v->refresh());
        status=label(root,"",14);list=label(root,"",12);list.setTextIsSelectable(true);refresh();
        ScrollView s=new ScrollView(this);s.addView(root);setContentView(s);
    }

    private JSONObject build(boolean random,boolean hold)throws Exception{
        JSONObject origin=new JSONObject().put("latitude",Double.parseDouble(originLat.getText().toString().trim())).put("longitude",Double.parseDouble(originLon.getText().toString().trim()));
        JSONObject destination=new JSONObject().put("businessName",businessName.getText().toString().trim()).put("placeId",placeId.getText().toString().trim())
                .put("latitude",Double.parseDouble(destLat.getText().toString().trim())).put("longitude",Double.parseDouble(destLon.getText().toString().trim()));
        JSONArray stopArray=new JSONArray();String raw=stops.getText().toString().trim();if(!raw.isEmpty())for(String line:raw.split("\\n")){String[] x=line.split(",");stopArray.put(new JSONObject().put("latitude",Double.parseDouble(x[0].trim())).put("longitude",Double.parseDouble(x[1].trim())).put("stopSeconds",x.length>2?Integer.parseInt(x[2].trim()):0));}
        int r=recurrence.getSelectedItemPosition();String repeat=r==1?"daily":r==2?"weekly":r==3?"monthly":"none";
        return new JSONObject().put("name",name.getText().toString().trim()).put("origin",origin).put("destination",destination).put("stops",stopArray)
                .put("averageSpeedMph",Double.parseDouble(speed.getText().toString().trim())).put("speedVariationPercent",8).put("gpsUpdateIntervalMs",1000)
                .put("speedProfile","road_aware").put("randomStops",random).put("holdAtDestination",hold).put("recurrence",repeat).put("enabled",true);
    }

    private void refresh(){try{list.setText(CampaignStore.all(this).toString(2));}catch(Exception e){list.setText(CampaignStore.all(this).toString());}}
    private TextView label(LinearLayout p,String t,int z){TextView v=new TextView(this);v.setText(t);v.setTextSize(z);v.setPadding(0,dp(8),0,dp(5));p.addView(v);return v;}
    private EditText field(LinearLayout p,String h,String v){EditText e=new EditText(this);e.setHint(h);e.setText(v);p.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Button button(LinearLayout p,String t){Button b=new Button(this);b.setText(t);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
