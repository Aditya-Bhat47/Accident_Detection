package com.example.accidentdetection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.accidentdetection.detection.AccidentDetector;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AccidentDetectorTest {

    /** Feeds fake sensor data at 50 Hz and GPS at 1 Hz. */
    private static class Sim {
        final AccidentDetector detector;
        final List<AccidentDetector.Result> alerts = new ArrayList<>();
        final List<String> rejects = new ArrayList<>();
        long t = 1000, nextFix = 1000;

        Sim(int sensitivity, boolean requireRiding) {
            detector = new AccidentDetector(sensitivity, requireRiding,
                    new AccidentDetector.Listener() {
                        @Override public void onAccident(AccidentDetector.Result r) { alerts.add(r); }
                        @Override public void onRejected(String reason) { rejects.add(reason); }
                    });
        }

        /** g = magnitude in g, vib = +/- jitter, kmh = GPS speed reported in this period. */
        void run(long ms, float g, float vib, float kmh) {
            long end = t + ms;
            int i = 0;
            while (t < end) {
                t += 20;
                float v = g + ((i++ % 2 == 0) ? vib : -vib);
                detector.onAccelerometer(0, 0, v * 9.80665f, t);
                if (t >= nextFix) { detector.onSpeed(kmh, t); nextFix += 1000; }
            }
        }
    }

    @Test
    public void realCrash_raisesAlert() {
        Sim s = new Sim(1, true);
        s.run(12000, 1f, 0.25f, 40);   // riding at 40 km/h
        s.run(40, 6f, 0, 40);          // impact
        s.run(1240, 1f, 0.6f, 25);     // tumbling, GPS still catching up
        s.run(7000, 1f, 0.02f, 0);     // lying still
        assertEquals(1, s.alerts.size());
        assertTrue(s.alerts.get(0).score >= 80);
    }

    @Test
    public void phoneDropsOntoRoad_isIgnored() {
        Sim s = new Sim(1, true);
        s.run(12000, 1f, 0.25f, 40);
        s.run(300, 0.05f, 0, 40);      // free-fall
        s.run(40, 6f, 0, 40);          // lands
        s.run(1240, 1f, 0.6f, 25);
        s.run(7000, 1f, 0.02f, 0);
        assertTrue(s.alerts.isEmpty());
    }

    @Test
    public void phoneFallsInsideBag_riderKeepsRiding_isIgnored() {
        Sim s = new Sim(1, true);
        s.run(12000, 1f, 0.25f, 40);
        s.run(200, 0.05f, 0, 40);
        s.run(40, 5f, 0, 40);
        s.run(9000, 1f, 0.25f, 40);    // still at 40 km/h
        assertTrue(s.alerts.isEmpty());
    }

    @Test
    public void pothole_isIgnored() {
        Sim s = new Sim(1, true);
        s.run(12000, 1f, 0.25f, 40);
        s.run(40, 4.2f, 0, 40);
        s.run(9000, 1f, 0.25f, 40);
        assertTrue(s.alerts.isEmpty());
    }

    @Test
    public void dropWhileWalking_isIgnored() {
        Sim s = new Sim(1, true);
        s.run(10000, 1f, 0.3f, 5);
        s.run(40, 6f, 0, 5);
        s.run(7000, 1f, 0.02f, 0);
        assertTrue(s.alerts.isEmpty());
        assertFalse(s.rejects.isEmpty());
    }

    @Test
    public void crashButPhoneKeepsMoving_isIgnored() {
        Sim s = new Sim(1, true);
        s.run(12000, 1f, 0.25f, 40);
        s.run(40, 6f, 0, 40);
        s.run(1240, 1f, 0.6f, 25);
        s.run(7000, 1f, 0.5f, 0);      // phone being handled
        assertTrue(s.alerts.isEmpty());
    }

    @Test
    public void demoMode_shakeThenRest_triggers_butDropOnBed_doesNot() {
        Sim shake = new Sim(1, false);
        shake.run(3000, 1f, 0.02f, 0);
        shake.run(40, 5f, 0, 0);
        shake.run(1240, 1f, 0.5f, 0);
        shake.run(7000, 1f, 0.02f, 0);
        assertEquals(1, shake.alerts.size());

        Sim drop = new Sim(1, false);
        drop.run(3000, 1f, 0.02f, 0);
        drop.run(300, 0.05f, 0, 0);
        drop.run(40, 5f, 0, 0);
        drop.run(1240, 1f, 0.5f, 0);
        drop.run(7000, 1f, 0.02f, 0);
        assertTrue(drop.alerts.isEmpty());
    }
}
