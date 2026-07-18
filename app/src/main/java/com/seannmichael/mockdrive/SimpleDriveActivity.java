package com.seannmichael.mockdrive;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class SimpleDriveActivity extends Activity {
    private EditText startAddress, startLat, startLon, destinationAddress, destinationLat, destinationLon, speed;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Drive A to B", true);

        LinearLayout intro = UiKit.card(this, root);
        intro.addView(UiKit.text(this, "Simple mock navigation", 22, true));
        intro.addView(UiKit.text(this, "Set the phone at location A, open Google Maps to destination B, and move the GPS along actual roads.", 14, false));

        LinearLayout startCard = UiKit.card(this, root);
        startCard.addView(UiKit.text(this, "Starting location A", 19, true));
        startAddress = UiKit.field(this, "Starting address", ""); startCard.addView(startAddress);
        Button findStart = UiKit.secondaryButton(this, "Find starting address"); startCard.addView(findStart);
        startLat = UiKit.field(this, "Starting latitude", "41.181097"); startCard.addView(startLat);
        startLon = UiKit.field(this, "Starting longitude", "-81.974890"); startCard.addView(startLon);
        findStart.setOnClickListener(v -> geocode(startAddress.getText().toString(), startLat, startLon, "Start"));

        LinearLayout destinationCard = UiKit.card(this, root);
        destinationCard.addView(UiKit.text(this, "Destination B", 19, true));
        destinationAddress = UiKit.field(this, "Destination address", ""); destinationCard.addView(destinationAddress);
        Button findDestination = UiKit.secondaryButton(this, "Find destination address"); destinationCard.addView(findDestination);
        destinationLat = UiKit.field(this, "Destination latitude", ""); destinationCard.addView(destinationLat);
        destinationLon = UiKit.field(this, "Destination longitude", ""); destinationCard.addView(destinationLon);
        findDestination.setOnClickListener(v -> geocode(destinationAddress.getText().toString(), destinationLat, destinationLon, "Destination"));

        LinearLayout driveCard = UiKit.card(this, root);
        driveCard.addView(UiKit.text(this, "Drive settings", 19, true));
        speed = UiKit.field(this, "Average speed mph", "35"); driveCard.addView(speed);
        Button start = UiKit.button(this, "Start navigation"); driveCard.addView(start);
        Button stop = UiKit.secondaryButton(this, "Stop simulation and restore real GPS"); driveCard.addView(stop);
        status = UiKit.text(this, "Ready", 15, true); driveCard.addView(status);

        start.setOnClickListener(v -> startNavigation());
        stop.setOnClickListener(v -> {
            startService(new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP));
            status.setText("Simulation stopped. Real GPS restored.");
        });

        UiKit.bottomNav(this, root, "Home");
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }

    private void geocode(String query, EditText latField, EditText lonField, String label) {
        if (query == null || query.trim().isEmpty()) { toast("Enter an address"); return; }
        status.setText("Finding " + label.toLowerCase() + "…");
        new Thread(() -> {
            try {
                JSONObject p = RouteEngine.geocode(query.trim());
                runOnUiThread(() -> {
                    latField.setText(String.valueOf(p.optDouble("latitude")));
                    lonField.setText(String.valueOf(p.optDouble("longitude")));
                    status.setText(label + " found: " + p.optString("label"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(label + " lookup failed: " + e.getMessage()));
            }
        }, "simple-geocode").start();
    }

    private void startNavigation() {
        try {
            double aLat = Double.parseDouble(startLat.getText().toString().trim());
            double aLon = Double.parseDouble(startLon.getText().toString().trim());
            double bLat = Double.parseDouble(destinationLat.getText().toString().trim());
            double bLon = Double.parseDouble(destinationLon.getText().toString().trim());
            double mph = Double.parseDouble(speed.getText().toString().trim());

            Intent hold = new Intent(this, MockLocationService.class)
                    .setAction(MockLocationService.ACTION_TELEPORT)
                    .putExtra(MockLocationService.EXTRA_LAT, aLat)
                    .putExtra(MockLocationService.EXTRA_LON, aLon);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(hold); else startService(hold);
            status.setText("Mock GPS set to A. Preparing road route…");

            JSONArray waypoints = new JSONArray()
                    .put(new JSONObject().put("latitude", aLat).put("longitude", aLon).put("stopSeconds", 0))
                    .put(new JSONObject().put("latitude", bLat).put("longitude", bLon).put("stopSeconds", 0));
            JSONObject trip = new JSONObject()
                    .put("name", "Simple A to B drive")
                    .put("waypoints", waypoints)
                    .put("averageSpeedMph", mph)
                    .put("speedVariationPercent", 5)
                    .put("gpsUpdateIntervalMs", 1000)
                    .put("speedProfile", "fixed")
                    .put("randomStops", false)
                    .put("holdAtDestination", true)
                    .put("recurrence", "none");
            JSONObject saved = TripStore.save(this, trip);

            new Handler().postDelayed(() -> {
                try {
                    String destination = bLat + "," + bLon;
                    String url = "https://www.google.com/maps/dir/?api=1&origin=" + aLat + "," + aLon +
                            "&destination=" + Uri.encode(destination) + "&travelmode=driving&dir_action=navigate";
                    Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    maps.setPackage("com.google.android.apps.maps");
                    try { startActivity(maps); } catch (Exception e) { maps.setPackage(null); startActivity(maps); }
                    TripScheduler.launch(this, saved);
                    status.setText("Navigation started. Mock GPS is moving from A to B.");
                } catch (Exception e) {
                    status.setText("Could not start navigation: " + e.getMessage());
                }
            }, 1500);
        } catch (Exception e) {
            toast("Enter valid start, destination, and speed values");
        }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
