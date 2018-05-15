package com.sensorberg.sdk.location;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationResult;
import com.sensorberg.sdk.BuildConfig;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.SensorbergServiceIntents;
import com.sensorberg.sdk.SensorbergServiceMessage;

import java.util.List;

import static com.sensorberg.SensorbergSdk.blocked;

public class GeofenceReceiver extends BroadcastReceiver {

    public static final String ACTION_GEOFENCE = "com.sensorberg.sdk.receiver.GEOFENCE";
    public static final String ACTION_LOCATION_UPDATE = "com.sensorberg.sdk.receiver.LOCATION_UPDATE";

    public static PendingIntent getGeofencePendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceReceiver.class);
        intent.setAction(ACTION_GEOFENCE);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent getLocationPendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceReceiver.class);
        intent.setAction(ACTION_LOCATION_UPDATE);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) { //TODO add wakelocks
        if (blocked()) return;
        String action = intent.getAction();
        if (action == null || action.isEmpty()) {
            Logger.log.geofenceError("Received intent without action",null);
            return;
        }
        switch (action) {
            case ACTION_GEOFENCE:
                handleGeofence(context, intent);
                break;
            case ACTION_LOCATION_UPDATE:
                handleLocationUpdate(context, intent);
                break;
            case LocationManager.PROVIDERS_CHANGED_ACTION:
                //Listening to this may cause trouble with some mock location apps that are spamming this action.
                handleProvidersChanged(context);
                break;
            default:
                Logger.log.geofenceError("Received intent with unknown action " + action, null);
                break;
        }
    }

    private boolean isLocationEnabled(LocationManager locationManager) {
        for (String provider : locationManager.getProviders(true)) {
            if (LocationManager.GPS_PROVIDER.equals(provider)
                    || LocationManager.NETWORK_PROVIDER.equals(provider)) {
                return true;
            }
        }
        return false;
    }

    private void handleProvidersChanged(Context context) {
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (isLocationEnabled(locationManager)) {
            Intent service = SensorbergServiceIntents.getServiceIntentWithMessage(
                    context, SensorbergServiceMessage.MSG_LOCATION_ENABLED);
            context.startService(service);
        }
    }

    private void handleLocationUpdate(Context context, Intent intent) {
        LocationResult result = LocationResult.extractResult(intent);
        LocationAvailability availability = LocationAvailability.extractLocationAvailability(intent);
        Intent service = SensorbergServiceIntents.getServiceIntentWithMessage(
                context, SensorbergServiceMessage.MSG_LOCATION_UPDATED);
        if (result != null) {
            Location location = result.getLastLocation();
            if (location != null) {
                service.putExtra(SensorbergServiceMessage.EXTRA_LOCATION, location);
            }
        }
        if (availability != null) {
            service.putExtra(SensorbergServiceMessage.EXTRA_LOCATION_AVAILABILITY,
                    availability.isLocationAvailable());
        }
        if (result != null || availability != null) {
            context.startService(service);
        } else {
            Logger.log.geofenceError("Received invalid location update", null);
        }
    }

    private void handleGeofence(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            Logger.log.geofenceError("GeofencingEvent is null", null);
            return;
        }
        if (event.hasError() && event.getErrorCode() == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
            //This runs in a case of e.g. disabling location on the device.
            //If we've registered geofence before, Google Play Service lets us know about removal here.
            //(But we don't rely only on it, cause we're smart and listen to disabling location anyway)
            Logger.log.geofence("Received GEOFENCE_NOT_AVAILABLE from service, will re-register");
            Intent service = SensorbergServiceIntents.getServiceIntentWithMessage(
                    context, SensorbergServiceMessage.MSG_GEOFENCE_NOT_AVAILABLE);
            context.startService(service);
            return;
        }
        try {
            List<GeofenceData> geofenceDatas = GeofenceData.from(event);
            boolean entry = event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER;
            for (GeofenceData geofenceData : geofenceDatas) {
                Logger.log.geofence("Received "+ (entry ? "entry" : "exit") +
                        " event "+geofenceData.getGeohash() + ", radius "+geofenceData.getRadius());
              Intent service = SensorbergServiceIntents.getServiceIntentWithMessage(
                  context, SensorbergServiceMessage.MSG_GEOFENCE_EVENT);
              service.putExtra(SensorbergServiceMessage.EXTRA_GEOFENCE_DATA, geofenceData);
              service.putExtra(SensorbergServiceMessage.EXTRA_GEOFENCE_ENTRY, entry);
              context.startService(service);
            }
        } catch (IllegalArgumentException ex) {
            Logger.log.geofenceError("GeofencingEvent is invalid", ex);
        }
    }
}
