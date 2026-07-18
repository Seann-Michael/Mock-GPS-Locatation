package com.seannmichael.mockdrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

public class TripAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent == null ? null : intent.getStringExtra("trip_id");
        if (id == null) return;
        JSONObject trip = TripStore.get(context, id);
        if (trip != null) {
            TripScheduler.launch(context, trip);
            TripScheduler.scheduleNext(context, trip);
        }
    }
}
