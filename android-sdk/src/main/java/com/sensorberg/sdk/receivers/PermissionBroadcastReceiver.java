package com.sensorberg.sdk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sensorberg.sdk.SensorbergService;
import com.sensorberg.sdk.SensorbergServiceMessage;
import com.sensorberg.sdk.internal.PermissionChecker;

import javax.inject.Inject;

import static com.sensorberg.SensorbergSdk.blocked;

/**
 * @author skraynick
 * @date 16-06-13
 */
public class PermissionBroadcastReceiver extends BroadcastReceiver {

    @Inject
    PermissionChecker permissionChecker;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (blocked()) return;
        final String action = intent.getAction();

        if (action.equals(SensorbergServiceMessage.EXTRA_LOCATION_PERMISSION)) {
            final int flagType = intent.getExtras().getInt("type");
            switch (flagType) {
                case SensorbergServiceMessage.MSG_LOCATION_SET:
                    shouldDisplayPermission(context, false);
                    break;
                case SensorbergServiceMessage.MSG_LOCATION_NOT_SET_WHEN_NEEDED:
                    shouldDisplayPermission(context, true);
                    break;
            }
        }
    }

    /**
     * Sends a flag for indicating whether to show a permissions dialogue or not.
     *
     * @param context
     * @param toShow
     */
    private void shouldDisplayPermission(Context context, boolean toShow) {
        Intent service = new Intent(context, SensorbergService.class);
        service.putExtra(SensorbergServiceMessage.EXTRA_GENERIC_TYPE, SensorbergServiceMessage.MSG_LOCATION_SERVICES_IS_SET);
        service.putExtra(SensorbergServiceMessage.EXTRA_LOCATION_PERMISSION, toShow);
        context.startService(service);
    }
}
