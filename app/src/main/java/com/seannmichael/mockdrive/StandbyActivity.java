package com.seannmichael.mockdrive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

public class StandbyActivity extends Activity {
    private TextView deviceStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestPermissionsIfNeeded();
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Mock Drive", false);

        LinearLayout hero = UiKit.card(this, root);
        hero.addView(UiKit.text(this, "Drive from A to B", 25, true));
        hero.addView(UiKit.text(this, "Set a mock starting location, choose a destination, open Google Maps, and simulate the drive along real roads.", 15, false));
        deviceStatus = UiKit.text(this, "Checking setup…", 15, true);
        hero.addView(deviceStatus);

        LinearLayout primary = UiKit.card(this, root);
        primary.addView(UiKit.text(this, "Start here", 20, true));
        Button drive = UiKit.button(this, "Set A and B and start navigation");
        primary.addView(drive);
        drive.setOnClickListener(v -> startActivity(new Intent(this, SimpleDriveActivity.class)));

        Button mock = UiKit.secondaryButton(this, "Set a stationary mock location");
        primary.addView(mock);
        mock.setOnClickListener(v -> startActivity(new Intent(this, QuickMockActivity.class)));

        LinearLayout controls = UiKit.card(this, root);
        controls.addView(UiKit.text(this, "Controls", 19, true));
        Button stop = UiKit.secondaryButton(this, "Stop simulation and restore real GPS");
        controls.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));
        Button settings = UiKit.secondaryButton(this, "Settings and diagnostics");
        controls.addView(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 100);
    }

    private void refresh() {
        boolean locationGranted = Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        deviceStatus.setText("Location permission: " + (locationGranted ? "Granted" : "Required") +
                "\nMock provider: select Mock Drive in Developer Options");
    }
}
