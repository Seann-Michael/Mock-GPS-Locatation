package com.seannmichael.mockdrive;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

public class DiagnosticsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Diagnostics", true);

        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, "Device diagnostics", 24, true));
        hero.addView(UiKit.whiteText(this, "A quick health check of permissions, GPS and saved data.", 14, false));

        // Location / mock setup
        LinearLayout gps = UiKit.card(this, root);
        gps.addView(UiKit.text(this, "Location & mock setup", 19, true));
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsOn = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        UiKit.statusRow(this, gps, "GPS provider", gpsOn ? "Enabled" : "Disabled",
                gpsOn ? UiKit.OK : UiKit.BAD, gpsOn ? UiKit.OK_BG : UiKit.BAD_BG);
        String mock = "Unknown";
        try { mock = Settings.Secure.getString(getContentResolver(), "mock_location"); } catch (Exception ignored) {}
        boolean mockOn = "1".equals(mock);
        UiKit.statusRow(this, gps, "Developer mock-location", mockOn ? "On" : (mock == null ? "Unknown" : "Off"),
                mockOn ? UiKit.OK : UiKit.WARN, mockOn ? UiKit.OK_BG : UiKit.WARN_BG);

        // Keys & credentials
        LinearLayout keys = UiKit.card(this, root);
        keys.addView(UiKit.text(this, "Keys & credentials", 19, true));
        String api = AppPreferences.apiKey(this);
        boolean apiActive = api != null && !api.startsWith("revoked-");
        UiKit.statusRow(this, keys, "Remote API key", apiActive ? "Active" : "Revoked",
                apiActive ? UiKit.OK : UiKit.WARN, apiActive ? UiKit.OK_BG : UiKit.WARN_BG);
        boolean placesSet = !GooglePlacesEngine.getApiKey(this).isEmpty();
        UiKit.statusRow(this, keys, "Google Places key", placesSet ? "Configured" : "Missing",
                placesSet ? UiKit.OK : UiKit.WARN, placesSet ? UiKit.OK_BG : UiKit.WARN_BG);
        boolean licenseSet = !AppPreferences.licenseKey(this).isEmpty();
        UiKit.statusRow(this, keys, "License key", licenseSet ? "Stored" : "Missing",
                licenseSet ? UiKit.OK : UiKit.WARN, licenseSet ? UiKit.OK_BG : UiKit.WARN_BG);

        // Remote control API
        LinearLayout service = UiKit.card(this, root);
        service.addView(UiKit.text(this, "Remote control API", 19, true));
        boolean apiRunning = AppPreferences.apiEnabled(this);
        UiKit.statusRow(this, service, "Service", apiRunning ? "Running" : "Stopped",
                apiRunning ? UiKit.OK : UiKit.MUTED, apiRunning ? UiKit.OK_BG : UiKit.BLUE_LIGHT);
        UiKit.statusRow(this, service, "Restart after reboot", AppPreferences.apiAutoStart(this) ? "On" : "Off",
                UiKit.MUTED, UiKit.BLUE_LIGHT);
        service.addView(UiKit.text(this, "Listening on port " + ApiService.PORT + ". Use a private VPN such as Tailscale for remote access.", 13, false));

        // Environment
        LinearLayout env = UiKit.card(this, root);
        env.addView(UiKit.text(this, "Environment", 19, true));
        env.addView(UiKit.text(this, "Package: " + getPackageName(), 14, false));
        env.addView(UiKit.text(this, "Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")", 14, false));
        env.addView(UiKit.text(this, "Queued trips: " + TripStore.all(this).length(), 14, false));

        UiKit.setStickyScreen(this, root, "Settings");
    }
}
