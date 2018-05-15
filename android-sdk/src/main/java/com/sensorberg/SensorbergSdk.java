package com.sensorberg;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;

import com.sensorberg.di.Component;
import com.sensorberg.sdk.Conversion;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.SensorbergService;
import com.sensorberg.sdk.SensorbergServiceIntents;
import com.sensorberg.sdk.SensorbergServiceMessage;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.internal.interfaces.Platform;
import com.sensorberg.sdk.location.GeofenceReceiver;
import com.sensorberg.sdk.receivers.GenericBroadcastReceiver;
import com.sensorberg.sdk.receivers.NetworkInfoBroadcastReceiver;
import com.sensorberg.sdk.receivers.PermissionBroadcastReceiver;
import com.sensorberg.sdk.receivers.ScannerBroadcastReceiver;
import com.sensorberg.sdk.receivers.SensorbergBroadcastReceiver;
import com.sensorberg.sdk.receivers.SensorbergCodeReceiver;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.utils.AttributeValidator;

import net.danlew.android.joda.JodaTimeAndroid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.Getter;
import lombok.Setter;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * {@code SensorbergSdk} This is the entry point to the Sensorberg SDK. You should use this class to manage the SDK.
 *
 * @since 1.0
 */

public class SensorbergSdk implements Platform.ForegroundStateListener {

    protected static Context context;

    @Getter
    protected boolean presentationDelegationEnabled;

    protected final Messenger messenger = new Messenger(new IncomingHandler());

    protected static final Set<SensorbergSdkEventListener> listeners = new HashSet<>();

    @Setter
    private static Component component;

    @Inject
    @Named("androidBluetoothPlatform")
    protected BluetoothPlatform bluetoothPlatform;

    static class IncomingHandler extends Handler {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SensorbergServiceMessage.MSG_PRESENT_ACTION:
                    Bundle bundle = msg.getData();
                    bundle.setClassLoader(BeaconEvent.class.getClassLoader());
                    BeaconEvent beaconEvent = bundle.getParcelable(SensorbergServiceMessage.MSG_PRESENT_ACTION_BEACONEVENT);
                    notifyEventListeners(beaconEvent);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Constructor to be used for starting the SDK.
     *
     * @param ctx    {@code Context} Context used for starting the service.
     * @param apiKey {@code String} Your API key that you can get from your Sensorberg dashboard.
     */
    public SensorbergSdk(Context ctx, String apiKey) {
        if (blocked()) {
            disableAll(context);
            return;
        }
        init(ctx);
        getComponent().inject(this);
        activateService(apiKey);
    }

    public static void init(Context ctx) {
        if (blocked()) return;
        context = ctx;
        initLibraries(context);
    }

    public static Component getComponent() {
        buildComponentAndInject(context);
        return component;
    }

    private static void buildComponentAndInject(Context context) {
        if (component == null && context != null) {
            component = Component.Initializer.init((Application) context.getApplicationContext());
        }
    }

    synchronized private static void initLibraries(Context ctx) {
        if (ctx != null) {
            JodaTimeAndroid.init(ctx);
        }
    }

    /**
     * To receive Sensorberg SDK events, you should register your {@code SensorbergSdkEventListener} with this method. Depending on how you structure
     * your app, this can be done on an Application or on an Activity level.
     *
     * @param listener {@code SensorbergSdkEventListener} Your implementation of the listener that will receive Sensorberg SDK events that
     *                 should be presented via UI.
     */
    public void registerEventListener(SensorbergSdkEventListener listener) {

        if (blocked()) return;

        if (isSensorbergProcess(context)) {
            // the host app should only register for even listening on their own process
            return;
        }

        if (listener != null) {
            listeners.add(listener);
        }

        if (!listeners.isEmpty() && !isPresentationDelegationEnabled()) {
            setPresentationDelegationEnabled(true);
        }
    }

    /**
     * If you don't want to receive Sensorberg SDK events any more, you should unregister your {@code SensorbergSdkEventListener} with this method.
     * Depending on how you structure your app, this can be done on an Application or on an Activity level.
     *
     * @param listener {@code SensorbergSdkEventListener} Reference to your implementation of the listener that was registered with
     *                 {@code registerEventListener}.
     */
    public void unregisterEventListener(SensorbergSdkEventListener listener) {

        if (blocked()) return;

        listeners.remove(listener);

        if (listeners.isEmpty() && isPresentationDelegationEnabled()) {
            setPresentationDelegationEnabled(false);
        }
    }

    protected void setPresentationDelegationEnabled(boolean value) {
        presentationDelegationEnabled = value;
        if (value) {
            registerForPresentationDelegation();
        } else {
            unRegisterFromPresentationDelegation();
        }
    }

    protected static void notifyEventListeners(BeaconEvent beaconEvent) {
        for (SensorbergSdkEventListener listener : listeners) {
            listener.presentBeaconEvent(beaconEvent);
        }
    }

    protected void activateService(String apiKey) {
        if (bluetoothPlatform.isBluetoothLowEnergySupported()) {
            context.startService(SensorbergServiceIntents.getStartServiceIntent(context, apiKey));
        }
    }

    public void enableService(Context context, String apiKey) {
        if (blocked()) return;
        ScannerBroadcastReceiver.setManifestReceiverEnabled(true, context);
        activateService(apiKey);
        hostApplicationInForeground();
    }

    public void disableService(Context context) {
        if (blocked()) return;
        context.startService(SensorbergServiceIntents.getShutdownServiceIntent(context));
    }

    public void hostApplicationInBackground() {
        if (blocked()) return;
        Logger.log.applicationStateChanged("hostApplicationInBackground");
        context.startService(SensorbergServiceIntents.getAppInBackgroundIntent(context));
        unRegisterFromPresentationDelegation();
    }

    public void hostApplicationInForeground() {
        if (blocked()) return;
        context.startService(SensorbergServiceIntents.getAppInForegroundIntent(context));
        if (presentationDelegationEnabled) {
            registerForPresentationDelegation();
        }
    }

    protected void unRegisterFromPresentationDelegation() {
        context.startService(SensorbergServiceIntents.getIntentWithReplyToMessenger(context,
                SensorbergServiceMessage.MSG_UNREGISTER_PRESENTATION_DELEGATE, messenger));
    }

    protected void registerForPresentationDelegation() {
        context.startService(SensorbergServiceIntents.getIntentWithReplyToMessenger(context,
                SensorbergServiceMessage.MSG_REGISTER_PRESENTATION_DELEGATE, messenger));
    }

    public void changeAPIToken(String newApiToken) {
        if (blocked()) return;
        if (!TextUtils.isEmpty(newApiToken)) {
            context.startService(SensorbergServiceIntents.getApiTokenIntent(context, newApiToken));
        } else {
            Logger.log.logError("Cannot set empty token");
        }
    }

    public void setAdvertisingIdentifier(String advertisingIdentifier) {
        if (blocked()) return;
        Intent service = SensorbergServiceIntents.getAdvertisingIdentifierIntent(context, advertisingIdentifier);
        context.startService(service);
    }

    /**
     * To set the logging and whether to show a message notifying the user logging is enabled or not.
     *
     * @param enableLogging - true|false if to enable logging or not.
     */
    public void setLogging(boolean enableLogging) {
        if (blocked()) {
            android.util.Log.w("Sensorberg", "Beacon Sdk not compatible with Android oreo and above. All functionality disabled");
            return;
        }
        context.startService(SensorbergServiceIntents.getServiceLoggingIntent(context, enableLogging));
    }

    public void sendLocationFlagToReceiver(int flagType) {
        if (blocked()) return;
        Intent intent = new Intent();
        intent.setAction(SensorbergServiceMessage.EXTRA_LOCATION_PERMISSION);
        intent.putExtra("type", flagType);
        context.sendBroadcast(intent);
    }

    /**
     * * Call this to let SDK know the Action conversion status changed
     *
     * @param context            the callers context
     * @param actionInstanceUuid instance Uuid of the {@link com.sensorberg.sdk.action.Action} to update the status
     * @param conversion         the new conversion status
     */
    public static void notifyConversionStatus(Context context, String actionInstanceUuid, Conversion conversion) {
        if (blocked()) return;
        Intent intent = SensorbergServiceIntents.getConversionIntent(context, actionInstanceUuid, conversion.getValue());
        context.startService(intent);
    }

    /**
     * Pass here key-values params that are used for message targeting.
     * Valid key and values are limited to alphanumerical characters and underscore (_).
     * To clear the list pass null.
     *
     * @param attributes Map of attributes that will be passed.
     * @throws IllegalArgumentException if invalid key/value was passed.
     */
    public static void setAttributes(Map<String, String> attributes) throws IllegalArgumentException {
        if (blocked()) return;
        HashMap<String, String> map;
        if (attributes != null) {
            map = new HashMap<>(attributes);
        } else {
            map = new HashMap<>();
        }
        if (AttributeValidator.isInputValid(map)) {
            Intent intent = SensorbergServiceIntents.getServiceIntentWithMessage(context, SensorbergServiceMessage.MSG_ATTRIBUTES);
            intent.putExtra(SensorbergServiceMessage.EXTRA_ATTRIBUTES, map);
            context.startService(intent);
        } else {
            throw new IllegalArgumentException("Attributes can contain only alphanumerical characters and underscore");
        }
    }

    public static boolean isSensorbergProcess(Context context) {
        String processName = "";
        int pID = android.os.Process.myPid();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
            if (processInfo.pid == pID) {
                processName = processInfo.processName;
                break;
            }
        }
        return processName.endsWith(":sensorberg");
    }

    public static boolean blocked() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    private static void disableAll(Context context) {
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, SensorbergService.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, ScannerBroadcastReceiver.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, GenericBroadcastReceiver.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, SensorbergCodeReceiver.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, NetworkInfoBroadcastReceiver.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, PermissionBroadcastReceiver.class);
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(false, context, GeofenceReceiver.class);
    }
}
