package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LicenseActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int p=dp(16);root.setPadding(p,p,p,p);
        text(root,"License",28);
        text(root,"License validation will be connected to the future licensing server. This page currently stores the entered key on this device but does not treat it as verified.",14);
        EditText key=new EditText(this);key.setHint("License key");key.setText(AppPreferences.licenseKey(this));root.addView(key,new LinearLayout.LayoutParams(-1,-2));
        Button save=button(root,"Save License Key");save.setOnClickListener(v->{AppPreferences.saveLicenseKey(this,key.getText().toString().trim());Toast.makeText(this,"License key saved as unverified",Toast.LENGTH_LONG).show();});
        Button clear=button(root,"Remove License Key");clear.setOnClickListener(v->{AppPreferences.clearLicenseKey(this);key.setText("");Toast.makeText(this,"License key removed",Toast.LENGTH_LONG).show();});
        text(root,"Status: Not connected to licensing server",16);
        text(root,"Planned: activation, expiration, device limits, transfer, revocation, and offline grace period.",14);
        setContentView(root);
    }
    private TextView text(LinearLayout p,String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(8),0,dp(5));p.addView(v);return v;}
    private Button button(LinearLayout p,String s){Button b=new Button(this);b.setText(s);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}