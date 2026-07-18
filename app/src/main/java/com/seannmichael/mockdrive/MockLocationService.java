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
    private Thread worker;
    private LocationManager manager;
    private Point heldPoint;
    private String historyId = "";
    private long injectionCount;
    private int currentSegment;
    private int totalSegments;
    private String phase = "idle";

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) { log("SERVICE", "Stop requested"); stopEverything(); return START_NOT_STICKY; }
        if (ACTION_PAUSE.equals(action)) { paused = true; phase = "paused"; log("SERVICE", "Paused"); updateNotice("Paused"); return START_STICKY; }
        if (ACTION_RESUME.equals(action)) { paused = false; phase = "driving"; log("SERVICE", "Resumed"); updateNotice("Resumed"); return START_STICKY; }
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
        try {
            enableProvider();
            heldPoint = new Point(lat, lon);
            running = true;
            injectAndRecord(heldPoint, 0f, 0f, 3f);
            worker = new Thread(() -> {
                log("THREAD", "Start-location hold worker started");
                while (running && heldPoint != null) {
                    try { injectAndRecord(heldPoint, 0f, 0f, 3f); Thread.sleep(1000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); log("THREAD", "Start-location hold interrupted"); return; }
                    catch (Throwable e) { error("Start-location hold", e); return; }
                }
            }, "mock-hold");
            worker.start();
        } catch (SecurityException e) { error("Teleport security", e); updateNotice("Select Mock Drive as mock location app"); }
    }

    private void startTrip(String tripJson) {
        stopWorker();
        worker = new Thread(() -> {
            String id = "";
            try {
                JSONObject trip = new JSONObject(tripJson == null ? "{}" : tripJson);
                id = trip.optString("id", "");
                JSONObject history = HistoryStore.findByTrip(this, id);
                if (history == null) history = HistoryStore.begin(this, trip);
                historyId = history == null ? "" : history.optString("historyId", "");
                injectionCount = 0;
                currentSegment = 0;
                totalSegments = 0;
                phase = "routing";
                if (!historyId.isEmpty()) SimulationDiagnostics.begin(this, historyId, trip);
                log("SERVICE", "Trip worker starting tripId=" + id + " thread=" + Thread.currentThread().getName());
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "routing");
                JSONArray waypoints = trip.getJSONArray("waypoints");
                log("ROUTE", "Requesting route for " + waypoints.length() + " waypoints");
                long routeStarted = SystemClock.elapsedRealtime();
                JSONArray coordinates = RouteEngine.roadRoute(waypoints);
                log("ROUTE", "Route received in " + (SystemClock.elapsedRealtime() - routeStarted) + " ms with " + coordinates.length() + " points");
                if (!historyId.isEmpty()) SimulationDiagnostics.saveRoute(this, historyId, coordinates);
                List<Point> points = parse(coordinates);
                if (points.size() < 2) throw new Exception("Route is empty");
                totalSegments = points.size() - 1;

                enableProvider();
                running = true;
                paused = false;
                heldPoint = null;
                phase = "driving";
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "active");

                double averageMph = clamp(trip.optDouble("averageSpeedMph", 35), 1, 150);
                double variation = clamp(trip.optDouble("speedVariationPercent", 8), 0, 80) / 100.0;
                int updateMs = (int) clamp(trip.optInt("gpsUpdateIntervalMs", 1000), 200, 10000);
                boolean randomStops = trip.optBoolean("randomStops", false);
                int randomStopChance = trip.optInt("randomStopChancePercent", 2);
                int randomStopMax = trip.optInt("randomStopMaxSeconds", 20);
                boolean hold = trip.optBoolean("holdAtDestination", true);
                float accuracy = (float) clamp(trip.optDouble("accuracyMeters", 3), 1, 100);
                Random random = new Random();
                log("CONFIG", "speedMph=" + averageMph + " variation=" + variation + " updateMs=" + updateMs + " holdAtDestination=" + hold);

                int segment = 0;
                double onSegment = 0;
                Point current = points.get(0);
                injectAndRecord(current, 0f, 0f, accuracy);

                while (running && segment < points.size() - 1) {
                    while (paused && running) { injectAndRecord(current, 0f, 0f, accuracy); Thread.sleep(500); }
                    if (!running) break;

                    if (randomStops && random.nextInt(100) < randomStopChance) {
                        int seconds = 1 + random.nextInt(Math.max(1, randomStopMax));
                        log("STOP", "Random traffic stop for " + seconds + " seconds at segment " + segment);
                        updateNotice("Traffic stop: " + seconds + " sec");
                        for (int s = 0; s < seconds && running; s++) { injectAndRecord(current, 0f, 0f, accuracy); Thread.sleep(1000); }
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
                    from = points.get(segment); to = points.get(segment + 1); length = distance(from, to);
                    current = interpolate(from, to, Math.min(1, onSegment / Math.max(.1, length)));
                    currentSegment = segment;
                    injectAndRecord(current, bearing(from, to), (float) metersPerSecond, accuracy);
                    updateNotice("Driving " + Math.round(mph) + " mph");
                    Thread.sleep(updateMs);

                    for (int i = 1; i < waypoints.length() - 1; i++) {
                        JSONObject w = waypoints.getJSONObject(i);
                        Point stop = new Point(w.getDouble("latitude"), w.getDouble("longitude"));
                        if (!w.optBoolean("visited", false) && distance(current, stop) < Math.max(20, metersPerSecond * 2)) {
                            w.put("visited", true);
                            int seconds = Math.max(0, w.optInt("stopSeconds", 0));
                            log("WAYPOINT", "Reached waypoint " + i + ", holding " + seconds + " seconds");
                            for (int s = 0; s < seconds && running; s++) {
                                updateNotice("Waypoint stop: " + (seconds - s) + " sec");
                                injectAndRecord(current, 0f, 0f, accuracy);
                                Thread.sleep(1000);
                            }
                        }
                    }
                }

                if (running) {
                    current = points.get(points.size() - 1);
                    phase = "destination";
                    injectAndRecord(current, 0f, 0f, accuracy);
                    if (!id.isEmpty()) TripStore.updateStatus(this, id, "completed");
                    log("COMPLETE", "Destination reached after " + injectionCount + " injections");
                    updateNotice("Destination reached");
                    if (hold) {
                        heldPoint = current;
                        phase = "holding_destination";
                        while (running) { injectAndRecord(heldPoint, 0f, 0f, accuracy); Thread.sleep(1000); }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("THREAD", "Trip worker interrupted phase=" + phase + " running=" + running);
            } catch (SecurityException e) {
                error("Trip security", e);
                updateNotice("Select Mock Drive as mock location app");
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "failed");
            } catch (Throwable e) {
                error("Trip failure phase=" + phase, e);
                updateNotice("Trip failed: " + e.getMessage());
                if (!id.isEmpty()) TripStore.updateStatus(this, id, "failed");
            } finally {
                log("THREAD", "Trip worker exited phase=" + phase + " running=" + running + " injections=" + injectionCount);
                if (!historyId.isEmpty()) HistoryStore.attachDiagnostics(this, historyId, SimulationDiagnostics.summary(this, historyId));
            }
        }, "mock-trip");
        worker.setUncaughtExceptionHandler((thread, throwable) -> error("Uncaught on " + thread.getName(), throwable));
        worker.start();
    }

    private void enableProvider() {
        log("PROVIDER", "Rebuilding GPS test provider");
        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); log("PROVIDER", "Existing GPS test provider removed"); }
        catch (Exception e) { log("PROVIDER", "Provider removal ignored: " + e.getClass().getSimpleName()); }
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
        log("PROVIDER", "GPS test provider enabled=" + manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    private void injectAndRecord(Point p, float bearing, float speed, float accuracy) {
        Location injected = new Location(LocationManager.GPS_PROVIDER);
        injected.setLatitude(p.lat); injected.setLongitude(p.lon); injected.setAccuracy(accuracy); injected.setAltitude(0);
        injected.setBearing(bearing); injected.setSpeed(speed); injected.setTime(System.currentTimeMillis());
        injected.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (Build.VERSION.SDK_INT >= 26) {
            injected.setBearingAccuracyDegrees(3f); injected.setSpeedAccuracyMetersPerSecond(.5f); injected.setVerticalAccuracyMeters(accuracy);
        }
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, injected);
        injectionCount++;
        Location reported = null;
        try { reported = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (SecurityException e) { error("Readback location", e); }
        boolean enabled = false;
        try { enabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        if (!historyId.isEmpty()) SimulationDiagnostics.injection(this, historyId, injectionCount, currentSegment, totalSegments, injected, reported, enabled, worker != null && worker.isAlive(), phase);
        if (injectionCount == 1 || injectionCount % 10 == 0) {
            float delta = reported == null ? -1 : injected.distanceTo(reported);
            log("HEARTBEAT", "injection=" + injectionCount + " segment=" + currentSegment + "/" + totalSegments + " providerEnabled=" + enabled + " readbackDeltaMeters=" + delta);
        }
    }

    private void stopEverything() {
        stopWorker();
        try { manager.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        phase = "stopped";
        log("SERVICE", "Foreground service stopped and provider removed");
        if (!historyId.isEmpty()) HistoryStore.attachDiagnostics(this, historyId, SimulationDiagnostics.summary(this, historyId));
        stopForeground(true); stopSelf();
    }

    private void stopWorker() {
        running = false; paused = false; heldPoint = null;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private void log(String category, String message) { if (!historyId.isEmpty()) SimulationDiagnostics.event(this, historyId, category, message); }
    private void error(String where, Throwable error) { if (!historyId.isEmpty()) SimulationDiagnostics.exception(this, historyId, where, error); }
    private List<Point> parse(JSONArray a) throws Exception { List<Point> out = new ArrayList<>(); for (int i = 0; i < a.length(); i++) { JSONArray c = a.getJSONArray(i); out.add(new Point(c.getDouble(1), c.getDouble(0))); } return out; }
    private void createChannel() { if (Build.VERSION.SDK_INT >= 26) { NotificationChannel c = new NotificationChannel(CHANNEL, "Mock Drive", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    private Notification notice(String text) { PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, NavigationRunActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this); return b.setContentTitle("Mock Drive").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pi).build(); }
    private void updateNotice(String text) { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE, notice(text)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double distance(Point a, Point b) { double earth = 6371000, p1 = Math.toRadians(a.lat), p2 = Math.toRadians(b.lat); double dp = Math.toRadians(b.lat-a.lat), dl = Math.toRadians(b.lon-a.lon); double h = Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2); return earth*2*Math.atan2(Math.sqrt(h), Math.sqrt(1-h)); }
    private static Point interpolate(Point a, Point b, double f) { return new Point(a.lat+(b.lat-a.lat)*f, a.lon+(b.lon-a.lon)*f); }
    private static float bearing(Point a, Point b) { double p1=Math.toRadians(a.lat), p2=Math.toRadians(b.lat), dl=Math.toRadians(b.lon-a.lon); double y=Math.sin(dl)*Math.cos(p2), x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl); return (float)((Math.toDegrees(Math.atan2(y,x))+360)%360); }
    @Override public IBinder onBind(Intent intent) { return null; }
    private static final class Point { final double lat, lon; Point(double lat, double lon) { this.lat=lat; this.lon=lon; } }
}