package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DiagnosticsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int p=dp(16);root.setPadding(p,p,p,p);
        text(root,"Diagnostics",28);
        text(root,"Package: "+getPackageName(),14);
        text(root,"Android version: "+android.os.Build.VERSION.RELEASE+" (API "+android.os.Build.VERSION.SDK_INT+")",14);
        String mock="Unknown";
        try { mock=Settings.Secure.getString(getContentResolver(),"mock_location"); } catch(Exception ignored) {}
        text(root,"Developer mock-location setting: "+mock,14);
        LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        text(root,"GPS provider enabled: "+lm.isProviderEnabled(LocationManager.GPS_PROVIDER),14);
        String api=AppPreferences.apiKey(this);
        text(root,"API key: "+(api!=null&&!api.startsWith("revoked-")?"Active":"Revoked / missing"),14);
        text(root,"Google Places key: "+(!GooglePlacesEngine.getApiKey(this).isEmpty()?"Configured":"Missing"),14);
        text(root,"License key: "+(!AppPreferences.licenseKey(this).isEmpty()?"Stored but unverified":"Missing"),14);
        text(root,"Queued trips: "+TripStore.all(this).length(),14);
        text(root,"Remote API port: 8765\nUse a private VPN such as Tailscale for remote access.",14);
        setContentView(root);
    }
    private TextView text(LinearLayout p,String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(8),0,dp(5));p.addView(v);return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}