package com.example.accidentdetection;

import android.content.*;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean on = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("monitoring_enabled", false);
        if (on) {
            try {
                ContextCompat.startForegroundService(ctx,
                        new Intent(ctx, MonitoringService.class).setAction(MonitoringService.ACTION_START));
            } catch (Exception ignored) { }
        }
    }
}