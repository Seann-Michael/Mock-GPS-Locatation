package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;

public class GeneralSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"App preferences",true);
        LinearLayout card=UiKit.card(this,root);card.addView(UiKit.text(this,"Display and units",20,true));card.addView(UiKit.text(this,"These choices only change how information is shown.",13,false));
        Spinner units=new Spinner(this);String[] unitValues={"Miles","Kilometers"};units.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,unitValues));if("Kilometers".equals(AppPreferences.units(this)))units.setSelection(1);card.addView(units);
        Spinner theme=new Spinner(this);String[] themeValues={"System default","Light","Dark"};theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themeValues));String saved=AppPreferences.theme(this);for(int i=0;i<themeValues.length;i++)if(themeValues[i].equals(saved))theme.setSelection(i);card.addView(theme);
        Button save=UiKit.button(this,"Save preferences");card.addView(save);save.setOnClickListener(v->{AppPreferences.setUnits(this,units.getSelectedItem().toString());AppPreferences.setTheme(this,theme.getSelectedItem().toString());Toast.makeText(this,"Preferences saved",Toast.LENGTH_SHORT).show();});
        ScrollView s=new ScrollView(this);s.addView(root);setContentView(s);
    }
}
