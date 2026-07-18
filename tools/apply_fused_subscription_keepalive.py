from pathlib import Path

path = Path("app/src/main/java/com/seannmichael/mockdrive/MockLocationService.java")
text = path.read_text()

if "fusedKeepaliveCallbackCount" in text:
    print("Fused subscription keepalive already applied")
    raise SystemExit(0)

replacements = [
    (
        "import android.os.IBinder;\nimport android.os.SystemClock;",
        "import android.os.IBinder;\nimport android.os.Looper;\nimport android.os.SystemClock;",
    ),
    (
        "import com.google.android.gms.location.FusedLocationProviderClient;\nimport com.google.android.gms.location.LocationAvailability;\nimport com.google.android.gms.location.LocationServices;",
        "import com.google.android.gms.location.FusedLocationProviderClient;\nimport com.google.android.gms.location.LocationAvailability;\nimport com.google.android.gms.location.LocationCallback;\nimport com.google.android.gms.location.LocationRequest;\nimport com.google.android.gms.location.LocationResult;\nimport com.google.android.gms.location.LocationServices;\nimport com.google.android.gms.location.Priority;",
    ),
    (
        "    private volatile boolean fusedAvailabilityRequestInFlight;",
        "    private volatile boolean fusedAvailabilityRequestInFlight;\n"
        "    private LocationCallback fusedKeepaliveCallback;\n"
        "    private volatile boolean fusedKeepaliveRegistered;\n"
        "    private volatile long fusedKeepaliveCallbackCount;\n"
        "    private volatile long fusedKeepaliveLastCallbackElapsedMs = -1;\n"
        "    private volatile Location fusedKeepaliveLastLocation;\n"
        "    private volatile String fusedKeepaliveError = \"\";",
    ),
    (
        "        refreshFusedAvailability();\n    }\n\n    private void injectAndRecord",
        "        ensureFusedKeepalive();\n"
        "        refreshFusedAvailability();\n"
        "    }\n\n"
        "    private void injectAndRecord",
    ),
    (
        "        try { fusedReported = Tasks.await(fusedClient.getLastLocation(), 3, TimeUnit.SECONDS); }\n"
        "        catch (Throwable e) { error(\"Fused readback\", e); }\n\n"
        "        boolean gpsEnabled = false;",
        "        try { fusedReported = Tasks.await(fusedClient.getLastLocation(), 3, TimeUnit.SECONDS); }\n"
        "        catch (Throwable e) { error(\"Fused readback\", e); }\n\n"
        "        Location callbackLocation = fusedKeepaliveLastLocation == null ? null : new Location(fusedKeepaliveLastLocation);\n"
        "        long callbackAgeMs = fusedKeepaliveLastCallbackElapsedMs < 0 ? -1 :\n"
        "                Math.max(0, SystemClock.elapsedRealtime() - fusedKeepaliveLastCallbackElapsedMs);\n"
        "        float callbackDeltaMeters = callbackLocation == null ? -1 : fusedInjected.distanceTo(callbackLocation);\n\n"
        "        boolean gpsEnabled = false;",
    ),
    (
        "                    .put(\"fusedAvailabilityError\", fusedAvailabilityError)\n"
        "                    .put(\"gpsInjectedComplete\", locationComplete(gpsInjected))",
        "                    .put(\"fusedAvailabilityError\", fusedAvailabilityError)\n"
        "                    .put(\"fusedKeepaliveRegistered\", fusedKeepaliveRegistered)\n"
        "                    .put(\"fusedKeepaliveCallbackCount\", fusedKeepaliveCallbackCount)\n"
        "                    .put(\"fusedKeepaliveLastCallbackAgeMs\", callbackAgeMs)\n"
        "                    .put(\"fusedKeepaliveLastCallbackProvider\", callbackLocation == null ? \"\" : callbackLocation.getProvider())\n"
        "                    .put(\"fusedKeepaliveLastCallbackDistanceMeters\", callbackDeltaMeters)\n"
        "                    .put(\"fusedKeepaliveError\", fusedKeepaliveError)\n"
        "                    .put(\"gpsInjectedComplete\", locationComplete(gpsInjected))",
    ),
    (
        "                    \" fusedAvailable=\" + (fusedLocationAvailable == null ? \"unknown\" : fusedLocationAvailable) +\n"
        "                    \" gpsDeltaMeters=\" + gpsDelta + \" fusedDeltaMeters=\" + fusedDelta +",
        "                    \" fusedAvailable=\" + (fusedLocationAvailable == null ? \"unknown\" : fusedLocationAvailable) +\n"
        "                    \" callbackRegistered=\" + fusedKeepaliveRegistered +\n"
        "                    \" callbackCount=\" + fusedKeepaliveCallbackCount +\n"
        "                    \" callbackAgeMs=\" + callbackAgeMs +\n"
        "                    \" gpsDeltaMeters=\" + gpsDelta + \" fusedDeltaMeters=\" + fusedDelta +",
    ),
    (
        "        if (fusedReported == null) warning(\"Fused provider returned no location at update \" + injectionCount);\n"
        "        else if (fusedDelta > 5) warning(\"Fused provider differs from requested location by \" + fusedDelta + \" meters at update \" + injectionCount);\n"
        "    }\n\n"
        "    private void refreshFusedAvailability()",
        "        if (fusedReported == null) warning(\"Fused provider returned no location at update \" + injectionCount);\n"
        "        else if (fusedDelta > 5) warning(\"Fused provider differs from requested location by \" + fusedDelta + \" meters at update \" + injectionCount);\n"
        "        if (fusedKeepaliveRegistered && injectionCount > 5 && callbackAgeMs > 3000) {\n"
        "            warning(\"Fused subscription callback is stale by \" + callbackAgeMs + \" ms at update \" + injectionCount);\n"
        "        }\n"
        "    }\n\n"
        "    private void refreshFusedAvailability()",
    ),
    (
        "    private boolean locationComplete(Location location) {",
        "    private void ensureFusedKeepalive() {\n"
        "        if (fusedKeepaliveRegistered) return;\n"
        "        if (fusedKeepaliveCallback == null) {\n"
        "            fusedKeepaliveCallback = new LocationCallback() {\n"
        "                @Override public void onLocationResult(LocationResult result) {\n"
        "                    Location location = result == null ? null : result.getLastLocation();\n"
        "                    if (location == null) return;\n"
        "                    fusedKeepaliveLastLocation = new Location(location);\n"
        "                    fusedKeepaliveLastCallbackElapsedMs = SystemClock.elapsedRealtime();\n"
        "                    fusedKeepaliveCallbackCount++;\n"
        "                }\n\n"
        "                @Override public void onLocationAvailability(LocationAvailability availability) {\n"
        "                    fusedLocationAvailable = availability != null && availability.isLocationAvailable();\n"
        "                    fusedAvailabilityCheckedElapsedMs = SystemClock.elapsedRealtime();\n"
        "                    fusedAvailabilityError = \"\";\n"
        "                }\n"
        "            };\n"
        "        }\n"
        "        try {\n"
        "            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L).build();\n"
        "            fusedClient.requestLocationUpdates(request, fusedKeepaliveCallback, Looper.getMainLooper())\n"
        "                    .addOnCompleteListener(task -> {\n"
        "                        if (task.isSuccessful()) {\n"
        "                            fusedKeepaliveRegistered = true;\n"
        "                            fusedKeepaliveError = \"\";\n"
        "                            log(\"PROVIDER\", \"High-accuracy Fused subscription keepalive registered\");\n"
        "                        } else {\n"
        "                            Throwable error = task.getException();\n"
        "                            fusedKeepaliveRegistered = false;\n"
        "                            fusedKeepaliveError = error == null ? \"Registration failed\" :\n"
        "                                    error.getClass().getSimpleName() + \": \" + safe(error.getMessage());\n"
        "                            warning(\"Fused subscription keepalive registration failed: \" + fusedKeepaliveError);\n"
        "                        }\n"
        "                    });\n"
        "        } catch (Throwable error) {\n"
        "            fusedKeepaliveRegistered = false;\n"
        "            fusedKeepaliveError = error.getClass().getSimpleName() + \": \" + safe(error.getMessage());\n"
        "            warning(\"Fused subscription keepalive setup failed: \" + fusedKeepaliveError);\n"
        "        }\n"
        "    }\n\n"
        "    private void stopFusedKeepalive() {\n"
        "        LocationCallback callback = fusedKeepaliveCallback;\n"
        "        fusedKeepaliveRegistered = false;\n"
        "        if (callback != null) {\n"
        "            try {\n"
        "                fusedClient.removeLocationUpdates(callback).addOnCompleteListener(task ->\n"
        "                        log(\"PROVIDER\", task.isSuccessful() ?\n"
        "                                \"Fused subscription keepalive removed\" :\n"
        "                                \"Fused subscription keepalive removal failed\"));\n"
        "            } catch (Throwable error) {\n"
        "                log(\"PROVIDER\", \"Fused subscription keepalive removal error: \" + error.getClass().getSimpleName());\n"
        "            }\n"
        "        }\n"
        "    }\n\n"
        "    private boolean locationComplete(Location location) {",
    ),
    (
        "        stopWorker();\n        phase = \"stopped\";",
        "        stopWorker();\n        phase = \"stopped\";\n        stopFusedKeepalive();",
    ),
    (
        "        fusedAvailabilityError = \"\";\n        try {",
        "        fusedAvailabilityError = \"\";\n"
        "        fusedKeepaliveCallbackCount = 0;\n"
        "        fusedKeepaliveLastCallbackElapsedMs = -1;\n"
        "        fusedKeepaliveLastLocation = null;\n"
        "        fusedKeepaliveError = \"\";\n"
        "        try {",
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Required source block not found:\n{old[:180]}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Applied Fused continuous-subscription keepalive and callback diagnostics")
