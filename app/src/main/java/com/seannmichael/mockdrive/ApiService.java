package com.seannmichael.mockdrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ApiService extends Service {
    public static final String ACTION_START = "api.start";
    public static final String ACTION_STOP = "api.stop";
    private volatile boolean running;
    private ServerSocket server;
    private Thread thread;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { shutdown(); return START_NOT_STICKY; }
        startForeground(77, notification("API listening on port 8765"));
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            try {
                server = new ServerSocket(8765);
                while (running) {
                    Socket socket = server.accept();
                    new Thread(() -> handle(socket), "api-client").start();
                }
            } catch (Exception ignored) { }
        }, "mock-api");
        thread.start();
    }

    private void handle(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String request = in.readLine();
            if (request == null) return;
            String[] first = request.split(" ");
            String method = first[0];
            String path = first.length > 1 ? first[1] : "/";
            int length = 0;
            String authorization = "";
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                if (lower.startsWith("authorization:")) authorization = line.substring(line.indexOf(':') + 1).trim();
            }
            char[] chars = new char[length];
            int read = 0;
            while (read < length) {
                int n = in.read(chars, read, length - read);
                if (n < 0) break;
                read += n;
            }
            String body = new String(chars, 0, read);
            if (!authorization.equals("Bearer " + TripStore.token(this))) {
                respond(socket, 401, new JSONObject().put("error", "unauthorized").toString());
                return;
            }

            if ("GET".equals(method) && "/api/v1/status".equals(path)) {
                respond(socket, 200, new JSONObject().put("ok", true).put("port", 8765).toString());
            } else if ("GET".equals(method) && "/api/v1/trips".equals(path)) {
                respond(socket, 200, TripStore.all(this).toString());
            } else if ("POST".equals(method) && "/api/v1/google-business/resolve".equals(path)) {
                JSONObject j = new JSONObject(body);
                if (j.has("googleApiKey")) GooglePlacesEngine.setApiKey(this, j.optString("googleApiKey"));
                JSONObject match = GooglePlacesEngine.findTarget(this,
                        j.optString("searchQuery"), j.optString("targetBusinessName"),
                        j.optString("targetPlaceId"), j.optString("addressHint"));
                respond(socket, 200, match.toString());
            } else if ("POST".equals(method) && "/api/v1/location".equals(path)) {
                JSONObject j = new JSONObject(body);
                Intent s = new Intent(this, MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT)
                        .putExtra(MockLocationService.EXTRA_LAT, j.getDouble("latitude"))
                        .putExtra(MockLocationService.EXTRA_LON, j.getDouble("longitude"));
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
                respond(socket, 200, new JSONObject().put("status", "holding").toString());
            } else if ("POST".equals(method) && "/api/v1/trips".equals(path)) {
                JSONObject trip = TripStore.save(this, new JSONObject(body));
                if (trip.optLong("startAtEpochMs", 0) > 0) TripScheduler.schedule(this, trip);
                respond(socket, 201, trip.toString());
            } else if (path.matches("/api/v1/trips/[^/]+/(start|pause|resume|stop)")) {
                String[] p = path.split("/");
                String id = p[4], command = p[5];
                JSONObject trip = TripStore.get(this, id);
                if (trip == null) { respond(socket, 404, "{\"error\":\"not found\"}"); return; }
                if ("start".equals(command)) TripScheduler.launch(this, trip);
                else {
                    String action = "pause".equals(command) ? MockLocationService.ACTION_PAUSE : "resume".equals(command) ? MockLocationService.ACTION_RESUME : MockLocationService.ACTION_STOP;
                    startService(new Intent(this, MockLocationService.class).setAction(action));
                }
                respond(socket, 200, new JSONObject().put("status", command).toString());
            } else if ("DELETE".equals(method) && path.startsWith("/api/v1/trips/")) {
                String id = path.substring("/api/v1/trips/".length());
                TripScheduler.cancel(this, id);
                respond(socket, TripStore.delete(this, id) ? 200 : 404, "{\"deleted\":true}");
            } else {
                respond(socket, 404, "{\"error\":\"not found\"}");
            }
        } catch (Exception e) {
            try { respond(socket, 400, new JSONObject().put("error", e.getMessage()).toString()); } catch (Exception ignored) {}
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void respond(Socket socket, int code, String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 201 ? "Created" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : "Bad Request";
        String headers = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(data); out.flush();
    }

    private Notification notification(String text) {
        String channel = "mock_api";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(channel, "Mock Drive API", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        return b.setContentTitle("Mock Drive API").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setOngoing(true).build();
    }

    private void shutdown() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        stopForeground(true); stopSelf();
    }
    @Override public void onDestroy() { shutdown(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
