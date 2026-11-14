package io.autohmg.cupfight;

import java.util.ArrayDeque;

import static io.autohmg.cupfight.DuelEngine.Driver.LOCAL;
import static io.autohmg.cupfight.DuelEngine.EndReason.CORRIDOR_END;

/** A single vehicle's longitudinal test using timestamped speed from AAOS. */
final class VehicleMeasurement {
    private static final long MIN_SPEED_INTERVAL_NANOS = 20_000_000L;
    private static final long MAX_SPEED_INTERVAL_NANOS = 500_000_000L;
    private static final long SPEED_WINDOW_NANOS = 300_000_000L;

    private final DuelEngine engine = new DuelEngine();
    private final ArrayDeque<SpeedSample> speeds = new ArrayDeque<>();
    private boolean hasAcceleration;
    private boolean hasScore;
    private int warnings;
    private boolean running;

    void start() {
        engine.beginVehicleMeasurement();
        speeds.clear();
        hasAcceleration = false;
        hasScore = false;
        warnings = 0;
        running = true;
    }

    boolean isRunning() {
        return running;
    }

    void onSpeed(float metersPerSecond, long timestampNanos) {
        if (!running || !Float.isFinite(metersPerSecond) || timestampNanos <= 0) {
            return;
        }
        SpeedSample previous = speeds.peekLast();
        if (previous != null) {
            long interval = timestampNanos - previous.timestampNanos;
            if (interval < MIN_SPEED_INTERVAL_NANOS) {
                return;
            }
            if (interval > MAX_SPEED_INTERVAL_NANOS) {
                interruptSpeed();
            }
        }
        speeds.addLast(new SpeedSample(timestampNanos, metersPerSecond));
        long windowStart = timestampNanos - SPEED_WINDOW_NANOS;
        if (speeds.getFirst().timestampNanos > windowStart) {
            return;
        }

        // Keep the two samples bracketing the start so irregular intervals use the same window.
        SpeedSample beforeStart = speeds.removeFirst();
        while (speeds.getFirst().timestampNanos <= windowStart) {
            beforeStart = speeds.removeFirst();
        }
        SpeedSample afterStart = speeds.getFirst();
        speeds.addFirst(beforeStart);
        double fraction = (windowStart - beforeStart.timestampNanos)
                / (double) (afterStart.timestampNanos - beforeStart.timestampNanos);
        double startSpeed = beforeStart.metersPerSecond
                + ((double) afterStart.metersPerSecond - beforeStart.metersPerSecond) * fraction;
        float acceleration = (float) ((metersPerSecond - startSpeed) / (SPEED_WINDOW_NANOS / 1_000_000_000.0));
        if (!Float.isFinite(acceleration)) {
            interruptSpeed();
            return;
        }
        // Attribute the estimate to the middle of its interval on the vehicle clock.
        engine.onFrame(windowStart + SPEED_WINDOW_NANOS / 2, acceleration, 0, 0, 0);
        hasScore |= hasAcceleration;
        hasAcceleration = true;
    }

    void onForwardCollisionWarning(long timestampNanos) {
        if (running && timestampNanos > 0) {
            warnings++;
            engine.onSafetyAlert(LOCAL, timestampNanos);
        }
    }

    void interruptSpeed() {
        speeds.clear();
        hasAcceleration = false;
        engine.breakFrameSequence();
    }

    Result finish() {
        running = false;
        DuelEngine.Result score = engine.finish(CORRIDOR_END);
        return new Result(hasScore, score.localRemaining, warnings,
                engine.getProtectedBrakeCount(LOCAL));
    }

    private static final class SpeedSample {
        final long timestampNanos;
        final float metersPerSecond;

        SpeedSample(long timestampNanos, float metersPerSecond) {
            this.timestampNanos = timestampNanos;
            this.metersPerSecond = metersPerSecond;
        }
    }

    static final class Result {
        final boolean hasScore;
        final float remaining;
        final int warnings;
        final int protectedBrakes;

        Result(boolean hasScore, float remaining, int warnings, int protectedBrakes) {
            this.hasScore = hasScore;
            this.remaining = remaining;
            this.warnings = warnings;
            this.protectedBrakes = protectedBrakes;
        }
    }
}
