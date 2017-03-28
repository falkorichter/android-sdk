package com.sensorberg.sdk.settings;

public final class SharedPreferencesKeys {

    public static final class Scanner {

        public static final String TIMEOUT_MILLIES = "com.sensorberg.preferences.scanner.exitTimeoutMillis";

        public static final String TIMEOUT_GRACE_FOREGROUND_MILLIES = "com.sensorberg.preferences.scanner.exitForegroundGraceMillis";

        public static final String TIMEOUT_GRACE_BACKGROUND_MILLIES = "com.sensorberg.preferences.scanner.exitBackgroundGraceMillis";

        public static final String FORE_GROUND_SCAN_TIME = "com.sensorberg.preferences.scanner.foreGroundScanTime";

        public static final String FORE_GROUND_WAIT_TIME = "com.sensorberg.preferences.scanner.foreGroundWaitTime";

        public static final String BACKGROUND_SCAN_TIME = "com.sensorberg.preferences.scanner.backgroundScanTime";

        public static final String BACKGROUND_WAIT_TIME = "com.sensorberg.preferences.scanner.backgroundWaitTime";

        public static final String CLEAN_BEACON_MAP_RESTART_TIMEOUT = "com.sensorberg.preferences.scanner.cleanBeaconMapRestartTimeout";

        public static final String SHOULD_RESTORE_BEACON_STATES = "com.sensorberg.preferences.settings.restoreBeaconStates";

        public static final String MIN_RSSI = "com.sensorberg.preferences.settings.scannerMinRssi";

        public static final String MAX_DISTANCE = "com.sensorberg.preferences.settings.scannerMaxDistance";

        private Scanner() {
            throw new IllegalAccessError("Utility class");
        }
    }

    public static final class Location {

        public static final String GEOHASH_MAX_AGE = "com.sensorberg.preferences.location.geohashMaxAge";

        public static final String GEOHASH_MIN_ACCURACY_RADIUS = "com.sensorberg.preferences.location.geohashMinAccuracyRadius";

        public static final String GEOFENCE_MIN_UPDATE_TIME = "com.sensorberg.preferences.geofence.minUpdateTime";

        public static final String GEOFENCE_MIN_UPDATE_DISTANCE = "com.sensorberg.preferences.geofence.minUpdateDistance";

        public static final String GEOFENCE_MAX_DEVICE_SPEED = "com.sensorberg.preferences.geofence.maxDeviceSpeed";

        public static final String GEOFENCE_NOTIFICATION_RESPONSIVENESS = "com.sensorberg.preferences.geofence.notificationResponsiveness";

        private Location() {
            throw new IllegalAccessError("Utility class");
        }
    }

    public static final class Settings {

        public static final String REVISION = "com.sensorberg.preferences.settings.revision";

        public static final String UPDATE_INTERVAL = "com.sensorberg.preferences.settings.updateInterval";

        public static final String MESSAGE_DELAY_WINDOW_LENGTH = "com.sensorberg.preferences.settings.messageDelayWindowLength";

        private Settings() {
            throw new IllegalAccessError("Utility class");
        }
    }

    public static final class Platform {

        public static final String POST_TO_SERVICE_COUNTER = "com.sensorberg.preferences.platform.serviceIntentCounter";

        public static final String CACHE_OBJECT_TIME_TO_LIVE = "com.sensorberg.preferences.platform.cacheObjectTimeToLive";

        private Platform() {
            throw new IllegalAccessError("Utility class");
        }
    }

    public static final class Network {

        public static final String MAX_RESOLVE_RETRIES = "com.sensorberg.preferences.settings.maxResolveRetries";

        public static final String TIME_BETWEEN_RESOLVE_RETRIES = "com.sensorberg.preferences.settings.timeBetweenResolveRetries";

        public static final String HISTORY_UPLOAD_INTERVAL = "com.sensorberg.preferences.settings.timeBetweenHistoryUploads";

        public static final String BEACON_LAYOUT_UPDATE_INTERVAL = "com.sensorberg.preferences.settings.timeBetweenBeaconLayoutUpdates";

        public static final String ADVERTISING_IDENTIFIER = "com.sensorberg.preferences.network.advertisingIdentifier";

        public static final String BEACON_REPORT_LEVEL = "com.sensorberg.preferences.network.beaconReportLevel";

        public static final String KEY_RESOLVE_RESPONSE = "com.sensorberg.preferences.transport.resolved";

        private Network() {
            throw new IllegalAccessError("Utility class");
        }
    }

    private SharedPreferencesKeys() {
        throw new IllegalAccessError("Utility class");
    }
}
