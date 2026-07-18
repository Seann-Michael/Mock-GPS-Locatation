package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"Settings",true);
        LinearLayout hero=UiKit.hero(this,root);hero.addView(UiKit.whiteText(this,"Settings",27,true));hero.addView(UiKit.whiteText(this,"Choose a section. Each page only shows the controls related to that topic.",15,false));
        LinearLayout diagnostics=UiKit.card(this,root);diagnostics.addView(UiKit.text(this,"Navigation diagnostics",20,true));diagnostics.addView(UiKit.text(this,"View live GPS injection status and save a ZIP after a failed trip.",13,false));Button diagnosticsButton=UiKit.button(this,"Open navigation diagnostics");diagnostics.addView(diagnosticsButton);diagnosticsButton.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));
        LinearLayout general=UiKit.card(this,root);general.addView(UiKit.text(this,"App preferences",20,true));general.addView(UiKit.text(this,"Units and appearance",13,false));Button generalButton=UiKit.secondaryButton(this,"Open app preferences");general.addView(generalButton);generalButton.setOnClickListener(v->startActivity(new Intent(this,GeneralSettingsActivity.class)));
        LinearLayout access=UiKit.card(this,root);access.addView(UiKit.text(this,"API and access",20,true));access.addView(UiKit.text(this,"API key, Google key, license and billing",13,false));Button accessButton=UiKit.secondaryButton(this,"Open API and access");access.addView(accessButton);accessButton.setOnClickListener(v->startActivity(new Intent(this,AccessSettingsActivity.class)));
        LinearLayout device=UiKit.card(this,root);device.addView(UiKit.text(this,"Phone setup",20,true));device.addView(UiKit.text(this,"Developer Options, battery settings and diagnostics",13,false));Button deviceButton=UiKit.secondaryButton(this,"Open phone setup");device.addView(deviceButton);deviceButton.setOnClickListener(v->startActivity(new Intent(this,DeviceSettingsActivity.class)));
        UiKit.setStickyScreen(this,root,"Settings");
    }
}
