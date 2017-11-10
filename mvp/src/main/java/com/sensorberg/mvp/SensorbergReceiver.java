package com.sensorberg.mvp;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.sensorberg.SensorbergSdk;
import com.sensorberg.sdk.Conversion;
import com.sensorberg.sdk.action.Action;
import com.sensorberg.sdk.action.InAppAction;
import com.sensorberg.sdk.action.UriMessageAction;
import com.sensorberg.sdk.action.VisitWebsiteAction;

import java.util.Locale;

public class SensorbergReceiver extends BroadcastReceiver {

    private static final String OREO_NOTIFICATION_CHANNEL = "default";

    @Override
    public void onReceive(Context context, Intent intent) {
        Action action = intent.getExtras().getParcelable(Action.INTENT_KEY);
        Log.d("SensorbergSDK", "MyActionPresenter onReceive action = " + action.toString());
        String delayString = String.format(Locale.getDefault(), "delay : %d", action.getDelayTime());

        switch (action.getType()) {
            case MESSAGE_URI:
                UriMessageAction uriMessageAction = (UriMessageAction) action;
                showNotification(context, action.getUuid().hashCode(), uriMessageAction.getTitle(), uriMessageAction.getContent() + delayString,
                        Uri.parse(uriMessageAction.getUri()), action);
                break;
            case MESSAGE_WEBSITE:
                VisitWebsiteAction visitWebsiteAction = (VisitWebsiteAction) action;
                showNotification(context, action.getUuid().hashCode(), visitWebsiteAction.getSubject(), visitWebsiteAction.getBody() + delayString,
                        visitWebsiteAction.getUri(), action);
                break;
            case MESSAGE_IN_APP:
                InAppAction inAppAction = (InAppAction) action;
                showNotification(context, action.getUuid().hashCode(), inAppAction.getSubject(), inAppAction.getBody() + delayString,
                        inAppAction.getUri(), action);
                break;
        }
    }

    public static void showNotification(Context context, int id, String title, String content, Uri uri, Action action) {

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("conversion", action.getInstanceUuid());

            PendingIntent openApplicationWithAction = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new NotificationCompat.Builder(context, OREO_NOTIFICATION_CHANNEL)
                    .setContentIntent(openApplicationWithAction)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setSmallIcon(R.drawable.ic_beacon)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build();
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(id, notification);
            SensorbergSdk.notifyConversionStatus(context, action.getInstanceUuid(), Conversion.NOTIFICATION_SHOWN);
        } else {
            SensorbergSdk.notifyConversionStatus(context, action.getInstanceUuid(), Conversion.NOTIFICATION_DISABLED);
        }


    }

    @TargetApi(Build.VERSION_CODES.O)
    public static void initChannels(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                OREO_NOTIFICATION_CHANNEL, "Location Info", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Get here the latest location based information");
        notificationManager.createNotificationChannel(channel);
    }
}
