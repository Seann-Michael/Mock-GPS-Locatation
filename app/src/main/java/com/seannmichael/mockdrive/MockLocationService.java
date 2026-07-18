package com.seannmichael.mockdrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MockLocationService extends Service {
    public static final String ACTION_START = "com.seannmichael.mockdrive.START";
    public static final String ACTION_STOP = "com.seannmichael.mockdrive.STOP";
    public static final String ACTION_PAUSE = "com.seannmichael.mockdrive.PAUSE";
    public static final String ACTION_RESUME = "com.seannmichael.mockdrive.RESUME";
    public static final String ACTION_TELEPORT = "com.seannmichael.mockdrive.TELEPORT";
    public static final String EXTRA_TRIP = "trip_json";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";

    private static final String CHANNEL = "mock_drive";
    private static final int NOTICE = 42;
    private static final String FUSED_PROVIDER = "fused";

    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean completed;
    private Thread worker;
    private LocationManager manager;
    private FusedLocationProviderClient fusedClient;
    private Point heldPoint;
    private String historyId = "";
    private String currentTripId = "";
    private long injectionCount;
    private int currentSegment;
    private int totalSegments;
    private String phase = "idle";
    private int configuredUpdateMs = 1000;

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            log("SERVICE", "Stop requested by user or app");
            stopEverything(true);
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            paused = true;
            phase = "paused";
            log("SERVICE", "Paused");
            updateNotice("Paused");
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            paused = false;
            phase = "driving";
            log("SERVICE", "Resumed");
            updateNotice("Resumed");
            return START_STICKY;
        }
        if (ACTION_TELEPORT.equals(action)) {
            startForeground(NOTICE, notice("Holding mock location"));
            teleport(intent.getDoubleExtra(EXTRA_LAT, 0), intent.getDoubleExtra(EXTRA_LON, 0));
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            startForeground(NOTICE, notice("Preparing route"));
            startTrip(intent.getStringExtra(EXTRA_TRIP));
        }
        return START_STICKY;
    }

    private void teleport(double lat, double lon) {
        stopWorker();
        phase = "holding_start";
        completed = false;
        heldPoint = new Point(lat, lon);
        running = true;
        worker = new Thread(() -> {
            try {
                enableProviders();
                log("THREAD", "Start-location hold worker started");
                while (running && heldPoint != null) {
                    JSONObject runtime = baseRuntime("holding_start")
                            .put("requestedSpeedMph", 0)
                            .put("requestedSpeedMps", 0)
                            .put("requestedBearingDegrees", 0)
                            .put("holdLatitude", heldPoint.lat)
                            .put("holdLongitude", heldPoint.lon);
                    injectAndRecord(heldPoint, 0f, 0f, 3f, runtime);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("THREAD", "Start-location hold interrupted");
            } catch (Throwable e) {
                error("Start-location hold", e);
                updateNotice("Mock location failed: " + safe(e.getMessage()));
            }
        }, "mock-hold");
        worker.setUncaughtExceptionHandler((thread, throwable) -> error("Uncaught on " + thread.getName(), throwable));
        worker.start();
    }

    private void startTrip(String tripJson) {
        stopWorker();
        worker = new Thread(() -> {
            try {
                JSONObject trip = new JSONObject(tripJson == null ? "{}" : tripJson);
                currentTripId = trip.optString("id", "");
                JSONObject history = HistoryStore.findByTrip(this, currentTripId);
                if (history == null) history = HistoryStore.begin(this, trip);
                historyId = history == null ? "" : history.optString("historyId", "");
                injectionCount = 0;
                currentSegment = 0;
                totalSegments = 0;
                completed = false;
                phase = "routing";
                if (!historyId.isEmpty()) SimulationDiagnostics.begin(this, historyId, trip);
                log("SERVICE", "Trip worker starting tripId=" + currentTripId + " thread=" + Thread.currentThread().getName());
                if (!currentTripId.isEmpty()) TripStore.updateStatus(this, currentTripId, "routing");

                JSONArray waypoints = trip.getJSONArray("waypoints");
                log("ROUTE", "Requesting route for " + waypoints.length() + " waypoints");
                long routeStarted = SystemClock.elapsedRealtime();
                JSONArray coordinates = RouteEngine.roadRoute(waypoints);
                log("ROUTE", "Route received in " + (SystemClock.elapsedRealtime() - routeStarted) + " ms with " + coordinates.length() + " points");
                if (!historyId.isEmpty()) SimulationDiagnostics.saveRoute(this, historyId, coordinates);

                List<Point> points = parse(coordinates);
                if (points.size() < 2) throw new Exception("Route is empty");
                totalSegments = points.size() - 1;
                double[] cumulative = cumulativeDistances(points);
                double totalRouteMeters = cumulative[cumulative.length - 1];
                if (!historyId.isEmpty()) SimulationDiagnostics.saveRoutePlan(this, historyId, routePlan(points, cumulative));
                log("ROUTE", String.format(Locale.US, "Route plan has %d segments and %.1f meters", totalSegments, totalRouteMeters));

                enableProviders();
                running = true;
                paused = false;
                heldPoint = null;
                phase = "driving";
                if (!currentTripId.isEmpty()) TripStore.updateStatus(this, currentTripId, "active");

                double averageMph = clamp(trip.optDouble("averageSpeedMph", 35), 1, 150);
                double variation = clamp(trip.optDouble("speedVariationPercent", 8), 0, 80) / 100.0;
                configuredUpdateMs = (int) clamp(trip.optInt("gpsUpdateIntervalMs", 1000), 200, 10000);
                boolean randomStops = trip.optBoolean("randomStops", false);
                int randomStopChance = trip.optInt("randomStopChancePercent", 2);
                int randomStopMax = trip.optInt("randomStopMaxSeconds", 20);
                boolean hold = trip.optBoolean("holdAtDestination", true);
                float accuracy = (float) clamp(trip.optDouble("accuracyMeters", 3), 1, 100);
                Random random = new Random();
                log("CONFIG", "speedMph=" + averageMph + " variation=" + variation + " updateMs=" + configuredUpdateMs + " holdAtDestination=" + hold + " providers=gps+fused");

                int segment = 0;
                double onSegment = 0;
                Point current = points.get(0);
                JSONObject initial = baseRuntime("driving")
                        .put("routeProgressPercent", 0)
                        .put("distanceTraveledMeters", 0)
                        .put("distanceRemainingMeters", totalRouteMeters)
                        .put("requestedSpeedMph", 0)
                        .put("requestedSpeedMps", 0)
                        .put("requestedBearingDegrees", 0);
                injectAndRecord(current, 0f, 0f, accuracy, initial);

                while (running && segment < points.size() - 1) {
                    while (paused && running) {
                        JSONObject pausedRuntime = baseRuntime("paused")
                                .put("requestedSpeedMph", 0)
                                .put("requestedSpeedMps", 0)
                                .put("requestedBearingDegrees", 0);
                        injectAndRecord(current, 0f, 0f, accuracy, pausedRuntime);
                        Thread.sleep(500);
                    }
                    if (!running) break;

                    if (randomStops && random.nextInt(100) < randomStopChance) {
                        int seconds = 1 + random.nextInt(Math.max(1, randomStopMax));
                        log("STOP", "Random traffic stop for " + seconds + " seconds at segment " + segment);
                        updateNotice("Traffic stop: " + seconds + " sec");
                        for (int s = 0; s < seconds && running; s++) {
                            JSONObject stopRuntime = baseRuntime("traffic_stop")
                                    .put("stopSecondsRemaining", seconds - s)
                                    .put("requestedSpeedMph", 0)
                                    .put("requestedSpeedMps", 0)
                                    .put("requestedBearingDegrees", 0);
                            injectAndRecord(current, 0f, 0f, accuracy, stopRuntime);
                            Thread.sleep(1000);
                        }
                    }

                    long loopStarted = SystemClock.elapsedRealtime();
                    double mph = averageMph * (1 + ((random.nextDouble() * 2 - 1) * variation));
                    double metersPerSecond = mph * 0.44704;
                    double step = metersPerSecond * configuredUpdateMs / 1000.0;

                    Point from = points.get(segment);
                    Point to = points.get(segment + 1);
                    double length = distance(from, to);
                    if (length < .2) {
                        segment++;
                        onSegment = 0;
                        continue;
                    }

                    onSegment += step;
                    while (onSegment >= length && segment < points.size() - 1) {
                        onSegment -= length;
                        segment++;
                        if (segment >= points.size() - 1) break;
                        from = points.get(segment);
                        to = points.get(segment + 1);
                        length = distance(from, to);
                    }
                    if (segment >= points.size() - 1) break;

                    from = points.get(segment);
                    to = points.get(segment + 1);
                    length = distance(from, to);
                    double fraction = Math.min(1, onSegment / Math.max(.1, length));
                    current = interpolate(from, to, fraction);
                    currentSegment = segment;
                    float heading = bearing(from, to);
                    double traveledMeters = cumulative[segment] + Math.min(onSegment, length);
                    double remainingMeters = Math.max(0, totalRouteMeters - traveledMeters);
                    double progress = totalRouteMeters <= 0 ? 0 : traveledMeters * 100.0 / totalRouteMeters;

                    JSONObject runtime = baseRuntime("driving")
                            .put("segment", segment)
                            .put("totalSegments", totalSegments)
                            .put("segmentStart", pointJson(from))
                            .put("segmentEnd", pointJson(to))
                            .put("segmentLengthMeters", length)
                            .put("distanceIntoSegmentMeters", onSegment)
                            .put("segmentFraction", fraction)
                            .put("requestedStepMeters", step)
                            .put("requestedSpeedMph", mph)
                            .put("requestedSpeedMps", metersPerSecond)
                            .put("requestedBearingDegrees", heading)
                            .put("distanceTraveledMeters", traveledMeters)
                            .put("distanceRemainingMeters", remainingMeters)
                            .put("routeProgressPercent", progress)
                            .put("loopStartedElapsedMs", loopStarted);
                    injectAndRecord(current, heading, (float) metersPerSecond, accuracy, runtime);
                    updateNotice("Driving " + Math.round(mph) + " mph");

                    long workMs = SystemClock.elapsedRealtime() - loopStarted;
                    long sleepMs = Math.max(1, configuredUpdateMs - workMs);
                    if (workMs > configuredUpdateMs) {
                        warning("Movement update took " + workMs + " ms, longer than configured interval " + configuredUpdateMs + " ms");
                    }
                    Thread.sleep(sleepMs);

                    for (int i = 1; i < waypoints.length() - 1; i++) {
                        JSONObject w = waypoints.getJSONObject(i);
                        Point stop = new Point(w.getDouble("latitude"), w.getDouble("longitude"));
                        if (!w.optBoolean("visited", false) && distance(current, stop) < Math.max(20, metersPerSecond * 2)) {
                            w.put("visited", true);
                            int seconds = Math.max(0, w.optInt("stopSeconds", 0));
                            log("WAYPOINT", "Reached waypoint " + i + ", holding " + seconds + " seconds");
                            for (int s = 0; s < seconds && running; s++) {
                                updateNotice("Waypoint stop: " + (seconds - s) + " sec");
                                JSONObject waypointRuntime = baseRuntime("waypoint_stop")
                                        .put("waypointIndex", i)
                                        .put("stopSecondsRemaining", seconds - s)
                                        .put("requestedSpeedMph", 0)
                                        .put("requestedSpeedMps", 0)
                                        .put("requestedBearingDegrees", 0);
                                injectAndRecord(current, 0f, 0f, accuracy, waypointRuntime);
                                Thread.sleep(1000);
                            }
                        }
                    }
                }

                if (running) {
                    current = points.get(points.size() - 1);
                    phase = "destination";
                    JSONObject destinationRuntime = baseRuntime("destination")
                            .put("segment", totalSegments)
                            .put("totalSegments", totalSegments)
                            .put("routeProgressPercent", 100)
                            .put("distanceTraveledMeters", totalRouteMeters)
                            .put("distanceRemainingMeters", 0)
                            .put("requestedSpeedMph", 0)
                            .put("requestedSpeedMps", 0)
                            .put("requestedBearingDegrees", 0);
                    injectAndRecord(current, 0f, 0f, accuracy, destinationRuntime);
                    completed = true;
                    if (!currentTripId.isEmpty()) TripStore.updateStatus(this, currentTripId, "completed");
                    log("COMPLETE", "Destination reached after " + injectionCount + " injections");
                    updateNotice("Destination reached");
                    if (hold) {
                        heldPoint = current;
                        phase = "holding_destination";
                        while (running) {
                            JSONObject holdRuntime = baseRuntime("holding_destination")
                                    .put("routeProgressPercent", 100)
                                    .put("distanceRemainingMeters", 0)
                                    .put("requestedSpeedMph", 0)
                                    .put("requestedSpeedMps", 0)
                                    .put("requestedBearingDegrees", 0);
                            injectAndRecord(heldPoint, 0f, 0f, accuracy, holdRuntime);
                            Thread.sleep(1000);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("THREAD", "Trip worker interrupted phase=" + phase + " running=" + running);
            } catch (SecurityException e) {
                error("Trip security", e);
                updateNotice("Select Mock Drive as mock location app");
                if (!currentTripId.isEmpty()) TripStore.updateStatus(this, currentTripId, "failed");
            } catch (Throwable e) {
                error("Trip failure phase=" + phase, e);
                updateNotice("Trip failed: " + safe(e.getMessage()));
                if (!currentTripId.isEmpty()) TripStore.updateStatus(this, currentTripId, "failed");
            } finally {
                log("THREAD", "Trip worker exited phase=" + phase + " running=" + running + " injections=" + injectionCount);
                if (!historyId.isEmpty()) HistoryStore.attachDiagnostics(this, historyId, SimulationDiagnostics.summary(this, historyId));
            }
        }, "mock-trip");
        worker.setUncaughtExceptionHandler((thread, throwable) -> error("Uncaught on " + thread.getName(), throwable));
        worker.start();
    }

    private void enableProviders() throws Exception {
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

    private void injectAndRecord(Point point, float heading, float speed, float accuracy, JSONObject runtime) {
        long started = SystemClock.elapsedRealtime();
        Location gpsInjected = buildLocation(LocationManager.GPS_PROVIDER, point, heading, speed, accuracy);
        Location fusedInjected = buildLocation(FUSED_PROVIDER, point, heading, speed, accuracy);
        Location gpsReported = null;
        Location fusedReported = null;
        boolean gpsSuccess = false;
        boolean fusedSuccess = false;
        String gpsError = "";
        String fusedError = "";

        try {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsInjected);
            gpsSuccess = true;
        } catch (Throwable e) {
            gpsError = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
            error("GPS injection", e);
        }

        try {
            Tasks.await(fusedClient.setMockLocation(fusedInjected), 3, TimeUnit.SECONDS);
            fusedSuccess = true;
        } catch (Throwable e) {
            fusedError = e.getClass().getSimpleName() + ": " + safe(e.getMessage());
            error("Fused injection", e);
        }

        injectionCount++;
        try { gpsReported = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (Throwable e) { error("GPS readback", e); }
        try { fusedReported = Tasks.await(fusedClient.getLastLocation(), 3, TimeUnit.SECONDS); }
        catch (Throwable e) { error("Fused readback", e); }

        boolean gpsEnabled = false;
        try { gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) {}

        try {
            runtime.put("count", injectionCount)
                    .put("phase", phase)
                    .put("segment", currentSegment)
                    .put("totalSegments", totalSegments)
                    .put("gpsProviderEnabled", gpsEnabled)
                    .put("workerAlive", worker != null && worker.isAlive())
                    .put("threadName", Thread.currentThread().getName())
                    .put("gpsInjectionSucceeded", gpsSuccess)
                    .put("fusedInjectionSucceeded", fusedSuccess)
                    .put("gpsInjectionError", gpsError)
                    .put("fusedInjectionError", fusedError)
                    .put("requestedLatitude", point.lat)
                    .put("requestedLongitude", point.lon)
                    .put("requestedAccuracyMeters", accuracy)
                    .put("configuredUpdateIntervalMs", configuredUpdateMs)
                    .put("injectionWorkDurationMs", SystemClock.elapsedRealtime() - started);
        } catch (Exception ignored) {}

        if (!historyId.isEmpty()) {
            SimulationDiagnostics.injection(this, historyId, runtime, gpsInjected, gpsReported, fusedInjected, fusedReported);
        }

        float gpsDelta = gpsReported == null ? -1 : gpsInjected.distanceTo(gpsReported);
        float fusedDelta = fusedReported == null ? -1 : fusedInjected.distanceTo(fusedReported);
        if (injectionCount == 1 || injectionCount % 10 == 0) {
            log("HEARTBEAT", "injection=" + injectionCount + " segment=" + currentSegment + "/" + totalSegments +
                    " gpsSuccess=" + gpsSuccess + " fusedSuccess=" + fusedSuccess +
                    " gpsDeltaMeters=" + gpsDelta + " fusedDeltaMeters=" + fusedDelta +
                    " workMs=" + (SystemClock.elapsedRealtime() - started));
        }
        if (!fusedSuccess) warning("Fused location injection failed at update " + injectionCount + ": " + fusedError);
        if (fusedReported == null) warning("Fused provider returned no location at update " + injectionCount);
        else if (fusedDelta > 5) warning("Fused provider differs from requested location by " + fusedDelta + " meters at update " + injectionCount);
    }

    private Location buildLocation(String provider, Point point, float heading, float speed, float accuracy) {
        Location location = new Location(provider);
        location.setLatitude(point.lat);
        location.setLongitude(point.lon);
        location.setAccuracy(accuracy);
        location.setAltitude(0);
        location.setBearing(heading);
        location.setSpeed(speed);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (Build.VERSION.SDK_INT >= 26) {
            location.setBearingAccuracyDegrees(3f);
            location.setSpeedAccuracyMetersPerSecond(.5f);
            location.setVerticalAccuracyMeters(accuracy);
        }
        return location;
    }

    private JSONObject baseRuntime(String runtimePhase) throws Exception {
        return new JSONObject()
                .put("phase", runtimePhase)
                .put("serviceRunning", running)
                .put("workerAlive", worker != null && worker.isAlive())
                .put("threadName", Thread.currentThread().getName())
                .put("segment", currentSegment)
                .put("totalSegments", totalSegments);
    }

    private JSONArray routePlan(List<Point> points, double[] cumulative) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < points.size() - 1; i++) {
            Point from = points.get(i);
            Point to = points.get(i + 1);
            out.put(new JSONObject()
                    .put("segment", i)
                    .put("from", pointJson(from))
                    .put("to", pointJson(to))
                    .put("lengthMeters", distance(from, to))
                    .put("headingDegrees", bearing(from, to))
                    .put("distanceFromStartMeters", cumulative[i])
                    .put("distanceAtSegmentEndMeters", cumulative[i + 1]));
        }
        return out;
    }

    private JSONObject pointJson(Point point) throws Exception {
        return new JSONObject().put("latitude", point.lat).put("longitude", point.lon);
    }

    private double[] cumulativeDistances(List<Point> points) {
        double[] out = new double[points.size()];
        for (int i = 1; i < points.size(); i++) out[i] = out[i - 1] + distance(points.get(i - 1), points.get(i));
        return out;
    }

    private void stopEverything(boolean userRequested) {
        boolean wasCompleted = completed;
        stopWorker();
        phase = "stopped";
        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); }
        catch (Exception e) { log("PROVIDER", "GPS provider removal on stop: " + e.getClass().getSimpleName()); }
        try { Tasks.await(fusedClient.setMockMode(false), 3, TimeUnit.SECONDS); }
        catch (Exception e) { log("PROVIDER", "Fused mock disable on stop: " + e.getClass().getSimpleName()); }
        log("SERVICE", "Foreground service stopped; GPS provider removed and Fused mock mode disabled");
        if (userRequested && !wasCompleted && !currentTripId.isEmpty()) {
            TripStore.updateStatus(this, currentTripId, "stopped");
        }
        if (!historyId.isEmpty()) {
            HistoryStore.attachDiagnostics(this, historyId, SimulationDiagnostics.summary(this, historyId));
        }
        stopForeground(true);
        stopSelf();
    }

    private void stopWorker() {
        running = false;
        paused = false;
        heldPoint = null;
        Thread current = worker;
        worker = null;
        if (current != null) current.interrupt();
    }

    private void log(String category, String message) {
        if (!historyId.isEmpty()) SimulationDiagnostics.event(this, historyId, category, message);
    }

    private void warning(String message) {
        if (!historyId.isEmpty()) SimulationDiagnostics.warning(this, historyId, message);
    }

    private void error(String where, Throwable error) {
        if (!historyId.isEmpty()) SimulationDiagnostics.exception(this, historyId, where, error);
    }

    private List<Point> parse(JSONArray array) throws Exception {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONArray coordinate = array.getJSONArray(i);
            out.add(new Point(coordinate.getDouble(1), coordinate.getDouble(0)));
        }
        return out;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Mock Drive", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notice(String text) {
        Intent open = historyId.isEmpty() ? new Intent(this, NavigationRunActivity.class) : new Intent(this, HistoryDetailActivity.class).putExtra("history_id", historyId);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setContentTitle("Mock Drive")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    private void updateNotice(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE, notice(text));
    }

    private static String safe(String value) { return value == null || value.trim().isEmpty() ? "Unknown error" : value; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static double distance(Point a, Point b) {
        double earth = 6371000;
        double p1 = Math.toRadians(a.lat);
        double p2 = Math.toRadians(b.lat);
        double dp = Math.toRadians(b.lat - a.lat);
        double dl = Math.toRadians(b.lon - a.lon);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return earth * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static Point interpolate(Point a, Point b, double fraction) {
        return new Point(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction);
    }

    private static float bearing(Point a, Point b) {
        double p1 = Math.toRadians(a.lat);
        double p2 = Math.toRadians(b.lat);
        double dl = Math.toRadians(b.lon - a.lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class Point {
        final double lat;
        final double lon;
        Point(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
}
