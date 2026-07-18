package com.seannmichael.mockdrive;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class GooglePlacesEngine {
    private static final String PREFS = "google_places";
    private static final String KEY_API = "api_key";

    private GooglePlacesEngine() {}

    public static void setApiKey(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API, key == null ? "" : key.trim()).apply();
    }

    public static String getApiKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "");
    }

    public static JSONArray autocomplete(Context context, String input) throws Exception {
        String key = requireKey(context);
        if (input == null || input.trim().length() < 2) return new JSONArray();
        JSONObject request = new JSONObject();
        request.put("input", input.trim());
        request.put("includeQueryPredictions", false);
        request.put("regionCode", "US");
        request.put("languageCode", "en");
        JSONObject root = post("https://places.googleapis.com/v1/places:autocomplete", key,
                "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat", request.toString());
        JSONArray results = new JSONArray();
        JSONArray suggestions = root.optJSONArray("suggestions");
        if (suggestions == null) return results;
        for (int i = 0; i < suggestions.length() && results.length() < 8; i++) {
            JSONObject prediction = suggestions.optJSONObject(i) == null ? null : suggestions.optJSONObject(i).optJSONObject("placePrediction");
            if (prediction == null) continue;
            String placeId = prediction.optString("placeId", "");
            JSONObject text = prediction.optJSONObject("text");
            String label = text == null ? "" : text.optString("text", "");
            if (!placeId.isEmpty() && !label.isEmpty()) results.put(new JSONObject().put("placeId", placeId).put("label", label));
        }
        return results;
    }

    public static JSONObject placeDetails(Context context, String placeId) throws Exception {
        String key = requireKey(context);
        if (placeId == null || placeId.trim().isEmpty()) throw new Exception("Place ID is required");
        String encoded = URLEncoder.encode(placeId.trim(), "UTF-8");
        JSONObject place = get("https://places.googleapis.com/v1/places/" + encoded, key,
                "id,displayName,formattedAddress,location,googleMapsUri,nationalPhoneNumber,internationalPhoneNumber,websiteUri,primaryType,types");
        JSONObject location = place.optJSONObject("location");
        if (location == null) throw new Exception("Google did not return coordinates for this place");
        JSONObject displayName = place.optJSONObject("displayName");
        return new JSONObject()
                .put("placeId", place.optString("id", placeId))
                .put("label", displayName == null ? place.optString("formattedAddress", "") : displayName.optString("text", ""))
                .put("businessName", displayName == null ? "" : displayName.optString("text", ""))
                .put("formattedAddress", place.optString("formattedAddress", ""))
                .put("googleMapsUri", place.optString("googleMapsUri", ""))
                .put("phoneNumber", place.optString("nationalPhoneNumber", place.optString("internationalPhoneNumber", "")))
                .put("website", place.optString("websiteUri", ""))
                .put("primaryType", place.optString("primaryType", ""))
                .put("types", place.optJSONArray("types") == null ? new JSONArray() : place.optJSONArray("types"))
                .put("latitude", location.getDouble("latitude"))
                .put("longitude", location.getDouble("longitude"));
    }

    public static JSONObject findTarget(Context context, String searchQuery, String targetName,
                                        String targetPlaceId, String addressHint) throws Exception {
        String key = requireKey(context);
        if (searchQuery == null || searchQuery.trim().isEmpty()) throw new Exception("Search query is required");
        if ((targetPlaceId == null || targetPlaceId.trim().isEmpty()) && (targetName == null || targetName.trim().isEmpty()))
            throw new Exception("Enter a target Place ID or business name");
        JSONObject request = new JSONObject();
        request.put("textQuery", searchQuery.trim());
        request.put("pageSize", 20);
        JSONObject root = post("https://places.googleapis.com/v1/places:searchText", key,
                "places.id,places.displayName,places.formattedAddress,places.location,places.googleMapsUri,nextPageToken", request.toString());
        JSONArray places = root.optJSONArray("places");
        if (places == null || places.length() == 0) throw new Exception("Google returned no matching places");
        JSONObject best = null; double bestScore = -1;
        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.getJSONObject(i);
            String id = place.optString("id", "");
            String name = place.optJSONObject("displayName") == null ? "" : place.optJSONObject("displayName").optString("text", "");
            String address = place.optString("formattedAddress", "");
            if (targetPlaceId != null && !targetPlaceId.trim().isEmpty() && id.equals(targetPlaceId.trim())) { best = place; bestScore = 1000; break; }
            double score = similarity(normalize(name), normalize(targetName)) * 70.0;
            if (addressHint != null && !addressHint.trim().isEmpty()) score += similarity(normalize(address), normalize(addressHint)) * 25.0;
            if (normalize(name).equals(normalize(targetName))) score += 20;
            if (score > bestScore) { bestScore = score; best = place; }
        }
        if (best == null || bestScore < 45) throw new Exception("Target business was not confidently found in the Google results");
        JSONObject location = best.optJSONObject("location");
        if (location == null) throw new Exception("Matched business did not include coordinates");
        return new JSONObject().put("placeId", best.optString("id"))
                .put("businessName", best.optJSONObject("displayName") == null ? "" : best.optJSONObject("displayName").optString("text"))
                .put("formattedAddress", best.optString("formattedAddress"))
                .put("googleMapsUri", best.optString("googleMapsUri"))
                .put("latitude", location.getDouble("latitude")).put("longitude", location.getDouble("longitude"))
                .put("matchScore", bestScore);
    }

    private static String requireKey(Context context) throws Exception {
        String key = getApiKey(context);
        if (key == null || key.trim().isEmpty()) throw new Exception("Add your Google Places API key in Settings first");
        return key.trim();
    }

    private static JSONObject get(String endpoint, String apiKey, String fieldMask) throws Exception {
        HttpURLConnection c = null;
        try { c = (HttpURLConnection) new URL(endpoint).openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("X-Goog-Api-Key", apiKey); c.setRequestProperty("X-Goog-FieldMask", fieldMask); return readResponse(c); }
        finally { if (c != null) c.disconnect(); }
    }

    private static JSONObject post(String endpoint, String apiKey, String fieldMask, String body) throws Exception {
        HttpURLConnection c = null;
        try { c = (HttpURLConnection) new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); c.setRequestProperty("X-Goog-Api-Key", apiKey); c.setRequestProperty("X-Goog-FieldMask", fieldMask); try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); } return readResponse(c); }
        finally { if (c != null) c.disconnect(); }
    }

    private static JSONObject readResponse(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder(); String line; while ((line = reader.readLine()) != null) response.append(line); reader.close();
        if (code < 200 || code >= 300) throw new Exception("Google Places returned " + code + ": " + response);
        return new JSONObject(response.toString());
    }

    private static String normalize(String value) { if (value == null) return ""; return value.toLowerCase(Locale.US).replace('&', ' ').replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private static double similarity(String a, String b) { if (a.isEmpty() || b.isEmpty()) return 0; if (a.equals(b)) return 1; Set<String> left = new HashSet<>(), right = new HashSet<>(); for (String s : a.split(" ")) if (!s.isEmpty()) left.add(s); for (String s : b.split(" ")) if (!s.isEmpty()) right.add(s); Set<String> intersection = new HashSet<>(left); intersection.retainAll(right); Set<String> union = new HashSet<>(left); union.addAll(right); return union.isEmpty() ? 0 : ((double) intersection.size() / union.size()); }
}
