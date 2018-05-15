package com.sensorberg.sdk.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public abstract class SensorbergBroadcastReceiver extends BroadcastReceiver{

    public static void setManifestReceiverEnabled(boolean enabled, Context context, Class<?> relevantClass) {
        try{
            ComponentName component = new ComponentName(context, relevantClass);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(
                    component,
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception e){
            //seems like the host did not include the ScannnerBroadcastReceiver in the manifest. This should still work. Only if the app is killed, the broadcast is dead.
        }
    }
}
