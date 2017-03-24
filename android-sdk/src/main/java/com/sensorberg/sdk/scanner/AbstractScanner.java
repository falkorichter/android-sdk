package com.sensorberg.sdk.scanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Message;
import android.util.Pair;

import com.sensorberg.SensorbergSdk;
import com.sensorberg.sdk.Constants;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.internal.interfaces.Clock;
import com.sensorberg.sdk.internal.interfaces.FileManager;
import com.sensorberg.sdk.internal.interfaces.HandlerManager;
import com.sensorberg.sdk.internal.interfaces.Platform;
import com.sensorberg.sdk.internal.interfaces.RunLoop;
import com.sensorberg.sdk.internal.interfaces.ServiceScheduler;
import com.sensorberg.sdk.location.LocationHelper;
import com.sensorberg.sdk.model.BeaconId;
import com.sensorberg.sdk.settings.DefaultSettings;
import com.sensorberg.sdk.settings.SettingsManager;
import com.sensorberg.sdk.settings.TimeConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.UUID;

import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractScanner implements RunLoop.MessageHandlerCallback, Platform.ForegroundStateListener {

    private static final long NEVER_STOPPED = 0L;

    long waitTime;

    long scanTime;

    long exitGraceTime;

    private final SettingsManager settingsManager;

    private final Clock clock;

    @Getter
    private final ServiceScheduler serviceScheduler;

    private final BluetoothPlatform bluetoothPlatform;

    private final ScanCallback scanCallback = new ScanCallback();

    private final Object listenersMonitor = new Object();

    private final List<ScannerListener> listeners = new ArrayList<>();

    private final Object enteredBeaconsMonitor = new Object();

    private final BeaconMap enteredBeacons;

    @Getter
    private final RunLoop runLoop;

    private long lastStopTimestamp = NEVER_STOPPED; // this.platform.getClock().now(); // or 0L

    private long started;

    private boolean scanning;

    private long lastExitCheckTimestamp;

    private long lastBreakLength = 0;

    private long lastScanStart;

    private boolean running = false;

    private long start = 0;

    private long stop = 0;

    @Inject
    LocationHelper locationHelper;

    @Inject
    SharedPreferences prefs;

    @Getter @Setter private RssiListener rssiListener = RssiListener.NONE;

    AbstractScanner(SettingsManager stgMgr, boolean shouldRestoreBeaconStates, Clock clk, FileManager fileManager,
            ServiceScheduler scheduler, HandlerManager handlerManager, BluetoothPlatform btPlatform) {
        settingsManager = stgMgr;
        clock = clk;
        serviceScheduler = scheduler;
        scanning = false;
        runLoop = handlerManager.getScannerRunLoop(this);
        bluetoothPlatform = btPlatform;

        File beaconFile = shouldRestoreBeaconStates ? fileManager.getFile("enteredBeaconsCache") : null;
        enteredBeacons = new BeaconMap(fileManager, beaconFile);

        waitTime = settingsManager.getBackgroundWaitTime();
        scanTime = settingsManager.getBackgroundScanTime();
        exitGraceTime = settingsManager.getExitBackgroundGraceMillis();
        if (exitGraceTime >= scanTime) {
            exitGraceTime = scanTime / 2;
        }

        SensorbergSdk.getComponent().inject(this);

        start = prefs.getLong(Constants.SharedPreferencesKeys.Scanner.SCAN_START_TIMESTAMP, 0);
        stop = prefs.getLong(Constants.SharedPreferencesKeys.Scanner.SCAN_STOP_TIMESTAMP, 0);
    }

    /**
     * Adds a {@link ScannerListener} to the {@link List} of {@link ScannerListener}s.
     *
     * @param listener the {@link ScannerListener} to be added
     */
    public void addScannerListener(ScannerListener listener) {
        synchronized (listenersMonitor) {
            listeners.add(listener);
        }
    }

    private void checkAndExitEnteredBeacons() {
        synchronized (enteredBeaconsMonitor) {
            final long now = clock.now();
            lastExitCheckTimestamp = now;
            if (enteredBeacons.size() > 0) {
                enteredBeacons.filter(new BeaconMap.Filter() {
                    public boolean filter(EventEntry beaconEntry, BeaconId beaconId) {
                        long timeSinceWeSawTheBeacon = now - beaconEntry.getLastBeaconTime() - beaconEntry.getScanPauseTime();
                        if (timeSinceWeSawTheBeacon > settingsManager.getExitTimeoutMillis()) {
                            ScanEvent scanEvent = new ScanEvent(beaconId, now, false, locationHelper.getGeohash(), beaconEntry.getPairingId());
                            runLoop.sendMessage(ScannerEvent.EVENT_DETECTED, scanEvent);
                            Logger.log.beaconResolveState(scanEvent,
                                    " exited (time since we saw the beacon: " + (int) (timeSinceWeSawTheBeacon / 1000) + " seconds)");
                            return true;
                        }
                        return false;
                    }
                });
            }
        }
    }

    /**
     * Clears the {@link ScanEvent} cache.
     */
    @SuppressWarnings("WeakerAccess") //public API
    public void clearCache() {
        synchronized (enteredBeaconsMonitor) {
            enteredBeacons.clear();
        }
    }

    /**
     * Returns a flag indicating whether the {@link Scanner} is currently running.
     *
     * @return a flag indicating whether the {@link Scanner} is currently running
     */
    public boolean isScanRunning() {
        return scanning;
    }

    private void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

        if (settingsManager.getScannerMinRssi() != DefaultSettings.DEFAULT_SCANNER_MIN_RSSI &&
                rssi < settingsManager.getScannerMinRssi()) {
            return;
        }

        Pair<BeaconId, Integer> beacon = ScanHelper.getBeaconID(scanRecord);
        if (beacon != null) {

            int calRssi = beacon.second;
            if (settingsManager.getScannerMaxDistance() != DefaultSettings.DEFAULT_SCANNER_MAX_DISTANCE &&
                    getDistanceFromRSSI(rssi, calRssi) > settingsManager.getScannerMaxDistance()) {
                return;
            }

            BeaconId beaconId = beacon.first;
            synchronized (enteredBeaconsMonitor) {
                long now = clock.now();
                EventEntry entry = enteredBeacons.get(beaconId);

                if (entry == null) {
                    String address = device != null ? device.getAddress() : null;
                    ScanEvent scanEvent = new ScanEvent(beaconId, now, true, address, rssi, calRssi, locationHelper.getGeohash(), UUID.randomUUID().toString());
                    runLoop.sendMessage(ScannerEvent.EVENT_DETECTED, scanEvent);
                    entry = new EventEntry(now, 0, ScanEventType.ENTRY.getMask(), scanEvent.getPairingId());
                    Logger.log.beaconResolveState(scanEvent, "entered");
                } else {
                    entry = new EventEntry(now, 0, entry.getEventMask(), entry.getPairingId());
                    Logger.log.beaconSeenAgain(beaconId);
                    if (this.rssiListener != RssiListener.NONE) {
                        runLoop.sendMessage(ScannerEvent.RSSI_UPDATED, new Pair<>(beaconId, rssi));
                    }
                }
                enteredBeacons.put(beaconId, entry);
            }
        }
    }

    @Override
    public void handleMessage(Message message) {
        ScannerEvent queueEvent = new ScannerEvent(message.what, message.obj);
        switch (queueEvent.getType()) {
            case ScannerEvent.LOGICAL_SCAN_START_REQUESTED: {
                if (!scanning) {
                    lastExitCheckTimestamp = clock.now();
                    if (lastStopTimestamp != NEVER_STOPPED
                            && lastExitCheckTimestamp - lastStopTimestamp > settingsManager.getCleanBeaconMapRestartTimeout()) {
                        clearCache();
                        Logger.log.scannerStateChange("clearing the currently seen beacon, since we were turned off too long.");
                    }
                    started = clock.now();
                    scanning = true;
                    runLoop.sendMessage(ScannerEvent.UN_PAUSE_SCAN);
                }
                break;
            }
            case ScannerEvent.PAUSE_SCAN: {
                bluetoothPlatform.stopLeScan();
                synchronized (enteredBeaconsMonitor) {
                    if (running) {
                        running = false;
                        stop = clock.now();
                        prefs.edit().putLong(Constants.SharedPreferencesKeys.Scanner.SCAN_STOP_TIMESTAMP, stop).apply();
                    }
                }
                Logger.log.scannerStateChange("sleeping for " + waitTime + " millis");
                scheduleExecution(ScannerEvent.UN_PAUSE_SCAN, waitTime);
                runLoop.cancelFixedRateExecution();
                break;
            }
            case ScannerEvent.UN_PAUSE_SCAN: {
                lastScanStart = clock.now();
                lastBreakLength = clock.now() - lastExitCheckTimestamp;
                Logger.log.scannerStateChange("starting to scan again, scan break was " + lastBreakLength + " millis");
                if (scanning) {
                    Logger.log.debug("ScannerStatusUnpause" + Boolean.toString(scanning));
                    Logger.log.scannerStateChange("scanning for " + scanTime + " millis, exit grace time is "+exitGraceTime+" millis");
                    synchronized (enteredBeaconsMonitor) {
                        if (!running) {
                            running = true;
                            start = clock.now();
                            prefs.edit().putLong(Constants.SharedPreferencesKeys.Scanner.SCAN_START_TIMESTAMP, start).apply();
                        }
                        if (stop != 0) {
                            enteredBeacons.addScanPauseTime(start - stop);
                        }
                    }
                    bluetoothPlatform.startLeScan(scanCallback);
                    scheduleExecution(ScannerEvent.PAUSE_SCAN, scanTime);
                    runLoop.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            loop();
                        }
                    }, exitGraceTime, TimeConstants.ONE_SECOND);
                }
                break;
            }
            case ScannerEvent.SCAN_STOP_REQUESTED: {
                started = 0;
                scanning = false;
                clearScheduledExecutions();
                bluetoothPlatform.stopLeScan();
                synchronized (enteredBeaconsMonitor) {
                    if (running) {
                        running = false;
                        stop = clock.now();
                        prefs.edit().putLong(Constants.SharedPreferencesKeys.Scanner.SCAN_STOP_TIMESTAMP, stop).apply();
                    }
                }
                lastStopTimestamp = clock.now();
                runLoop.cancelFixedRateExecution();
                Logger.log.scannerStateChange("scan stopped");
                break;
            }
            case ScannerEvent.EVENT_DETECTED: {
                ScanEvent scanEvent = (ScanEvent) queueEvent.getData();
                synchronized (listenersMonitor) {
                    for (ScannerListener listener : listeners) {
                        listener.onScanEventDetected(scanEvent);
                    }
                }
                break;
            }
            case ScannerEvent.RSSI_UPDATED: {
                //noinspection unchecked -> see useage of ScannerEvent.RSSI_UPDATED
                Pair<BeaconId, Integer> value = (Pair<BeaconId, Integer>) queueEvent.getData();
                this.rssiListener.onRssiUpdated(value.first, value.second);
                break;

            }
            default: {
                throw new IllegalArgumentException("unhandled case " + queueEvent.getData());
            }
        }
    }

    protected abstract void clearScheduledExecutions();

    private void loop() {
        if (bluetoothPlatform.isLeScanRunning()) {
            checkAndExitEnteredBeacons();
        }
    }

    /**
     * Removes a {@link ScannerListener} from the {@link List} of {@link ScannerListener}s.
     *
     * @param listener the {@link ScannerListener} to be removed
     */
    public void removeScannerListener(ScannerListener listener) {
        synchronized (listenersMonitor) {
            listeners.remove(listener);
        }
    }


    /**
     * Starts scanning.
     */
    public void start() {
        Logger.log.debug("Scan: Scanner started");
        runLoop.sendMessage(ScannerEvent.LOGICAL_SCAN_START_REQUESTED);
    }


    /**
     * Stop the scanning.
     */
    public void stop() {
        Logger.log.debug("Scan: Scanner stopped");
        runLoop.sendMessage(ScannerEvent.SCAN_STOP_REQUESTED);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private class ScanCallback implements BluetoothAdapter.LeScanCallback {

        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            AbstractScanner.this.onLeScan(device, rssi, scanRecord);
        }
    }


    @Override
    public void hostApplicationInForeground() {
        if (isNotSetupForForegroundScanning()) {
            waitTime = settingsManager.getForeGroundWaitTime();
            scanTime = settingsManager.getForeGroundScanTime();
            exitGraceTime = settingsManager.getExitForegroundGraceMillis();
            if (exitGraceTime >= scanTime) {
                exitGraceTime = scanTime / 2;
            }
            if (scanning) {
                long lastWaitTime = clock.now() - lastExitCheckTimestamp;
                clearScheduledExecutions();
                if (lastWaitTime > waitTime) {
                    Logger.log.scannerStateChange("We have been waiting longer than the foreground wait time, so we´e going to scan right away");
                    runLoop.sendMessage(ScannerEvent.UN_PAUSE_SCAN);
                } else {
                    long timeRemainingToWait = waitTime - lastWaitTime;
                    Logger.log.scannerStateChange(
                            "We have been waiting longer than the foreground wait time, so we´e going to scan in " + timeRemainingToWait + " millis");
                    scheduleExecution(ScannerEvent.UN_PAUSE_SCAN, waitTime - lastWaitTime);
                }
            }
        }
    }

    abstract void scheduleExecution(int type, long delay);

    private boolean isNotSetupForForegroundScanning() {
        return waitTime != settingsManager.getForeGroundWaitTime() || scanTime != settingsManager.getForeGroundScanTime();
    }

    @Override
    public void hostApplicationInBackground() {
        waitTime = settingsManager.getBackgroundWaitTime();
        scanTime = settingsManager.getBackgroundScanTime();
        exitGraceTime = settingsManager.getExitBackgroundGraceMillis();
        if (exitGraceTime >= scanTime) {
            exitGraceTime = scanTime / 2;
        }
        if ((clock.now() - lastScanStart) > scanTime) {
            Logger.log.scannerStateChange("We have been scanning longer than the background scan, so we´e going to pause right away");
            clearScheduledExecutions();
            runLoop.sendMessage(ScannerEvent.PAUSE_SCAN);
        }
    }

    @SuppressWarnings("EmptyMethod")
    public interface RssiListener {

        RssiListener NONE = new RssiListener() {
            @Override
            public void onRssiUpdated(BeaconId beaconId, Integer rssiValue) {

            }
        };

        @SuppressWarnings("UnusedParameters")
        void onRssiUpdated(BeaconId beaconId, Integer rssiValue);
    }

    private static double getDistanceFromRSSI(double rssi, int calRssi) {
        double dist;
        double near = rssi / calRssi;
        if (near < 1.0f) {
            dist = Math.pow(near, 10);
        } else {
            dist =  ((0.89976f) * Math.pow(near, 7.7095f) + 0.111f);
        }
        return dist;
    }
}
