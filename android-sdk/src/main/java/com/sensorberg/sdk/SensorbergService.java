package com.sensorberg.sdk;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.sensorberg.SensorbergSdk;
import com.sensorberg.sdk.internal.PermissionChecker;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.internal.interfaces.Clock;
import com.sensorberg.sdk.internal.interfaces.FileManager;
import com.sensorberg.sdk.internal.interfaces.HandlerManager;
import com.sensorberg.sdk.internal.interfaces.Platform;
import com.sensorberg.sdk.internal.interfaces.PlatformIdentifier;
import com.sensorberg.sdk.internal.interfaces.ServiceScheduler;
import com.sensorberg.sdk.internal.transport.interfaces.Transport;
import com.sensorberg.sdk.location.GeofenceData;
import com.sensorberg.sdk.model.persistence.ActionConversion;
import com.sensorberg.sdk.receivers.GenericBroadcastReceiver;
import com.sensorberg.sdk.receivers.ScannerBroadcastReceiver;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.sdk.resolver.ResolverConfiguration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import static com.sensorberg.sdk.SensorbergServiceMessage.MSG_ATTRIBUTES;

@SuppressWarnings({"WeakerAccess", "pmd:TooManyMethods", "squid:S1200"})
public class SensorbergService extends Service {

    @Inject
    protected FileManager fileManager;

    @Inject
    protected ServiceScheduler serviceScheduler;

    @Inject
    @Named("realHandlerManager")
    protected HandlerManager handlerManager;

    @Inject
    protected Clock clock;

    @Inject
    @Named("androidBluetoothPlatform")
    protected BluetoothPlatform bluetoothPlatform;

    @Inject
    @Named("realTransport")
    protected Transport transport;

    @Inject
    @Named("androidPlatformIdentifier")
    protected PlatformIdentifier platformIdentifier;

    @Inject
    @Named("androidPlatform")
    protected Platform platform;

    @Inject Gson gson;
    @Inject SharedPreferences preferences;

    protected MessengerList presentationDelegates = new MessengerList();

    protected InternalApplicationBootstrapper bootstrapper;

    @Override
    public void onCreate() {
        super.onCreate();
        //we need to init this because SensorbergService can be started outside of SensorbergSdk constructor
        //(like for example when called from BroadcastReceiver)
        SensorbergSdk.init(getBaseContext());
        SensorbergSdk.getComponent().inject(this);

        Logger.log.logServiceState("onCreate");
    }

    protected void logError(String message) {
        Logger.log.logError(message);
    }

    protected void logError(String message, Exception e) {
        Logger.log.logError(message, e);
    }

    @SuppressWarnings("checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.NPathComplexityCheck")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.log.logServiceState("onStartCommand");
        if (intent != null && intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, 0) == MSG_ATTRIBUTES) {
            //Save attributes regardless of further processing.
            saveAttributes(intent);
        }

        if (!bluetoothPlatform.isBluetoothLowEnergySupported()) {
            logError("isBluetoothLowEnergySupported not true, shutting down.");
            return stopSensorbergService();
        }

        if (!platform.registerBroadcastReceiver()) {
            logError("no BroadcastReceiver registered for Action:com.sensorberg.android.PRESENT_ACTION");
            return stopSensorbergService();
        }

        if (!new PermissionChecker(this).hasScanPermissionCheckAndroid6()) {
            logError("no permission to scan");
            if(intent != null){
                String apikey = intent.getStringExtra(SensorbergServiceMessage.EXTRA_API_KEY);
                if (apikey != null && !apikey.isEmpty()) {
                    ResolverConfiguration configuration = new ResolverConfiguration();
                    configuration.setApiToken(apikey);
                    persistConfiguration(configuration);
                }
            }
            return stopSensorbergService();
        }

        //since we want to start or restart, it's a good idea to try and init the libraries again
        SensorbergSdk.init(getBaseContext());

        if (intent == null) {
            return restartSensorbergService();
        } else {
            return handleIntent(intent);
        }
    }

    protected int handleIntent(Intent intent) {
        Logger.log.serviceHandlesMessage(
                SensorbergServiceMessage.stringFrom(intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, -1)));

        handleDebuggingIntent(intent);

        if (handleIntentEvenIfNoBootstrapperPresent(intent)) {
            return stopSensorbergService();
        }

        if (bootstrapper == null) {
            updateDiskConfiguration(intent);
        }

        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_START_SERVICE)) {
            return startSensorbergService(intent.getStringExtra(SensorbergServiceMessage.EXTRA_API_KEY));
        }

        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE)) {
            return handleIntentMessage(intent);
        }

        return START_STICKY;
    }

    protected int startSensorbergService(String apiKey) {
        if (bootstrapper == null && (!TextUtils.isEmpty(apiKey))) {
            ResolverConfiguration configuration = new ResolverConfiguration();
            configuration.setApiToken(apiKey);
            bootstrapper = createBootstrapper(configuration);
            persistConfiguration(bootstrapper.resolver.configuration);
            bootstrapper.startScanning();
            return START_STICKY;
        } else if (bootstrapper != null) {
            bootstrapper.startScanning();
            logError("start intent was sent, but the scanner was already set up");
            return START_STICKY;
        } else {
            logError("Intent to start the service was not correctly sent. not starting the service");
            return stopSensorbergService();
        }
    }

    protected int restartSensorbergService() {
        logError("there was no intent in onStartCommand we must assume we are beeing restarted due to a kill event");
        bootstrapper = createBootstrapperFromDiskConfiguration();
        if (bootstrapper != null) {
            bootstrapper.startScanning();
        }
        return START_STICKY;
    }

    protected int stopSensorbergService() {
        stopSelf();
        return START_NOT_STICKY;
    }

    protected void handleDebuggingIntent(Intent intent) {
        switch (intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, -1)) {
            case SensorbergServiceMessage.MSG_TYPE_DISABLE_LOGGING: {
                Logger.log.verbose("Logging Disabled");
                Logger.log = Logger.QUIET_LOG;
                transport.setLoggingEnabled(false);
                break;
            }
            case SensorbergServiceMessage.MSG_TYPE_ENABLE_LOGGING: {
                Logger.log.verbose("Logging Enabled");
                Logger.enableVerboseLogging();
                transport.setLoggingEnabled(true);
                break;
            }
        }
    }

    protected SensorbergServiceConfiguration loadOrCreateNewServiceConfiguration(FileManager fileManager) {
        SensorbergServiceConfiguration diskConf = SensorbergServiceConfiguration.loadFromDisk(fileManager);

        if (diskConf == null) {
            diskConf = new SensorbergServiceConfiguration(new ResolverConfiguration());
        } else if (diskConf.resolverConfiguration == null) {
            diskConf.resolverConfiguration = new ResolverConfiguration();
        }

        return diskConf;
    }

    protected void updateDiskConfiguration(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE)) {
            int type = intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, -1);
            SensorbergServiceConfiguration diskConf = loadOrCreateNewServiceConfiguration(fileManager);

            Logger.log.serviceHandlesMessage(SensorbergServiceMessage.stringFrom(type));

            switch (type) {
                case SensorbergServiceMessage.MSG_SET_API_TOKEN: {
                    if (intent.hasExtra(SensorbergServiceMessage.MSG_SET_API_TOKEN_TOKEN)) {
                        String apiToken = intent.getStringExtra(SensorbergServiceMessage.MSG_SET_API_TOKEN_TOKEN);
                        diskConf.resolverConfiguration.setApiToken(apiToken);
                    }
                    break;
                }
                case SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER: {
                    if (intent.hasExtra(SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER_ADVERTISING_IDENTIFIER)) {
                        String advertisingIdentifier = intent.getStringExtra(
                                SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER_ADVERTISING_IDENTIFIER);
                        diskConf.resolverConfiguration.setAdvertisingIdentifier(advertisingIdentifier);
                    }
                    break;
                }
            }

            diskConf.writeToDisk(fileManager);
        }
    }

    protected boolean handleIntentEvenIfNoBootstrapperPresent(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE)) {
            int type = intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, -1);
            switch (type) {
                case SensorbergServiceMessage.MSG_SHUTDOWN: {
                    Logger.log.serviceHandlesMessage(SensorbergServiceMessage.stringFrom(type));
                    MinimalBootstrapper minimalBootstrapper = bootstrapper != null ? bootstrapper : new MinimalBootstrapper(serviceScheduler);
                    SensorbergServiceConfiguration.removeConfigurationFromDisk(fileManager);
                    ScannerBroadcastReceiver.setManifestReceiverEnabled(false, this);
                    GenericBroadcastReceiver.setManifestReceiverEnabled(false, this);

                    minimalBootstrapper.unscheduleAllPendingActions();
                    minimalBootstrapper.stopScanning();
                    minimalBootstrapper.stopAllScheduledOperations();
                    bootstrapper = null;
                    return true;
                }
            }
        }
        return false;
    }

    protected InternalApplicationBootstrapper createBootstrapperFromDiskConfiguration() {
        InternalApplicationBootstrapper newBootstrapper = null;

        try {
            SensorbergServiceConfiguration diskConf = SensorbergServiceConfiguration.loadFromDisk(fileManager);
            if (diskConf != null && diskConf.isComplete()) {
                newBootstrapper = createBootstrapper(diskConf.resolverConfiguration);
            } else {
                logError("configuration from disk could not be loaded or is not complete");
            }
        } catch (Exception e) {
            logError("something went wrong when loading the configuration from disk", e);
        }

        return newBootstrapper;
    }

    private InternalApplicationBootstrapper createBootstrapper(ResolverConfiguration resolverConfiguration) {
        InternalApplicationBootstrapper newBootstrapper = new InternalApplicationBootstrapper(transport, serviceScheduler, handlerManager, clock,
                bluetoothPlatform, resolverConfiguration);
        return newBootstrapper;
    }

    private void persistConfiguration(ResolverConfiguration resolverConfiguration) {
        SensorbergServiceConfiguration conf = new SensorbergServiceConfiguration(resolverConfiguration);
        conf.writeToDisk(fileManager);
    }

    protected int handleIntentMessage(Intent intent) {
        int what = intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, -1);
        Logger.log.serviceHandlesMessage(SensorbergServiceMessage.stringFrom(what));

        if (!isBootstrapperInitialized()) {
            logError("couldn't start the SDK!");
            return stopSensorbergService();
        }

        switch (what) {
            case SensorbergServiceMessage.MSG_BEACON_LAYOUT_UPDATE:
                bootstrapper.updateBeaconLayout();
                break;
            case SensorbergServiceMessage.MSG_SDK_SCANNER_MESSAGE:
                Bundle message = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_GENERIC_WHAT);
                bootstrapper.scanner.handlePlatformMessage(message);
                break;
            case SensorbergServiceMessage.MSG_SETTINGS_UPDATE:
                bootstrapper.updateSettings();
                break;
            case SensorbergServiceMessage.MSG_UPLOAD_HISTORY:
                bootstrapper.uploadHistory();
                break;
            case SensorbergServiceMessage.GENERIC_TYPE_BEACON_ACTION: {
                presentBeaconEvent(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_APPLICATION_IN_FOREGROUND: {
                bootstrapper.hostApplicationInForeground();
                break;
            }
            case SensorbergServiceMessage.MSG_APPLICATION_IN_BACKGROUND: {
                bootstrapper.hostApplicationInBackground();
                break;
            }
            case SensorbergServiceMessage.MSG_CONVERSION:
                updateActionConversion(intent);
                break;
            case MSG_ATTRIBUTES:
                bootstrapper.reloadAttributes();
                break;
            case SensorbergServiceMessage.MSG_SET_API_TOKEN: {
                setApiToken(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_REGISTER_PRESENTATION_DELEGATE: {
                registerPresentationDelegate(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_UNREGISTER_PRESENTATION_DELEGATE: {
                unregisterPresentationDelegate(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_PING: {
                bootstrapper.startScanning();
                break;
            }
            case SensorbergServiceMessage.MSG_BLUETOOTH: {
                processBluetoothStateMessage(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER: {
                setAdvertisingIdentifier(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_LOCATION_SERVICES_IS_SET: {
                if (intent.getBooleanExtra(SensorbergServiceMessage.EXTRA_LOCATION_PERMISSION, false)) {
                    Logger.log.debug("Location Permission. Scanner should stop");
                    bootstrapper.stopScanning();
                } else {
                    bootstrapper.startScanning();
                    bootstrapper.startGeofences();
                    Logger.log.debug("Location Permission. Scanner should start");
                }
            }
            case SensorbergServiceMessage.MSG_LOCATION_UPDATED: {
                onLocationChanged(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_LOCATION_ENABLED: {
                if (bootstrapper.geofenceAvailable) {
                    bootstrapper.geofenceManager.ping();
                }
                break;
            }
            case SensorbergServiceMessage.MSG_GEOFENCE_EVENT: {
                onGeofenceEvent(intent);
                break;
            }
            case SensorbergServiceMessage.MSG_GEOFENCE_NOT_AVAILABLE: {
                onGeofenceNotAvailable(intent);
                break;
            }
        }
        return START_STICKY;
    }

    protected boolean isBootstrapperInitialized() {
        if (bootstrapper == null) {
            bootstrapper = createBootstrapperFromDiskConfiguration();
        }

        return bootstrapper != null;
    }

    protected void onLocationChanged(Intent intent) {
        if (!bootstrapper.geofenceAvailable) return;
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_LOCATION_AVAILABILITY)) {
            boolean available = intent.getBooleanExtra(
                    SensorbergServiceMessage.EXTRA_LOCATION_AVAILABILITY, false);
            if (available) {
                //Not used, might come handy at one point.
                //bootstrapper.geofenceManager.ping();
            }
        }
        Location location = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_LOCATION);
        if (location != null) {
            bootstrapper.geofenceManager.onLocationChanged(location);
        }
    }

    protected void onGeofenceEvent(Intent intent) {
        if (!bootstrapper.geofenceAvailable) return;
        GeofenceData data = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_GEOFENCE_DATA);
        if (data == null) {
            Logger.log.geofenceError("Intent missing GeofenceData", null);
            return;
        }
        if (!intent.hasExtra(SensorbergServiceMessage.EXTRA_GEOFENCE_ENTRY)) {
            Logger.log.geofenceError("Intent missing geofence entry", null);
            return;
        }
        boolean entry = intent.getBooleanExtra(SensorbergServiceMessage.EXTRA_GEOFENCE_ENTRY, true);
        bootstrapper.geofenceManager.onGeofenceEvent(data, entry, "<unused>");
    }

    protected void onGeofenceNotAvailable(Intent intent) {
        if (!bootstrapper.geofenceAvailable) return;
        bootstrapper.geofenceManager.onGeofenceNotAvailable();
    }

    protected void presentBeaconEvent(Intent intent) {
        try {
            BeaconEvent beaconEvent = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_GENERIC_WHAT);
            int index = intent.getIntExtra(SensorbergServiceMessage.EXTRA_GENERIC_INDEX, 0);
            Logger.log.beaconResolveState(beaconEvent, "end of the delay, now showing the BeaconEvent");
            bootstrapper.presentEventDirectly(beaconEvent, index);
        } catch (Exception e) {
            logError("Problem showing BeaconEvent: " + e.getMessage());
        }
    }

    protected void updateActionConversion(Intent intent) {
        ActionConversion conversion = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_CONVERSION);
        if (conversion == null) {
            logError("Intent missing ActionConversion");
            return;
        }
        bootstrapper.onConversionUpdate(conversion);
    }

    protected void saveAttributes(Intent intent) {
        Serializable extra = intent.getSerializableExtra(SensorbergServiceMessage.EXTRA_ATTRIBUTES);
        if (extra == null) {
            logError("Intent has no valid attributes");
            return;
        }
        try {
            HashMap<String, String> attributes = (HashMap<String, String>) extra;
            InternalApplicationBootstrapper.saveAttributes(attributes, gson, preferences);
        } catch (ClassCastException ex) {
            logError("Intent contains no attributes data", ex);
        }
    }

    protected void setApiToken(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.MSG_SET_API_TOKEN_TOKEN)) {
            String apiToken = intent.getStringExtra(SensorbergServiceMessage.MSG_SET_API_TOKEN_TOKEN);
            bootstrapper.setApiToken(apiToken);
            persistConfiguration(bootstrapper.resolver.configuration);
        }
    }

    protected void registerPresentationDelegate(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_MESSENGER)) {
            Messenger messenger = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_MESSENGER);
            presentationDelegates.add(messenger);
        }
    }

    protected void unregisterPresentationDelegate(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_MESSENGER)) {
            Messenger messenger = intent.getParcelableExtra(SensorbergServiceMessage.EXTRA_MESSENGER);
            presentationDelegates.remove(messenger);
        }
    }

    protected void processBluetoothStateMessage(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.EXTRA_BLUETOOTH_STATE)) {
            boolean bluetoothOn = intent.getBooleanExtra(SensorbergServiceMessage.EXTRA_BLUETOOTH_STATE, true);
            if (bluetoothOn) {
                bootstrapper.startScanning();
            } else {
                bootstrapper.stopScanning();
            }
        }
    }

    protected void setAdvertisingIdentifier(Intent intent) {
        if (intent.hasExtra(SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER_ADVERTISING_IDENTIFIER)) {
            String advertisingIdentifier = intent.getStringExtra(
                    SensorbergServiceMessage.MSG_SET_API_ADVERTISING_IDENTIFIER_ADVERTISING_IDENTIFIER);
            platformIdentifier.setAdvertisingIdentifier(advertisingIdentifier);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Logger.log.logServiceState("onUnbind");
        return false;
    }

    @Override
    public void onDestroy() {
        Logger.log.logServiceState("onDestroy");
        if (bootstrapper != null) {
            bootstrapper.stopScanning();
            bootstrapper.saveAllDataBeforeDestroy();
        }
        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (bootstrapper != null) {
            bootstrapper.saveAllDataBeforeDestroy();
        }
        Logger.log.logServiceState("onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected class MessengerList {

        private final Set<Messenger> storage = new HashSet<>();

        public int getSize() {
            return storage.size();
        }

        public void add(Messenger replyTo) {
            storage.clear(); //TODO we're limiting this to only one delegate?
            storage.add(replyTo);
            if (storage.size() >= 1) {
                bootstrapper.sentPresentationDelegationTo(this);
            }
        }

        public void remove(Messenger replyTo) {
            storage.remove(replyTo);
            storage.clear();  //TODO we're limiting this to only one delegate?
            if (storage.size() == 0) {
                bootstrapper.sentPresentationDelegationTo(null);
            }
        }

        public void send(BeaconEvent beaconEvent) {
            for (Messenger messenger : storage) {
                try {
                    Message message = Message.obtain(null, SensorbergServiceMessage.MSG_PRESENT_ACTION);
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(SensorbergServiceMessage.MSG_PRESENT_ACTION_BEACONEVENT, beaconEvent);
                    message.setData(bundle);
                    messenger.send(message);
                } catch (DeadObjectException d) {
                    //we need to remove this object!!
                } catch (RemoteException e) {
                    logError("something went wrong sending BeaconEvent through Messenger", e);
                }
            }
        }
    }
}
