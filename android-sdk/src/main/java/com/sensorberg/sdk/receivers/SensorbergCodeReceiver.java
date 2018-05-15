package com.sensorberg.sdk.receivers;

import com.sensorberg.sdk.SensorbergService;
import com.sensorberg.sdk.SensorbergServiceIntents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static com.sensorberg.SensorbergSdk.blocked;

public class SensorbergCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (blocked()) return;
        Intent loggingIntent = new Intent(context, SensorbergService.class);
        if (intent.getData().getAuthority().endsWith("73676723741")) {
            loggingIntent = SensorbergServiceIntents.getServiceLoggingIntent(context, true);
        } else if (intent.getData().getAuthority().endsWith("73676723740")) {
            loggingIntent = SensorbergServiceIntents.getServiceLoggingIntent(context, false);
        }
        context.startService(loggingIntent);
    }
}
