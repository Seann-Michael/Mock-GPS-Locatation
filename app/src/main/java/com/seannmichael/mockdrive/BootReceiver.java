package com.seannmichael.mockdrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        TripScheduler.restore(context);
        try {
            Intent api = new Intent(context, ApiService.class).setAction(ApiService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(api); else context.startService(api);
        } catch (Exception ignored) { }
    }
}
