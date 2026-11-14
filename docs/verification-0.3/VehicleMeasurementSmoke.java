package io.autohmg.cupfight;

import java.util.Locale;

public final class VehicleMeasurementSmoke {
    private static final long BASE_NANOS = 10_000_000_000L;
    private static final float TEN_KMH = 10f / 3.6f;

    private static long timestamp(long milliseconds) {
        return BASE_NANOS + milliseconds * 1_000_000L;
    }

    private static void speed(VehicleMeasurement measurement, long milliseconds, float speedMps) {
        measurement.onSpeed(speedMps, timestamp(milliseconds));
    }

    private static VehicleMeasurement.Result quantizedDrive(int warningMs) {
        VehicleMeasurement measurement = new VehicleMeasurement();
        measurement.start();
        for (int ms = 0; ms <= 5100; ms += 100) {
            speed(measurement, ms, ms >= 600 && ms < 3200 ? TEN_KMH : 0f);
            if (warningMs >= 0 && warningMs > ms - 100 && warningMs <= ms) {
                measurement.onForwardCollisionWarning(timestamp(warningMs));
            }
        }
        return measurement.finish();
    }

    private static void require(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }

    public static void main(String[] args) {
        VehicleMeasurement.Result unprotected = quantizedDrive(-1);
        require(unprotected.hasScore && unprotected.remaining > 20f && unprotected.remaining < 100f,
                "single 10 km/h steps exhausted cup or produced no score: " + unprotected.remaining);
        require(unprotected.protectedBrakes == 0 && unprotected.warnings == 0,
                "drive received protection without a warning");

        VehicleMeasurement.Result protectedBrake = quantizedDrive(4650);
        require(protectedBrake.hasScore && protectedBrake.remaining > unprotected.remaining + 15f
                        && protectedBrake.remaining < 100f && protectedBrake.protectedBrakes == 1
                        && protectedBrake.warnings == 1,
                "late warning did not refund brake without also refunding acceleration: "
                        + protectedBrake.remaining);

        VehicleMeasurement.Result outsideWindow = quantizedDrive(4900);
        require(outsideWindow.hasScore && outsideWindow.remaining == unprotected.remaining
                        && outsideWindow.protectedBrakes == 0 && outsideWindow.warnings == 1,
                "out-of-window warning granted protection");

        VehicleMeasurement measurement = new VehicleMeasurement();
        measurement.start();
        for (long ms : new long[]{0, 60, 140, 270, 345, 470, 590, 625, 780, 910, 1050}) {
            speed(measurement, ms, (float) (ms * 0.0025));
        }
        VehicleMeasurement.Result steadyAcceleration = measurement.finish();
        require(steadyAcceleration.hasScore && steadyAcceleration.remaining == 100f,
                "irregular timestamps created jerk under constant acceleration");

        measurement.start();
        speed(measurement, 0, 0f);
        speed(measurement, 300, 0f);
        speed(measurement, 1000, TEN_KMH);
        speed(measurement, 1300, TEN_KMH);
        require(!measurement.finish().hasScore, "discontinuous acceleration estimates scored a gap");

        System.out.printf(Locale.ROOT,
                "Vehicle measurement smoke passed. Quantized 0→10→0 km/h: %.1f%% unprotected, %.1f%% with late warning, %.1f%% with expired warning.%n",
                unprotected.remaining, protectedBrake.remaining, outsideWindow.remaining);
    }
}

