package com.seannmichael.mockdrive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;

public class StandbyActivity extends Activity {
    private LinearLayout setupCard;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestPermissionsIfNeeded();
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Mock Drive", false);

        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, "Drive from A to B", 28, true));
        hero.addView(UiKit.whiteText(this, "Set a mock starting point, launch Google Maps, and simulate movement along real roads.", 15, false));

        // Setup readiness — at-a-glance status with an action shortcut.
        setupCard = UiKit.card(this, root);
        refresh();

        LinearLayout primary = UiKit.card(this, root);
        primary.addView(UiKit.text(this, "Start a drive", 21, true));
        primary.addView(UiKit.text(this, "Enter location A and destination B", 14, false));
        Button drive = UiKit.button(this, "Open A to B navigation");
        primary.addView(drive);
        drive.setOnClickListener(v -> startActivity(new Intent(this, SimpleDriveActivity.class)));

        LinearLayout quick = UiKit.card(this, root);
        quick.addView(UiKit.text(this, "Quick mock", 20, true));
        quick.addView(UiKit.text(this, "Place the phone at one fixed location without starting a drive", 14, false));
        Button mock = UiKit.secondaryButton(this, "Set stationary location");
        quick.addView(mock);
        mock.setOnClickListener(v -> startActivity(new Intent(this, QuickMockActivity.class)));

        LinearLayout controls = UiKit.card(this, root);
        controls.addView(UiKit.text(this, "GPS controls", 20, true));
        Button stop = UiKit.secondaryButton(this, "Stop simulation and restore real GPS");
        controls.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));

        UiKit.setStickyScreen(this, root, "Home");
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 100);
    }

    private void refresh() {
        if (setupCard == null) return;
        setupCard.removeAllViews();
        setupCard.addView(UiKit.text(this, "Device setup", 20, true));
        boolean locationGranted = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        UiKit.statusRow(this, setupCard, "Location permission", locationGranted ? "Granted" : "Required",
                locationGranted ? UiKit.OK : UiKit.BAD, locationGranted ? UiKit.OK_BG : UiKit.BAD_BG);
        UiKit.statusRow(this, setupCard, "Mock location app", "Select in Dev Options", UiKit.WARN, UiKit.WARN_BG);
        Button dev = UiKit.secondaryButton(this, "Open Developer Options");
        setupCard.addView(dev);
        dev.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
    }
}
