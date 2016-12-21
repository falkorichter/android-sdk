package com.sensorberg.sdk.scanner;

import android.content.SharedPreferences;
import android.os.Message;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.internal.interfaces.Clock;
import com.sensorberg.sdk.internal.interfaces.HandlerManager;
import com.sensorberg.sdk.internal.interfaces.RunLoop;
import com.sensorberg.sdk.internal.transport.interfaces.Transport;
import com.sensorberg.sdk.internal.transport.interfaces.TransportHistoryCallback;
import com.sensorberg.sdk.model.persistence.ActionConversion;
import com.sensorberg.sdk.model.persistence.BeaconAction;
import com.sensorberg.sdk.model.persistence.BeaconScan;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.sdk.resolver.ResolverListener;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Setter;

public class BeaconActionHistoryPublisher implements ScannerListener, RunLoop.MessageHandlerCallback {

    private static final String SUPRESSION_TIME_STORE_SHARED_PREFS_TAG = "com.sensorberg.sdk.SupressionTimeStore";

    private static final int MSG_PUBLISH_HISTORY = 1;
    private static final int MSG_DELETE_ALL_DATA = 6;
    private static final int MSG_SAVE_SUPPRESSION_STORE = 7;

    private Clock clock;

    private final RunLoop runloop;

    private final Transport transport;

    @Setter
    private ResolverListener resolverListener = ResolverListener.NONE;

    private final SharedPreferences sharedPreferences;

    private final Gson gson;

    private final Object lock = new Object();

    private List<BeaconScan> beaconScans = Collections.synchronizedList(new LinkedList<BeaconScan>());

    private List<BeaconAction> beaconActions = Collections.synchronizedList(new LinkedList<BeaconAction>());

    private Map<String, ActionConversion> actionConversions = Collections.synchronizedMap(new HashMap<String, ActionConversion>());

    private HashMap<String, Long> suppressionTimeStore = new HashMap<>();

    public BeaconActionHistoryPublisher(Transport transport, Clock clock,
                                        HandlerManager handlerManager, SharedPreferences sharedPrefs, Gson gson) {
        this.transport = transport;
        this.clock = clock;
        runloop = handlerManager.getBeaconPublisherRunLoop(this);
        sharedPreferences = sharedPrefs;
        this.gson = gson;

        loadAllData();
    }

    @Override
    public void onScanEventDetected(ScanEvent scanEvent) {
        synchronized (lock) {
            beaconScans.add(BeaconScan.from(scanEvent));
        }
        saveAllData();
    }

    @Override
    public void handleMessage(Message queueEvent) {
        switch (queueEvent.what) {
            case MSG_PUBLISH_HISTORY:
                publishHistorySynchronously();
                break;
            case MSG_DELETE_ALL_DATA:
                deleteAllData();
                break;
            case MSG_SAVE_SUPPRESSION_STORE:
                saveSuppressionTimeStore();
                break;
        }
    }

    private void publishHistorySynchronously() {
        final List<BeaconScan> notSentScans;
        final List<BeaconAction> notSentActions;
        final List<String> notSentConversionsKeys;
        final List<ActionConversion> notSentConversionsValues;
        synchronized (lock) {
            notSentScans = new LinkedList<>(beaconScans);
            notSentActions = new LinkedList<>(beaconActions);
            notSentConversionsKeys = new LinkedList<>(actionConversions.keySet());
            notSentConversionsValues = new LinkedList<>(actionConversions.values());
        }

        if (notSentScans.isEmpty() && notSentActions.isEmpty() && notSentConversionsKeys.isEmpty()) {
            Logger.log.logBeaconHistoryPublisherState("nothing to report");
            return;
        } else {
            Logger.log.logBeaconHistoryPublisherState("reporting "
                    + notSentScans.size() + " scans and "
                    + notSentActions.size() + " actions and " +
                    +notSentConversionsKeys.size() + " conversions");
        }

        TransportHistoryCallback transportHistoryCallback = new TransportHistoryCallback() {
            @Override
            public void onSuccess(List<BeaconScan> scanObjectList, List<BeaconAction> actionList, List<ActionConversion> conversions) {
                synchronized (lock) {
                    beaconActions.removeAll(notSentActions);
                    beaconScans.removeAll(notSentScans);
                    actionConversions.keySet().removeAll(notSentConversionsKeys);
                }
                Logger.log.logBeaconHistoryPublisherState("published "
                        + notSentActions.size() + " campaignStats and "
                        + notSentScans.size() + " beaconStats and " +
                        +notSentConversionsKeys.size() + " actionConversions successfully.");
                saveAllData();
            }

            @Override
            public void onFailure(Exception throwable) {
                Logger.log.logError("not able to publish history", throwable);
            }

            @Override
            public void onInstantActions(List<BeaconEvent> instantActions) {
                resolverListener.onResolutionsFinished(instantActions);
            }
        };

        transport.publishHistory(notSentScans, notSentActions, notSentConversionsValues, transportHistoryCallback);
    }

    public void publishHistory() {
        runloop.add(runloop.obtainMessage(MSG_PUBLISH_HISTORY));
    }

    public void onActionPresented(BeaconEvent beaconEvent) {
        synchronized (lock) {
            beaconActions.add(BeaconAction.from(beaconEvent));
        }
        saveAllData();
        if (beaconEvent.isReportImmediately()) {
            publishHistory();
        }
    }

    public void onConversionUpdate(ActionConversion incoming) {
        ActionConversion existing = actionConversions.get(incoming.getAction());
        if (existing != null && incoming.getType() <= existing.getType()) {
            Logger.log.verbose("Conversion " + existing.getAction() + " type change rejected. " +
                    "Type can be changed only to higher. " +
                    "Existing type: " + existing.getType() + " Incoming type: " + incoming.getType());
            return;
        }
        synchronized (lock) {
            actionConversions.put(incoming.getAction(), incoming);
        }
        saveAllData();
    }

    public void deleteAllObjects() {
        runloop.sendMessage(MSG_DELETE_ALL_DATA);
    }


    /**
     * List not sent scans.
     *
     * @return - A list of notSentBeaconScans.
     */
    public boolean actionShouldBeSuppressed(final long lastAllowedPresentationTime, final UUID actionUUID) {
        Long lastPresentation = suppressionTimeStore.get(actionUUID.toString());
        boolean value = lastPresentation != null && lastPresentation >= lastAllowedPresentationTime;
        if (!value) {
            synchronized (lock) {
                suppressionTimeStore.put(actionUUID.toString(), clock.now());
            }
            saveAllData();
            runloop.add(runloop.obtainMessage(MSG_SAVE_SUPPRESSION_STORE));
        }
        return value;
    }


    /**
     * Get the count for only once suppression.
     *
     * @param actionUUID - The beacon action UUID.
     * @return - Select class object.
     */
    public boolean actionWasShownBefore(final UUID actionUUID) {
        boolean value = suppressionTimeStore.containsKey(actionUUID.toString());
        if (!value) {
            synchronized (lock) {
                suppressionTimeStore.put(actionUUID.toString(), clock.now());
            }
            saveAllData();
            runloop.add(runloop.obtainMessage(MSG_SAVE_SUPPRESSION_STORE));
        }
        return value;
    }

    private void loadAllData() {
        synchronized (lock) {
            String actionJson = sharedPreferences.getString(BeaconAction.SHARED_PREFS_TAG, "");
            if (!actionJson.isEmpty()) {
                Type listType = new TypeToken<List<BeaconAction>>() {
                }.getType();
                beaconActions = Collections.synchronizedList((List<BeaconAction>) gson.fromJson(actionJson, listType));
            }

            String scanJson = sharedPreferences.getString(BeaconScan.SHARED_PREFS_TAG, "");
            if (!scanJson.isEmpty()) {
                Type listType = new TypeToken<List<BeaconScan>>() {
                }.getType();
                beaconScans = Collections.synchronizedList((List<BeaconScan>) gson.fromJson(scanJson, listType));
            }

            String conversionJson = sharedPreferences.getString(ActionConversion.SHARED_PREFS_TAG, "");
            if (!conversionJson.isEmpty()) {
                Type mapType = new TypeToken<HashMap<String, ActionConversion>>() {
                }.getType();
                actionConversions = Collections.synchronizedMap((Map<String, ActionConversion>) gson.fromJson(conversionJson, mapType));
            }
            Logger.log.logBeaconHistoryPublisherState("loaded "
                    + beaconActions.size() + " campaignStats and "
                    + beaconScans.size() + " beaconStats "
                    + actionConversions.size() + " actionConversions from shared preferences");

            String supressionTimeStoreString = sharedPreferences.getString(SUPRESSION_TIME_STORE_SHARED_PREFS_TAG, "");
            if (!supressionTimeStoreString.isEmpty()) {
                Type hashMapType = new TypeToken<HashMap<String, Long>>() {
                }.getType();
                suppressionTimeStore = gson.fromJson(supressionTimeStoreString, hashMapType);
            }
        }
    }

    public void saveAllData() {
        String actionsJson;
        String scansJson;
        String conversionJson;
        String supressionTimeStoreJson;
        synchronized (lock) {
            actionsJson = gson.toJson(beaconActions);
            scansJson = gson.toJson(beaconScans);
            conversionJson = gson.toJson(actionConversions);
            supressionTimeStoreJson = gson.toJson(suppressionTimeStore);
        }
        if (Logger.isVerboseLoggingEnabled()) {
            try {
                Logger.log.logBeaconHistoryPublisherState("size of the stats" +
                        " campaignStats:" + actionsJson.getBytes("UTF-8").length +
                        " beaconStats:" + scansJson.getBytes("UTF-8").length +
                        " conversionStats:" + conversionJson.getBytes("UTF-8").length);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor
                .putString(BeaconAction.SHARED_PREFS_TAG, actionsJson)
                .putString(BeaconScan.SHARED_PREFS_TAG, scansJson)
                .putString(ActionConversion.SHARED_PREFS_TAG, conversionJson)
                .putString(SUPRESSION_TIME_STORE_SHARED_PREFS_TAG, supressionTimeStoreJson)
                .apply();
        Logger.log.logBeaconHistoryPublisherState(
                "saved " + beaconActions.size() + " campaignStats and "
                        + beaconScans.size() + " beaconStats and "
                        + actionConversions.size() + " actionConversions and "
                        + suppressionTimeStore.size() + " supression related items to shared preferences");
    }

    public void saveSuppressionTimeStore() {
        String supressionTimeStoreJson;
        synchronized (lock) {
            supressionTimeStoreJson = gson.toJson(suppressionTimeStore);
        }
        sharedPreferences
                .edit()
                .putString(SUPRESSION_TIME_STORE_SHARED_PREFS_TAG, supressionTimeStoreJson)
                .apply();
    }

    private void deleteSavedFromSharedPreferences(String key) {
        if (sharedPreferences.contains(key)) {
            sharedPreferences.edit().remove(key).apply();
        }
    }

    public void deleteAllData() {
        Logger.log.logBeaconHistoryPublisherState("will purge the saved data of "
                + beaconActions.size() + " campaignStats and "
                + beaconScans.size() + " beaconStats and "
                + actionConversions.size() + "actionConversions");
        synchronized (lock) {
            beaconActions = Collections.synchronizedList(new LinkedList<BeaconAction>());
            beaconScans = Collections.synchronizedList(new LinkedList<BeaconScan>());
            actionConversions = Collections.synchronizedMap(new HashMap<String, ActionConversion>());
            suppressionTimeStore = new HashMap<>();
        }

        deleteSavedFromSharedPreferences(SUPRESSION_TIME_STORE_SHARED_PREFS_TAG);
        deleteSavedFromSharedPreferences(BeaconScan.SHARED_PREFS_TAG);
        deleteSavedFromSharedPreferences(BeaconAction.SHARED_PREFS_TAG);
        deleteSavedFromSharedPreferences(ActionConversion.SHARED_PREFS_TAG);
    }
}
