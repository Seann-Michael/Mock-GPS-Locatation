package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class DeviceSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Phone setup",true);
        LinearLayout setup=UiKit.card(this,root);setup.addView(UiKit.text(this,"Required setup",20,true));setup.addView(UiKit.text(this,"Mock Drive must be selected as the phone's mock location app.",13,false));
        Button developer=UiKit.button(this,"Open Developer Options");setup.addView(developer);developer.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        Button battery=UiKit.secondaryButton(this,"Open battery optimization");setup.addView(battery);battery.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));

        LinearLayout help=UiKit.card(this,root);help.addView(UiKit.text(this,"Troubleshooting",20,true));help.addView(UiKit.text(this,"Use Diagnostics to check permissions, GPS status and saved trip information.",13,false));
        Button diagnostics=UiKit.secondaryButton(this,"Open diagnostics");help.addView(diagnostics);diagnostics.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));
        ScrollView s=new ScrollView(this);s.addView(root);setContentView(s);
    }
}
