package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class QuickMockActivity extends Activity {
    private EditText search,lat,lon;
    private TextView status;
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Quick Mock",true);
        LinearLayout info=UiKit.card(this,root);info.addView(UiKit.text(this,"Set the phone location instantly",21,true));info.addView(UiKit.text(this,"No campaign is required. Search an address or enter coordinates, then hold that location until you clear it.",14,false));
        LinearLayout card=UiKit.card(this,root);
        search=UiKit.field(this,"Address or business name","");card.addView(search);
        Button find=UiKit.button(this,"Find address");card.addView(find);find.setOnClickListener(v->lookup());
        lat=UiKit.field(this,"Latitude","41.1432");lon=UiKit.field(this,"Longitude","-81.8552");card.addView(lat);card.addView(lon);
        Button set=UiKit.button(this,"Set and hold mock location");card.addView(set);set.setOnClickListener(v->setLocation());
        Button clear=UiKit.button(this,"Clear mock location");card.addView(clear);clear.setOnClickListener(v->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP));status.setText("Mock location cleared. Real GPS restored.");});
        status=UiKit.text(this,"Ready",15,true);card.addView(status);
        ScrollView s=new ScrollView(this);s.addView(root);setContentView(s);
    }
    private void lookup(){String q=search.getText().toString().trim();if(q.isEmpty())return;status.setText("Searching…");new Thread(()->{try{JSONObject p=RouteEngine.geocode(q);runOnUiThread(()->{lat.setText(String.valueOf(p.optDouble("latitude")));lon.setText(String.valueOf(p.optDouble("longitude")));status.setText("Found: "+p.optString("label"));});}catch(Exception e){runOnUiThread(()->status.setText("Lookup failed: "+e.getMessage()));}},"quick-geocode").start();}
    private void setLocation(){try{double a=Double.parseDouble(lat.getText().toString().trim()),o=Double.parseDouble(lon.getText().toString().trim());Intent i=new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT).putExtra(MockLocationService.EXTRA_LAT,a).putExtra(MockLocationService.EXTRA_LON,o);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);status.setText("Mock GPS active at "+a+", "+o);}catch(Exception e){Toast.makeText(this,"Enter valid coordinates",Toast.LENGTH_LONG).show();}}
}