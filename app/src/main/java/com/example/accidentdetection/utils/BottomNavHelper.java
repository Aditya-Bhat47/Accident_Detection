package com.example.accidentdetection.utils;

import android.app.Activity;
import android.content.Intent;

import com.example.accidentdetection.R;
import com.example.accidentdetection.ui.ContactsActivity;
import com.example.accidentdetection.ui.HistoryActivity;
import com.example.accidentdetection.ui.MainActivity;
import com.example.accidentdetection.ui.ProfileActivity;
import com.example.accidentdetection.ui.SettingsActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class BottomNavHelper {

    public static void setup(Activity activity, BottomNavigationView nav, int selectedId) {
        nav.setSelectedItemId(selectedId);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) return true;

            Class<?> target;
            if (id == R.id.nav_home) target = MainActivity.class;
            else if (id == R.id.nav_contacts) target = ContactsActivity.class;
            else if (id == R.id.nav_history) target = HistoryActivity.class;
            else if (id == R.id.nav_profile) target = ProfileActivity.class;
            else target = SettingsActivity.class;

            Intent intent = new Intent(activity, target);
            if (id == R.id.nav_home) {
                // Reuse the existing Home instead of stacking a new one
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);

            // Home stays alive underneath; other tabs replace each other
            if (!(activity instanceof MainActivity)) activity.finish();
            return true;
        });
    }
}