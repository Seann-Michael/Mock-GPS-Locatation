package com.seannmichael.mockdrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailActivity extends BaseActivity {
    private static final int SAVE_DIAGNOSTICS = 4401;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private JSONObject record;
    private String historyId = "";
    private File pendingZip;
    private TextView statusText;
    private TextView summaryText;
    private TextView liveDiagnostics;
    private TextView storedDiagnostics;
    private Button stopButton;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshLiveData();
            refreshHandler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        historyId = getIntent().getStringExtra("history_id");
        if (historyId == null) historyId = "";
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshTask);
        refreshHandler.post(refreshTask);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private void render() {
        record = HistoryStore.get(this, historyId);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Simulation details", true);
        if (record == null) {
            LinearLayout card = UiKit.card(this, root);
            card.addView(UiKit.text(this, "History record not found", 20, true));
            UiKit.setStickyScreen(this, root, "History");
            return;
        }

        LinearLayout hero = UiKit.hero(this, root);
        statusText = UiKit.whiteText(this, record.optString("status", "unknown").toUpperCase(Locale.US), 25, true);
        hero.addView(statusText);
        hero.addView(UiKit.whiteText(this,
                record.optString("startAddress") + " → " + record.optString("endAddress"), 14, false));

        LinearLayout summary = UiKit.card(this, root);
        summary.addView(UiKit.text(this, "Trip summary", 20, true));
        summaryText = UiKit.text(this, tripSummary(), 15, false);
        summary.addView(summaryText);

        JSONObject trip = record.optJSONObject("trip");
        LinearLayout setup = UiKit.card(this, root);
        setup.addView(UiKit.text(this, "Simulation setup", 20, true));
        setup.addView(UiKit.text(this,
                "Start address: " + record.optString("startAddress") +
                        "\nEnding address: " + record.optString("endAddress") +
                        "\nConfigured speed: " + Math.round(trip == null ? 0 : trip.optDouble("averageSpeedMph")) + " mph" +
                        "\nUpdate interval: " + (trip == null ? 0 : trip.optInt("gpsUpdateIntervalMs", 1000)) + " ms" +
                        "\nSpeed variation: " + Math.round((trip == null ? 0 : trip.optDouble("speedVariationPercent", 0))) + "%" +
                        "\nTrip ID: " + record.optString("tripId") +
                        "\nHistory ID: " + record.optString("historyId"), 15, false));

        LinearLayout live = UiKit.card(this, root);
        live.addView(UiKit.text(this, "Live navigation diagnostics", 20, true));
        live.addView(UiKit.text(this,
                "This refreshes every second while the simulation runs. It separately verifies Android GPS and Google Play services Fused Location, which Google Maps commonly uses.",
                13, false));
        liveDiagnostics = UiKit.text(this, "Waiting for the first runtime update…", 13, false);
        live.addView(liveDiagnostics);

        LinearLayout diag = UiKit.card(this, root);
        diag.addView(UiKit.text(this, "Diagnostic event summary", 20, true));
        diag.addView(UiKit.text(this,
                "The complete ZIP includes route_segments.json with the heading and length of every segment, plus every GPS and Fused injection, readback, speed, bearing, timing measurement, warning, and exception.",
                13, false));
        storedDiagnostics = UiKit.text(this,
                record.optString("diagnostics", "No diagnostic details were recorded."), 12, false);
        diag.addView(storedDiagnostics);

        LinearLayout actions = UiKit.card(this, root);
        actions.addView(UiKit.text(this, "Actions", 20, true));

        stopButton = UiKit.button(this, "Stop active navigation");
        actions.addView(stopButton);
        stopButton.setOnClickListener(v -> stopNavigation());

        Button save = UiKit.secondaryButton(this, "Save complete diagnostics ZIP");
        actions.addView(save);
        save.setOnClickListener(v -> saveDiagnostics());

        Button email = UiKit.secondaryButton(this, "Email diagnostic summary");
        actions.addView(email);
        email.setOnClickListener(v -> shareEmail());

        Button clone = UiKit.secondaryButton(this, "Clone and run again");
        actions.addView(clone);
        clone.setOnClickListener(v -> cloneAndRun());

        updateStopButton();
        UiKit.setStickyScreen(this, root, "History");
    }

    private void refreshLiveData() {
        JSONObject latestRecord = HistoryStore.get(this, historyId);
        if (latestRecord != null) record = latestRecord;
        if (record == null || statusText == null) return;

        statusText.setText(record.optString("status", "unknown").toUpperCase(Locale.US));
        summaryText.setText(tripSummary());
        updateStopButton();

        String stateText = SimulationDiagnostics.readLiveState(this, historyId);
        if (stateText == null || stateText.trim().isEmpty()) {
            liveDiagnostics.setText("No runtime update has been written yet. The GPS service may still be provisioning the route.");
        } else {
            try {
                JSONObject state = new JSONObject(stateText);
                liveDiagnostics.setText(readableState(state) + "\n\nRaw latest state:\n" + state.toString(2));
            } catch (Exception e) {
                liveDiagnostics.setText(stateText);
            }
        }

        String currentSummary = SimulationDiagnostics.summary(this, historyId);
        if (currentSummary != null && !currentSummary.trim().isEmpty()) storedDiagnostics.setText(currentSummary);
    }

    private String readableState(JSONObject state) {
        JSONObject gps = state.optJSONObject("gpsReported");
        JSONObject fused = state.optJSONObject("fusedReported");
        double progress = state.optDouble("routeProgressPercent", -1);
        double speed = state.optDouble("requestedSpeedMph", -1);
        double bearing = state.optDouble("requestedBearingDegrees", -1);
        double remainingMeters = state.optDouble("distanceRemainingMeters", -1);
        StringBuilder out = new StringBuilder();
        out.append("Phase: ").append(state.optString("phase", "unknown"));
        out.append("\nUpdate: ").append(state.optLong("count", 0));
        out.append("\nRoute segment: ").append(state.optInt("segment", 0)).append(" / ").append(state.optInt("totalSegments", 0));
        if (progress >= 0) out.append("\nRoute progress: ").append(String.format(Locale.US, "%.2f%%", progress));
        if (remainingMeters >= 0) out.append("\nDistance remaining: ").append(String.format(Locale.US, "%.2f miles", remainingMeters / 1609.344));
        if (speed >= 0) out.append("\nRequested speed: ").append(String.format(Locale.US, "%.2f mph", speed));
        if (bearing >= 0) out.append("\nRequested heading: ").append(String.format(Locale.US, "%.2f°", bearing));
        out.append("\nGPS injection: ").append(state.optBoolean("gpsInjectionSucceeded", false) ? "Success" : "FAILED");
        out.append("\nFused injection: ").append(state.optBoolean("fusedInjectionSucceeded", false) ? "Success" : "FAILED");
        out.append("\nGPS provider enabled: ").append(state.optBoolean("gpsProviderEnabled", false));
        out.append("\nWorker alive: ").append(state.optBoolean("workerAlive", false));
        out.append("\nInjection work time: ").append(state.optLong("injectionWorkDurationMs", -1)).append(" ms");
        if (gps != null) {
            out.append("\nGPS reported: ").append(coordinate(gps));
            out.append("\nGPS readback difference: ").append(numberOrUnavailable(state, "gpsReportedDistanceFromInjectedMeters", " meters"));
        }
        if (fused != null) {
            out.append("\nFused reported: ").append(coordinate(fused));
            out.append("\nFused readback difference: ").append(numberOrUnavailable(state, "fusedReportedDistanceFromInjectedMeters", " meters"));
        }
        String fusedError = state.optString("fusedInjectionError", "");
        if (!fusedError.isEmpty()) out.append("\nFused error: ").append(fusedError);
        String gpsError = state.optString("gpsInjectionError", "");
        if (!gpsError.isEmpty()) out.append("\nGPS error: ").append(gpsError);
        return out.toString();
    }

    private String coordinate(JSONObject location) {
        return String.format(Locale.US, "%.7f, %.7f at %.2f mph / %.2f°",
                location.optDouble("latitude"),
                location.optDouble("longitude"),
                location.optDouble("speedMph", -1),
                location.optDouble("bearingDegrees", -1));
    }

    private String numberOrUnavailable(JSONObject object, String key, String suffix) {
        if (!object.has(key) || object.isNull(key)) return "Not available";
        return String.format(Locale.US, "%.3f%s", object.optDouble(key), suffix);
    }

    private void updateStopButton() {
        if (stopButton == null || record == null) return;
        String status = record.optString("status", "");
        boolean active = "running".equals(status) || "routing".equals(status) || "active".equals(status) || "starting".equals(status);
        stopButton.setEnabled(active);
        stopButton.setText(active ? "Stop active navigation" : "Navigation is not running");
    }

    private void stopNavigation() {
        if (record == null) return;
        stopButton.setEnabled(false);
        stopButton.setText("Stopping navigation…");
        Intent stop = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP);
        startService(stop);
        Toast.makeText(this, "Stopping mock navigation and restoring normal location", Toast.LENGTH_LONG).show();
        refreshHandler.postDelayed(this::refreshLiveData, 750);
    }

    private String tripSummary() {
        if (record == null) return "No record";
        return "Start: " + format(record.optLong("startTime")) +
                "\nEnd: " + format(record.optLong("endTime")) +
                "\nDuration: " + duration(record.optLong("durationMs")) +
                "\nDistance: " + String.format(Locale.US, "%.2f miles", record.optDouble("miles")) +
                "\nStatus: " + record.optString("status", "unknown");
    }

    private void saveDiagnostics() {
        try {
            pendingZip = SimulationDiagnostics.exportZip(this, record.optString("historyId"));
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType("application/zip");
            save.putExtra(Intent.EXTRA_TITLE, pendingZip.getName());
            startActivityForResult(save, SAVE_DIAGNOSTICS);
        } catch (Exception e) {
            Toast.makeText(this, "Could not prepare diagnostics: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_DIAGNOSTICS || resultCode != RESULT_OK || data == null || data.getData() == null || pendingZip == null) return;
        try (FileInputStream input = new FileInputStream(pendingZip);
             OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new Exception("Unable to open selected file");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
            Toast.makeText(this, "Diagnostics ZIP saved", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save diagnostics: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void cloneAndRun() {
        try {
            JSONObject cloned = HistoryStore.cloneTrip(this, record.optString("historyId"));
            JSONArray waypoints = cloned.getJSONArray("waypoints");
            JSONObject start = waypoints.getJSONObject(0);
            JSONObject end = waypoints.getJSONObject(waypoints.length() - 1);
            double startLat = start.getDouble("latitude");
            double startLon = start.getDouble("longitude");
            double endLat = end.getDouble("latitude");
            double endLon = end.getDouble("longitude");
            Intent hold = new Intent(this, MockLocationService.class)
                    .setAction(MockLocationService.ACTION_TELEPORT)
                    .putExtra(MockLocationService.EXTRA_LAT, startLat)
                    .putExtra(MockLocationService.EXTRA_LON, startLon);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(hold); else startService(hold);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    String url = "https://www.google.com/maps/dir/?api=1&origin=" + startLat + "," + startLon +
                            "&destination=" + Uri.encode(endLat + "," + endLon) +
                            "&travelmode=driving&dir_action=navigate";
                    Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    maps.setPackage("com.google.android.apps.maps");
                    try { startActivity(maps); }
                    catch (Exception e) { maps.setPackage(null); startActivity(maps); }
                    TripScheduler.launch(this, cloned);
                    Toast.makeText(this, "Cloned simulation started", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not reopen route: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 1500);
        } catch (Exception e) {
            Toast.makeText(this, "Could not clone: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareEmail() {
        Intent email = new Intent(Intent.ACTION_SEND);
        email.setType("message/rfc822");
        email.putExtra(Intent.EXTRA_SUBJECT, "Mock Drive diagnostics - " + record.optString("status"));
        email.putExtra(Intent.EXTRA_TEXT, emailBody());
        try { startActivity(Intent.createChooser(email, "Email simulation diagnostics")); }
        catch (Exception e) { Toast.makeText(this, "No email app is available", Toast.LENGTH_LONG).show(); }
    }

    private String emailBody() {
        JSONObject trip = record.optJSONObject("trip");
        return "Mock Drive simulation diagnostics\n\nStatus: " + record.optString("status") +
                "\nStart: " + format(record.optLong("startTime")) +
                "\nEnd: " + format(record.optLong("endTime")) +
                "\nDuration: " + duration(record.optLong("durationMs")) +
                "\nMiles: " + String.format(Locale.US, "%.2f", record.optDouble("miles")) +
                "\nStart address: " + record.optString("startAddress") +
                "\nEnding address: " + record.optString("endAddress") +
                "\nSpeed: " + Math.round(trip == null ? 0 : trip.optDouble("averageSpeedMph")) + " mph" +
                "\nTrip ID: " + record.optString("tripId") +
                "\nHistory ID: " + record.optString("historyId") +
                "\n\nDiagnostics summary:\n" + SimulationDiagnostics.summary(this, historyId) +
                "\n\nFor complete raw evidence, export the diagnostics ZIP from the simulation history page.";
    }

    private String format(long time) {
        return time <= 0 ? "Not recorded" : DateFormat.getDateTimeInstance().format(new Date(time));
    }

    private String duration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + " hr " + minutes + " min" : minutes + " min " + (seconds % 60) + " sec";
    }
}
