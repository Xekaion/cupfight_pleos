package io.autohmg.cupfight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.autohmg.cupfight.DuelEngine.Driver.LOCAL;
import static io.autohmg.cupfight.DuelEngine.Driver.OPPONENT;

/** Replays synthetic inputs on one clock so the protected and unprotected runs can be compared. */
final class DriveSimulation {
    enum Scenario { SAFE_BRAKE, UNPROTECTED_BRAKE, ROUTE_SPLIT }

    static final long FRAME_MILLIS = 100;
    private static final int BRAKE_START = 61;
    private static final int BRAKE_RELEASE = 65;
    private static final int SAFETY_ALERT = 68;
    private static final int SEGMENT_END = 88;
    private static final int SPLIT = 38;
    private static final int PARK_DELAY = 18;

    private final Scenario scenario;
    private final ArrayList<ReplayFrame> replay = new ArrayList<>();
    private int frame;

    DriveSimulation(Scenario scenario) {
        this.scenario = scenario;
        replay.add(new ReplayFrame(100f, 100f, 0f, 0f, 0f, false));
    }

    int advance(DuelEngine duel) {
        frame++;
        if (duel.getState() != DuelEngine.State.ACTIVE) {
            return 0;
        }
        long timestamp = frame * FRAME_MILLIS * 1_000_000L;
        if (scenario == Scenario.SAFE_BRAKE && frame == SAFETY_ALERT) {
            float beforeWarning = duel.getRemaining(LOCAL);
            duel.onSafetyAlert(LOCAL, timestamp);
            if (duel.getProtectedBrakeCount(LOCAL) > 0) {
                applyBrakeProtection(duel.getRemaining(LOCAL) - beforeWarning);
            }
        }
        float localBraking = within(BRAKE_START, BRAKE_RELEASE) ? -2.9f : 0f;
        float localLateral = 0f;
        if (within(11, 16)) {
            localLateral = 0.8f;
        } else if (within(29, 34)) {
            localLateral = 1.5f;
        } else if (within(47, 52)) {
            localLateral = 2.6f;
        }
        float peerLateral = within(32, 37) ? 1.6f : 0f;
        duel.onFrame(timestamp, localBraking, localLateral, 0f, peerLateral);
        replay.add(new ReplayFrame(duel.getRemaining(LOCAL), duel.getRemaining(OPPONENT),
                localLateral, localBraking, peerLateral, false));

        if (frame == endFrame()) {
            duel.finish(scenario == Scenario.ROUTE_SPLIT
                    ? DuelEngine.EndReason.ROUTE_SPLIT : DuelEngine.EndReason.CORRIDOR_END);
            return 0;
        }
        // Keep braking cues silent around abrupt deceleration. A safety signal can arrive later.
        if (frame >= BRAKE_START && frame <= BRAKE_RELEASE) {
            return 0;
        }
        float spill = duel.getUnprotectedSpill(LOCAL);
        if (spill >= 3f) {
            return 3;
        }
        if (spill >= 1.7f) {
            return 2;
        }
        return spill >= 0.5f ? 1 : 0;
    }

    boolean hasParked() {
        return frame >= endFrame() + PARK_DELAY;
    }

    List<ReplayFrame> replayFrames() {
        return Collections.unmodifiableList(new ArrayList<>(replay));
    }

    ReplayFrame liveFrame() {
        ReplayFrame latest = replay.get(replay.size() - 1);
        // The safety scenario settles its pending brake loss when its delayed alert arrives.
        if (scenario == Scenario.SAFE_BRAKE && frame >= BRAKE_START && frame < SAFETY_ALERT) {
            float pending = replay.get(BRAKE_START - 1).localRemaining();
            return new ReplayFrame(pending, latest.opponentRemaining(), latest.localLateral(),
                    latest.localBraking(), latest.opponentLateral(), false);
        }
        return latest;
    }

    static List<ReplayFrame> preview() {
        DriveSimulation simulation = new DriveSimulation(Scenario.UNPROTECTED_BRAKE);
        DuelEngine engine = new DuelEngine();
        engine.offer(true, true, true);
        engine.accept(LOCAL);
        engine.accept(OPPONENT);
        while (!simulation.hasParked()) {
            simulation.advance(engine);
        }
        return simulation.replayFrames();
    }

    private void applyBrakeProtection(float refund) {
        float beforeBrake = replay.get(BRAKE_START - 1).localRemaining();
        for (int i = BRAKE_START; i < replay.size(); i++) {
            ReplayFrame sample = replay.get(i);
            float brakeLoss = Math.max(0f, beforeBrake - sample.localRemaining());
            replay.set(i, new ReplayFrame(sample.localRemaining() + Math.min(refund, brakeLoss),
                    sample.opponentRemaining(), sample.localLateral(), sample.localBraking(),
                    sample.opponentLateral(), i <= BRAKE_RELEASE));
        }
    }

    record ReplayFrame(float localRemaining, float opponentRemaining, float localLateral,
                       float localBraking, float opponentLateral, boolean protectedBrake) {}

    private int endFrame() {
        return scenario == Scenario.ROUTE_SPLIT ? SPLIT : SEGMENT_END;
    }

    private boolean within(int start, int end) {
        return frame >= start && frame < end;
    }
}
