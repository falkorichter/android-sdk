package com.sensorberg.sdk.receivers;

import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.SensorbergService;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import static com.sensorberg.SensorbergSdk.blocked;

public class GenericBroadcastReceiver extends SensorbergBroadcastReceiver{

    public static void setManifestReceiverEnabled(boolean enabled, Context context) {
        SensorbergBroadcastReceiver.setManifestReceiverEnabled(enabled, context, GenericBroadcastReceiver.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (blocked()) return;
        Intent service = new Intent(context, SensorbergService.class);
        service.putExtras(intent.getExtras());
        try {
            context.startService(service);
        } catch (RuntimeException e) {
            // so apparently devices that fail to launch your service.
            // even if everything is configured correctly
            // https://chromium.googlesource.com/android_tools/+/master/sdk/sources/android-23/android/app/ContextImpl.java?autodive=0%2F%2F#1256
            // https://sensorberg.atlassian.net/browse/AND-248
            Logger.log.logError("System bug throwing error.", e);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private String toString(Intent intent) {
        StringBuilder builder = new StringBuilder("action:" + intent.getAction());
        Bundle extras = intent.getExtras();
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            builder.append("\nextra key:\"").append(key).append("\" value:\"").append(value).append("\" of type: ").append(value.getClass().getName());
        }
        return builder.toString();
    }
}
