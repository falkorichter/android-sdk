package com.sensorberg.sdk;

import com.sensorberg.sdk.model.persistence.ActionConversion;

public enum Conversion {

    /**
     * The host app wanted/tried to shown a notification,
     * but couldn't because the user disabled notifications for the app.
     * Host app can check for notification enabled/disable by calling
     * NotificationManagerCompat.areNotificationsEnabled() method.
     */
    NOTIFICATION_DISABLED(ActionConversion.TYPE_NOTIFICATION_DISABLED),
    /**
     * The host app received the action, but did not acted upon it.
     * That might happen if the Action is triggered on the app simply to execute some background operation.
     */
    ACTION_SUPPRESSED(ActionConversion.TYPE_SUPPRESSED),
    /**
     * The host app received the action and directly shown a notification to the user.
     * The usual implementation will call AbstractActionReceiver.getConversionSuccessfulIntent()
     * to generate an intent that will change the conversion status to success when launched.
     * This intent will normally be used as content or action intent for the notification.
     */
    NOTIFICATION_SHOWN(ActionConversion.TYPE_IGNORED),
    /**
     * The action conversion is success.
     * That normally means that the host app shown a notification to the user and the user acted upon it.
     * Normally the host app doesn't use this value directly,
     * as this value is automatically set from the AbstractActionReceiver.getConversionSuccessfulIntent()
     */
    SUCCESS(ActionConversion.TYPE_SUCCESS);

    private final int value;

    Conversion(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
