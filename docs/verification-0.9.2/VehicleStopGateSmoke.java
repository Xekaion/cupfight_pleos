package io.autohmg.cupfight;

/** Standalone regression check; run with the production gate and DuelEngine. */
public final class VehicleStopGateSmoke {
    private static final long SECOND = 1_000_000_000L;
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static VehicleStopGate stopped() {
        VehicleStopGate gate = new VehicleStopGate();
        for (int i = 0; i <= 10; i++) {
            long time = SECOND + i * 100_000_000L;
            gate.onSpeed(0, time, time);
        }
        return gate;
    }

    public static void main(String[] args) {
        VehicleStopGate gate = new VehicleStopGate();
        check(!gate.isStopped(SECOND), "No speed signal must stay locked");
        gate.onSpeed(0, SECOND, SECOND);
        check(!gate.isStopped(SECOND), "One zero sample must not unlock");
        check(!gate.isStopped(SECOND * 2), "Elapsed time without samples must not unlock");
        gate = stopped();
        check(gate.isStopped(SECOND * 2), "One second of continuous zero speed unlocks");
        check(gate.isStopped(SECOND * 2 + 500_000_000L), "Freshness boundary");
        check(!gate.isStopped(SECOND * 2 + 500_000_001L), "Silent stream must lock");
        gate.onSpeed(0, SECOND * 3, SECOND * 3);
        check(!gate.isStopped(SECOND * 3), "A stream gap requires a new confirmation");
        gate = stopped();
        gate.onSpeed(0.02f, SECOND * 2 + 100_000_000L, SECOND * 2 + 100_000_000L);
        check(!gate.isStopped(SECOND * 2 + 100_000_000L), "Creeping locks immediately");
        gate = stopped();
        gate.onSpeed(-1, SECOND * 2 + 100_000_000L, SECOND * 2 + 100_000_000L);
        check(gate.isMoving(SECOND * 2 + 100_000_000L), "Reverse speed counts as movement");
        gate = stopped();
        check(!gate.onSpeed(0, SECOND, SECOND * 2), "Delayed sample rejected");
        check(!gate.isStopped(SECOND * 2), "Delayed zero cannot unlock");
        gate = stopped();
        gate.onSpeed(Float.NaN, SECOND * 3, SECOND * 3);
        check(!gate.isStopped(SECOND * 3), "Invalid speed locks");
        gate = stopped();
        gate.onSpeed(0, SECOND * 4, SECOND * 3);
        check(!gate.isStopped(SECOND * 3), "Future timestamp locks");
        gate = stopped();
        check(!gate.onSpeed(1, SECOND * 2, SECOND * 2), "Duplicate timestamp rejected");
        gate.invalidate();
        check(!gate.isStopped(SECOND * 2), "Permission loss/disconnect reset locks");

        DuelEngine duel = new DuelEngine();
        check(duel.offer(true, true, true), "Two stopped vehicles can be offered");
        duel.accept(DuelEngine.Driver.LOCAL);
        duel.accept(DuelEngine.Driver.OPPONENT);
        duel.onFrame(SECOND, 0, 0, 0, 0);
        duel.onFrame(SECOND + 100_000_000L, 0, 0, 0, 0);
        check(duel.getState() == DuelEngine.State.ACTIVE, "A traffic stop does not end a duel");
        System.out.println("PASS " + checks + " stop-gate and duel assertions");
    }
}
