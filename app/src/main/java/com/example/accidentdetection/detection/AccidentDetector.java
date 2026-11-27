package com.example.accidentdetection.detection;

import java.util.Locale;

/**
 * Multi-layer accident detector. Pure Java (no Android classes), so it can be unit tested.
 * All times are in milliseconds from ONE clock (use SystemClock.elapsedRealtime()).
 */
public class AccidentDetector {

    public static class Result {
        public final float peakG;
        public final float speedBeforeKmh;
        public final int score;
        public final boolean dropSignature;
        public final String summary;

        Result(float peakG, float speedBeforeKmh, int score, boolean dropSignature, String summary) {
            this.peakG = peakG;
            this.speedBeforeKmh = speedBeforeKmh;
            this.score = score;
            this.dropSignature = dropSignature;
            this.summary = summary;
        }
    }

    public interface Listener {
        void onAccident(Result result);

        /** Optional: tells you WHY an impact was ignored. Very useful while tuning. */
        default void onRejected(String reason) { }
    }

    private static final float G = 9.80665f;

    // Layer 2: "phone was dropped" signature (free fall just before the impact)
    private static final float FREEFALL_G = 0.6f;
    private static final long FREEFALL_MIN_MS = 80;
    private static final long FREEFALL_LINK_MS = 400;
    private static final int DROP_PENALTY = 30;          // set to 15 to let strong crashes through

    // Layer 3: was the phone really travelling in a vehicle?
    private static final float RIDING_KMH = 20f;
    private static final long RIDING_MIN_MS = 8000;       // at least 8 s at speed
    private static final long RIDING_GAP_MS = 3000;
    private static final long FAST_MEMORY_MS = 10_000;

    // Layer 4: speed after the impact (GPS reacts ~2 s late)
    private static final long SPEED_LAG_MS = 2500;
    private static final float STOPPED_KMH = 10f;
    private static final float RESUME_KMH = 15f;

    // Layer 5: stillness after the impact
    private static final float STILL_TOLERANCE_G = 0.15f;
    private static final float STILL_MIN_RATIO = 0.70f;
    private static final float STILL_GOOD_RATIO = 0.80f;

    // Timing
    private static final long SETTLE_MS = 1500;
    private static final long OBSERVE_MS = 4000;
    private static final long REARM_MS = 1000;
    private static final long COOLDOWN_MS = 60_000;

    // Layer 6: confidence score needed to raise an alert
    private static final int TRIGGER_SCORE = 80;

    private enum State { IDLE, SETTLING, OBSERVING }

    private final Listener listener;
    private final float threshold;
    private final boolean requireRiding;

    private State state = State.IDLE;
    private long blockedUntil = -1;

    private long ffStart = -1, lastFfDuration = 0, lastFfEnd = -1;
    private long rideStart = -1, lastFastTime = -1;
    private float lastFastKmh = 0;

    private long impactTime, observeStart;
    private float peakG, speedBefore, postMaxSpeed;
    private boolean dropSignature;
    private int stillSamples, totalSamples, postFixes;

    /**
     * @param sensitivity   0 = low, 1 = medium, 2 = high (from Settings)
     * @param requireRiding true for real use. false = demo mode (no GPS needed):
     *                      a hard shake followed by stillness can trigger it.
     */
    public AccidentDetector(int sensitivity, boolean requireRiding, Listener listener) {
        this.threshold = thresholdFor(sensitivity);
        this.requireRiding = requireRiding;
        this.listener = listener;
    }

    public static float thresholdFor(int sensitivity) {
        switch (sensitivity) {
            case 0:  return 4.5f;
            case 2:  return 2.5f;
            default: return 3.5f;
        }
    }

    // ------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------

    /** Every accelerometer reading (m/s squared, gravity included). */
    public void onAccelerometer(float x, float y, float z, long timeMs) {
        float g = (float) Math.sqrt(x * x + y * y + z * z) / G;
        trackFreeFall(g, timeMs);
        if (timeMs < blockedUntil) return;

        switch (state) {
            case IDLE:
                if (g >= threshold) onImpact(g, timeMs);
                break;
            case SETTLING:
                if (g > peakG) peakG = g;
                if (timeMs - impactTime >= SETTLE_MS) {
                    state = State.OBSERVING;
                    observeStart = timeMs;
                    stillSamples = 0;
                    totalSamples = 0;
                }
                break;
            case OBSERVING:
                totalSamples++;
                if (Math.abs(g - 1f) < STILL_TOLERANCE_G) stillSamples++;
                if (timeMs - observeStart >= OBSERVE_MS) evaluate(timeMs);
                break;
        }
    }

    /** Every GPS speed update in km/h (skip fixes without a speed or with poor accuracy). */
    public void onSpeed(float kmh, long timeMs) {
        if (kmh >= RIDING_KMH) {
            if (rideStart < 0 || timeMs - lastFastTime > RIDING_GAP_MS) rideStart = timeMs;
            lastFastTime = timeMs;
            lastFastKmh = kmh;
        }

        if (state == State.OBSERVING && timeMs - impactTime >= SPEED_LAG_MS) {
            postFixes++;
            if (kmh > postMaxSpeed) postMaxSpeed = kmh;
            if (requireRiding && kmh >= RESUME_KMH) {
                reject(timeMs, String.format(Locale.US,
                        "Ignored: still moving at %.0f km/h after the impact "
                                + "(phone fell inside the vehicle or a pothole)", kmh));
            }
        }
    }

    public void reset() {
        state = State.IDLE;
        blockedUntil = -1;
        rideStart = -1;
        lastFastTime = -1;
        ffStart = -1;
        lastFfEnd = -1;
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    /** Layer 2: remembers the most recent free-fall (accelerometer reads near 0 g). */
    private void trackFreeFall(float g, long t) {
        if (g < FREEFALL_G) {
            if (ffStart < 0) ffStart = t;
        } else if (ffStart >= 0) {
            lastFfDuration = t - ffStart;
            lastFfEnd = t;
            ffStart = -1;
        }
    }

    /** Layer 1 + 3: an impact happened; was the phone travelling at speed? */
    private void onImpact(float g, long t) {
        boolean wasRiding = rideStart >= 0
                && lastFastTime - rideStart >= RIDING_MIN_MS
                && t - lastFastTime <= FAST_MEMORY_MS;

        if (requireRiding && !wasRiding) {
            blockedUntil = t + REARM_MS;
            listener.onRejected(String.format(Locale.US,
                    "Ignored %.1fg impact: phone was not travelling at speed "
                            + "(dropped while stationary or walking)", g));
            return;
        }

        state = State.SETTLING;
        impactTime = t;
        peakG = g;
        speedBefore = requireRiding ? lastFastKmh : 0f;
        postFixes = 0;
        postMaxSpeed = 0f;
        dropSignature = lastFfEnd >= 0
                && lastFfDuration >= FREEFALL_MIN_MS
                && (t - lastFfEnd) <= FREEFALL_LINK_MS;
    }

    /** Layers 4, 5, 6: decide after watching the aftermath. */
    private void evaluate(long t) {
        float stillRatio = totalSamples == 0 ? 0f : (float) stillSamples / totalSamples;

        if (stillRatio < STILL_MIN_RATIO) {
            reject(t, String.format(Locale.US,
                    "Ignored: phone kept moving after the impact (%.0f%% still) "
                            + "- pothole, braking or the phone was picked up", stillRatio * 100));
            return;
        }
        if (requireRiding && postMaxSpeed >= RESUME_KMH) {
            reject(t, "Ignored: vehicle kept moving after the impact");
            return;
        }

        int score = 40;                                        // impact reached the threshold
        if (peakG >= threshold * 1.5f) score += 10;            // a really hard impact
        boolean stopVerified = !requireRiding || (postFixes > 0 && postMaxSpeed <= STOPPED_KMH);
        if (stopVerified) score += 30;                         // GPS confirms the stop
        score += stillRatio >= STILL_GOOD_RATIO ? 20 : 10;     // and it stays still
        if (dropSignature) score -= DROP_PENALTY;              // looks like a dropped phone

        if (score < TRIGGER_SCORE) {
            reject(t, String.format(Locale.US,
                    "Ignored: confidence %d/100 is below %d%s",
                    score, TRIGGER_SCORE, dropSignature ? " (free-fall = dropped phone)" : ""));
            return;
        }

        state = State.IDLE;
        blockedUntil = t + COOLDOWN_MS;
        String summary = String.format(Locale.US,
                "peak %.1fg, %.0f km/h before, confidence %d/100", peakG, speedBefore, score);
        listener.onAccident(new Result(peakG, speedBefore, score, dropSignature, summary));
    }

    private void reject(long t, String reason) {
        state = State.IDLE;
        blockedUntil = t + REARM_MS;
        listener.onRejected(reason);
    }
}