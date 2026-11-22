package com.example.accidentdetection.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.database.Contact;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackingService extends Service {

    public static final String ACTION_STOP = "com.example.accidentdetection.TRACK_STOP";

    private static final String CHANNEL_ID = "tracking_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long UPDATE_INTERVAL_MS = 2 * 60 * 1000L;   // 2 minutes
    private static final long MAX_DURATION_MS = 30 * 60 * 1000L;     // 30 minutes

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private volatile Location lastLocation;
    private long endTime;
    private boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable sendTask = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() >= endTime) {
                stopTracking();
                return;
            }
            sendUpdate();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };


    public static void start(Context context) {
        ContextCompat.startForegroundService(context,
                new Intent(context, TrackingService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, TrackingService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fused = LocationServices.getFusedLocationProviderClient(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        running = true;

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        endTime = System.currentTimeMillis() + MAX_DURATION_MS;
        startLocationUpdates();
        // The first SOS message already carries the location, so the first
        // follow-up goes out after one interval.
        handler.postDelayed(sendTask, UPDATE_INTERVAL_MS);
        return START_NOT_STICKY;
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build();
        callback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location l = result.getLastLocation();
                if (l != null) lastLocation = l;
            }
        };
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopTracking();   // location permission was revoked
        }
    }

    private void sendUpdate() {
        final Location loc = lastLocation;
        if (loc == null) return;
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) return;

        final String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(new Date());
        final String message = String.format(Locale.US,
                "LIVE UPDATE (%s): I have not confirmed I am safe. "
                        + "My current location: https://maps.google.com/?q=%.6f,%.6f",
                time, loc.getLatitude(), loc.getLongitude());

        // Database access must not run on the main thread
        AppDatabase.executor.execute(() -> {
            List<Contact> contacts = AppDatabase.get(this).contactDao().getAllNow();
            SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            for (Contact c : contacts) {
                try {
                    sms.sendMultipartTextMessage(c.phone, null, parts, null, null);
                } catch (Exception ignored) {
                    // one failed number must not stop the others
                }
            }
        });
    }

    private void stopTracking() {
        running = false;
        handler.removeCallbacks(sendTask);
        if (callback != null) {
            fused.removeLocationUpdates(callback);
            callback = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sendTask);
        if (callback != null) fused.removeLocationUpdates(callback);
        super.onDestroy();
    }

    // ---------- Notification ----------

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, TrackingService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Sharing your live location")
                .setContentText("Your emergency contacts get an update every 2 minutes.")
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        "I'M SAFE - STOP", stopPending)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Live tracking", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}