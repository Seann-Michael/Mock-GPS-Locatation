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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean providerReady;
    private volatile long injectionCount;
    private volatile long lastInjectionElapsed;
    private volatile int currentSegment;
    private volatile int totalSegments;
    private volatile double currentLat;
    private volatile double currentLon;
    private volatile double currentSpeedMph;
    private volatile String phase = "idle";
    private volatile String lastError = "";
    private Thread worker;
    private LocationManager manager;
    private Point heldPoint;

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
        DiagnosticLogger.log(this, "SERVICE", "onCreate");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        DiagnosticLogger.log(this, "SERVICE", "onStartCommand action=" + action + " startId=" + startId);
        if (ACTION_STOP.equals(action)) { stopEverything(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(action)) { paused = true; phase = "paused"; updateNotice("Paused"); writeState(); return START_STICKY; }
        if (ACTION_RESUME.equals(action)) { paused = false; phase = "driving"; updateNotice("Resumed"); writeState(); return START_STICKY; }
        if (ACTION_TELEPORT.equals(action)) {
            startForeground(NOTICE, notice("Holding mock location"));
            teleport(intent.getDoubleExtra(EXTRA_LAT, 0), intent.getDoubleExtra(EXTRA_LON, 0));
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String tripJson = intent.getStringExtra(EXTRA_TRIP);
            DiagnosticLogger.beginTrip(this, tripJson);
            startForeground(NOTICE, notice("Preparing route"));
            startTrip(tripJson);
        }
        return START_STICKY;
    }

    private void teleport(double lat, double lon) {
        stopWorker();
        phase = "holding_start";
        lastError = "";
        currentLat = lat;
        currentLon = lon;
        try {
            ensureProvider();
            heldPoint = new Point(lat, lon);
            running = true;
            diagnosticInject(heldPoint, 0f, 0f, 3f);
            worker = new Thread(() -> {
                try {
                    while (running && heldPoint != null) {
                        diagnosticInject(heldPoint, 0f, 0f, 3f);
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    DiagnosticLogger.log(this, "THREAD", "Start-location hold interrupted");
                } catch (Throwable e) {
                    recordFailure("teleport worker", e);
                } finally {
                    writeState();
                }
            }, "mock-hold");
            worker.setUncaughtExceptionHandler((thread, error) -> recordFailure("uncaught " + thread.getName(), error));
            worker.start();
            writeState();
        } catch (SecurityException e) {
            recordFailure("teleport security", e);
            updateNotice("Select Mock Drive as mock location app");
        }
    }

    private void startTrip(String tripJson) {
        stopWorker();
        phase = "routing";
        lastError = "";
        injectionCount = 0;
        lastInjectionElapsed = 0;
        currentSegment = 0;
        totalSegments = 0;
        worker = new Thread(() -> {
            String id = "";
            try {
                JSONObject trip = new JSONObject(tripJson == null ? "{}" : tripJson);
                id = trip.optString("id", "");
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "routing");
                JSONArray waypoints = trip.getJSONArray("waypoints");
                DiagnosticLogger.log(this, "ROUTE", "Requesting road route for " + waypoints.length() + " waypoints");
                JSONArray coordinates = RouteEngine.roadRoute(waypoints);
                DiagnosticLogger.saveRoute(this, coordinates.toString());
                List<Point> points = parse(coordinates);
                if (points.size() < 2) throw new Exception("Route is empty");
                totalSegments = points.size() - 1;
                DiagnosticLogger.log(this, "ROUTE", "Route loaded points=" + points.size() + " segments=" + totalSegments);

                // Keep the provider continuously active from the held start location.
                ensureProvider();
                running = true;
                paused = false;
                heldPoint = null;
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "active");

                double averageMph = clamp(trip.optDouble("averageSpeedMph", 35), 1, 150);
                currentSpeedMph = averageMph;
                double variation = clamp(trip.optDouble("speedVariationPercent", 8), 0, 80) / 100.0;
                int updateMs = (int) clamp(trip.optInt("gpsUpdateIntervalMs", 1000), 200, 10000);
                boolean randomStops = trip.optBoolean("randomStops", false);
                int randomStopChance = trip.optInt("randomStopChancePercent", 2);
                int randomStopMax = trip.optInt("randomStopMaxSeconds", 20);
                boolean hold = trip.optBoolean("holdAtDestination", true);
                float accuracy = (float) clamp(trip.optDouble("accuracyMeters", 3), 1, 100);
                Random random = new Random();

                phase = "driving";
                int segment = 0;
                double onSegment = 0;
                Point current = points.get(0);
                diagnosticInject(current, 0f, 0f, accuracy);
                long lastHeartbeat = 0;

                while (running && segment < points.size() - 1) {
                    while (paused && running) {
                        diagnosticInject(current, 0f, 0f, accuracy);
                        Thread.sleep(500);
                    }
                    if (!running) break;

                    if (randomStops && random.nextInt(100) < randomStopChance) {
                        int seconds = 1 + random.nextInt(Math.max(1, randomStopMax));
                        updateNotice("Traffic stop: " + seconds + " sec");
                        for (int s = 0; s < seconds && running; s++) {
                            diagnosticInject(current, 0f, 0f, accuracy);
                            Thread.sleep(1000);
                        }
                    }

                    double mph = averageMph * (1 + ((random.nextDouble() * 2 - 1) * variation));
                    double metersPerSecond = mph * 0.44704;
                    double step = metersPerSecond * updateMs / 1000.0;
                    Point from = points.get(segment);
                    Point to = points.get(segment + 1);
                    double length = distance(from, to);
                    if (length < .2) { segment++; onSegment = 0; continue; }
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
                    current = interpolate(from, to, Math.min(1, onSegment / Math.max(.1, length)));
                    currentSegment = segment;
                    diagnosticInject(current, bearing(from, to), (float) metersPerSecond, accuracy);
                    updateNotice("Driving " + Math.round(mph) + " mph");
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastHeartbeat >= 5000) {
                        DiagnosticLogger.log(this, "HEARTBEAT", "segment=" + segment + "/" + totalSegments + " injections=" + injectionCount + " lat=" + current.lat + " lon=" + current.lon);
                        lastHeartbeat = now;
                    }
                    Thread.sleep(updateMs);

                    for (int i = 1; i < waypoints.length() - 1; i++) {
                        JSONObject w = waypoints.getJSONObject(i);
                        Point stop = new Point(w.getDouble("latitude"), w.getDouble("longitude"));
                        if (!w.optBoolean("visited", false) && distance(current, stop) < Math.max(20, metersPerSecond * 2)) {
                            w.put("visited", true);
                            int seconds = Math.max(0, w.optInt("stopSeconds", 0));
                            for (int s = 0; s < seconds && running; s++) {
                                updateNotice("Waypoint stop: " + (seconds - s) + " sec");
                                diagnosticInject(current, 0f, 0f, accuracy);
                                Thread.sleep(1000);
                            }
                        }
                    }
                }

                if (running) {
                    current = points.get(points.size() - 1);
                    phase = "destination";
                    diagnosticInject(current, 0f, 0f, accuracy);
                    if (!id.isEmpty()) TripStore.updateStatus(this, id, "completed");
                    updateNotice("Destination reached");
                    DiagnosticLogger.log(this, "COMPLETE", "Destination reached after injections=" + injectionCount);
                    if (hold) {
                        heldPoint = current;
                        phase = "holding_destination";
                        while (running) {
                            diagnosticInject(heldPoint, 0f, 0f, accuracy);
                            Thread.sleep(500);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                DiagnosticLogger.log(this, "THREAD", "Trip worker interrupted phase=" + phase);
            } catch (SecurityException e) {
                recordFailure("trip security", e);
                updateNotice("Select Mock Drive as mock location app");
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "failed");
            } catch (Throwable e) {
                recordFailure("trip phase=" + phase, e);
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "failed");
            } finally {
                DiagnosticLogger.log(this, "THREAD", "Trip worker exiting running=" + running + " phase=" + phase);
                writeState();
            }
        }, "mock-trip");
        worker.setUncaughtExceptionHandler((thread, error) -> recordFailure("uncaught " + thread.getName(), error));
        worker.start();
        writeState();
    }

    private List<Point> parse(JSONArray a) throws Exception {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONArray c = a.getJSONArray(i);
            out.add(new Point(c.getDouble(1), c.getDouble(0)));
        }
        return out;
    }

    private synchronized void ensureProvider() {
        if (providerReady) {
            try {
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                return;
            } catch (RuntimeException ignored) {
                providerReady = false;
            }
        }
        DiagnosticLogger.log(this, "PROVIDER", "Registering GPS test provider");
        try {
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        } catch (IllegalArgumentException ignored) {
            DiagnosticLogger.log(this, "PROVIDER", "Provider already exists");
        }
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        providerReady = true;
        DiagnosticLogger.log(this, "PROVIDER", "enabled=" + manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    private void inject(Point p, float bearing, float speed, float accuracy) {
        ensureProvider();
        Location l = new Location(LocationManager.GPS_PROVIDER);
        l.setLatitude(p.lat);
        l.setLongitude(p.lon);
        l.setAccuracy(accuracy);
        l.setAltitude(0);
        l.setBearing(bearing);
        l.setSpeed(speed);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (Build.VERSION.SDK_INT >= 26) {
            l.setBearingAccuracyDegrees(3f);
            l.setSpeedAccuracyMetersPerSecond(.5f);
            l.setVerticalAccuracyMeters(accuracy);
        }
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, l);
    }

    private void diagnosticInject(Point p, float bearing, float speed, float accuracy) {
        try {
            inject(p, bearing, speed, accuracy);
            injectionCount++;
            lastInjectionElapsed = SystemClock.elapsedRealtime();
            currentLat = p.lat;
            currentLon = p.lon;
            DiagnosticLogger.log(this, "INJECT", "ok #" + injectionCount + " lat=" + p.lat + " lon=" + p.lon + " speedMps=" + speed + " bearing=" + bearing);
            writeState();
        } catch (RuntimeException e) {
            DiagnosticLogger.exception(this, "inject", e);
            throw e;
        }
    }

    private void recordFailure(String where, Throwable error) {
        lastError = error.getClass().getSimpleName() + ": " + (error.getMessage() == null ? "" : error.getMessage());
        phase = "failed";
        DiagnosticLogger.exception(this, where, error);
        updateNotice("Trip failed: " + lastError);
        writeState();
    }

    private void writeState() {
        DiagnosticLogger.state(this, running, worker != null && worker.isAlive(), injectionCount,
                lastInjectionElapsed, currentSegment, totalSegments, currentLat, currentLon,
                currentSpeedMph, phase, lastError);
    }

    private void stopEverything() {
        DiagnosticLogger.log(this, "SERVICE", "Stop requested");
        stopWorker();
        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        providerReady = false;
        phase = "stopped";
        stopForeground(true);
        stopSelf();
        writeState();
    }

    private void stopWorker() {
        running = false;
        paused = false;
        heldPoint = null;
        Thread old = worker;
        worker = null;
        if (old != null) old.interrupt();
    }

    @Override public void onDestroy() {
        DiagnosticLogger.log(this, "SERVICE", "onDestroy running=" + running);
        writeState();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Mock Drive", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notice(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("Mock Drive").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pi).build();
    }

    private void updateNotice(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE, notice(text));
    }

    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double distance(Point a, Point b) {
        double earth = 6371000, p1 = Math.toRadians(a.lat), p2 = Math.toRadians(b.lat);
        double dp = Math.toRadians(b.lat - a.lat), dl = Math.toRadians(b.lon - a.lon);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return earth * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
    private static Point interpolate(Point a, Point b, double f) { return new Point(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f); }
    private static float bearing(Point a, Point b) {
        double p1 = Math.toRadians(a.lat), p2 = Math.toRadians(b.lat), dl = Math.toRadians(b.lon - a.lon);
        double y = Math.sin(dl) * Math.cos(p2), x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    private static final class Point {
        final double lat, lon;
        Point(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
}
