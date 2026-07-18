package com.seannmichael.mockdrive;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class LicenseActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "License", true);

        LinearLayout status = UiKit.card(this, root);
        status.addView(UiKit.text(this, "License status", 19, true));
        UiKit.statusRow(this, status, "Server connection", "Not connected", UiKit.WARN, UiKit.WARN_BG);
        boolean stored = !AppPreferences.licenseKey(this).isEmpty();
        UiKit.statusRow(this, status, "Stored key", stored ? "Saved (unverified)" : "None",
                stored ? UiKit.OK : UiKit.MUTED, stored ? UiKit.OK_BG : UiKit.BLUE_LIGHT);

        LinearLayout keyCard = UiKit.card(this, root);
        keyCard.addView(UiKit.text(this, "License key", 19, true));
        keyCard.addView(UiKit.text(this, "The key is stored on this device but is not treated as verified until a licensing server is connected.", 13, false));
        EditText key = UiKit.field(this, "License key", AppPreferences.licenseKey(this));
        keyCard.addView(key);
        Button save = UiKit.button(this, "Save license key");
        keyCard.addView(save);
        save.setOnClickListener(v -> { AppPreferences.saveLicenseKey(this, key.getText().toString().trim()); toast("License key saved as unverified"); recreate(); });
        Button clear = UiKit.secondaryButton(this, "Remove license key");
        keyCard.addView(clear);
        clear.setOnClickListener(v -> { AppPreferences.clearLicenseKey(this); key.setText(""); toast("License key removed"); recreate(); });

        LinearLayout planned = UiKit.card(this, root);
        planned.addView(UiKit.text(this, "Planned", 19, true));
        planned.addView(UiKit.text(this, "Activation, expiration, device limits, transfer, revocation, and an offline grace period.", 14, false));

        UiKit.setStickyScreen(this, root, "Settings");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
