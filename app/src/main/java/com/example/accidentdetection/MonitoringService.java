package com.example.accidentdetection;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.accidentdetection.detection.AccidentDetector;
import com.example.accidentdetection.ui.AlertActivity;
import com.example.accidentdetection.ui.MainActivity;
import com.example.accidentdetection.utils.GpsFilter;

public class MonitoringService extends Service
        implements SensorEventListener, LocationListener {

    public static final String ACTION_START = "com.example.accidentdetection.START";
    public static final String ACTION_STOP  = "com.example.accidentdetection.STOP";
    public static final String ACTION_PAUSE = "com.example.accidentdetection.PAUSE";
    public static final String ACTION_UPDATE = "com.example.accidentdetection.UPDATE";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";

    private static final String CH_MONITOR = "monitoring";
    private static final String CH_ALERT = "accident_alert";
    private static final int ID_MONITOR = 101, ID_ALERT = 102;
    private static final long PAUSE_MS = 60 * 60 * 1000L;

    public static volatile boolean running = false;

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private AccidentDetector detector;
    private volatile long pausedUntil = 0;
    private long lastBroadcast = 0;

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS;
            updateNotification();
            return START_STICKY;
        }
        if (!running) startMonitoring();
        return START_STICKY;
    }

    private void startMonitoring() {
        startForeground(ID_MONITOR, buildNotification());
        running = true;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "accident:monitor");
        wakeLock.acquire();

        SharedPreferences p = getSharedPreferences("app_prefs", MODE_PRIVATE);
        int sensitivity = p.getInt("sensitivity", 1);
        boolean demo = p.getBoolean("demo_mode", false); // optional, add a switch later

        detector = new AccidentDetector(sensitivity, !demo, new AccidentDetector.Listener() {
            @Override public void onAccident(AccidentDetector.Result r) { triggerAlert(); }
            @Override public void onRejected(String reason) { /* log only */ }
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) {
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
            } catch (Exception ignored) { }
        }
    }

    // ---- sensors ----
    @Override public void onSensorChanged(SensorEvent e) {
        if (detector == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil) return;
        detector.onAccelerometer(e.values[0], e.values[1], e.values[2], now);
    }
    @Override public void onAccuracyChanged(Sensor s, int a) { }

    // ---- GPS ----
    @Override public void onLocationChanged(Location loc) {
        long now = SystemClock.elapsedRealtime();
        double kmh = GpsFilter.speedKmh(loc);
        if (kmh >= 0 && detector != null && now >= pausedUntil) {
            detector.onSpeed((float)kmh, now);
        }
        if (now - lastBroadcast >= 1000) {   // update Home cards at most 1/s
            lastBroadcast = now;
            Intent i = new Intent(ACTION_UPDATE).setPackage(getPackageName());
            i.putExtra(EXTRA_SPEED, kmh);
            i.putExtra(EXTRA_LAT, loc.getLatitude());
            i.putExtra(EXTRA_LNG, loc.getLongitude());
            sendBroadcast(i);
        }
    }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    // ---- alert ----
    private Intent buildAlertIntent() {
        Intent i = new Intent(this, AlertActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // add the extras your AlertActivity expects (e.g. test mode = false)
        return i;
    }

    private void triggerAlert() {
        if (AlertActivity.isShowing) return;   // already counting down
        pausedUntil = SystemClock.elapsedRealtime() + 60_000; // avoid double triggers

        Intent alert = buildAlertIntent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, alert,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Possible accident detected")
                .setContentText("Tap to cancel the alert")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID_ALERT, n);

        // Fallback: works when the app is in the foreground
        try { startActivity(alert); } catch (Exception ignored) { }
    }

    // ---- notifications ----
    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return; // no channels before Android 8

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CH_MONITOR, "Monitoring", NotificationManager.IMPORTANCE_LOW));

        NotificationChannel alert = new NotificationChannel(
                CH_ALERT, "Accident alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(alert);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 1,
                new Intent(this, MonitoringService.class).setAction(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, MonitoringService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        boolean paused = SystemClock.elapsedRealtime() < pausedUntil;
        return new NotificationCompat.Builder(this, CH_MONITOR)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(paused ? "Monitoring paused (1 hour)" : "Accident monitoring active")
                .setContentText("Your phone is watching for crashes")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(0, "Pause 1 h", pause)
                .addAction(0, "Stop", stop)
                .build();
    }

    private void updateNotification() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify(ID_MONITOR, buildNotification());
    }

    @Override public void onDestroy() {
        running = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) locationManager.removeUpdates(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit().putBoolean("monitoring_enabled", false).apply();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}