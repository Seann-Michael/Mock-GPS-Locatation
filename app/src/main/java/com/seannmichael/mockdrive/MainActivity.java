package com.seannmichael.mockdrive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText startLat, startLon, endLat, endLon, speed;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNeededPermissions();

        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Mock Drive");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("Enter Point A and Point B coordinates. The app will request an actual driving route and simulate movement along the roads.");
        help.setTextSize(16);
        help.setPadding(0, dp(8), 0, dp(16));
        root.addView(help);

        startLat = field(root, "Point A latitude", "41.1432");
        startLon = field(root, "Point A longitude", "-81.8552");
        endLat = field(root, "Point B latitude", "41.0814");
        endLon = field(root, "Point B longitude", "-81.5190");
        speed = field(root, "Speed (mph)", "45");

        Button dev = button("Open Developer Options");
        dev.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        root.addView(dev);

        Button start = button("Build Route and Start");
        start.setOnClickListener(v -> buildAndStart());
        root.addView(start);

        Button stop = button("Stop Simulation");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, MockLocationService.class);
            i.setAction(MockLocationService.ACTION_STOP);
            startService(i);
            status.setText("Stopped");
        });
        root.addView(stop);

        status = new TextView(this);
        status.setText("Select Mock Drive as the mock location app in Developer Options before starting.");
        status.setTextSize(15);
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildAndStart() {
        final double aLat, aLon, bLat, bLon, mph;
        try {
            aLat = Double.parseDouble(startLat.getText().toString().trim());
            aLon = Double.parseDouble(startLon.getText().toString().trim());
            bLat = Double.parseDouble(endLat.getText().toString().trim());
            bLon = Double.parseDouble(endLon.getText().toString().trim());
            mph = Double.parseDouble(speed.getText().toString().trim());
            if (mph <= 0 || mph > 150) throw new IllegalArgumentException();
        } catch (Exception e) {
            Toast.makeText(this, "Enter valid coordinates and a speed from 1 to 150 mph.", Toast.LENGTH_LONG).show();
            return;
        }

        status.setText("Requesting road route…");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String endpoint = String.format(Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                        aLon, aLat, bLon, bLat);
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "MockDrive/1.0");
                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("Routing server returned " + code);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();

                JSONObject root = new JSONObject(body.toString());
                JSONArray routes = root.getJSONArray("routes");
                if (routes.length() == 0) throw new Exception("No driving route found");
                JSONArray coords = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                if (coords.length() < 2) throw new Exception("Route was empty");

                Intent service = new Intent(this, MockLocationService.class);
                service.setAction(MockLocationService.ACTION_START);
                service.putExtra(MockLocationService.EXTRA_COORDS, coords.toString());
                service.putExtra(MockLocationService.EXTRA_SPEED_MPH, mph);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);

                runOnUiThread(() -> status.setText("Driving route loaded: " + coords.length() + " road points. Simulation started."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Could not build route: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> p = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 100);
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
