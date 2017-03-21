package com.sensorberg.sdk.settings;

import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.internal.interfaces.BeaconHistoryUploadIntervalListener;
import com.sensorberg.sdk.internal.interfaces.MessageDelayWindowLengthListener;
import com.sensorberg.sdk.internal.transport.interfaces.Transport;
import com.sensorberg.sdk.internal.transport.interfaces.TransportSettingsCallback;
import com.sensorberg.sdk.internal.transport.model.SettingsResponse;

import android.content.SharedPreferences;

import lombok.Setter;

public class SettingsManager {

    private final Transport transport;

    private final SharedPreferences preferences;

    @Setter
    private SettingsUpdateCallback settingsUpdateCallback = SettingsUpdateCallback.NONE;

    @Setter
    private MessageDelayWindowLengthListener messageDelayWindowLengthListener = MessageDelayWindowLengthListener.NONE;

    protected Settings settings;

    public SettingsManager(Transport trans, SharedPreferences prefs) {
        transport = trans;
        preferences = prefs;
        transport.setBeaconHistoryUploadIntervalListener(mBeaconHistoryUploadIntervalListener);
        updateSettings(new Settings(prefs));
    }

    protected void updateSettings(Settings stgs) {
        settings = stgs;
        messageDelayWindowLengthListener.setMessageDelayWindowLength(getMessageDelayWindowLength());

        settings.persistToPreferences(preferences);
    }

    public void updateSettingsFromNetwork() {
        transport.loadSettings(transportSettingsCallback);
    }

    private TransportSettingsCallback transportSettingsCallback = new TransportSettingsCallback() {
        @Override
        public void nothingChanged() {
            //all is good nothing to do
            Logger.log.logSettingsUpdateState("nothingChanged");
        }

        @Override
        public void onFailure(Exception e) {
            Logger.log.logSettingsUpdateState("onFailure");
            Logger.log.logError("settings update failed", e);
        }

        @Override
        public void onSettingsFound(SettingsResponse networkSettingsResponse) {
            Settings newSettings;

            if (networkSettingsResponse == null) {
                newSettings = new Settings();
                preferences.edit().clear().apply();
            } else {
                newSettings = new Settings(networkSettingsResponse.getRevision(), networkSettingsResponse.getSettings(), settingsUpdateCallback);
            }

            updateSettings(newSettings);
        }
    };

    private BeaconHistoryUploadIntervalListener mBeaconHistoryUploadIntervalListener = new BeaconHistoryUploadIntervalListener() {
        @Override
        public void historyUploadIntervalChanged(Long newHistoryUploadIntervalMillis) {
            if (newHistoryUploadIntervalMillis != settings.getHistoryUploadInterval()) {
                settings.setHistoryUploadInterval(newHistoryUploadIntervalMillis);
                settingsUpdateCallback.onHistoryUploadIntervalChange(newHistoryUploadIntervalMillis);
                settings.persistToPreferences(preferences);
            }
        }
    };

    private Settings getSettings() {
        return settings;
    }

    public long getExitTimeoutMillis() {
        return getSettings().getExitTimeoutMillis();
    }

    public long getExitForegroundGraceMillis() {
        return getSettings().getExitForegroundGraceMillis();
    }

    public long getExitBackgroundGraceMillis() {
        return getSettings().getExitBackgroundGraceMillis();
    }

    public long getCleanBeaconMapRestartTimeout() {
        return getSettings().getCleanBeaconMapRestartTimeout();
    }

    public long getForeGroundWaitTime() {
        return getSettings().getForeGroundWaitTime();
    }

    public long getForeGroundScanTime() {
        return getSettings().getForeGroundScanTime();
    }

    public long getBackgroundWaitTime() {
        return getSettings().getBackgroundWaitTime();
    }

    public long getBackgroundScanTime() {
        return getSettings().getBackgroundScanTime();
    }

    public long getGeohashMaxAge() {
        return getSettings().getGeohashMaxAge();
    }

    public int getGeohashMinAccuracyRadius() {
        return getSettings().getGeohashMinAccuracyRadius();
    }

    public long getGeofenceMinUpdateTime() {
        return getSettings().getGeofenceMinUpdateTime();
    }

    public int getGeofenceMinUpdateDistance() {
        return getSettings().getGeofenceMinUpdateDistance();
    }

    public int getGeofenceMaxDeviceSpeed() {
        return (getSettings().getGeofenceMaxDeviceSpeed() * 1000 / 3600); //to m/s
    }

    public int getGeofenceNotificationResponsiveness() {
        return getSettings().getGeofenceNotificationResponsiveness();
    }

    public boolean isShouldRestoreBeaconStates() {
        return getSettings().isShouldRestoreBeaconStates();
    }

    public int getMaxRetries() {
        return getSettings().getMaxRetries();
    }

    public long getMillisBetweenRetries() {
        return getSettings().getMillisBetweenRetries();
    }

    public long getLayoutUpdateInterval() {
        return getSettings().getLayoutUpdateInterval();
    }

    public long getSettingsUpdateInterval() {
        return getSettings().getSettingsUpdateInterval();
    }

    public long getHistoryUploadInterval() {
        return getSettings().getHistoryUploadInterval();
    }

    /**
     * Beacon report level.
     * REPORT_ALL = 0;
     * REPORT_ONLY_CONTAINED = 1;
     * REPORT_NONE = 2;
     *
     * @return the current set beacon report level
     */
    public int getBeaconReportLevel() {
        return getSettings().getBeaconReportLevel();
    }

    public int getScannerMinRssi() {
        return getSettings().getScannerMinRssi();
    }

    public int getScannerMaxDistance() {
        return getSettings().getScannerMaxDistance();
    }

    public long getMessageDelayWindowLength() {
        return getSettings().getMessageDelayWindowLength();
    }

    public Long getSettingsRevision() {
        return getSettings().getRevision();
    }
}
