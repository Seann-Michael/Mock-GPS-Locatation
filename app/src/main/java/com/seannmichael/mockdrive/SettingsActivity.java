package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private TextView apiStatus;
    private EditText placesKey;
    private Spinner units, theme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); root.setPadding(p,p,p,p);
        label(root, "Settings", 28);
        label(root, "API access", 21);
        apiStatus = label(root, "", 14);
        button(root, "Generate New API Key").setOnClickListener(v -> { String key=AppPreferences.generateApiKey(this);refreshApi();copy(key);toast("New API key generated and copied. The previous key is no longer valid."); });
        button(root, "Copy Current API Key").setOnClickListener(v -> { String key=AppPreferences.apiKey(this);if(key==null||key.startsWith("revoked-"))toast("No active API key");else{copy(key);toast("API key copied");} });
        button(root, "Revoke API Key").setOnClickListener(v -> { AppPreferences.revokeApiKey(this);refreshApi();toast("API key revoked"); });
        label(root, "Treat API keys like passwords. Anyone with the key and network access can control the simulator.", 13);
        label(root, "Google Places", 21);
        placesKey = field(root, "Google Places API key", GooglePlacesEngine.getApiKey(this));
        placesKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button(root, "Save Google Places Key").setOnClickListener(v -> { GooglePlacesEngine.setApiKey(this,placesKey.getText().toString().trim());toast("Google Places key saved locally"); });
        label(root, "Application preferences", 21);
        units=spinner(root,new String[]{"Miles","Kilometers"},AppPreferences.units(this));
        theme=spinner(root,new String[]{"System default","Light","Dark"},AppPreferences.theme(this));
        button(root,"Save Preferences").setOnClickListener(v->{AppPreferences.setUnits(this,units.getSelectedItem().toString());AppPreferences.setTheme(this,theme.getSelectedItem().toString());toast("Preferences saved");});
        label(root,"Phone setup",21);
        button(root,"Open Developer Options").setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        button(root,"Open Battery Optimization Settings").setOnClickListener(v->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(root,"License").setOnClickListener(v->startActivity(new Intent(this,LicenseActivity.class)));
        button(root,"Billing").setOnClickListener(v->startActivity(new Intent(this,BillingActivity.class)));
        button(root,"Diagnostics").setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);refreshApi();
    }
    private void refreshApi(){String key=AppPreferences.apiKey(this);boolean active=key!=null&&!key.startsWith("revoked-");apiStatus.setText(active?"Active key: "+key.substring(0,Math.min(14,key.length()))+"…":"No active API key");}
    private void copy(String text){((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Mock Drive API key",text));}
    private TextView label(LinearLayout p,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(8),0,dp(5));p.addView(v);return v;}
    private EditText field(LinearLayout p,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);p.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Button button(LinearLayout p,String text){Button b=new Button(this);b.setText(text);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private Spinner spinner(LinearLayout p,String[] values,String selected){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));for(int i=0;i<values.length;i++)if(values[i].equals(selected))s.setSelection(i);p.addView(s);return s;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}