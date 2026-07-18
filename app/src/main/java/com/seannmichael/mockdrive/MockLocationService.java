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

import java.util.ArrayList;
import java.util.List;

public class MockLocationService extends Service {
    public static final String ACTION_START = "com.seannmichael.mockdrive.START";
    public static final String ACTION_STOP = "com.seannmichael.mockdrive.STOP";
    public static final String EXTRA_COORDS = "coords";
    public static final String EXTRA_SPEED_MPH = "speed_mph";

    private static final String CHANNEL_ID = "mock_drive";
    private static final int NOTIFICATION_ID = 42;
    private volatile boolean running;
    private Thread worker;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSimulation();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            String json = intent.getStringExtra(EXTRA_COORDS);
            double mph = intent.getDoubleExtra(EXTRA_SPEED_MPH, 25.0);
            startForeground(NOTIFICATION_ID, notification("Preparing route…"));
            startSimulation(json, mph);
        }
        return START_NOT_STICKY;
    }

    private void startSimulation(String json, double mph) {
        stopWorkerOnly();
        worker = new Thread(() -> {
            try {
                List<Point> points = parse(json);
                if (points.size() < 2) throw new Exception("Route has fewer than two points");
                enableMockProvider();
                running = true;
                double metersPerSecond = mph * 0.44704;
                int segment = 0;
                double traveledOnSegment = 0.0;

                inject(points.get(0), 0f, (float) metersPerSecond);
                while (running && segment < points.size() - 1) {
                    Point from = points.get(segment);
                    Point to = points.get(segment + 1);
                    double segmentLength = distanceMeters(from, to);
                    if (segmentLength < 0.2) {
                        segment++;
                        traveledOnSegment = 0;
                        continue;
                    }

                    double step = metersPerSecond;
                    traveledOnSegment += step;
                    while (traveledOnSegment >= segmentLength && segment < points.size() - 1) {
                        traveledOnSegment -= segmentLength;
                        segment++;
                        if (segment >= points.size() - 1) break;
                        from = points.get(segment);
                        to = points.get(segment + 1);
                        segmentLength = distanceMeters(from, to);
                    }
                    if (segment >= points.size() - 1) break;

                    from = points.get(segment);
                    to = points.get(segment + 1);
                    segmentLength = distanceMeters(from, to);
                    double fraction = Math.max(0, Math.min(1, traveledOnSegment / segmentLength));
                    Point current = interpolate(from, to, fraction);
                    float bearing = bearing(from, to);
                    inject(current, bearing, (float) metersPerSecond);
                    updateNotification("Simulating at " + Math.round(mph) + " mph");
                    Thread.sleep(1000);
                }

                if (running) {
                    Point last = points.get(points.size() - 1);
                    inject(last, 0f, 0f);
                    updateNotification("Destination reached");
                    Thread.sleep(2500);
                }
            } catch (SecurityException e) {
                updateNotification("Select Mock Drive in Developer Options");
            } catch (Exception e) {
                updateNotification("Simulation error: " + e.getMessage());
            } finally {
                running = false;
                stopForeground(false);
                stopSelf();
            }
        }, "mock-drive-worker");
        worker.start();
    }

    private List<Point> parse(String json) throws Exception {
        JSONArray a = new JSONArray(json);
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONArray c = a.getJSONArray(i);
            out.add(new Point(c.getDouble(1), c.getDouble(0)));
        }
        return out;
    }

    private void enableMockProvider() {
        try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
        );
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
    }

    private void inject(Point p, float bearing, float speed) {
        Location l = new Location(LocationManager.GPS_PROVIDER);
        l.setLatitude(p.lat);
        l.setLongitude(p.lon);
        l.setAccuracy(3.0f);
        l.setAltitude(0.0);
        l.setBearing(bearing);
        l.setSpeed(speed);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if (Build.VERSION.SDK_INT >= 26) {
            l.setBearingAccuracyDegrees(2f);
            l.setSpeedAccuracyMetersPerSecond(0.3f);
            l.setVerticalAccuracyMeters(3f);
        }
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, l);
    }

    private void stopSimulation() {
        stopWorkerOnly();
        try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        stopForeground(true);
        stopSelf();
    }

    private void stopWorkerOnly() {
        running = false;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Mock Drive", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Active mock driving simulation");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Mock Drive")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification(text));
    }

    private static double distanceMeters(Point a, Point b) {
        double earth = 6371000.0;
        double p1 = Math.toRadians(a.lat);
        double p2 = Math.toRadians(b.lat);
        double dp = Math.toRadians(b.lat - a.lat);
        double dl = Math.toRadians(b.lon - a.lon);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return earth * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static Point interpolate(Point a, Point b, double f) {
        return new Point(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f);
    }

    private static float bearing(Point a, Point b) {
        double p1 = Math.toRadians(a.lat);
        double p2 = Math.toRadians(b.lat);
        double dl = Math.toRadians(b.lon - a.lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static class Point {
        final double lat;
        final double lon;
        Point(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }
}
