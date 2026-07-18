from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


service_path = Path("app/src/main/java/com/seannmichael/mockdrive/MockLocationService.java")
service = service_path.read_text()

service = replace_once(
    service,
    "import com.google.android.gms.location.FusedLocationProviderClient;\nimport com.google.android.gms.location.LocationServices;",
    "import com.google.android.gms.location.FusedLocationProviderClient;\nimport com.google.android.gms.location.LocationAvailability;\nimport com.google.android.gms.location.LocationServices;",
    "location availability import",
)

service = replace_once(
    service,
    '    private static final String FUSED_PROVIDER = "fused";\n\n',
    "",
    "remove synthetic fused provider label",
)

service = replace_once(
    service,
    "    private int configuredUpdateMs = 1000;\n",
    "    private int configuredUpdateMs = 1000;\n"
    "    private volatile boolean gpsProviderReady;\n"
    "    private volatile boolean fusedMockReady;\n"
    "    private volatile Boolean fusedLocationAvailable;\n"
    "    private volatile long fusedAvailabilityCheckedElapsedMs = -1;\n"
    "    private volatile String fusedAvailabilityError = \"\";\n"
    "    private volatile boolean fusedAvailabilityRequestInFlight;\n",
    "provider state fields",
)

old_enable = '''    private void enableProviders() throws Exception {
        log("PROVIDER", "Rebuilding GPS test provider");
        try {
            manager.removeTestProvider(LocationManager.GPS_PROVIDER);
            log("PROVIDER", "Existing GPS test provider removed");
        } catch (Exception e) {
            log("PROVIDER", "GPS provider removal ignored: " + e.getClass().getSimpleName());
        }
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        log("PROVIDER", "GPS test provider enabled=" + manager.isProviderEnabled(LocationManager.GPS_PROVIDER));

        long started = SystemClock.elapsedRealtime();
        Tasks.await(fusedClient.setMockMode(true), 5, TimeUnit.SECONDS);
        log("PROVIDER", "Fused mock mode enabled in " + (SystemClock.elapsedRealtime() - started) + " ms");
    }
'''

new_enable = '''    private void enableProviders() throws Exception {
        if (!gpsProviderReady) {
            log("PROVIDER", "Creating GPS test provider");
            try {
                manager.removeTestProvider(LocationManager.GPS_PROVIDER);
                log("PROVIDER", "Existing GPS test provider removed");
            } catch (Exception e) {
                log("PROVIDER", "GPS provider removal ignored: " + e.getClass().getSimpleName());
            }
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            gpsProviderReady = true;
            log("PROVIDER", "GPS test provider enabled=" + manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
        } else {
            try { manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true); }
            catch (Exception e) { log("PROVIDER", "GPS provider re-enable ignored: " + e.getClass().getSimpleName()); }
            log("PROVIDER", "Reusing existing GPS test provider between hold and drive");
        }

        if (!fusedMockReady) {
            long started = SystemClock.elapsedRealtime();
            Tasks.await(fusedClient.setMockMode(true), 5, TimeUnit.SECONDS);
            fusedMockReady = true;
            try {
                Tasks.await(fusedClient.flushLocations(), 3, TimeUnit.SECONDS);
                log("PROVIDER", "Fused pending locations flushed after mock mode enabled");
            } catch (Exception e) {
                log("PROVIDER", "Fused flush did not complete: " + e.getClass().getSimpleName());
            }
            log("PROVIDER", "Fused mock mode enabled in " + (SystemClock.elapsedRealtime() - started) + " ms");
        } else {
            log("PROVIDER", "Reusing existing Fused mock session between hold and drive");
        }
        refreshFusedAvailability();
    }
'''

service = replace_once(service, old_enable, new_enable, "provider setup")

service = replace_once(
    service,
    "        Location gpsInjected = buildLocation(LocationManager.GPS_PROVIDER, point, heading, speed, accuracy);\n"
    "        Location fusedInjected = buildLocation(FUSED_PROVIDER, point, heading, speed, accuracy);",
    "        Location gpsInjected = buildLocation(LocationManager.GPS_PROVIDER, point, heading, speed, accuracy);\n"
    "        // Fused mock input is still a GPS-quality fix. Keeping provider=\"gps\" avoids presenting it as a synthetic provider.\n"
    "        Location fusedInjected = new Location(gpsInjected);",
    "GPS-quality fused location",
)

service = replace_once(
    service,
    "        injectionCount++;\n        try { gpsReported = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }",
    "        refreshFusedAvailability();\n        injectionCount++;\n        try { gpsReported = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }",
    "availability refresh per update",
)

service = replace_once(
    service,
    '                    .put("fusedInjectionError", fusedError)\n'
    '                    .put("requestedLatitude", point.lat)',
    '                    .put("fusedInjectionError", fusedError)\n'
    '                    .put("fusedAvailabilityKnown", fusedLocationAvailable != null)\n'
    '                    .put("fusedLocationAvailable", fusedLocationAvailable != null && fusedLocationAvailable)\n'
    '                    .put("fusedAvailabilityAgeMs", fusedAvailabilityCheckedElapsedMs < 0 ? -1 : Math.max(0, SystemClock.elapsedRealtime() - fusedAvailabilityCheckedElapsedMs))\n'
    '                    .put("fusedAvailabilityError", fusedAvailabilityError)\n'
    '                    .put("gpsInjectedComplete", locationComplete(gpsInjected))\n'
    '                    .put("fusedInjectedComplete", locationComplete(fusedInjected))\n'
    '                    .put("gpsInjectedWallClockAgeMs", Math.max(0, System.currentTimeMillis() - gpsInjected.getTime()))\n'
    '                    .put("fusedInjectedWallClockAgeMs", Math.max(0, System.currentTimeMillis() - fusedInjected.getTime()))\n'
    '                    .put("gpsInjectedElapsedAgeMs", elapsedAgeMs(gpsInjected))\n'
    '                    .put("fusedInjectedElapsedAgeMs", elapsedAgeMs(fusedInjected))\n'
    '                    .put("requestedLatitude", point.lat)',
    "runtime quality fields",
)

service = replace_once(
    service,
    '                    " gpsSuccess=" + gpsSuccess + " fusedSuccess=" + fusedSuccess +\n'
    '                    " gpsDeltaMeters=" + gpsDelta + " fusedDeltaMeters=" + fusedDelta +\n',
    '                    " gpsSuccess=" + gpsSuccess + " fusedSuccess=" + fusedSuccess +\n'
    '                    " fusedAvailable=" + (fusedLocationAvailable == null ? "unknown" : fusedLocationAvailable) +\n'
    '                    " gpsDeltaMeters=" + gpsDelta + " fusedDeltaMeters=" + fusedDelta +\n',
    "heartbeat availability",
)

service = replace_once(
    service,
    "    private Location buildLocation(String provider, Point point, float heading, float speed, float accuracy) {\n",
    '''    private void refreshFusedAvailability() {
        if (fusedAvailabilityRequestInFlight) return;
        fusedAvailabilityRequestInFlight = true;
        try {
            fusedClient.getLocationAvailability().addOnCompleteListener(task -> {
                fusedAvailabilityRequestInFlight = false;
                fusedAvailabilityCheckedElapsedMs = SystemClock.elapsedRealtime();
                if (task.isSuccessful()) {
                    LocationAvailability availability = task.getResult();
                    fusedLocationAvailable = availability != null && availability.isLocationAvailable();
                    fusedAvailabilityError = "";
                } else {
                    Throwable error = task.getException();
                    fusedLocationAvailable = null;
                    fusedAvailabilityError = error == null ? "Availability task failed" : error.getClass().getSimpleName() + ": " + safe(error.getMessage());
                }
            });
        } catch (Throwable error) {
            fusedAvailabilityRequestInFlight = false;
            fusedAvailabilityCheckedElapsedMs = SystemClock.elapsedRealtime();
            fusedLocationAvailable = null;
            fusedAvailabilityError = error.getClass().getSimpleName() + ": " + safe(error.getMessage());
        }
    }

    private boolean locationComplete(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= 33) return location.isComplete();
        return location.getProvider() != null && location.hasAccuracy() && location.getTime() != 0 && location.getElapsedRealtimeNanos() != 0;
    }

    private long elapsedAgeMs(Location location) {
        if (location == null || location.getElapsedRealtimeNanos() <= 0) return -1;
        return Math.max(0, (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000L);
    }

    private Location buildLocation(String provider, Point point, float heading, float speed, float accuracy) {
''',
    "availability and completeness helpers",
)

old_stop = '''        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); }
        catch (Exception e) { log("PROVIDER", "GPS provider removal on stop: " + e.getClass().getSimpleName()); }
        try { Tasks.await(fusedClient.setMockMode(false), 3, TimeUnit.SECONDS); }
        catch (Exception e) { log("PROVIDER", "Fused mock disable on stop: " + e.getClass().getSimpleName()); }
        log("SERVICE", "Foreground service stopped; GPS provider removed and Fused mock mode disabled");
'''

new_stop = '''        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); }
        catch (Exception e) { log("PROVIDER", "GPS provider removal on stop: " + e.getClass().getSimpleName()); }
        gpsProviderReady = false;
        fusedMockReady = false;
        fusedLocationAvailable = null;
        fusedAvailabilityCheckedElapsedMs = -1;
        fusedAvailabilityError = "";
        try {
            fusedClient.setMockMode(false).addOnCompleteListener(task -> {
                if (task.isSuccessful()) log("PROVIDER", "Fused mock mode disabled");
                else log("PROVIDER", "Fused mock disable failed asynchronously");
            });
        } catch (Exception e) {
            log("PROVIDER", "Fused mock disable on stop: " + e.getClass().getSimpleName());
        }
        log("SERVICE", "Foreground service stopped; GPS provider removed and Fused mock shutdown requested");
'''

service = replace_once(service, old_stop, new_stop, "non-blocking provider shutdown")
service_path.write_text(service)


diag_path = Path("app/src/main/java/com/seannmichael/mockdrive/SimulationDiagnostics.java")
diag = diag_path.read_text()

old_location_json = '''        return new JSONObject()
                .put("provider", location.getProvider())
                .put("latitude", location.getLatitude())
                .put("longitude", location.getLongitude())
                .put("accuracyMeters", location.hasAccuracy() ? location.getAccuracy() : -1)
                .put("speedMps", location.hasSpeed() ? location.getSpeed() : -1)
                .put("speedMph", location.hasSpeed() ? location.getSpeed() * 2.2369362920544 : -1)
                .put("bearingDegrees", location.hasBearing() ? location.getBearing() : -1)
                .put("timeEpochMs", location.getTime())
                .put("elapsedRealtimeNanos", location.getElapsedRealtimeNanos())
                .put("isMock", Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider());
    }
'''

new_location_json = '''        return new JSONObject()
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
'''

diag = replace_once(diag, old_location_json, new_location_json, "location JSON quality data")
diag_path.write_text(diag)


history_path = Path("app/src/main/java/com/seannmichael/mockdrive/HistoryDetailActivity.java")
history = history_path.read_text()

history = replace_once(
    history,
    '        out.append("\\nFused injection: ").append(state.optBoolean("fusedInjectionSucceeded", false) ? "Success" : "FAILED");\n'
    '        out.append("\\nGPS provider enabled: ").append(state.optBoolean("gpsProviderEnabled", false));',
    '        out.append("\\nFused injection: ").append(state.optBoolean("fusedInjectionSucceeded", false) ? "Success" : "FAILED");\n'
    '        out.append("\\nFused location available: ");\n'
    '        if (!state.optBoolean("fusedAvailabilityKnown", false)) out.append("Unknown");\n'
    '        else out.append(state.optBoolean("fusedLocationAvailable", false));\n'
    '        out.append("\\nFused availability age: ").append(state.optLong("fusedAvailabilityAgeMs", -1)).append(" ms");\n'
    '        out.append("\\nGPS injected location complete: ").append(state.optBoolean("gpsInjectedComplete", false));\n'
    '        out.append("\\nFused injected location complete: ").append(state.optBoolean("fusedInjectedComplete", false));\n'
    '        out.append("\\nGPS provider enabled: ").append(state.optBoolean("gpsProviderEnabled", false));',
    "readable availability state",
)

history = replace_once(
    history,
    '        return String.format(Locale.US, "%.7f, %.7f at %.2f mph / %.2f°",\n'
    '                location.optDouble("latitude"),',
    '        return String.format(Locale.US, "%s %.7f, %.7f at %.2f mph / %.2f°",\n'
    '                location.optString("provider", "unknown"),\n'
    '                location.optDouble("latitude"),',
    "show reported provider",
)

history_path.write_text(history)
print("Applied location quality and Fused availability changes.")
