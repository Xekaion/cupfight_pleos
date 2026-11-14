package io.autohmg.cupfight;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Read-only AAOS signals for Pleos. Start and stop on the main thread. */
public final class PleosVehicleSignals {
    public enum Gear { PARK, DRIVE, OTHER, UNKNOWN }

    public enum Capability {
        CONNECTING,
        AVAILABLE,
        UNSUPPORTED,
        PERMISSION_REQUIRED,
        UNAVAILABLE
    }

    /** Event timestamps use the vehicle's elapsed-realtime clock, in nanoseconds. */
    public interface Listener {
        /** A zero timestamp means the gear was cleared without a vehicle timestamp. */
        void onGearChanged(Gear gear, long vehicleTimestampNanos);

        void onSpeedChanged(float metersPerSecond, long vehicleTimestampNanos);

        void onForwardCollisionWarning(long vehicleTimestampNanos);

        /** False also covers an unknown/disconnected platform restriction state. */
        void onInteractionAllowedChanged(boolean allowed);

        void onCapabilitiesChanged(Capability gear, Capability speed,
                                   Capability forwardCollisionWarning);
    }

    // Android VehiclePropertyIds documents this system property; it is absent from public SDK stubs.
    private static final int FORWARD_COLLISION_WARNING_STATE = 0x11401003;
    private static final String READ_ADAS_STATES = "android.car.permission.READ_ADAS_STATES";
    // android.hardware.automotive.vehicle.ForwardCollisionWarningState.aidl
    private static final int FCW_NO_WARNING = 1;
    private static final int FCW_WARNING = 2;

    private enum Signal {
        GEAR(VehiclePropertyIds.GEAR_SELECTION, Car.PERMISSION_POWERTRAIN, 0f),
        SPEED(VehiclePropertyIds.PERF_VEHICLE_SPEED, Car.PERMISSION_SPEED, 10f),
        FCW(FORWARD_COLLISION_WARNING_STATE, READ_ADAS_STATES, 0f);

        private final int id;
        private final String permission;
        private final float rateHz;

        Signal(int id, String permission, float rateHz) {
            this.id = id;
            this.permission = permission;
            this.rateHz = rateHz;
        }
    }

    private final Context context;
    private final Listener listener;
    private final EnumMap<Signal, Capability> capabilities = new EnumMap<>(Signal.class);
    private Connection connection;
    private Gear gear = Gear.UNKNOWN;

    public PleosVehicleSignals(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = Objects.requireNonNull(listener);
    }

    public void start() {
        if (connection != null) {
            return;
        }
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            setAllCapabilities(Capability.UNSUPPORTED);
            return;
        }
        try {
            Connection active = new Connection();
            connection = active;
            setAllCapabilities(Capability.CONNECTING);
            if (connection == active) {
                active.open();
            }
        } catch (LinkageError error) {
            disconnect();
            setAllCapabilities(Capability.UNSUPPORTED);
        } catch (RuntimeException error) {
            disconnect();
            setAllCapabilities(Capability.UNAVAILABLE);
        }
    }

    public void stop() {
        disconnect();
        setAllCapabilities(Capability.UNAVAILABLE);
    }

    private void disconnect() {
        Connection active = connection;
        connection = null;
        if (active != null) {
            active.close();
        }
    }

    private void setAllCapabilities(Capability status) {
        listener.onInteractionAllowedChanged(false);
        boolean changed = false;
        for (Signal signal : Signal.values()) {
            if (capabilities.put(signal, status) != status) {
                changed = true;
            }
        }
        clearGear(0);
        if (changed) {
            publishCapabilities();
        }
    }

    private void setCapability(Signal signal, Capability status) {
        Capability previous = capabilities.put(signal, status);
        if (previous != status) {
            publishCapabilities();
        }
    }

    private void publishCapabilities() {
        listener.onCapabilitiesChanged(capabilities.get(Signal.GEAR),
                capabilities.get(Signal.SPEED), capabilities.get(Signal.FCW));
    }

    private void clearGear(long timestampNanos) {
        if (gear != Gear.UNKNOWN) {
            gear = Gear.UNKNOWN;
            listener.onGearChanged(Gear.UNKNOWN, timestampNanos);
        }
    }

    private static Gear decodeGear(int value) {
        switch (value) {
            case VehicleGear.GEAR_PARK:
                return Gear.PARK;
            case VehicleGear.GEAR_DRIVE:
                return Gear.DRIVE;
            case VehicleGear.GEAR_NEUTRAL:
            case VehicleGear.GEAR_REVERSE:
            case VehicleGear.GEAR_FIRST:
            case VehicleGear.GEAR_SECOND:
            case VehicleGear.GEAR_THIRD:
            case VehicleGear.GEAR_FOURTH:
            case VehicleGear.GEAR_FIFTH:
            case VehicleGear.GEAR_SIXTH:
            case VehicleGear.GEAR_SEVENTH:
            case VehicleGear.GEAR_EIGHTH:
            case VehicleGear.GEAR_NINTH:
                return Gear.OTHER;
            default:
                return Gear.UNKNOWN;
        }
    }

    // Load the optional android.car library only when starting on AAOS.
    private final class Connection implements Car.CarServiceLifecycleListener {
        private Car car;
        private CarPropertyManager properties;
        private PropertyCallback callback;
        private CarUxRestrictionsManager uxRestrictions;

        private void open() {
            Car created = Car.createCar(context, new Handler(Looper.getMainLooper()),
                    Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, this);
            if (connection != this) {
                if (created != null) {
                    created.disconnect();
                }
                return;
            }
            car = created;
            if (car == null) {
                connection = null;
                setAllCapabilities(Capability.UNAVAILABLE);
            }
        }

        @Override
        public void onLifecycleChanged(Car service, boolean ready) {
            if (connection != this) {
                return;
            }
            unsubscribe();
            if (!ready) {
                setAllCapabilities(Capability.UNAVAILABLE);
                return;
            }
            setAllCapabilities(Capability.CONNECTING);
            try {
                Object manager = service.getCarManager(Car.PROPERTY_SERVICE);
                if (!(manager instanceof CarPropertyManager)) {
                    setAllCapabilities(Capability.UNAVAILABLE);
                    return;
                }
                properties = (CarPropertyManager) manager;
                Object uxManager = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                if (uxManager instanceof CarUxRestrictionsManager) {
                    uxRestrictions = (CarUxRestrictionsManager) uxManager;
                    uxRestrictions.registerListener(restrictions -> {
                        if (connection == this && uxRestrictions != null) {
                            listener.onInteractionAllowedChanged(
                                    !restrictions.isRequiresDistractionOptimization());
                        }
                    });
                    android.car.drivingstate.CarUxRestrictions current =
                            uxRestrictions.getCurrentCarUxRestrictions();
                    listener.onInteractionAllowedChanged(current != null
                            && !current.isRequiresDistractionOptimization());
                }
                Set<Integer> exposed = new HashSet<>();
                for (CarPropertyConfig<?> config : properties.getPropertyList()) {
                    exposed.add(config.getPropertyId());
                }
                callback = new PropertyCallback();
                for (Signal signal : Signal.values()) {
                    subscribe(signal, exposed);
                }
            } catch (RuntimeException error) {
                unsubscribe();
                setAllCapabilities(Capability.UNAVAILABLE);
            }
        }

        private void subscribe(Signal signal, Set<Integer> exposed) {
            if (context.checkSelfPermission(signal.permission) != PackageManager.PERMISSION_GRANTED) {
                setCapability(signal, Capability.PERMISSION_REQUIRED);
                return;
            }
            if (!exposed.contains(signal.id)) {
                setCapability(signal, Capability.UNSUPPORTED);
                return;
            }
            PropertyCallback activeCallback = callback;
            activeCallback.subscribed.add(signal);
            try {
                if (properties.registerCallback(activeCallback, signal.id, signal.rateHz)) {
                    return;
                }
                setCapability(signal, Capability.UNAVAILABLE);
            } catch (SecurityException error) {
                setCapability(signal, Capability.PERMISSION_REQUIRED);
            } catch (IllegalArgumentException error) {
                setCapability(signal, Capability.UNSUPPORTED);
            }
            activeCallback.subscribed.remove(signal);
        }

        private void unsubscribe() {
            if (uxRestrictions != null) {
                try {
                    uxRestrictions.unregisterListener();
                } catch (RuntimeException ignored) {
                    // The service may disappear while closing.
                }
                uxRestrictions = null;
            }
            PropertyCallback oldCallback = callback;
            callback = null;
            if (properties != null && oldCallback != null && !oldCallback.subscribed.isEmpty()) {
                try {
                    properties.unregisterCallback(oldCallback);
                } catch (RuntimeException ignored) {
                    // The service or permission can disappear before unregistering.
                }
            }
            properties = null;
        }

        private void close() {
            unsubscribe();
            Car closing = car;
            car = null;
            if (closing != null) {
                try {
                    closing.disconnect();
                } catch (RuntimeException ignored) {
                    // The connection may already have been closed by the system.
                }
            }
        }

        private final class PropertyCallback implements CarPropertyManager.CarPropertyEventCallback {
            private final EnumSet<Signal> subscribed = EnumSet.noneOf(Signal.class);
            private final long[] lastTimestampNanos = new long[Signal.values().length];
            private boolean fcwActive;

            @Override
            @SuppressWarnings({"rawtypes", "deprecation"})
            public void onChangeEvent(CarPropertyValue event) {
                if (connection != Connection.this || callback != this || event.getAreaId() != 0) {
                    return;
                }
                Signal signal = signalFor(event.getPropertyId());
                if (signal == null || !subscribed.contains(signal)) {
                    return;
                }
                long timestamp = event.getTimestamp();
                int index = signal.ordinal();
                if (timestamp > 0 && timestamp < lastTimestampNanos[index]) {
                    return;
                }
                if (event.getStatus() != CarPropertyValue.STATUS_AVAILABLE || timestamp <= 0) {
                    if (timestamp > 0) {
                        lastTimestampNanos[index] = timestamp;
                    }
                    unavailable(signal, timestamp);
                    return;
                }
                if (timestamp == lastTimestampNanos[index]) {
                    return;
                }
                lastTimestampNanos[index] = timestamp;
                Object value = event.getValue();
                switch (signal) {
                    case GEAR:
                        Gear decoded = value instanceof Integer ? decodeGear((Integer) value) : Gear.UNKNOWN;
                        if (decoded == Gear.UNKNOWN) {
                            unavailable(signal, timestamp);
                            return;
                        }
                        gear = decoded;
                        setCapability(signal, Capability.AVAILABLE);
                        listener.onGearChanged(decoded, timestamp);
                        break;
                    case SPEED:
                        if (!(value instanceof Float) || !Float.isFinite((Float) value)) {
                            unavailable(signal, timestamp);
                            return;
                        }
                        setCapability(signal, Capability.AVAILABLE);
                        listener.onSpeedChanged((Float) value, timestamp);
                        break;
                    case FCW:
                        if (!(value instanceof Integer)) {
                            unavailable(signal, timestamp);
                            return;
                        }
                        int state = (Integer) value;
                        if (state == FCW_WARNING || state == FCW_NO_WARNING) {
                            boolean previous = fcwActive;
                            fcwActive = state == FCW_WARNING;
                            setCapability(signal, Capability.AVAILABLE);
                            if (fcwActive && !previous) {
                                listener.onForwardCollisionWarning(timestamp);
                            }
                        } else {
                            unavailable(signal, timestamp);
                        }
                        break;
                }
            }

            private void unavailable(Signal signal, long timestampNanos) {
                if (signal == Signal.GEAR) {
                    clearGear(timestampNanos);
                }
                setCapability(signal, Capability.UNAVAILABLE);
            }

            @Override
            public void onErrorEvent(int propertyId, int areaId) {
                // This callback reports write failures. All subscriptions here are read-only.
            }

            private Signal signalFor(int propertyId) {
                switch (propertyId) {
                    case VehiclePropertyIds.GEAR_SELECTION:
                        return Signal.GEAR;
                    case VehiclePropertyIds.PERF_VEHICLE_SPEED:
                        return Signal.SPEED;
                    case FORWARD_COLLISION_WARNING_STATE:
                        return Signal.FCW;
                    default:
                        return null;
                }
            }
        }
    }
}
