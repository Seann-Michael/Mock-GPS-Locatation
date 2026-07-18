package com.seannmichael.mockdrive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public final class TripScheduler {
    private TripScheduler() {}

    public static void schedule(Context context, JSONObject trip) throws Exception {
        TripStore.save(context, trip);
        long when = trip.optLong("startAtEpochMs", 0);
        if (when <= 0) return;
        Intent i = new Intent(context, TripAlarmReceiver.class);
        i.putExtra("trip_id", trip.getString("id"));
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(trip.getString("id")), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        // On Android 12+ exact alarms require a special permission that is not granted by default
        // (and is fully denied for most apps on Android 14+). Fall back to an inexact alarm instead
        // of crashing with SecurityException so scheduling always works; exact timing is best-effort.
        boolean exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } catch (SecurityException denied) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
        TripStore.updateStatus(context, trip.getString("id"), "scheduled");
    }

    public static void scheduleNext(Context context, JSONObject trip) {
        try {
            String recurrence = trip.optString("recurrence", "none");
            if ("none".equals(recurrence)) return;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(Math.max(System.currentTimeMillis(), trip.optLong("startAtEpochMs", System.currentTimeMillis())));
            if ("daily".equals(recurrence)) c.add(Calendar.DAY_OF_YEAR, 1);
            else if ("weekly".equals(recurrence)) c.add(Calendar.WEEK_OF_YEAR, 1);
            else if ("monthly".equals(recurrence)) c.add(Calendar.MONTH, 1);
            else return;
            trip.put("startAtEpochMs", c.getTimeInMillis());
            trip.put("status", "scheduled");
            schedule(context, trip);
        } catch (Exception ignored) {}
    }

    public static void cancel(Context context, String id) {
        Intent i = new Intent(context, TripAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode(id), i, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    public static void restore(Context context) {
        JSONArray all = TripStore.all(context);
        long now = System.currentTimeMillis();
        for (int n = 0; n < all.length(); n++) {
            try {
                JSONObject t = all.getJSONObject(n);
                long when = t.optLong("startAtEpochMs", 0);
                if (when > now && "scheduled".equals(t.optString("status"))) schedule(context, t);
            } catch (Exception ignored) {}
        }
    }

    static void launch(Context context, JSONObject trip) {
        Intent s = new Intent(context, MockLocationService.class);
        s.setAction(MockLocationService.ACTION_START);
        s.putExtra(MockLocationService.EXTRA_TRIP, trip.toString());
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);
    }

    private static int requestCode(String id) { return id.hashCode() & 0x7fffffff; }
}
