package com.seannmichael.mockdrive;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LicenseActivity extends BaseActivity {
    private EditText licenseKey;
    private TextView statusValue;
    private TextView statusDetail;
    private Button removeButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Licensing", true);

        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, "Device license", 27, true));
        hero.addView(UiKit.whiteText(this,
                "Store the license assigned to this phone. Verification and billing will be connected later.",
                15, false));

        LinearLayout statusCard = UiKit.card(this, root);
        statusCard.addView(UiKit.text(this, "License status", 20, true));
        statusValue = UiKit.text(this, "", 24, true);
        statusValue.setTextColor(UiKit.BLUE_DARK);
        statusCard.addView(statusValue);
        statusDetail = UiKit.text(this, "", 14, false);
        statusCard.addView(statusDetail);

        LinearLayout keyCard = UiKit.card(this, root);
        keyCard.addView(UiKit.text(this, "License key", 20, true));
        keyCard.addView(UiKit.text(this,
                "The key is stored only on this Android device. It is not currently sent to a licensing server.",
                13, false));

        licenseKey = UiKit.field(this, "Enter license key", AppPreferences.licenseKey(this));
        licenseKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        licenseKey.setSingleLine(true);
        keyCard.addView(licenseKey);

        Button saveButton = UiKit.button(this, "Save license key");
        keyCard.addView(saveButton);
        saveButton.setOnClickListener(v -> saveLicense());

        removeButton = UiKit.secondaryButton(this, "Remove license key");
        keyCard.addView(removeButton);
        removeButton.setOnClickListener(v -> removeLicense());

        LinearLayout futureCard = UiKit.card(this, root);
        futureCard.addView(UiKit.text(this, "Planned licensing features", 20, true));
        futureCard.addView(UiKit.text(this,
                "Activation and verification\n" +
                "Expiration and renewal status\n" +
                "Device limits and transfers\n" +
                "Revocation support\n" +
                "Offline grace period",
                14, false));

        refreshStatus();
        UiKit.setStickyScreen(this, root, "Settings");
    }

    private void saveLicense() {
        String value = licenseKey.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Enter a license key first", Toast.LENGTH_LONG).show();
            return;
        }
        AppPreferences.saveLicenseKey(this, value);
        licenseKey.setText(value);
        refreshStatus();
        Toast.makeText(this, "License key saved locally as unverified", Toast.LENGTH_LONG).show();
    }

    private void removeLicense() {
        AppPreferences.clearLicenseKey(this);
        licenseKey.setText("");
        refreshStatus();
        Toast.makeText(this, "License key removed", Toast.LENGTH_LONG).show();
    }

    private void refreshStatus() {
        boolean hasKey = !AppPreferences.licenseKey(this).trim().isEmpty();
        statusValue.setText(hasKey ? "Key stored locally" : "No license key");
        statusDetail.setText(hasKey
                ? "This key has not been verified. Server-based license validation is a future feature."
                : "Add a license key when one is assigned to this device.");
        removeButton.setEnabled(hasKey);
        removeButton.setAlpha(hasKey ? 1f : 0.5f);
    }
}
