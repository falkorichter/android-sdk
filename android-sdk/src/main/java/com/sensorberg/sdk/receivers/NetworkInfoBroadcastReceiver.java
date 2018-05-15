package com.sensorberg.sdk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.ArrayList;
import java.util.List;

import static com.sensorberg.SensorbergSdk.blocked;

public class NetworkInfoBroadcastReceiver extends BroadcastReceiver {

    @SuppressWarnings("squid:S1444")
    public static NetworkInfo latestNetworkInfo = null;

    public static final List<NetworkInfoListener> listener = new ArrayList<NetworkInfoListener>();

    @SuppressWarnings("squid:S1444")
    public static NotificationBuilder notificationBuilder = NotificationBuilder.NONE;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (blocked()) return;
        triggerListenerWithCurrentState(context);
    }

    public static String infoToString(NetworkInfo networkInfoMaybeNull) {
        if (networkInfoMaybeNull == null) {
            return "not connected";
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append("Connected: ").append(networkInfoMaybeNull.isConnected()).append('\n');
            builder.append("ConnectedOrConnecting: ").append(networkInfoMaybeNull.isConnectedOrConnecting()).append('\n');
            builder.append("State: ").append(networkInfoMaybeNull.getState()).append('\n');
            builder.append("Extra Info: ").append(networkInfoMaybeNull.getExtraInfo()).append('\n');
            builder.append("Sub type name: ").append(networkInfoMaybeNull.getSubtypeName()).append('\n');
            builder.append("Sub type: ").append(networkInfoMaybeNull.getSubtype()).append('\n');
            builder.append("Reason: ").append(networkInfoMaybeNull.getReason()).append('\n');
            builder.append("Type name: ").append(networkInfoMaybeNull.getTypeName()).append('\n');
            builder.append("Type: ").append(typeToString(networkInfoMaybeNull.getType())).append('\n');
            return builder.toString();
        }

    }

    private static String typeToString(int type) {
        switch (type) {
            case ConnectivityManager.TYPE_ETHERNET:
                return "TYPE_ETHERNET";
            case ConnectivityManager.TYPE_BLUETOOTH:
                return "TYPE_BLUETOOTH";
            case ConnectivityManager.TYPE_MOBILE:
                return "TYPE_MOBILE";
            case ConnectivityManager.TYPE_MOBILE_DUN:
                return "TYPE_MOBILE_DUN";
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return "TYPE_MOBILE_HIPRI";
            case ConnectivityManager.TYPE_MOBILE_MMS:
                return "TYPE_MOBILE_MMS";
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                return "TYPE_MOBILE_SUPL";
            case ConnectivityManager.TYPE_VPN:
                return "TYPE_VPN";
            case ConnectivityManager.TYPE_WIFI:
                return "TYPE_WIFI";
            case ConnectivityManager.TYPE_WIMAX:
                return "TYPE_WIMAX";
            default:
                return "unknown";
        }
    }

    public static void triggerListenerWithCurrentState(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        latestNetworkInfo = manager.getActiveNetworkInfo();
        for (NetworkInfoListener networkInfoListener : listener) {
            networkInfoListener.onNetworkInfoChanged(latestNetworkInfo);
        }

        notificationBuilder.buildNotification(context, latestNetworkInfo);
    }

    /**
     * maps the network states to lan, wifi or the broadband types (edge, hsdpa...)
     *
     * @return the mapped network type name or "unknown"
     */
    public static String getNetworkInfoString() {
        if (latestNetworkInfo == null) {
            return "unknown";
        }
        switch (latestNetworkInfo.getType()) {
            case ConnectivityManager.TYPE_ETHERNET:
                return "lan";
            case ConnectivityManager.TYPE_MOBILE:
                return latestNetworkInfo.getSubtypeName().toLowerCase();
            case ConnectivityManager.TYPE_WIFI:
                return "wifi";
            default:
                return "unknown";
        }
    }


    public interface NetworkInfoListener {

        void onNetworkInfoChanged(NetworkInfo networkInfoMaybeNull);
    }

    public interface NotificationBuilder {

        NotificationBuilder NONE = new NotificationBuilder() {
            @Override
            public void buildNotification(Context context, NetworkInfo latestNetworkInfo) {

            }
        };

        void buildNotification(Context context, NetworkInfo latestNetworkInfo);
    }
}
