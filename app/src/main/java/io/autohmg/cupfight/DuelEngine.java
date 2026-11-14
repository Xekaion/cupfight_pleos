package io.autohmg.cupfight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Scores paired acceleration frames and runs one duel on a single event loop. */
public final class DuelEngine {
    public enum State { IDLE, OFFERED, ACTIVE, FINISHED }
    public enum Driver { LOCAL, OPPONENT }
    public enum EndReason { CORRIDOR_END, ROUTE_SPLIT }
    public enum Outcome { LOCAL_WIN, OPPONENT_WIN, DRAW }

    private static final float FULL_CUP = 100f;
    private static final long MAX_FRAME_GAP_NANOS = 500_000_000L;
    private static final long SAFE_BRAKE_WINDOW_NANOS = 1_500_000_000L;
    private static final float HARD_BRAKE_MPS2 = -2.5f;
    private static final double JERK_THRESHOLD = 2.5;
    private static final double SPILL_PERCENT_PER_ACCELERATION_CHANGE = 1.8;

    private State state = State.IDLE;
    private boolean localAccepted;
    private boolean opponentAccepted;
    private final DriverScore localScore = new DriverScore();
    private final DriverScore opponentScore = new DriverScore();
    private Result result;

    private boolean hasFrame;
    private boolean lastFrameValid;
    private long lastTimestampNanos;
    private float lastLocalLongitudinal;
    private float lastLocalLateral;
    private float lastOpponentLongitudinal;
    private float lastOpponentLateral;

    public boolean offer(boolean localStopped, boolean opponentStopped, boolean sharedRoute) {
        if (state != State.IDLE || !localStopped || !opponentStopped || !sharedRoute) {
            return false;
        }
        state = State.OFFERED;
        return true;
    }

    public boolean accept(Driver driver) {
        if (state != State.OFFERED || hasAccepted(driver)) {
            return false;
        }
        if (driver == Driver.LOCAL) {
            localAccepted = true;
        } else {
            opponentAccepted = true;
        }
        if (localAccepted && opponentAccepted) {
            state = State.ACTIVE;
        }
        return true;
    }

    public boolean cancelOffer() {
        if (state != State.OFFERED) {
            return false;
        }
        reset();
        return true;
    }

    void beginVehicleMeasurement() {
        reset();
        state = State.ACTIVE;
    }

    void breakFrameSequence() {
        lastFrameValid = false;
    }

    /** Acceleration samples in m/s² on a shared monotonic clock. */
    public void onFrame(long timestampNanos, float localLongitudinal, float localLateral,
                        float opponentLongitudinal, float opponentLateral) {
        long intervalNanos = timestampNanos - lastTimestampNanos;
        if (state != State.ACTIVE || (hasFrame && intervalNanos <= 0)) {
            return;
        }
        boolean valid = Float.isFinite(localLongitudinal) && Float.isFinite(localLateral)
                && Float.isFinite(opponentLongitudinal) && Float.isFinite(opponentLateral);
        // A delayed or invalid frame breaks the scoring interval for both participants.
        boolean canScore = hasFrame && lastFrameValid && valid && intervalNanos <= MAX_FRAME_GAP_NANOS;
        double threshold = canScore ? JERK_THRESHOLD * (intervalNanos / 1_000_000_000.0) : 0;
        localScore.onFrame(timestampNanos, localLongitudinal, localLateral,
                lastLocalLongitudinal, lastLocalLateral, valid, canScore, threshold);
        opponentScore.onFrame(timestampNanos, opponentLongitudinal, opponentLateral,
                lastOpponentLongitudinal, lastOpponentLateral, valid, canScore, threshold);
        hasFrame = true;
        lastFrameValid = valid;
        lastTimestampNanos = timestampNanos;
        lastLocalLongitudinal = localLongitudinal;
        lastLocalLateral = localLateral;
        lastOpponentLongitudinal = opponentLongitudinal;
        lastOpponentLateral = opponentLateral;
    }

    /** Records a simulated safety alert on the same clock as the acceleration frames. */
    public void onSafetyAlert(Driver driver, long timestampNanos) {
        if (state == State.ACTIVE) {
            score(driver).onSafetyAlert(timestampNanos);
        }
    }

    /** Counts distinct hard-brake episodes matched by one or more safety alerts. */
    public int getProtectedBrakeCount(Driver driver) {
        return score(driver).protectedBrakeCount;
    }

    /** Current penalty on the latest frame in percentage points, before the cup's zero floor. */
    public float getUnprotectedSpill(Driver driver) {
        return score(driver).unprotectedFrameSpill();
    }

    public Result finish(EndReason reason) {
        if (state != State.ACTIVE) {
            return result;
        }
        Objects.requireNonNull(reason);
        float localRemaining = localScore.remaining();
        float opponentRemaining = opponentScore.remaining();
        Outcome outcome = Outcome.DRAW;
        if (localRemaining > opponentRemaining) {
            outcome = Outcome.LOCAL_WIN;
        } else if (localRemaining < opponentRemaining) {
            outcome = Outcome.OPPONENT_WIN;
        }
        result = new Result(reason, outcome, localRemaining, opponentRemaining);
        state = State.FINISHED;
        return result;
    }

    public void reset() {
        state = State.IDLE;
        localAccepted = false;
        opponentAccepted = false;
        localScore.reset();
        opponentScore.reset();
        hasFrame = false;
        result = null;
    }

    public State getState() {
        return state;
    }

    public boolean hasAccepted(Driver driver) {
        return Objects.requireNonNull(driver) == Driver.LOCAL ? localAccepted : opponentAccepted;
    }

    public float getRemaining(Driver driver) {
        return score(driver).remaining();
    }

    public Result getResult() {
        return result;
    }

    private DriverScore score(Driver driver) {
        return Objects.requireNonNull(driver) == Driver.LOCAL ? localScore : opponentScore;
    }

    private static double spillFor(double change, double threshold) {
        return Math.max(0, change - threshold) * SPILL_PERCENT_PER_ACCELERATION_CHANGE;
    }

    private static final class DriverScore {
        private final List<Long> recentAlerts = new ArrayList<>();
        private final List<BrakeEpisode> recentBrakes = new ArrayList<>();
        private double settledSpill;
        private int protectedBrakeCount;
        private BrakeEpisode activeBrake;
        private BrakeEpisode lastFrameBrake;
        private double lastFrameSpill;
        private double lastFrameLateralSpill;

        private void onFrame(long timestampNanos, float longitudinal, float lateral,
                             float previousLongitudinal, float previousLateral,
                             boolean valid, boolean canScore, double threshold) {
            if (!canScore) {
                activeBrake = null;
            }
            expireOldEvents(timestampNanos);
            lastFrameBrake = null;
            lastFrameSpill = 0;
            lastFrameLateralSpill = 0;
            if (!valid) {
                return;
            }

            boolean braking = longitudinal <= HARD_BRAKE_MPS2;
            if (braking) {
                if (activeBrake == null) {
                    activeBrake = new BrakeEpisode(timestampNanos);
                    recentBrakes.add(activeBrake);
                }
                activeBrake.lastHardNanos = timestampNanos;
                for (long alertNanos : recentAlerts) {
                    if (protectIfMatched(activeBrake, alertNanos)) {
                        break;
                    }
                }
            }
            if (canScore) {
                double lateralChange = (double) lateral - previousLateral;
                lastFrameSpill = spillFor(
                        Math.hypot((double) longitudinal - previousLongitudinal, lateralChange), threshold);
                if (activeBrake == null) {
                    settledSpill += lastFrameSpill;
                } else {
                    // Keep the cornering loss, including on the frame that releases the brake.
                    lastFrameLateralSpill = spillFor(Math.abs(lateralChange), threshold);
                    settledSpill += lastFrameLateralSpill;
                    lastFrameBrake = activeBrake;
                    activeBrake.spill += lastFrameSpill - lastFrameLateralSpill;
                }
            }
            if (!braking) {
                activeBrake = null;
            }
        }

        private void onSafetyAlert(long timestampNanos) {
            recentAlerts.add(timestampNanos);
            for (BrakeEpisode episode : recentBrakes) {
                protectIfMatched(episode, timestampNanos);
            }
        }

        private boolean protectIfMatched(BrakeEpisode episode, long alertNanos) {
            if (episode.protectedBrake) {
                return true;
            }
            if (alertNanos - episode.firstHardNanos < -SAFE_BRAKE_WINDOW_NANOS
                    || alertNanos - episode.lastHardNanos > SAFE_BRAKE_WINDOW_NANOS) {
                return false;
            }
            episode.protectedBrake = true;
            protectedBrakeCount++;
            return true;
        }

        private void expireOldEvents(long timestampNanos) {
            recentAlerts.removeIf(alert -> timestampNanos - alert > SAFE_BRAKE_WINDOW_NANOS);
            Iterator<BrakeEpisode> iterator = recentBrakes.iterator();
            while (iterator.hasNext()) {
                BrakeEpisode episode = iterator.next();
                if (episode != activeBrake && timestampNanos - episode.lastHardNanos > SAFE_BRAKE_WINDOW_NANOS) {
                    if (!episode.protectedBrake) {
                        settledSpill += episode.spill;
                    }
                    iterator.remove();
                }
            }
        }

        private float remaining() {
            // Keep provisional braking losses separate so a late alert can restore them even after zero.
            double spill = settledSpill;
            for (BrakeEpisode episode : recentBrakes) {
                if (!episode.protectedBrake) {
                    spill += episode.spill;
                }
            }
            return (float) Math.max(0, FULL_CUP - spill);
        }

        private float unprotectedFrameSpill() {
            double spill = lastFrameBrake != null && lastFrameBrake.protectedBrake
                    ? lastFrameLateralSpill : lastFrameSpill;
            return (float) Math.min(FULL_CUP, spill);
        }

        private void reset() {
            recentAlerts.clear();
            recentBrakes.clear();
            settledSpill = 0;
            protectedBrakeCount = 0;
            activeBrake = null;
            lastFrameBrake = null;
            lastFrameSpill = 0;
            lastFrameLateralSpill = 0;
        }
    }

    private static final class BrakeEpisode {
        private final long firstHardNanos;
        private long lastHardNanos;
        private double spill;
        private boolean protectedBrake;

        private BrakeEpisode(long timestampNanos) {
            firstHardNanos = timestampNanos;
            lastHardNanos = timestampNanos;
        }
    }

    public static final class Result {
        public final EndReason reason;
        public final Outcome outcome;
        public final float localRemaining;
        public final float opponentRemaining;

        private Result(EndReason reason, Outcome outcome, float localRemaining,
                       float opponentRemaining) {
            this.reason = reason;
            this.outcome = outcome;
            this.localRemaining = localRemaining;
            this.opponentRemaining = opponentRemaining;
        }
    }
}
