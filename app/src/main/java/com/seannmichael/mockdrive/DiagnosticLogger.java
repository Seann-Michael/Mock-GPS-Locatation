package com.seannmichael.mockdrive;

import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

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

public final class DiagnosticLogger {
    private static final Object LOCK = new Object();
    private static final String DIR = "navigation_diagnostics";
    private static final String LOG = "navigation.log";
    private static final String STATE = "live_state.json";
    private static final String TRIP = "trip.json";
    private static final String ROUTE = "route.json";
    private static final String DEVICE = "phone_info.json";
    private static final String EXCEPTIONS = "exceptions.log";
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private DiagnosticLogger() {}

    public static File directory(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void beginTrip(Context context, String tripJson) {
        synchronized (LOCK) {
            File dir = directory(context);
            deleteContents(dir);
            writeText(new File(dir, TRIP), pretty(tripJson));
            try {
                JSONObject device = new JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("brand", Build.BRAND)
                        .put("model", Build.MODEL)
                        .put("device", Build.DEVICE)
                        .put("androidVersion", Build.VERSION.RELEASE)
                        .put("apiLevel", Build.VERSION.SDK_INT)
                        .put("appPackage", context.getPackageName())
                        .put("startedAtEpochMs", System.currentTimeMillis());
                writeText(new File(dir, DEVICE), device.toString(2));
            } catch (Exception ignored) {}
            log(context, "SESSION", "Navigation diagnostics session started");
        }
    }

    public static void saveRoute(Context context, String routeJson) {
        synchronized (LOCK) {
            writeText(new File(directory(context), ROUTE), pretty(routeJson));
        }
    }

    public static void log(Context context, String category, String message) {
        synchronized (LOCK) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(directory(context), LOG), true))) {
                writer.write(FORMAT.format(new Date()) + " [" + category + "] " + message);
                writer.newLine();
            } catch (Exception ignored) {}
        }
    }

    public static void exception(Context context, String where, Throwable error) {
        synchronized (LOCK) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            String text = FORMAT.format(new Date()) + " [" + where + "] " + error + "\n" + stack + "\n";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(directory(context), EXCEPTIONS), true))) {
                writer.write(text);
            } catch (Exception ignored) {}
            log(context, "ERROR", where + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    public static void state(Context context, boolean running, boolean workerAlive, long injections,
                             long lastInjectionElapsed, int segment, int totalSegments,
                             double latitude, double longitude, double speedMph,
                             String phase, String lastError) {
        synchronized (LOCK) {
            try {
                LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                boolean gpsEnabled = false;
                try { gpsEnabled = manager != null && manager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
                JSONObject state = new JSONObject()
                        .put("timestamp", System.currentTimeMillis())
                        .put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
                        .put("phase", phase == null ? "unknown" : phase)
                        .put("serviceRunning", running)
                        .put("workerAlive", workerAlive)
                        .put("gpsProviderEnabled", gpsEnabled)
                        .put("injectionCount", injections)
                        .put("lastInjectionAgeMs", lastInjectionElapsed <= 0 ? -1 : SystemClock.elapsedRealtime() - lastInjectionElapsed)
                        .put("segment", segment)
                        .put("totalSegments", totalSegments)
                        .put("latitude", latitude)
                        .put("longitude", longitude)
                        .put("speedMph", speedMph)
                        .put("lastError", lastError == null ? "" : lastError)
                        .put("memoryUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                writeText(new File(directory(context), STATE), state.toString(2));
            } catch (Exception ignored) {}
        }
    }

    public static String readLiveState(Context context) {
        return readText(new File(directory(context), STATE));
    }

    public static String readLogTail(Context context, int maxChars) {
        String all = readText(new File(directory(context), LOG));
        if (all.length() <= maxChars) return all;
        return all.substring(all.length() - maxChars);
    }

    public static File exportZip(Context context) throws Exception {
        synchronized (LOCK) {
            File source = directory(context);
            File exportDir = new File(context.getCacheDir(), "diagnostic_exports");
            if (!exportDir.exists()) exportDir.mkdirs();
            File zip = new File(exportDir, "MockDrive-Diagnostics-" + System.currentTimeMillis() + ".zip");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
                File[] files = source.listFiles();
                if (files != null) {
                    byte[] buffer = new byte[8192];
                    for (File file : files) {
                        if (!file.isFile()) continue;
                        out.putNextEntry(new ZipEntry(file.getName()));
                        try (FileInputStream in = new FileInputStream(file)) {
                            int n;
                            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                        }
                        out.closeEntry();
                    }
                }
            }
            return zip;
        }
    }

    private static String pretty(String json) {
        try { return new JSONObject(json == null ? "{}" : json).toString(2); }
        catch (Exception ignored) { return json == null ? "" : json; }
    }

    private static void writeText(File file, String text) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private static String readText(File file) {
        if (!file.exists()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 2_000_000)];
            int read = in.read(data);
            return read <= 0 ? "" : new String(data, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    private static void deleteContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) file.delete();
    }
}
