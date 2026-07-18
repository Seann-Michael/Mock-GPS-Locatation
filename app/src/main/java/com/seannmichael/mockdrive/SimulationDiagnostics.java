package com.seannmichael.mockdrive;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.location.Location;
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
    private static final String LOCATION_ENGINE_REVISION = "gps-fused-quality-v2";
    private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private SimulationDiagnostics() {}

    public static File directory(Context context, String historyId) {
        File dir = new File(new File(context.getFilesDir(), ROOT), safe(historyId));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void begin(Context context, String historyId, JSONObject trip) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            File dir = directory(context, historyId);
            deleteContents(dir);
            write(new File(dir, "trip.json"), pretty(trip == null ? "{}" : trip.toString()));
            try {
                JSONObject build = buildInfo(context);
                JSONObject device = new JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("brand", Build.BRAND)
                        .put("model", Build.MODEL)
                        .put("device", Build.DEVICE)
                        .put("androidVersion", Build.VERSION.RELEASE)
                        .put("apiLevel", Build.VERSION.SDK_INT)
                        .put("package", context.getPackageName())
                        .put("appVersionName", build.optString("appVersionName", "unknown"))
                        .put("appVersionCode", build.optLong("appVersionCode", -1))
                        .put("locationEngineRevision", LOCATION_ENGINE_REVISION)
                        .put("sessionStartedEpochMs", System.currentTimeMillis())
                        .put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
                write(new File(dir, "device.json"), device.toString(2));
                event(context, historyId, "SESSION", "Detailed simulation diagnostics started app=" +
                        device.optString("appVersionName", "unknown") + " (" + device.optLong("appVersionCode", -1) +
                        ") engine=" + LOCATION_ENGINE_REVISION);
            } catch (Exception ignored) {
                event(context, historyId, "SESSION", "Detailed simulation diagnostics started");
            }
        }
    }

    public static void saveRoute(Context context, String historyId, JSONArray route) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            write(new File(directory(context, historyId), "route.json"), route == null ? "[]" : route.toString());
            event(context, historyId, "ROUTE", "Saved route geometry with " + (route == null ? 0 : route.length()) + " coordinates");
        }
    }

    public static void saveRoutePlan(Context context, String historyId, JSONArray routePlan) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            write(new File(directory(context, historyId), "route_segments.json"), routePlan == null ? "[]" : routePlan.toString());
            event(context, historyId, "ROUTE", "Saved " + (routePlan == null ? 0 : routePlan.length()) + " route segments with heading and length data");
        }
    }

    public static void event(Context context, String historyId, String category, String message) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            append(new File(directory(context, historyId), "events.log"),
                    TIME.format(new Date()) + " [" + category + "] " + message + "\n");
        }
    }

    public static void warning(Context context, String historyId, String message) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            String line = TIME.format(new Date()) + " [WARNING] " + message + "\n";
            append(new File(directory(context, historyId), "warnings.log"), line);
            append(new File(directory(context, historyId), "events.log"), line);
        }
    }

    public static void injection(Context context, String historyId, JSONObject runtime,
                                 Location gpsInjected, Location gpsReported,
                                 Location fusedInjected, Location fusedReported) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            try {
                JSONObject row = runtime == null ? new JSONObject() : new JSONObject(runtime.toString());
                JSONObject build = buildInfo(context);
                row.put("appVersionName", build.optString("appVersionName", "unknown"));
                row.put("appVersionCode", build.optLong("appVersionCode", -1));
                row.put("locationEngineRevision", LOCATION_ENGINE_REVISION);
                row.put("timestampEpochMs", System.currentTimeMillis());
                row.put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
                row.put("gpsInjected", locationJson(gpsInjected));
                row.put("gpsReported", locationJson(gpsReported));
                row.put("fusedInjected", locationJson(fusedInjected));
                row.put("fusedReported", locationJson(fusedReported));
                addComparison(row, "gps", gpsInjected, gpsReported);
                addComparison(row, "fused", fusedInjected, fusedReported);
                append(new File(directory(context, historyId), "location_updates.jsonl"), row.toString() + "\n");
                write(new File(directory(context, historyId), "latest_state.json"), row.toString(2));
            } catch (Exception e) {
                append(new File(directory(context, historyId), "logger_errors.log"),
                        TIME.format(new Date()) + " " + e + "\n");
            }
        }
    }

    private static void addComparison(JSONObject row, String prefix, Location injected, Location reported) throws Exception {
        if (injected != null && reported != null) {
            row.put(prefix + "ReportedDistanceFromInjectedMeters", injected.distanceTo(reported));
            row.put(prefix + "ReportedAgeMs", Math.max(0, System.currentTimeMillis() - reported.getTime()));
        } else {
            row.put(prefix + "ReportedDistanceFromInjectedMeters", JSONObject.NULL);
            row.put(prefix + "ReportedAgeMs", JSONObject.NULL);
        }
    }

    public static void exception(Context context, String historyId, String where, Throwable error) {
        if (historyId == null || historyId.isEmpty()) return;
        synchronized (LOCK) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            append(new File(directory(context, historyId), "exceptions.log"),
                    TIME.format(new Date()) + " [" + where + "] " + error + "\n" + stack + "\n");
            event(context, historyId, "ERROR", where + ": " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        }
    }

    public static String readLiveState(Context context, String historyId) {
        if (historyId == null || historyId.isEmpty()) return "";
        return read(new File(directory(context, historyId), "latest_state.json"));
    }

    public static String summary(Context context, String historyId) {
        if (historyId == null || historyId.isEmpty()) return "No diagnostic session is attached to this simulation.";
        File dir = directory(context, historyId);
        String device = read(new File(dir, "device.json"));
        String state = read(new File(dir, "latest_state.json"));
        String warnings = tail(read(new File(dir, "warnings.log")), 6000);
        String events = tail(read(new File(dir, "events.log")), 16000);
        String exceptions = tail(read(new File(dir, "exceptions.log")), 8000);
        StringBuilder out = new StringBuilder();
        try {
            JSONObject info = new JSONObject(device.isEmpty() ? "{}" : device);
            out.append("App version: ").append(info.optString("appVersionName", "unknown"))
                    .append(" (").append(info.optLong("appVersionCode", -1)).append(")")
                    .append("\nLocation engine: ").append(info.optString("locationEngineRevision", "legacy/unknown"))
                    .append("\nDevice: ").append(info.optString("manufacturer")).append(" ").append(info.optString("model"))
                    .append(" / Android ").append(info.optString("androidVersion")).append(" API ").append(info.optInt("apiLevel"))
                    .append("\n\n");
        } catch (Exception ignored) {
            out.append("App version: unknown\nLocation engine: legacy/unknown\n\n");
        }
        out.append("Latest runtime state:\n").append(state.isEmpty() ? "Not recorded" : state);
        if (!warnings.isEmpty()) out.append("\n\nWarnings:\n").append(warnings);
        if (!exceptions.isEmpty()) out.append("\n\nExceptions:\n").append(exceptions);
        out.append("\n\nRecent events:\n").append(events.isEmpty() ? "Not recorded" : events);
        return out.toString();
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

    private static JSONObject buildInfo(Context context) {
        JSONObject out = new JSONObject();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            out.put("appVersionName", info.versionName == null ? "unknown" : info.versionName);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            out.put("appVersionCode", versionCode);
        } catch (Exception ignored) {
            try {
                out.put("appVersionName", "unknown");
                out.put("appVersionCode", -1);
            } catch (Exception ignoredAgain) {}
        }
        return out;
    }

    private static Object locationJson(Location location) throws Exception {
        if (location == null) return JSONObject.NULL;
        return new JSONObject()
                .put("provider", location.getProvider())
                .put("latitude", location.getLatitude())
                .put("longitude", location.getLongitude())
                .put("hasAccuracy", location.hasAccuracy())
                .put("accuracyMeters", location.hasAccuracy() ? location.getAccuracy() : -1)
                .put("hasSpeed", location.hasSpeed())
                .put("speedMps", location.hasSpeed() ? location.getSpeed() : -1)
                .put("speedMph", location.hasSpeed() ? location.getSpeed() * 2.2369362920544 : -1)
                .put("hasBearing", location.hasBearing())
                .put("bearingDegrees", location.hasBearing() ? location.getBearing() : -1)
                .put("timeEpochMs", location.getTime())
                .put("wallClockAgeMs", Math.max(0, System.currentTimeMillis() - location.getTime()))
                .put("elapsedRealtimeNanos", location.getElapsedRealtimeNanos())
                .put("elapsedRealtimeAgeMs", elapsedAgeMs(location))
                .put("complete", locationComplete(location))
                .put("isMock", Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider());
    }

    private static boolean locationComplete(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= 33) return location.isComplete();
        return location.getProvider() != null && location.hasAccuracy() && location.getTime() != 0 && location.getElapsedRealtimeNanos() != 0;
    }

    private static long elapsedAgeMs(Location location) {
        if (location == null || location.getElapsedRealtimeNanos() <= 0) return -1;
        return Math.max(0, (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000L);
    }

    private static String pretty(String json) {
        try { return new JSONObject(json == null ? "{}" : json).toString(2); }
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
            byte[] bytes = new byte[(int)Math.min(file.length(), 8_000_000)];
            int read = input.read(bytes);
            return read <= 0 ? "" : new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static String tail(String value, int max) {
        return value.length() <= max ? value : value.substring(value.length() - max);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void deleteContents(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }
}
