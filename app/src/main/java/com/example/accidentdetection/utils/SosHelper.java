package com.example.accidentdetection.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.database.Contact;
import com.example.accidentdetection.database.HistoryEntry;
import com.example.accidentdetection.service.TrackingService;
import com.example.accidentdetection.ui.SettingsActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SosHelper {

    public static final String TYPE_AUTO = "Auto-detected";
    public static final String TYPE_MANUAL = "Manual SOS";
    public static final String TYPE_TEST = "Test alert";

    private static final long LOCATION_TIMEOUT_MS = 8000;

    public interface Callback {
        /** Always called on the main thread. */
        void onDone(int recipientsReached);
    }

    // =====================================================================
    // Public API
    // =====================================================================

    /** Gets the location, sends the SMS to all contacts, logs it, then calls the callback. */
    public static void sendSos(Context context, String type, Callback callback) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean handled = new AtomicBoolean(false);

        // Safety net: never wait more than 8 seconds for GPS
        final Runnable timeout = () -> {
            if (handled.compareAndSet(false, true)) dispatch(app, type, null, callback);
        };
        main.postDelayed(timeout, LOCATION_TIMEOUT_MS);

        if (!has(app, Manifest.permission.ACCESS_FINE_LOCATION)) {
            main.removeCallbacks(timeout);
            if (handled.compareAndSet(false, true)) dispatch(app, type, null, callback);
            return;
        }

        final FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(app);
        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            resolve(handled, main, timeout, app, type, loc, callback);
                        } else {
                            fused.getLastLocation().addOnCompleteListener(t ->
                                    resolve(handled, main, timeout, app, type,
                                            t.isSuccessful() ? t.getResult() : null, callback));
                        }
                    })
                    .addOnFailureListener(e ->
                            resolve(handled, main, timeout, app, type, null, callback));
        } catch (SecurityException e) {
            resolve(handled, main, timeout, app, type, null, callback);
        }
    }

    /** Writes a History row without sending anything (used for cancelled alerts). */
    public static void logHistory(Context context, String type, String status, String mapLink) {
        final Context app = context.getApplicationContext();
        HistoryEntry e = new HistoryEntry();
        e.timestamp = System.currentTimeMillis();
        e.type = type;
        e.status = status;
        e.mapLink = mapLink == null ? "" : mapLink;
        AppDatabase.executor.execute(() -> AppDatabase.get(app).historyDao().insert(e));
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private static void resolve(AtomicBoolean handled, Handler main, Runnable timeout,
                                Context app, String type, Location loc, Callback cb) {
        if (handled.compareAndSet(false, true)) {
            main.removeCallbacks(timeout);
            dispatch(app, type, loc, cb);
        }
    }

    private static void dispatch(Context app, String type, Location loc, Callback cb) {
        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            List<Contact> contacts = db.contactDao().getAllNow();   // primary first

            String link = loc == null ? "" : mapLink(loc);
            String message = buildMessage(app, link);
            int reached = sendSms(app, contacts, message);

            HistoryEntry entry = new HistoryEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.type = type;
            entry.status = reached > 0 ? "SOS sent" : "SOS failed";
            entry.mapLink = link;
            db.historyDao().insert(entry);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (reached > 0) {
                    maybeAutoCall(app, contacts);
                    startTracking(app);
                }
                if (cb != null) cb.onDone(reached);
            });
        });
    }

    private static String mapLink(Location loc) {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                loc.getLatitude(), loc.getLongitude());
    }

    private static String buildMessage(Context app, String link) {
        SharedPreferences p = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String custom = p.getString(SettingsActivity.KEY_SOS_MESSAGE,
                SettingsActivity.DEFAULT_MESSAGE);

        StringBuilder sb = new StringBuilder("EMERGENCY! ").append(custom);

        String name = p.getString("med_name", "");
        if (!name.isEmpty()) sb.append("\nName: ").append(name);

        sb.append(link.isEmpty() ? "\nLocation: unavailable" : "\nLocation: " + link);

        String blood = p.getString("med_blood", "");
        if (!blood.isEmpty() && !blood.equals("Select")) sb.append("\nBlood group: ").append(blood);
        String allergies = p.getString("med_allergies", "");
        if (!allergies.isEmpty()) sb.append("\nAllergies: ").append(allergies);
        String conditions = p.getString("med_conditions", "");
        if (!conditions.isEmpty()) sb.append("\nMedical: ").append(conditions);
        String doctor = p.getString("med_doctor", "");
        if (!doctor.isEmpty()) sb.append("\nDoctor: ").append(doctor);

        return sb.toString();
    }

    private static int sendSms(Context app, List<Contact> contacts, String message) {
        if (!has(app, Manifest.permission.SEND_SMS)) return 0;

        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? app.getSystemService(SmsManager.class)
                : SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(message);

        int count = 0;
        for (Contact c : contacts) {
            try {
                sms.sendMultipartTextMessage(c.phone, null, parts, null, null);
                count++;
            } catch (Exception ignored) {
                // one bad number must not stop the others
            }
        }
        return count;
    }

    private static void maybeAutoCall(Context app, List<Contact> contacts) {
        SharedPreferences p = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        if (!p.getBoolean(SettingsActivity.KEY_AUTO_CALL, false)) return;
        if (contacts.isEmpty() || !has(app, Manifest.permission.CALL_PHONE)) return;

        try {
            Intent call = new Intent(Intent.ACTION_CALL,
                    Uri.parse("tel:" + contacts.get(0).phone));   // list is sorted primary first
            call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(call);
        } catch (Exception ignored) {
        }
    }

    private static void startTracking(Context app) {
        if (!has(app, Manifest.permission.ACCESS_FINE_LOCATION)) return;
        try {
            TrackingService.start(app);
        } catch (Exception ignored) {
            // Android may block starting it from the background; the first SMS already went out
        }
    }

    private static boolean has(Context c, String permission) {
        return ContextCompat.checkSelfPermission(c, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static final String TYPE_FALSE_ALARM = "False alarm";

    /** Tells every contact it was a false alarm, stops live tracking and logs it. */
    public static void sendSafeMessage(Context context, Callback callback) {
        final Context app = context.getApplicationContext();
        TrackingService.stop(app);

        AppDatabase.executor.execute(() -> {
            AppDatabase db = AppDatabase.get(app);
            List<Contact> contacts = db.contactDao().getAllNow();
            int reached = sendSms(app, contacts,
                    "FALSE ALARM: I am safe. Please ignore my earlier emergency message. "
                            + "Sorry for the worry.");

            HistoryEntry entry = new HistoryEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.type = TYPE_FALSE_ALARM;
            entry.status = "Contacts told I am safe";
            entry.mapLink = "";
            db.historyDao().insert(entry);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onDone(reached);
            });
        });
    }
}