package com.seannmichael.mockdrive;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

public class NavigationRunActivity extends BaseActivity {
    private JSONObject trip;
    private TextView phase, details, diagnostics;
    private Button primary, continueCtr, openHistory;
    private String historyId = "";
    private JSONArray route = new JSONArray();
    private double routeMiles;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("trip_id");
        trip = TripStore.get(this, id == null ? "" : id);
        render();
        if (trip != null) provision();
    }

    private void render() {
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Navigation campaign", true);
        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, trip == null ? "Campaign unavailable" : trip.optString("name", "Navigation campaign"), 25, true));
        hero.addView(UiKit.whiteText(this, trip == null ? "The campaign could not be loaded." : "Provisioning, execution, and troubleshooting details stay attached to this campaign.", 14, false));

        LinearLayout statusCard = UiKit.card(this, root);
        statusCard.addView(UiKit.text(this, "Current status", 20, true));
        phase = UiKit.text(this, trip == null ? "Not available" : "Provisioning…", 23, true);
        phase.setTextColor(UiKit.BLUE_DARK);
        statusCard.addView(phase);

        LinearLayout detailCard = UiKit.card(this, root);
        detailCard.addView(UiKit.text(this, "Campaign details", 20, true));
        details = UiKit.text(this, trip == null ? "No data" : campaignSummary(), 14, false);
        detailCard.addView(details);

        LinearLayout diagCard = UiKit.card(this, root);
        diagCard.addView(UiKit.text(this, "Provisioning and diagnostics", 20, true));
        diagnostics = UiKit.text(this, "Waiting to begin.", 13, false);
        diagCard.addView(diagnostics);

        LinearLayout actions = UiKit.card(this, root);
        actions.addView(UiKit.text(this, "Actions", 20, true));
        primary = UiKit.button(this, "Start navigation");
        primary.setEnabled(false);
        actions.addView(primary);
        continueCtr = UiKit.secondaryButton(this, "Continue to simulated navigation");
        continueCtr.setVisibility(android.view.View.GONE);
        actions.addView(continueCtr);
        openHistory = UiKit.secondaryButton(this, "Open history record");
        openHistory.setEnabled(false);
        actions.addView(openHistory);
        Button stop = UiKit.secondaryButton(this, "Stop and restore real GPS");
        actions.addView(stop);

        primary.setOnClickListener(v -> startReadyCampaign());
        continueCtr.setOnClickListener(v -> beginDrive());
        openHistory.setOnClickListener(v -> {
            if (historyId.isEmpty()) return;
            Intent i = new Intent(this, HistoryDetailActivity.class);
            i.putExtra("history_id", historyId);
            startActivity(i);
        });
        stop.setOnClickListener(v -> startService(new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_STOP)));
        UiKit.setStickyScreen(this, root, "Drive");
    }

    private void provision() {
        phase.setText("Provisioning route…");
        append("Campaign loaded: " + trip.optString("id"));
        new Thread(() -> {
            try {
                JSONArray waypoints = trip.getJSONArray("waypoints");
                appendUi("Checking " + waypoints.length() + " route points.");
                route = RouteEngine.roadRoute(waypoints);
                routeMiles = routeMiles(route);
                trip.put("provisionedAtEpochMs", System.currentTimeMillis());
                trip.put("routePointCount", route.length());
                trip.put("routeMiles", routeMiles);
                trip.put("status", "provisioned");
                TripStore.save(this, trip);
                runOnUiThread(this::provisioned);
            } catch (Exception e) {
                runOnUiThread(() -> failed("Provisioning failed: " + e.getMessage()));
            }
        }, "campaign-provision").start();
    }

    private void provisioned() {
        details.setText(campaignSummary());
        append("Road route ready: " + route.length() + " points, " + String.format("%.2f", routeMiles) + " miles.");
        long startAt = trip.optLong("startAtEpochMs", 0);
        boolean immediate = startAt <= System.currentTimeMillis() + 5000 && "none".equals(trip.optString("recurrence", "none"));
        if (!immediate) {
            try {
                TripScheduler.schedule(this, trip);
                phase.setText("Scheduled");
                primary.setText("Scheduled for " + format(startAt));
                primary.setEnabled(false);
                append("Android automation registered for " + format(startAt) + ".");
            } catch (Exception e) {
                failed("Could not schedule: " + e.getMessage());
            }
            return;
        }
        phase.setText("Ready");
        primary.setEnabled(true);
        primary.setText("Start navigation now");
        // The run page is visible long enough for the user to verify all captured details.
    }

    private void startReadyCampaign() {
        if ("ctr".equals(trip.optString("navigationType"))) {
            phase.setText("CTR verification");
            append("Opening Google Maps Search for manual verification. Target selection is not automated.");
            String query = trip.optString("searchPhrase");
            Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)));
            maps.setPackage("com.google.android.apps.maps");
            try { startActivity(maps); } catch (Exception e) { maps.setPackage(null); startActivity(maps); }
            continueCtr.setVisibility(android.view.View.VISIBLE);
            primary.setEnabled(false);
        } else {
            beginDrive();
        }
    }

    private void beginDrive() {
        try {
            JSONObject history = HistoryStore.begin(this, trip);
            historyId = history.optString("historyId");
            trip.put("historyId", historyId);
            trip.put("status", "starting");
            TripStore.save(this, trip);
            openHistory.setEnabled(!historyId.isEmpty());
            phase.setText("Setting starting location…");
            append("History session created: " + historyId);

            JSONArray points = trip.getJSONArray("waypoints");
            JSONObject first = points.getJSONObject(0);
            Intent hold = new Intent(this, MockLocationService.class)
                    .setAction(MockLocationService.ACTION_TELEPORT)
                    .putExtra(MockLocationService.EXTRA_LAT, first.getDouble("latitude"))
                    .putExtra(MockLocationService.EXTRA_LON, first.getDouble("longitude"));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(hold); else startService(hold);
            append("Starting location applied. Waiting for Google Maps to initialize.");

            new Handler().postDelayed(() -> {
                try {
                    JSONObject last = points.getJSONObject(points.length() - 1);
                    String origin = first.getDouble("latitude") + "," + first.getDouble("longitude");
                    String destination = last.getDouble("latitude") + "," + last.getDouble("longitude");
                    StringBuilder waypointText = new StringBuilder();
                    for (int i = 1; i < points.length() - 1; i++) {
                        JSONObject w = points.getJSONObject(i);
                        if (waypointText.length() > 0) waypointText.append('|');
                        waypointText.append(w.getDouble("latitude")).append(',').append(w.getDouble("longitude"));
                    }
                    String url = "https://www.google.com/maps/dir/?api=1&origin=" + Uri.encode(origin) +
                            "&destination=" + Uri.encode(destination) + "&travelmode=driving&dir_action=navigate" +
                            (waypointText.length() == 0 ? "" : "&waypoints=" + Uri.encode(waypointText.toString()));
                    Intent maps = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    maps.setPackage("com.google.android.apps.maps");
                    try { startActivity(maps); } catch (Exception e) { maps.setPackage(null); startActivity(maps); }
                    TripScheduler.launch(this, trip);
                    phase.setText("Navigation running");
                    append("Google Maps opened and the fixed-speed GPS simulation started at " + Math.round(trip.optDouble("averageSpeedMph")) + " mph.");
                    HistoryStore.update(this, historyId, "running", routeMiles, diagnostics.getText().toString());
                } catch (Exception e) {
                    failed("Launch failed: " + e.getMessage());
                }
            }, 1800);
        } catch (Exception e) {
            failed("Could not start: " + e.getMessage());
        }
    }

    private String campaignSummary() {
        JSONObject business = trip.optJSONObject("destinationBusiness");
        String type = "ctr".equals(trip.optString("navigationType")) ? "CTR navigation" : "Simulated navigation";
        StringBuilder out = new StringBuilder();
        out.append("Type: ").append(type)
                .append("\nCampaign: ").append(trip.optString("name"))
                .append("\nStart: ").append(trip.optString("startAddress"))
                .append("\nDestination: ").append(trip.optString("endAddress"))
                .append("\nSpeed: ").append(Math.round(trip.optDouble("averageSpeedMph"))).append(" mph")
                .append("\nAdditional stops: ").append(Math.max(0, trip.optJSONArray("waypoints").length() - 2))
                .append("\nRepeat: ").append(trip.optString("recurrence", "none"))
                .append("\nChosen time: ").append(format(trip.optLong("startAtEpochMs")))
                .append("\nTime window: ").append(trip.optString("timeWindow"));
        if ("ctr".equals(trip.optString("navigationType"))) out.append("\nSearch phrase: ").append(trip.optString("searchPhrase")).append("\nSearch source: Google Maps Search");
        if (business != null) {
            out.append("\n\nTarget business: ").append(business.optString("businessName", business.optString("label")))
                    .append("\nPlace ID: ").append(business.optString("placeId"))
                    .append("\nPhone: ").append(orNotProvided(business.optString("phoneNumber")))
                    .append("\nWebsite: ").append(orNotProvided(business.optString("website")))
                    .append("\nGoogle Maps: ").append(orNotProvided(business.optString("googleMapsUri")));
        }
        if (routeMiles > 0) out.append("\nRoad distance: ").append(String.format("%.2f miles", routeMiles));
        return out.toString();
    }

    private void failed(String message) {
        phase.setText("Failed");
        append(message);
        trip.remove("status");
        try { trip.put("status", "failed"); TripStore.save(this, trip); } catch (Exception ignored) {}
        if (!historyId.isEmpty()) HistoryStore.update(this, historyId, "failed", routeMiles, diagnostics.getText().toString());
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void append(String message) { diagnostics.append((diagnostics.length() == 0 ? "" : "\n") + DateFormat.getTimeInstance().format(new Date()) + " — " + message); }
    private void appendUi(String message) { runOnUiThread(() -> append(message)); }
    private String format(long time) { return time <= 0 ? "Not set" : DateFormat.getDateTimeInstance().format(new Date(time)); }
    private String orNotProvided(String value) { return value == null || value.trim().isEmpty() ? "Not provided" : value; }

    private double routeMiles(JSONArray points) {
        double meters = 0;
        for (int i = 1; i < points.length(); i++) {
            JSONArray a = points.optJSONArray(i - 1), b = points.optJSONArray(i);
            if (a == null || b == null) continue;
            meters += distance(a.optDouble(1), a.optDouble(0), b.optDouble(1), b.optDouble(0));
        }
        return meters / 1609.344;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double earth = 6371000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return earth * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
