package com.seannmichael.mockdrive;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SimulationDiagnostics {
    private static final Object LOCK = new Object();
    private static final String ROOT = "simulation_history_diagnostics";
    private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private SimulationDiagnostics() {}

    public static File directory(Context context, String historyId) {
        File dir = new File(new File(context.getFilesDir(), ROOT), safe(historyId));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void begin(Context context, String historyId, JSONObject trip) {
        synchronized (LOCK) {
            File dir = directory(context, historyId);
            deleteContents(dir);
            write(new File(dir, "trip.json"), pretty(trip == null ? "{}" : trip.toString()));
            try {
                JSONObject device = new JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("brand", Build.BRAND)
                        .put("model", Build.MODEL)
                        .put("device", Build.DEVICE)
                        .put("androidVersion", Build.VERSION.RELEASE)
                        .put("apiLevel", Build.VERSION.SDK_INT)
                        .put("package", context.getPackageName())
                        .put("sessionStartedEpochMs", System.currentTimeMillis())
                        .put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
                write(new File(dir, "device.json"), device.toString(2));
            } catch (Exception ignored) {}
            event(context, historyId, "SESSION", "Detailed simulation diagnostics started");
        }
    }

    public static void saveRoute(Context context, String historyId, JSONArray route) {
        synchronized (LOCK) {
            write(new File(directory(context, historyId), "route.json"), route == null ? "[]" : route.toString());
            event(context, historyId, "ROUTE", "Saved route with " + (route == null ? 0 : route.length()) + " coordinates");
        }
    }

    public static void event(Context context, String historyId, String category, String message) {
        synchronized (LOCK) {
            append(new File(directory(context, historyId), "events.log"), TIME.format(new Date()) + " [" + category + "] " + message + "\n");
        }
    }

    public static void injection(Context context, String historyId, long count, int segment, int totalSegments,
                                 Location injected, Location androidReported, boolean providerEnabled,
                                 boolean workerAlive, String phase) {
        synchronized (LOCK) {
            try {
                JSONObject row = new JSONObject()
                        .put("timestampEpochMs", System.currentTimeMillis())
                        .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
                        .put("count", count)
                        .put("phase", phase)
                        .put("segment", segment)
                        .put("totalSegments", totalSegments)
                        .put("providerEnabled", providerEnabled)
                        .put("workerAlive", workerAlive)
                        .put("injected", locationJson(injected))
                        .put("androidReported", locationJson(androidReported));
                if (injected != null && androidReported != null) {
                    row.put("reportedDistanceFromInjectedMeters", injected.distanceTo(androidReported));
                    row.put("reportedAgeMs", Math.max(0, System.currentTimeMillis() - androidReported.getTime()));
                }
                append(new File(directory(context, historyId), "location_updates.jsonl"), row.toString() + "\n");
                write(new File(directory(context, historyId), "latest_state.json"), row.toString(2));
            } catch (Exception ignored) {}
        }
    }

    public static void exception(Context context, String historyId, String where, Throwable error) {
        synchronized (LOCK) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            append(new File(directory(context, historyId), "exceptions.log"),
                    TIME.format(new Date()) + " [" + where + "] " + error + "\n" + stack + "\n");
            event(context, historyId, "ERROR", where + ": " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        }
    }

    public static String summary(Context context, String historyId) {
        String state = read(new File(directory(context, historyId), "latest_state.json"));
        String tail = tail(read(new File(directory(context, historyId), "events.log")), 12000);
        return "Latest runtime state:\n" + (state.isEmpty() ? "Not recorded" : state) + "\n\nRecent events:\n" + tail;
    }

    public static File exportZip(Context context, String historyId) throws Exception {
        synchronized (LOCK) {
            File source = directory(context, historyId);
            File outDir = new File(context.getCacheDir(), "simulation_exports");
            if (!outDir.exists()) outDir.mkdirs();
            File zip = new File(outDir, "MockDrive-Simulation-" + safe(historyId) + ".zip");
            try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
                File[] files = source.listFiles();
                if (files != null) {
                    byte[] buffer = new byte[8192];
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        output.putNextEntry(new ZipEntry(file.getName()));
                        try (FileInputStream input = new FileInputStream(file)) {
                            int read;
                            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
                        }
                        output.closeEntry();
                    }
                }
            }
            return zip;
        }
    }

    private static JSONObject locationJson(Location location) throws Exception {
        if (location == null) return JSONObject.NULL;
        return new JSONObject()
                .put("provider", location.getProvider())
                .put("latitude", location.getLatitude())
                .put("longitude", location.getLongitude())
                .put("accuracyMeters", location.hasAccuracy() ? location.getAccuracy() : -1)
                .put("speedMps", location.hasSpeed() ? location.getSpeed() : -1)
                .put("bearingDegrees", location.hasBearing() ? location.getBearing() : -1)
                .put("timeEpochMs", location.getTime())
                .put("elapsedRealtimeNanos", location.getElapsedRealtimeNanos())
                .put("isMock", Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider());
    }

    private static String pretty(String json) {
        try { return new JSONObject(json).toString(2); }
        catch (Exception e) { return json == null ? "" : json; }
    }

    private static void append(File file, String text) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) { writer.write(text); }
        catch (Exception ignored) {}
    }

    private static void write(File file, String text) {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private static String read(File file) {
        if (!file.exists()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int)Math.min(file.length(), 4_000_000)];
            int read = input.read(bytes);
            return read <= 0 ? "" : new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static String tail(String value, int max) { return value.length() <= max ? value : value.substring(value.length() - max); }
    private static String safe(String value) { return value == null || value.isEmpty() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static void deleteContents(File dir) { File[] files = dir.listFiles(); if (files != null) for (File file : files) if (file.isFile()) file.delete(); }
}