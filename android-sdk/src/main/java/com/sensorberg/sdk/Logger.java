package com.sensorberg.sdk;

import android.bluetooth.BluetoothAdapter;

import com.sensorberg.sdk.model.BeaconId;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.sdk.scanner.ScanEvent;

import java.util.HashMap;
import java.util.Map;

import static com.sensorberg.SensorbergSdk.blocked;

public class Logger {

    @SuppressWarnings("squid:S1444")
    public static Log log;

    private static final String TAG = "Sensorberg";

    public static boolean isVerboseLoggingEnabled() {
        return log != QUIET_LOG;
    }

    public interface Log {

        void beaconResolveState(ScanEvent event, String state);

        void beaconResolveState(BeaconEvent event, String state);

        void beaconSeenAgain(BeaconId beaconId);

        void logBluetoothState(int state);

        void userPresent();

        void applicationStateChanged(String message);

        void scannerStateChange(String what);

        void serviceHandlesMessage(String message);

        void logError(String s, Throwable cause);

        void logError(String s);

        void logServiceState(String message);

        void logSettingsUpdateState(String state);

        void verbose(String message);

        void debug(String message);

        void logBeaconHistoryPublisherState(String s);

        void geofence(String s);

        void geofenceError(String s, Throwable cause);

        void logAttributes(String s);
    }

    public static void enableVerboseLogging() {
        if (blocked()) {
            android.util.Log.w(TAG, "Beacon Sdk not compatible with Android oreo and above. All functionality disabled");
            return;
        }
        log = new VerboseLogger();
    }

    public static final Log QUIET_LOG = new Log() {
        @Override
        public void beaconResolveState(ScanEvent event, String state) {

        }

        @Override
        public void beaconResolveState(BeaconEvent event, String state) {

        }

        @Override
        public void beaconSeenAgain(BeaconId beaconId) {

        }

        @Override
        public void scannerStateChange(String state) {

        }

        @Override
        public void logBluetoothState(int state) {

        }

        @Override
        public void userPresent() {

        }

        @Override
        public void applicationStateChanged(String state) {

        }

        @Override
        public void serviceHandlesMessage(String message) {

        }

        @Override
        public void logError(String s, Throwable cause) {

        }

        @Override
        public void logError(String errorString) {

        }

        @Override
        public void logServiceState(String state) {

        }

        @Override
        public void logSettingsUpdateState(String state) {

        }

        @Override
        public void verbose(String message) {

        }

        @Override
        public void debug(String message) {

        }

        @Override
        public void logBeaconHistoryPublisherState(String s) {

        }

        @Override
        public void geofence(String s) {

        }

        @Override
        public void geofenceError(String s, Throwable cause) {

        }

        @Override
        public void logAttributes(String s) {

        }
    };

    static {
        if (BuildConfig.BUILD_TYPE.equalsIgnoreCase("debug")) {
            log = new VerboseLogger();
        } else {
            log = QUIET_LOG;
        }
    }

    static class VerboseLogger implements Log {

        VerboseLogger() {
        }

        public void beaconResolveState(ScanEvent event, String state) {
            android.util.Log.d(TAG, event.getBeaconId().toTraditionalString() + " has switched to state " + state);
        }

        @Override
        public void beaconResolveState(BeaconEvent event, String state) {
            android.util.Log.d(TAG, "showing an action for a beacon scanevent " + event.getBeaconId().toTraditionalString() + " state " + state);
        }

        @Override
        public void beaconSeenAgain(BeaconId beaconId) {
            android.util.Log.d(TAG, beaconId.toTraditionalString() + " was seen again ");
        }

        @Override
        public void scannerStateChange(String state) {
            android.util.Log.d(TAG, "scanner has changed state:" + state);
        }

        @Override
        public void serviceHandlesMessage(String message) {
            android.util.Log.d(TAG, "service is handling message:" + message);
        }

        @Override
        public void logError(String s, Throwable cause) {
            if (cause != null) {
                logError(s + cause.getMessage());
            } else {
                logError(s);
            }
        }

        @Override
        public void logError(String s) {
            android.util.Log.e(TAG, s);
        }

        @Override
        public void logServiceState(String state) {
            android.util.Log.d(TAG, "service state:" + state);
        }

        @Override
        public void logSettingsUpdateState(String state) {
            android.util.Log.d(TAG, "settings update finished message:\"" + state + "\"");
        }

        @Override
        public void verbose(String message) {
            android.util.Log.v(TAG, message);
        }

        @Override
        public void debug(String message) {
            android.util.Log.d(TAG, message);
        }

        @Override
        public void logBeaconHistoryPublisherState(String state) {
            android.util.Log.d(TAG, "beaconHistoryPublisher state:" + state);
        }

        @Override
        public void geofence(String s) {
            android.util.Log.d(TAG, "Geofences: "+s);
        }

        @Override
        public void geofenceError(String s, Throwable cause) {
            android.util.Log.e(TAG, "Geofences: "+s, cause);
        }

        @Override
        public void logBluetoothState(int state) {
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    android.util.Log.d(TAG, "Bluetooth off");
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    android.util.Log.d(TAG, "Turning Bluetooth off... stopping scans");
                    break;
                case BluetoothAdapter.STATE_ON:
                    android.util.Log.d(TAG, "Bluetooth on");
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    android.util.Log.d(TAG, "Turning Bluetooth on... restarting scans");
                    break;
            }
        }

        @Override
        public void userPresent() {
            android.util.Log.d(TAG, "User present");
        }

        @Override
        public void applicationStateChanged(String message) {
            android.util.Log.d(TAG, "application has changed state:" + message);
        }

        @Override
        public void logAttributes(String s) {
            android.util.Log.d(TAG, "Attributes: " + s);
        }
    }

    public static class VerboseLoggerSeenAgainSlowed extends VerboseLogger {

        private final Map<BeaconId, Integer> counter = new HashMap<>();

        private final int countNeeded;

        public VerboseLoggerSeenAgainSlowed(int countNeeded) {
            this.countNeeded = countNeeded;
        }

        @Override
        public void beaconSeenAgain(BeaconId beaconId) {
            Integer value = counter.get(beaconId);
            if (value == null) {
                value = 1;
            }
            counter.put(beaconId, value);
            if (counter.get(beaconId) % countNeeded == 0) {
                android.util.Log.d(TAG, beaconId.toTraditionalString() + " was seen again ");
            }

        }
    }
}
