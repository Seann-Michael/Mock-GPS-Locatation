package com.seannmichael.mockdrive;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

public final class RouteEngine {
    private RouteEngine() {}

    public static JSONArray roadRoute(JSONArray waypoints) throws Exception {
        if (waypoints == null || waypoints.length() < 2) throw new IllegalArgumentException("At least two waypoints are required");
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < waypoints.length(); i++) {
            JSONObject w = waypoints.getJSONObject(i);
            if (i > 0) coords.append(';');
            coords.append(String.format(Locale.US, "%f,%f", w.getDouble("longitude"), w.getDouble("latitude")));
        }
        String endpoint = "https://router.project-osrm.org/route/v1/driving/" + coords + "?overview=full&geometries=geojson&steps=true";
        JSONObject root = new JSONObject(get(endpoint, "MockDrive/2.0"));
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) throw new Exception("No roadway route found");
        return routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
    }

    public static JSONObject geocode(String address) throws Exception {
        String endpoint = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + URLEncoder.encode(address, "UTF-8");
        JSONArray results = new JSONArray(get(endpoint, "MockDrive/2.0 contact=local-test-app"));
        if (results.length() == 0) throw new Exception("Address not found");
        JSONObject r = results.getJSONObject(0);
        JSONObject out = new JSONObject();
        out.put("latitude", Double.parseDouble(r.getString("lat")));
        out.put("longitude", Double.parseDouble(r.getString("lon")));
        out.put("label", r.optString("display_name", address));
        return out;
    }

    private static String get(String endpoint, String userAgent) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", userAgent);
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("Routing service returned " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            r.close();
            return b.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }
}