package com.sensorberg.sdk.scanner;

import android.bluetooth.BluetoothDevice;
import android.os.Message;
import android.util.Pair;

import com.sensorberg.SensorbergSdk;
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

    private final SettingsManager settingsManager;

    private final Clock clock;

    @Getter
    private final ServiceScheduler serviceScheduler;

    private final BluetoothPlatform bluetoothPlatform;

    private final CommonCallback scanCallback = new CommonCallback();

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

    private int errorCode = -1;     //-1 meaning no error

    private boolean errorInLastCycle = false;

    @Inject
    LocationHelper locationHelper;

    @Getter @Setter private RssiListener rssiListener = RssiListener.NONE;

    @Getter @Setter private ErrorListener errorListener = ErrorListener.NONE;

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

        SensorbergSdk.getComponent().inject(this);
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
        final long now = clock.now();
        lastExitCheckTimestamp = now;
        synchronized (enteredBeaconsMonitor) {
            if (enteredBeacons.size() > 0) {
                enteredBeacons.filter(new BeaconMap.Filter() {
                    public boolean filter(EventEntry beaconEntry, BeaconId beaconId) {
                        //might be negative!!!
                        long timeSinceWeSawTheBeacon = now - lastBreakLength - beaconEntry.getLastBeaconTime();
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
                    entry = new EventEntry(now, ScanEventType.ENTRY.getMask(), scanEvent.getPairingId());
                    Logger.log.beaconResolveState(scanEvent, "entered");
                } else {
                    entry = new EventEntry(now, entry.getEventMask(), entry.getPairingId());
                    Logger.log.beaconSeenAgain(beaconId);
                    if (this.rssiListener != RssiListener.NONE) {
                        runLoop.sendMessage(ScannerEvent.RSSI_UPDATED, new Pair<>(beaconId, rssi));
                    }
                }
                enteredBeacons.put(beaconId, entry);
            }
        }
    }

    public void onScanFailed(int errorCode) {
        runLoop.sendMessage(ScannerEvent.SCAN_FAILED, errorCode);
    }

    public void onScanWorking() {
        if (errorListener != ErrorListener.NONE && !errorInLastCycle && errorCode != -1) {
            //This cycle finished without error, but there was error before - scanning is ok now.
            errorCode = -1;
            errorListener.onScanWorking();
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
                onScanWorking();
                Logger.log.scannerStateChange("sleeping for" + waitTime + "millis");
                scheduleExecution(ScannerEvent.UN_PAUSE_SCAN, waitTime);
                runLoop.cancelFixedRateExecution();
                break;
            }
            case ScannerEvent.UN_PAUSE_SCAN: {
                lastScanStart = clock.now();
                lastBreakLength = clock.now() - lastExitCheckTimestamp;
                Logger.log.scannerStateChange("starting to scan again, scan break was " + lastBreakLength + "millis");
                if (scanning) {
                    Logger.log.debug("ScannerStatusUnpause" + Boolean.toString(scanning));
                    Logger.log.scannerStateChange("scanning for" + scanTime + "millis");
                    errorInLastCycle = false;
                    bluetoothPlatform.startLeScan(scanCallback);
                    scheduleExecution(ScannerEvent.PAUSE_SCAN, scanTime);

                    runLoop.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            loop();
                        }
                    }, 0, TimeConstants.ONE_SECOND);
                }
                break;
            }
            case ScannerEvent.SCAN_STOP_REQUESTED: {
                started = 0;
                scanning = false;
                clearScheduledExecutions();
                bluetoothPlatform.stopLeScan();
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
            case ScannerEvent.SCAN_FAILED: {
                errorInLastCycle = true;
                int errorCode = (int) queueEvent.getData();
                if (errorListener != ErrorListener.NONE && this.errorCode == -1) {
                    this.errorCode = errorCode;
                    errorListener.onScanFailed(errorCode);
                }
                break;
            }
            default: {
                throw new IllegalArgumentException("unhandled case " + queueEvent.getData());
            }
        }
    }

    protected abstract void clearScheduledExecutions();

    private void loop() {
        if (clock.now() > (started + settingsManager.getExitTimeoutMillis())) {
            if (bluetoothPlatform.isLeScanRunning()) {
                checkAndExitEnteredBeacons();
            }
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

    public class CommonCallback {

        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            AbstractScanner.this.onLeScan(device, rssi, scanRecord);
        }

        public void onScanFailed(int errorCode) {
            AbstractScanner.this.onScanFailed(errorCode);
        }
    }


    @Override
    public void hostApplicationInForeground() {
        if (isNotSetupForForegroundScanning()) {
            waitTime = settingsManager.getForeGroundWaitTime();
            scanTime = settingsManager.getForeGroundScanTime();
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

    public interface ErrorListener {
        ErrorListener NONE = new ErrorListener() {
            @Override
            public void onScanFailed(int errorCode) {

            }

            @Override
            public void onScanWorking() {

            }
        };

        void onScanFailed(int errorCode);
        void onScanWorking();
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
