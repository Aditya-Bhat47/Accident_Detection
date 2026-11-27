package com.example.accidentdetection.utils;

import android.location.Location;
import android.os.Build;

public class GpsFilter {
    public static float speedKmh(Location loc) {
        if (loc == null || !loc.hasSpeed()) return -1f;
        if (loc.hasAccuracy() && loc.getAccuracy() > 30f) return -1f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && loc.hasSpeedAccuracy()
                && loc.getSpeedAccuracyMetersPerSecond() > 3f) return -1f;
        return loc.getSpeed() * 3.6f;
    }
}