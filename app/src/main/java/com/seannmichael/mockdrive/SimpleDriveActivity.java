package com.seannmichael.mockdrive;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class SimpleDriveActivity extends BaseActivity {
    private AutoCompleteTextView startAddress,destinationAddress;
    private EditText startLat,startLon,destinationLat,destinationLon;
    private TextView status,speedValue;
    private int selectedSpeed=35;
    private final Handler searchHandler=new Handler();

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);
        UiKit.topBar(this,root,"Drive",true);

        LinearLayout hero=UiKit.hero(this,root);
        hero.addView(UiKit.whiteText(this,"Mock navigation",27,true));
        hero.addView(UiKit.whiteText(this,"Choose A, choose B, set one speed, and start the trip.",15,false));

        status=UiKit.text(this,"Ready to begin",15,true);
        status.setTextColor(UiKit.BLUE_DARK);

        LinearLayout startCard=UiKit.card(this,root);
        startCard.addView(UiKit.text(this,"A  Starting location",20,true));
        startCard.addView(UiKit.text(this,"Begin typing and choose the correct result",13,false));
        startAddress=UiKit.autocompleteField(this,"Search address or business");
        startCard.addView(startAddress);
        startLat=UiKit.field(this,"Latitude","41.181097");
        startLon=UiKit.field(this,"Longitude","-81.974890");
        startCard.addView(startLat);
        startCard.addView(startLon);

        LinearLayout destinationCard=UiKit.card(this,root);
        destinationCard.addView(UiKit.text(this,"B  Destination",20,true));
        destinationCard.addView(UiKit.text(this,"Choose where Google Maps should navigate",13,false));
        destinationAddress=UiKit.autocompleteField(this,"Search address or business");
        destinationCard.addView(destinationAddress);
        destinationLat=UiKit.field(this,"Latitude","");
        destinationLon=UiKit.field(this,"Longitude","");
        destinationCard.addView(destinationLat);
        destinationCard.addView(destinationLon);

        setupAutocomplete(startAddress,startLat,startLon,"Start");
        setupAutocomplete(destinationAddress,destinationLat,destinationLon,"Destination");

        LinearLayout speedCard=UiKit.card(this,root);
        speedCard.addView(UiKit.text(this,"Travel speed",20,true));
        speedCard.addView(UiKit.text(this,"This speed is locked in when the trip starts",13,false));
        speedValue=UiKit.text(this,"35 mph",30,true);
        speedValue.setTextColor(UiKit.BLUE_DARK);
        speedCard.addView(speedValue);
        SeekBar speedBar=new SeekBar(this);
        speedBar.setMax(95);
        speedBar.setProgress(30);
        speedCard.addView(speedBar,new LinearLayout.LayoutParams(-1,-2));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){selectedSpeed=progress+5;speedValue.setText(selectedSpeed+" mph");}
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){}
        });

        LinearLayout actionCard=UiKit.card(this,root);
        Button start=UiKit.button(this,"Start Google Maps navigation");
        actionCard.addView(start);
        Button stop=UiKit.secondaryButton(this,"Stop and restore real GPS");
        actionCard.addView(stop);
        Button diagnostics=UiKit.secondaryButton(this,"Open navigation diagnostics");
        actionCard.addView(diagnostics);
        actionCard.addView(status);
        start.setOnClickListener(v->startNavigation());
        stop.setOnClickListener(v->{startService(new Intent(this,MockLocationService.class).setAction(MockLocationService.ACTION_STOP));status.setText("Simulation stopped. Real GPS restored.");});
        diagnostics.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));

        UiKit.setStickyScreen(this,root,"Drive");
    }

    private void setupAutocomplete(AutoCompleteTextView input,EditText lat,EditText lon,String label){
        final ArrayList<String> labels=new ArrayList<>();
        final ArrayList<String> ids=new ArrayList<>();
        final PlacesSuggestionAdapter adapter=new PlacesSuggestionAdapter(this,labels);
        input.setAdapter(adapter);
        input.setOnFocusChangeListener((view,hasFocus)->{if(hasFocus){input.postDelayed(()->{InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);if(adapter.getCount()>0)input.showDropDown();},120);}});
        input.setOnItemClickListener((parent,view,position,id)->{
            if(position<0||position>=ids.size())return;
            String placeId=ids.get(position);status.setText("Loading "+label.toLowerCase()+" details…");
            new Thread(()->{try{JSONObject place=GooglePlacesEngine.placeDetails(this,placeId);runOnUiThread(()->{input.setText(place.optString("formattedAddress",place.optString("label","")),false);lat.setText(String.valueOf(place.optDouble("latitude")));lon.setText(String.valueOf(place.optDouble("longitude")));input.dismissDropDown();status.setText(label+" selected: "+place.optString("label"));});}catch(Exception e){runOnUiThread(()->status.setText("Could not load place: "+friendlyPlacesError(e)));}},"place-details").start();
        });
        input.addTextChangedListener(new TextWatcher(){
            Runnable pending;
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                if(pending!=null)searchHandler.removeCallbacks(pending);
                String q=s==null?"":s.toString().trim();
                if(q.length()<2){labels.clear();ids.clear();adapter.notifyDataSetChanged();input.dismissDropDown();return;}
                pending=()->new Thread(()->{try{JSONArray results=GooglePlacesEngine.autocomplete(SimpleDriveActivity.this,q);ArrayList<String> nextLabels=new ArrayList<>();ArrayList<String> nextIds=new ArrayList<>();for(int i=0;i<results.length();i++){JSONObject r=results.optJSONObject(i);if(r!=null){nextLabels.add(r.optString("label"));nextIds.add(r.optString("placeId"));}}runOnUiThread(()->{if(!q.equals(input.getText().toString().trim()))return;labels.clear();labels.addAll(nextLabels);ids.clear();ids.addAll(nextIds);adapter.notifyDataSetChanged();if(input.hasFocus()&&!labels.isEmpty())input.showDropDown();if(labels.isEmpty())status.setText("No Google Places matches found.");});}catch(Exception e){runOnUiThread(()->status.setText(friendlyPlacesError(e)));}},"places-autocomplete").start();
                searchHandler.postDelayed(pending,450);
            }
            @Override public void afterTextChanged(Editable s){}
        });
    }

    private String friendlyPlacesError(Exception e){String message=e.getMessage()==null?"Google Places request failed":e.getMessage();if(message.contains("API key"))return "Add your Google Places API key in Settings → API and access.";if(message.contains("403"))return "Google Places denied the request. Check Places API (New), billing, and key restrictions.";return message;}

    private void startNavigation(){
        try{
            double aLat=Double.parseDouble(startLat.getText().toString().trim());
            double aLon=Double.parseDouble(startLon.getText().toString().trim());
            double bLat=Double.parseDouble(destinationLat.getText().toString().trim());
            double bLon=Double.parseDouble(destinationLon.getText().toString().trim());
            JSONArray waypoints=new JSONArray().put(new JSONObject().put("latitude",aLat).put("longitude",aLon).put("stopSeconds",0)).put(new JSONObject().put("latitude",bLat).put("longitude",bLon).put("stopSeconds",0));
            JSONObject trip=new JSONObject().put("name","Simple A to B drive").put("waypoints",waypoints).put("averageSpeedMph",selectedSpeed).put("speedVariationPercent",0).put("gpsUpdateIntervalMs",1000).put("randomStops",false).put("holdAtDestination",true).put("recurrence","none");
            JSONObject saved=TripStore.save(this,trip);
            TripScheduler.launch(this,saved);
            status.setText("Starting route at "+selectedSpeed+" mph…");
            new Handler().postDelayed(()->{try{String url="https://www.google.com/maps/dir/?api=1&origin="+aLat+","+aLon+"&destination="+Uri.encode(bLat+","+bLon)+"&travelmode=driving&dir_action=navigate";Intent maps=new Intent(Intent.ACTION_VIEW,Uri.parse(url));maps.setPackage("com.google.android.apps.maps");try{startActivity(maps);}catch(Exception e){maps.setPackage(null);startActivity(maps);}status.setText("Navigation active at "+selectedSpeed+" mph.");}catch(Exception e){status.setText("Could not open Google Maps: "+e.getMessage());}},1200);
        }catch(Exception e){toast("Choose both locations or enter valid coordinates");}
    }

    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
