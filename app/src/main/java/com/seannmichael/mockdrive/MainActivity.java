package com.seannmichael.mockdrive;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Calendar;

public class MainActivity extends Activity {
    private EditText waypoints, speed, variation, interval, schedule, teleportLat, teleportLon, address, apiToken;
    private EditText businessDestination, googleApiKey, googleSearchQuery, targetBusinessName, targetPlaceId, targetAddressHint;
    private CheckBox randomStops, holdDestination;
    private Spinner recurrence, speedProfile;
    private TextView status, queue;
    private long scheduledEpoch;
    private JSONObject matchedGoogleBusiness;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestPermissionsIfNeeded();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); root.setPadding(p,p,p,p);
        addLabel(root, "Mock Drive 4", 28);
        addLabel(root, "Select this app under Developer options → Select mock location app.", 15);

        Button dev = button(root, "Open Developer Options");
        dev.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));

        addLabel(root, "Instant location spoof", 20);
        address = field(root, "Address or business name", "");
        button(root, "Find Location").setOnClickListener(v -> geocodeIntoTeleport());
        teleportLat = field(root, "Latitude", "41.1432");
        teleportLon = field(root, "Longitude", "-81.8552");
        button(root, "Set Location Now").setOnClickListener(v -> teleport());

        addLabel(root, "Google search result business destination", 20);
        addLabel(root, "The app searches Google Places for the keyword and identifies the intended business by Place ID or business identity. Result position does not matter.", 13);
        googleApiKey = field(root, "Google Places API key", GooglePlacesEngine.getApiKey(this));
        button(root, "Save Google API Key").setOnClickListener(v -> {
            GooglePlacesEngine.setApiKey(this, googleApiKey.getText().toString());
            toast("Google Places API key saved");
        });
        googleSearchQuery = field(root, "Search phrase, e.g. Medina junk removal", "");
        targetBusinessName = field(root, "Target business name, e.g. 1st Choice Junk Removal", "");
        targetPlaceId = field(root, "Target Google Place ID (preferred, optional)", "");
        targetAddressHint = field(root, "Target address/city hint", "Medina, Ohio");
        button(root, "Find Exact Business in Google Results").setOnClickListener(v -> findGoogleBusiness());

        addLabel(root, "Basic business destination fallback", 20);
        businessDestination = field(root, "Business name and city/state", "");
        button(root, "Resolve with OpenStreetMap").setOnClickListener(v -> resolveBusinessDestination());

        addLabel(root, "Multi-stop road trip", 20);
        addLabel(root, "One waypoint per line: latitude,longitude,stopSeconds. Include the start and intermediate stops. A matched Google business is automatically used as the final destination.", 14);
        waypoints = field(root, "Waypoints", "41.1432,-81.8552,0\n41.1200,-81.7200,60");
        waypoints.setMinLines(5);
        speed = field(root, "Average / maximum speed mph", "40");
        variation = field(root, "Speed variation percent", "8");
        interval = field(root, "GPS update interval ms", "1000");
        speedProfile = spinner(root, "Speed behavior", new String[]{"Road-aware estimated speeds", "Fixed selected speed", "Urban cautious", "Highway-biased"});
        randomStops = check(root, "Simulate random traffic stops", true);
        holdDestination = check(root, "Hold location at destination", true);

        button(root, "Choose First Schedule Date/Time").setOnClickListener(v -> chooseSchedule());
        schedule = field(root, "Scheduled time", "Start immediately");
        schedule.setFocusable(false);
        recurrence = spinner(root, "Repeat schedule", new String[]{"None", "Daily", "Weekly", "Monthly"});

        button(root, "Start Now").setOnClickListener(v -> startNow());
        button(root, "Save / Queue Trip").setOnClickListener(v -> saveTrip());
        button(root, "Pause").setOnClickListener(v -> command(MockLocationService.ACTION_PAUSE));
        button(root, "Resume").setOnClickListener(v -> command(MockLocationService.ACTION_RESUME));
        button(root, "Stop and Restore Real GPS").setOnClickListener(v -> command(MockLocationService.ACTION_STOP));

        addLabel(root, "Remote API", 20);
        apiToken = field(root, "API token", TripStore.token(this));
        button(root, "Save API Token").setOnClickListener(v -> { TripStore.setToken(this, apiToken.getText().toString().trim()); toast("Token saved"); });
        button(root, "Start API on Port 8765").setOnClickListener(v -> api(true));
        button(root, "Stop API").setOnClickListener(v -> api(false));
        addLabel(root, "Use Tailscale for access outside the local network. Do not expose port 8765 directly to the public internet.", 13);

        addLabel(root, "Trip queue", 20);
        button(root, "Refresh Queue").setOnClickListener(v -> refreshQueue());
        queue = new TextView(this); queue.setTextIsSelectable(true); root.addView(queue);
        status = new TextView(this); status.setPadding(0,dp(16),0,dp(20)); root.addView(status);
        refreshQueue();

        ScrollView s = new ScrollView(this); s.addView(root); setContentView(s);
    }

    private JSONObject buildTrip() throws Exception {
        JSONArray w = new JSONArray();
        String[] lines = waypoints.getText().toString().trim().split("\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] x = line.trim().split(",");
            JSONObject p = new JSONObject();
            p.put("latitude", Double.parseDouble(x[0].trim()));
            p.put("longitude", Double.parseDouble(x[1].trim()));
            p.put("stopSeconds", x.length > 2 ? Integer.parseInt(x[2].trim()) : 0);
            w.put(p);
        }
        if (matchedGoogleBusiness != null) {
            JSONObject destination = new JSONObject();
            destination.put("latitude", matchedGoogleBusiness.getDouble("latitude"));
            destination.put("longitude", matchedGoogleBusiness.getDouble("longitude"));
            destination.put("stopSeconds", 0);
            destination.put("googlePlaceId", matchedGoogleBusiness.optString("placeId"));
            destination.put("businessName", matchedGoogleBusiness.optString("businessName"));
            w.put(destination);
        }
        if (w.length() < 2) throw new Exception("Enter a start and select a destination");
        JSONObject t = new JSONObject();
        t.put("name", "Trip " + DateFormat.getDateTimeInstance().format(System.currentTimeMillis()));
        t.put("waypoints", w);
        t.put("averageSpeedMph", Double.parseDouble(speed.getText().toString()));
        t.put("speedVariationPercent", Double.parseDouble(variation.getText().toString()));
        t.put("gpsUpdateIntervalMs", Integer.parseInt(interval.getText().toString()));
        t.put("speedProfile", profileValue());
        t.put("randomStops", randomStops.isChecked());
        t.put("randomStopChancePercent", 2);
        t.put("randomStopMaxSeconds", 20);
        t.put("holdAtDestination", holdDestination.isChecked());
        t.put("startAtEpochMs", scheduledEpoch);
        t.put("recurrence", recurrenceValue());
        t.put("destinationBusiness", businessDestination.getText().toString().trim());
        if (matchedGoogleBusiness != null) t.put("googleBusiness", matchedGoogleBusiness);
        return t;
    }

    private void findGoogleBusiness() {
        GooglePlacesEngine.setApiKey(this, googleApiKey.getText().toString());
        status.setText("Searching Google results and matching the target business…");
        new Thread(() -> {
            try {
                JSONObject match = GooglePlacesEngine.findTarget(this,
                        googleSearchQuery.getText().toString(), targetBusinessName.getText().toString(),
                        targetPlaceId.getText().toString(), targetAddressHint.getText().toString());
                matchedGoogleBusiness = match;
                runOnUiThread(() -> status.setText("Matched Google business: " + match.optString("businessName") +
                        "\n" + match.optString("formattedAddress") + "\nPlace ID: " + match.optString("placeId") +
                        "\nThis verified listing will be the final destination."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Google business match failed: " + e.getMessage()));
            }
        }, "google-business-match").start();
    }

    private String recurrenceValue() { int i = recurrence.getSelectedItemPosition(); return i == 1 ? "daily" : i == 2 ? "weekly" : i == 3 ? "monthly" : "none"; }
    private String profileValue() { int i = speedProfile.getSelectedItemPosition(); return i == 0 ? "road_aware" : i == 2 ? "urban" : i == 3 ? "highway" : "fixed"; }

    private void startNow() { try { JSONObject t = TripStore.save(this, buildTrip()); TripScheduler.launch(this, t); status.setText("Trip started along actual roads."); refreshQueue(); } catch (Exception e) { toast(e.getMessage()); } }
    private void saveTrip() { try { JSONObject t = buildTrip(); if (scheduledEpoch > 0) TripScheduler.schedule(this, t); else TripStore.save(this, t); status.setText(scheduledEpoch > 0 ? "Trip scheduled with recurrence: " + recurrenceValue() : "Trip queued."); refreshQueue(); } catch (Exception e) { toast(e.getMessage()); } }

    private void teleport() {
        try {
            Intent i = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT)
                    .putExtra(MockLocationService.EXTRA_LAT, Double.parseDouble(teleportLat.getText().toString()))
                    .putExtra(MockLocationService.EXTRA_LON, Double.parseDouble(teleportLon.getText().toString()));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            status.setText("Holding selected mock location.");
        } catch (Exception e) { toast("Enter valid coordinates"); }
    }

    private void geocodeIntoTeleport() { geocode(address.getText().toString().trim(), false); }
    private void resolveBusinessDestination() { geocode(businessDestination.getText().toString().trim(), true); }
    private void geocode(String q, boolean appendDestination) {
        if (q.isEmpty()) return;
        status.setText("Finding location…");
        new Thread(() -> {
            try {
                JSONObject p = RouteEngine.geocode(q);
                runOnUiThread(() -> {
                    if (appendDestination) {
                        String existing = waypoints.getText().toString().trim();
                        String line = p.optDouble("latitude") + "," + p.optDouble("longitude") + ",0";
                        waypoints.setText(existing.isEmpty() ? line : existing + "\n" + line);
                        status.setText("Fallback destination added: " + p.optString("label"));
                    } else {
                        teleportLat.setText(String.valueOf(p.optDouble("latitude")));
                        teleportLon.setText(String.valueOf(p.optDouble("longitude")));
                        status.setText(p.optString("label"));
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> status.setText("Location lookup failed: " + e.getMessage())); }
        }).start();
    }

    private void chooseSchedule() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (d,y,m,day) -> new TimePickerDialog(this, (t,h,min) -> {
            Calendar x = Calendar.getInstance(); x.set(y,m,day,h,min,0); scheduledEpoch = x.getTimeInMillis();
            schedule.setText(DateFormat.getDateTimeInstance().format(scheduledEpoch));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show(), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void command(String action) { startService(new Intent(this, MockLocationService.class).setAction(action)); }
    private void api(boolean start) { Intent i = new Intent(this, ApiService.class).setAction(start ? ApiService.ACTION_START : ApiService.ACTION_STOP); if (start && Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); status.setText(start ? "API running on port 8765" : "API stopped"); }
    private void refreshQueue() { try { queue.setText(TripStore.all(this).toString(2)); } catch (Exception e) { queue.setText(TripStore.all(this).toString()); } }
    private Spinner spinner(LinearLayout p, String label, String[] values) { addLabel(p, label, 14); Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); p.addView(s); return s; }
    private EditText field(LinearLayout p, String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); p.addView(e, new LinearLayout.LayoutParams(-1,-2)); return e; }
    private Button button(LinearLayout p, String text) { Button b = new Button(this); b.setText(text); p.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b; }
    private CheckBox check(LinearLayout p, String text, boolean value) { CheckBox c = new CheckBox(this); c.setText(text); c.setChecked(value); p.addView(c); return c; }
    private void addLabel(LinearLayout p, String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setPadding(0,dp(8),0,dp(5)); p.addView(v); }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;
        java.util.ArrayList<String> p = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]),100);
    }
}
