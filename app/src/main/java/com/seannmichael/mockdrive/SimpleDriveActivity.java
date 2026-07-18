package com.seannmichael.mockdrive;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class SimpleDriveActivity extends BaseActivity {
    private final Handler searchHandler = new Handler();
    private final ArrayList<StopRow> stopRows = new ArrayList<>();
    private Spinner typeSpinner, searchSourceSpinner, recurrenceSpinner, timeWindowSpinner, startModeSpinner, weekdaySpinner, monthDaySpinner;
    private EditText campaignName, searchPhrase, startLat, startLon, destinationLat, destinationLon;
    private AutoCompleteTextView startAddress, destinationAddress;
    private LinearLayout ctrOptions, recurrenceOptions, weeklyOptions, monthlyOptions, specificStartBox, stopsContainer;
    private TextView status, speedValue, destinationDetails, standardLocationSummary, selectedDateText;
    private int selectedSpeed = 35;
    private long selectedDateMs;
    private JSONObject destinationPlace = new JSONObject();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Calendar today = Calendar.getInstance();
        selectedDateMs = today.getTimeInMillis();

        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Create navigation", true);
        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, "New navigation campaign", 27, true));
        hero.addView(UiKit.whiteText(this, "Choose the navigation type, timing, route, destination, and optional stops.", 15, false));
        status = UiKit.text(this, "Complete the required fields below.", 14, true);
        status.setTextColor(UiKit.BLUE_DARK);

        buildTypeCard(root);
        buildScheduleCard(root);
        buildStartCard(root);
        buildStopsCard(root);
        buildDestinationCard(root);
        buildSpeedCard(root);

        LinearLayout action = UiKit.card(this, root);
        action.addView(UiKit.text(this, "Review and provision", 20, true));
        action.addView(UiKit.text(this, "The next page will preserve the campaign details, provision the route, and either launch or schedule it.", 13, false));
        Button submit = UiKit.button(this, "Create navigation");
        action.addView(submit);
        action.addView(status);
        submit.setOnClickListener(v -> submitCampaign());

        UiKit.setStickyScreen(this, root, "Drive");
        updateVisibility();
    }

    private void buildTypeCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "1. Navigation type", 20, true));
        campaignName = UiKit.field(this, "Campaign name", "");
        card.addView(campaignName);
        typeSpinner = spinner("Simulated navigation", "CTR navigation");
        card.addView(typeSpinner);

        ctrOptions = new LinearLayout(this);
        ctrOptions.setOrientation(LinearLayout.VERTICAL);
        searchPhrase = UiKit.field(this, "Search phrase, for example: Medina junk removal", "");
        ctrOptions.addView(searchPhrase);
        searchSourceSpinner = spinner("Google Maps Search", "Google Places Search — future feature");
        ctrOptions.addView(searchSourceSpinner);
        TextView note = UiKit.text(this, "CTR mode records the phrase and target profile for testing and verification. Google Places Search is not available yet.", 13, false);
        ctrOptions.addView(note);
        card.addView(ctrOptions);
        typeSpinner.setOnItemSelectedListener(new SimpleSelectionListener(this::updateVisibility));
        searchSourceSpinner.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            if (searchSourceSpinner.getSelectedItemPosition() == 1) {
                searchSourceSpinner.setSelection(0);
                toast("Google Places Search is a future feature");
            }
        }));
    }

    private void buildScheduleCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "2. Timing", 20, true));
        recurrenceSpinner = spinner("One time", "Daily", "Weekly", "Monthly");
        card.addView(recurrenceSpinner);

        recurrenceOptions = new LinearLayout(this);
        recurrenceOptions.setOrientation(LinearLayout.VERTICAL);
        Button dateButton = UiKit.secondaryButton(this, "Select first run date");
        selectedDateText = UiKit.text(this, DateFormat.getDateInstance().format(selectedDateMs), 15, true);
        recurrenceOptions.addView(dateButton);
        recurrenceOptions.addView(selectedDateText);
        timeWindowSpinner = spinner("Start immediately", "12 AM–2 AM", "2 AM–4 AM", "4 AM–6 AM", "6 AM–8 AM", "8 AM–10 AM", "10 AM–12 PM", "12 PM–2 PM", "2 PM–4 PM", "4 PM–6 PM", "6 PM–8 PM", "8 PM–10 PM", "10 PM–12 AM");
        recurrenceOptions.addView(timeWindowSpinner);
        recurrenceOptions.addView(UiKit.text(this, "For a selected two-hour window, Mock Drive randomly chooses the exact minute when the campaign is saved.", 13, false));

        weeklyOptions = new LinearLayout(this);
        weeklyOptions.setOrientation(LinearLayout.VERTICAL);
        weeklyOptions.addView(UiKit.text(this, "Weekly day", 14, true));
        weekdaySpinner = spinner("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
        weeklyOptions.addView(weekdaySpinner);
        recurrenceOptions.addView(weeklyOptions);

        monthlyOptions = new LinearLayout(this);
        monthlyOptions.setOrientation(LinearLayout.VERTICAL);
        monthlyOptions.addView(UiKit.text(this, "Day of month", 14, true));
        String[] days = new String[28];
        for (int i = 0; i < days.length; i++) days[i] = String.valueOf(i + 1);
        monthDaySpinner = spinner(days);
        monthlyOptions.addView(monthDaySpinner);
        recurrenceOptions.addView(monthlyOptions);

        card.addView(recurrenceOptions);
        dateButton.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(selectedDateMs);
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance(); picked.set(year, month, day, 0, 0, 0); picked.set(Calendar.MILLISECOND, 0);
                selectedDateMs = picked.getTimeInMillis();
                selectedDateText.setText(DateFormat.getDateInstance().format(selectedDateMs));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        recurrenceSpinner.setOnItemSelectedListener(new SimpleSelectionListener(this::updateVisibility));
    }

    private void buildStartCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "3. Starting location", 20, true));
        startModeSpinner = spinner("Use standard location from Settings", "Random location within 10 miles of destination", "Use a specific address or location");
        card.addView(startModeSpinner);
        standardLocationSummary = UiKit.text(this, standardLocationLabel(), 14, false);
        card.addView(standardLocationSummary);

        specificStartBox = new LinearLayout(this);
        specificStartBox.setOrientation(LinearLayout.VERTICAL);
        startAddress = UiKit.autocompleteField(this, "Search starting address or place");
        specificStartBox.addView(startAddress);
        LinearLayout coords = coordinateRow();
        startLat = UiKit.field(this, "Latitude", "");
        startLon = UiKit.field(this, "Longitude", "");
        coords.addView(startLat, new LinearLayout.LayoutParams(0, -2, 1));
        coords.addView(startLon, new LinearLayout.LayoutParams(0, -2, 1));
        specificStartBox.addView(coords);
        card.addView(specificStartBox);
        setupAutocomplete(startAddress, startLat, startLon, null, "Start");
        startModeSpinner.setOnItemSelectedListener(new SimpleSelectionListener(this::updateVisibility));
    }

    private void buildStopsCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "4. Optional stops", 20, true));
        card.addView(UiKit.text(this, "Add places the simulated drive should visit before the final destination.", 13, false));
        stopsContainer = new LinearLayout(this);
        stopsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(stopsContainer);
        Button add = UiKit.secondaryButton(this, "+ Add stop");
        card.addView(add);
        add.setOnClickListener(v -> addStop());
    }

    private void buildDestinationCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "5. Target destination", 20, true));
        card.addView(UiKit.text(this, "Select the exact business or address from Google Places.", 13, false));
        destinationAddress = UiKit.autocompleteField(this, "Search business or destination");
        card.addView(destinationAddress);
        LinearLayout coords = coordinateRow();
        destinationLat = UiKit.field(this, "Latitude", "");
        destinationLon = UiKit.field(this, "Longitude", "");
        coords.addView(destinationLat, new LinearLayout.LayoutParams(0, -2, 1));
        coords.addView(destinationLon, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(coords);
        destinationDetails = UiKit.text(this, "No destination selected.", 13, false);
        card.addView(destinationDetails);
        setupAutocomplete(destinationAddress, destinationLat, destinationLon, place -> {
            destinationPlace = place;
            destinationDetails.setText("Business: " + place.optString("businessName", place.optString("label")) +
                    "\nPlace ID: " + place.optString("placeId") +
                    "\nPhone: " + blank(place.optString("phoneNumber")) +
                    "\nWebsite: " + blank(place.optString("website")) +
                    "\nAddress: " + place.optString("formattedAddress"));
        }, "Destination");
    }

    private void buildSpeedCard(LinearLayout root) {
        LinearLayout card = UiKit.card(this, root);
        card.addView(UiKit.text(this, "6. Travel speed", 20, true));
        card.addView(UiKit.text(this, "The selected speed is fixed when the navigation starts.", 13, false));
        speedValue = UiKit.text(this, "35 mph", 30, true);
        speedValue.setTextColor(UiKit.BLUE_DARK);
        card.addView(speedValue);
        SeekBar bar = new SeekBar(this);
        bar.setMax(95); bar.setProgress(30);
        card.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { selectedSpeed = progress + 5; speedValue.setText(selectedSpeed + " mph"); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void addStop() {
        if (stopRows.size() >= 5) { toast("A maximum of 5 additional stops is supported"); return; }
        StopRow row = new StopRow();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.text(this, "Stop " + (stopRows.size() + 1), 16, true));
        row.address = UiKit.autocompleteField(this, "Search stop address or place");
        box.addView(row.address);
        LinearLayout coordinates = coordinateRow();
        row.lat = UiKit.field(this, "Latitude", "");
        row.lon = UiKit.field(this, "Longitude", "");
        coordinates.addView(row.lat, new LinearLayout.LayoutParams(0, -2, 1));
        coordinates.addView(row.lon, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(coordinates);
        row.stopSeconds = UiKit.field(this, "Stop duration in seconds (optional)", "0");
        box.addView(row.stopSeconds);
        Button remove = UiKit.secondaryButton(this, "Remove stop");
        box.addView(remove);
        row.container = box;
        stopRows.add(row);
        stopsContainer.addView(box);
        setupAutocomplete(row.address, row.lat, row.lon, null, "Stop");
        remove.setOnClickListener(v -> { stopsContainer.removeView(box); stopRows.remove(row); });
    }

    private void submitCampaign() {
        try {
            boolean ctr = typeSpinner.getSelectedItemPosition() == 1;
            if (ctr && searchPhrase.getText().toString().trim().isEmpty()) throw new Exception("Enter the CTR search phrase");
            if (destinationLat.getText().toString().trim().isEmpty() || destinationLon.getText().toString().trim().isEmpty()) throw new Exception("Select a destination");
            double bLat = parse(destinationLat), bLon = parse(destinationLon);
            JSONObject start = resolveStart(bLat, bLon);
            JSONArray waypoints = new JSONArray();
            waypoints.put(new JSONObject().put("latitude", start.getDouble("latitude")).put("longitude", start.getDouble("longitude")).put("stopSeconds", 0).put("label", start.optString("label")));
            for (StopRow row : stopRows) {
                if (row.lat.getText().toString().trim().isEmpty() && row.lon.getText().toString().trim().isEmpty()) continue;
                waypoints.put(new JSONObject().put("latitude", parse(row.lat)).put("longitude", parse(row.lon)).put("stopSeconds", Math.max(0, parseInt(row.stopSeconds, 0))).put("label", row.address.getText().toString().trim()));
            }
            waypoints.put(new JSONObject().put("latitude", bLat).put("longitude", bLon).put("stopSeconds", 0).put("label", destinationAddress.getText().toString().trim()));

            String recurrence = recurrenceValue();
            long randomizedStart = calculateStartTime(recurrence);
            JSONObject trip = new JSONObject()
                    .put("name", campaignName.getText().toString().trim().isEmpty() ? "Navigation campaign" : campaignName.getText().toString().trim())
                    .put("navigationType", ctr ? "ctr" : "simulated")
                    .put("searchPhrase", ctr ? searchPhrase.getText().toString().trim() : "")
                    .put("searchSource", "google_maps")
                    .put("ctrSelectionMode", ctr ? "manual_verification" : "none")
                    .put("startMode", startModeValue())
                    .put("startAddress", start.optString("label"))
                    .put("endAddress", destinationAddress.getText().toString().trim())
                    .put("destinationBusiness", destinationPlace)
                    .put("waypoints", waypoints)
                    .put("averageSpeedMph", selectedSpeed)
                    .put("speedVariationPercent", 5)
                    .put("gpsUpdateIntervalMs", 1000)
                    .put("randomStops", false)
                    .put("holdAtDestination", true)
                    .put("recurrence", recurrence)
                    .put("startAtEpochMs", randomizedStart)
                    .put("timeWindow", timeWindowSpinner.getSelectedItem().toString())
                    .put("weeklyDay", weekdaySpinner.getSelectedItemPosition() + 1)
                    .put("monthlyDay", monthDaySpinner.getSelectedItemPosition() + 1)
                    .put("createdAtEpochMs", System.currentTimeMillis());
            JSONObject saved = TripStore.save(this, trip);
            Intent intent = new Intent(this, NavigationRunActivity.class);
            intent.putExtra("trip_id", saved.getString("id"));
            startActivity(intent);
        } catch (Exception e) {
            status.setText(e.getMessage());
            toast(e.getMessage());
        }
    }

    private JSONObject resolveStart(double destLat, double destLon) throws Exception {
        int mode = startModeSpinner.getSelectedItemPosition();
        if (mode == 0) {
            android.content.SharedPreferences p = getSharedPreferences("mock_default_location", 0);
            return new JSONObject().put("label", p.getString("address", "Standard location from Settings"))
                    .put("latitude", Double.longBitsToDouble(p.getLong("latitude", Double.doubleToLongBits(41.181097))))
                    .put("longitude", Double.longBitsToDouble(p.getLong("longitude", Double.doubleToLongBits(-81.974890))));
        }
        if (mode == 1) {
            Random r = new Random();
            double miles = 1 + r.nextDouble() * 9;
            double angle = r.nextDouble() * Math.PI * 2;
            double lat = destLat + (miles / 69.0) * Math.cos(angle);
            double lon = destLon + (miles / (69.0 * Math.cos(Math.toRadians(destLat)))) * Math.sin(angle);
            return new JSONObject().put("label", String.format(Locale.US, "Random point %.1f miles from destination", miles)).put("latitude", lat).put("longitude", lon);
        }
        if (startLat.getText().toString().trim().isEmpty() || startLon.getText().toString().trim().isEmpty()) throw new Exception("Select a specific starting location");
        return new JSONObject().put("label", startAddress.getText().toString().trim()).put("latitude", parse(startLat)).put("longitude", parse(startLon));
    }

    private long calculateStartTime(String recurrence) {
        if (timeWindowSpinner.getSelectedItemPosition() == 0 && "none".equals(recurrence)) return System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selectedDateMs);
        if ("weekly".equals(recurrence)) {
            c.set(Calendar.DAY_OF_WEEK, weekdaySpinner.getSelectedItemPosition() + 1);
            while (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.WEEK_OF_YEAR, 1);
        } else if ("monthly".equals(recurrence)) {
            c.set(Calendar.DAY_OF_MONTH, monthDaySpinner.getSelectedItemPosition() + 1);
            while (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.MONTH, 1);
        } else if ("daily".equals(recurrence)) {
            while (c.getTimeInMillis() < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        }
        int window = Math.max(1, timeWindowSpinner.getSelectedItemPosition());
        int startHour = (window - 1) * 2;
        int randomMinutes = new Random().nextInt(120);
        c.set(Calendar.HOUR_OF_DAY, startHour + randomMinutes / 60);
        c.set(Calendar.MINUTE, randomMinutes % 60);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < System.currentTimeMillis()) {
            if ("none".equals(recurrence)) c.add(Calendar.DAY_OF_YEAR, 1);
            else if ("daily".equals(recurrence)) c.add(Calendar.DAY_OF_YEAR, 1);
            else if ("weekly".equals(recurrence)) c.add(Calendar.WEEK_OF_YEAR, 1);
            else if ("monthly".equals(recurrence)) c.add(Calendar.MONTH, 1);
        }
        return c.getTimeInMillis();
    }

    private void updateVisibility() {
        boolean ctr = typeSpinner != null && typeSpinner.getSelectedItemPosition() == 1;
        if (ctrOptions != null) ctrOptions.setVisibility(ctr ? View.VISIBLE : View.GONE);
        boolean weekly = recurrenceSpinner != null && recurrenceSpinner.getSelectedItemPosition() == 2;
        boolean monthly = recurrenceSpinner != null && recurrenceSpinner.getSelectedItemPosition() == 3;
        if (weeklyOptions != null) weeklyOptions.setVisibility(weekly ? View.VISIBLE : View.GONE);
        if (monthlyOptions != null) monthlyOptions.setVisibility(monthly ? View.VISIBLE : View.GONE);
        if (specificStartBox != null) specificStartBox.setVisibility(startModeSpinner != null && startModeSpinner.getSelectedItemPosition() == 2 ? View.VISIBLE : View.GONE);
        if (standardLocationSummary != null) standardLocationSummary.setVisibility(startModeSpinner != null && startModeSpinner.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
    }

    private void setupAutocomplete(AutoCompleteTextView input, EditText lat, EditText lon, PlaceConsumer consumer, String label) {
        final ArrayList<String> labels = new ArrayList<>(), ids = new ArrayList<>();
        final PlacesSuggestionAdapter adapter = new PlacesSuggestionAdapter(this, labels);
        input.setAdapter(adapter);
        input.setOnFocusChangeListener((view, hasFocus) -> { if (hasFocus) input.postDelayed(() -> { InputMethodManager keyboard = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT); if (adapter.getCount() > 0) input.showDropDown(); }, 120); });
        input.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= ids.size()) return;
            String placeId = ids.get(position); status.setText("Loading " + label.toLowerCase() + " details…");
            new Thread(() -> { try { JSONObject place = GooglePlacesEngine.placeDetails(this, placeId); runOnUiThread(() -> { input.setText(place.optString("formattedAddress", place.optString("label", "")), false); lat.setText(String.valueOf(place.optDouble("latitude"))); lon.setText(String.valueOf(place.optDouble("longitude"))); input.dismissDropDown(); if (consumer != null) consumer.accept(place); status.setText(label + " selected: " + place.optString("label")); }); } catch (Exception e) { runOnUiThread(() -> status.setText(friendlyPlacesError(e))); } }, "place-details").start();
        });
        input.addTextChangedListener(new TextWatcher() {
            Runnable pending;
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (pending != null) searchHandler.removeCallbacks(pending);
                String q = s == null ? "" : s.toString().trim();
                if (q.length() < 2) { labels.clear(); ids.clear(); adapter.notifyDataSetChanged(); input.dismissDropDown(); return; }
                pending = () -> new Thread(() -> { try { JSONArray results = GooglePlacesEngine.autocomplete(SimpleDriveActivity.this, q); ArrayList<String> nextLabels = new ArrayList<>(), nextIds = new ArrayList<>(); for (int i = 0; i < results.length(); i++) { JSONObject r = results.optJSONObject(i); if (r != null) { nextLabels.add(r.optString("label")); nextIds.add(r.optString("placeId")); } } runOnUiThread(() -> { if (!q.equals(input.getText().toString().trim())) return; labels.clear(); labels.addAll(nextLabels); ids.clear(); ids.addAll(nextIds); adapter.notifyDataSetChanged(); if (input.hasFocus() && !labels.isEmpty()) input.showDropDown(); }); } catch (Exception e) { runOnUiThread(() -> status.setText(friendlyPlacesError(e))); } }, "places-autocomplete").start();
                searchHandler.postDelayed(pending, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private Spinner spinner(String... values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); s.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 8), UiKit.dp(this, 10), UiKit.dp(this, 8)); return s; }
    private LinearLayout coordinateRow() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, 0, 0, UiKit.dp(this, 4)); return row; }
    private String recurrenceValue() { int p = recurrenceSpinner.getSelectedItemPosition(); return p == 1 ? "daily" : p == 2 ? "weekly" : p == 3 ? "monthly" : "none"; }
    private String startModeValue() { int p = startModeSpinner.getSelectedItemPosition(); return p == 1 ? "random_near_destination" : p == 2 ? "specific" : "standard"; }
    private double parse(EditText e) { return Double.parseDouble(e.getText().toString().trim()); }
    private int parseInt(EditText e, int fallback) { try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; } }
    private String friendlyPlacesError(Exception e) { String m = e.getMessage() == null ? "Google Places request failed" : e.getMessage(); if (m.contains("API key")) return "Add your Google Places API key in Settings → API and access."; if (m.contains("403")) return "Google Places denied the request. Check billing, Places API, and key restrictions."; return m; }
    private String blank(String s) { return s == null || s.trim().isEmpty() ? "Not provided" : s; }
    private String standardLocationLabel() { android.content.SharedPreferences p = getSharedPreferences("mock_default_location", 0); return "Standard location: " + p.getString("address", "Not configured — current fallback will be used"); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private interface PlaceConsumer { void accept(JSONObject place); }
    private static final class StopRow { LinearLayout container; AutoCompleteTextView address; EditText lat, lon, stopSeconds; }
    private static final class SimpleSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action; SimpleSelectionListener(Runnable action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { action.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
