package com.sensorberg.mvp;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;

import com.sensorberg.ActionReceiver;
import com.sensorberg.sdk.action.Action;
import com.sensorberg.sdk.action.InAppAction;
import com.sensorberg.sdk.action.UriMessageAction;
import com.sensorberg.sdk.action.VisitWebsiteAction;
import com.sensorberg.sdk.model.BeaconId;

public class SensorbergReceiver extends ActionReceiver {

    @Override
    public void onAction(Action action, BeaconId beaconId, Context context) { /**/ }

    @Override
    public void onUriAction(UriMessageAction action, BeaconId beaconId, Context context) { /**/ }

    @Override
    public void onVisitWebsiteAction(VisitWebsiteAction action, BeaconId beaconId, Context context) { /**/ }

    @Override
    public void onInAppAction(InAppAction action, BeaconId beaconId, Context context) { /**/ }

    @Override
    public Notification onGetNotification(Action action, BeaconId beaconId, Uri uri, Context context) {
        return new NotificationCompat.Builder(context)
                .setContentTitle("SensorbergSDK")
                .setContentText(action.toString())
                .setContentIntent(
                        getNotificationContentPendingIntent(
                                action,
                                beaconId,
                                uri,
                                null,
                                context,
                                PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_beacon)
                .setAutoCancel(true)
                .build();
    }

    @Override
    public void onNotificationSuccess(Action action, BeaconId beaconId, Uri uri, Bundle bundle, Context context) {
        super.onNotificationSuccess(action, beaconId, uri, bundle, context);
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
