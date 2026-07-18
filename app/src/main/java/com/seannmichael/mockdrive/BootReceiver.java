package com.seannmichael.mockdrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        TripScheduler.restore(context);
        // Only reopen the network control API after a reboot if the user explicitly opted in.
        // Silently reopening a listening socket on every boot is a security surprise.
        if (!AppPreferences.apiAutoStart(context)) return;
        try {
            Intent api = new Intent(context, ApiService.class).setAction(ApiService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(api); else context.startService(api);
        } catch (Exception ignored) { }
    }
}
