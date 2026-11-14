package io.autohmg.cupfight;

/** A fresh continuous speed stream must confirm a full stop before controls unlock. */
final class VehicleStopGate {
    static final long STOP_CONFIRM_NANOS = 1_000_000_000L;
    static final long MAX_SAMPLE_AGE_NANOS = 500_000_000L;
    // Sensor noise tolerance only: 0.036 km/h. Creeping and reverse motion remain locked.
    static final float ZERO_SPEED_TOLERANCE_MPS = 0.01f;

    private long lastSample;
    private long stoppedSince;
    private boolean moving;

    boolean onSpeed(float speed, long timestamp, long now) {
        if (!Float.isFinite(speed) || timestamp <= 0 || timestamp > now
                || now - timestamp > MAX_SAMPLE_AGE_NANOS) {
            invalidate();
            return false;
        }
        if (timestamp <= lastSample) {
            return false;
        }
        if (lastSample == 0 || timestamp - lastSample > MAX_SAMPLE_AGE_NANOS) {
            stoppedSince = 0;
        }
        lastSample = timestamp;
        moving = Math.abs(speed) > ZERO_SPEED_TOLERANCE_MPS;
        if (moving) {
            stoppedSince = 0;
        } else if (stoppedSince == 0) {
            stoppedSince = timestamp;
        }
        return true;
    }

    boolean isStopped(long now) {
        return isFresh(now) && !moving && stoppedSince > 0
                && lastSample - stoppedSince >= STOP_CONFIRM_NANOS;
    }

    boolean isMoving(long now) {
        return isFresh(now) && moving;
    }

    boolean isFresh(long now) {
        return lastSample > 0 && now >= lastSample
                && now - lastSample <= MAX_SAMPLE_AGE_NANOS;
    }

    void invalidate() {
        lastSample = 0;
        stoppedSince = 0;
        moving = false;
    }
}
