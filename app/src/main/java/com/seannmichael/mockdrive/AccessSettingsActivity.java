package com.seannmichael.mockdrive;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AccessSettingsActivity extends BaseActivity {
    private TextView apiStatus;
    private TextView serviceStatus;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"API and access",true);
        LinearLayout api=UiKit.card(this,root);api.addView(UiKit.text(this,"Remote API key",20,true));api.addView(UiKit.text(this,"This key allows another device to control Mock Drive. Keep it private.",13,false));apiStatus=UiKit.text(this,"",14,true);api.addView(apiStatus);
        Button generate=UiKit.button(this,"Generate new API key");api.addView(generate);generate.setOnClickListener(v->{String key=AppPreferences.generateApiKey(this);copy(key);refresh();toast("New key generated and copied");});
        Button copy=UiKit.secondaryButton(this,"Copy current API key");api.addView(copy);copy.setOnClickListener(v->{String key=AppPreferences.apiKey(this);if(key==null||key.startsWith("revoked-"))toast("No active key");else{copy(key);toast("API key copied");}});
        Button revoke=UiKit.secondaryButton(this,"Revoke API key");api.addView(revoke);revoke.setOnClickListener(v->{AppPreferences.revokeApiKey(this);refresh();toast("API key revoked");});

        LinearLayout service=UiKit.card(this,root);service.addView(UiKit.text(this,"Remote control service",20,true));service.addView(UiKit.text(this,"Runs a local HTTP server on port "+ApiService.PORT+" so another device on the same private network (for example over Tailscale) can control Mock Drive. Leave it off unless you need it, and never expose the port to the public internet.",13,false));serviceStatus=UiKit.text(this,"",14,true);service.addView(serviceStatus);
        Button startApi=UiKit.button(this,"Start remote API");service.addView(startApi);startApi.setOnClickListener(v->{Intent i=new Intent(this,ApiService.class).setAction(ApiService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);serviceStatus.postDelayed(this::refreshService,300);toast("Remote API starting on port "+ApiService.PORT);});
        Button stopApi=UiKit.secondaryButton(this,"Stop remote API");service.addView(stopApi);stopApi.setOnClickListener(v->{startService(new Intent(this,ApiService.class).setAction(ApiService.ACTION_STOP));serviceStatus.postDelayed(this::refreshService,300);toast("Remote API stopped");});
        CheckBox autoStart=new CheckBox(this);autoStart.setText("Restart automatically after reboot");autoStart.setChecked(AppPreferences.apiAutoStart(this));service.addView(autoStart);autoStart.setOnCheckedChangeListener((b,checked)->AppPreferences.setApiAutoStart(this,checked));

        LinearLayout google=UiKit.card(this,root);google.addView(UiKit.text(this,"Google Places key",20,true));google.addView(UiKit.text(this,"Used for address and business suggestions on the Drive page.",13,false));
        EditText places=UiKit.field(this,"Google Places API key",GooglePlacesEngine.getApiKey(this));places.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);google.addView(places);
        Button savePlaces=UiKit.secondaryButton(this,"Save Google key");google.addView(savePlaces);savePlaces.setOnClickListener(v->{GooglePlacesEngine.setApiKey(this,places.getText().toString().trim());toast("Google key saved");});

        LinearLayout account=UiKit.card(this,root);account.addView(UiKit.text(this,"Account",20,true));account.addView(UiKit.text(this,"License and billing are placeholders for later.",13,false));
        Button license=UiKit.secondaryButton(this,"License");account.addView(license);license.setOnClickListener(v->startActivity(new Intent(this,LicenseActivity.class)));
        Button billing=UiKit.secondaryButton(this,"Billing");account.addView(billing);billing.setOnClickListener(v->startActivity(new Intent(this,BillingActivity.class)));
        ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(root);setContentView(s);refresh();
    }
    @Override protected void onResume(){super.onResume();refreshService();}
    private void refresh(){String key=AppPreferences.apiKey(this);boolean active=key!=null&&!key.startsWith("revoked-");apiStatus.setText(active?"Status: Active":"Status: No active key");refreshService();}
    private void refreshService(){if(serviceStatus!=null)serviceStatus.setText(AppPreferences.apiEnabled(this)?"Status: Running on port "+ApiService.PORT:"Status: Stopped");}
    private void copy(String text){((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Mock Drive API key",text));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
}