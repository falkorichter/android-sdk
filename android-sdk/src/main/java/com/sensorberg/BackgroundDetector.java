package com.sensorberg;

import com.sensorberg.sdk.SensorbergServiceIntents;
import com.sensorberg.sdk.internal.PermissionChecker;
import com.sensorberg.sdk.internal.interfaces.Platform;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class BackgroundDetector implements Application.ActivityLifecycleCallbacks {
    private final Runnable FOREGROUND = new Runnable() {
        @Override
        public void run() {
            if (!appForeGroundState && isInForeground) {
                appForeGroundState = true;
                foregroundStateListener.hostApplicationInForeground();
            }
        }
    };
    private final Runnable BACKGROUND = new Runnable() {
        @Override
        public void run() {
            if (!isInForeground) {
                appForeGroundState = false;
                foregroundStateListener.hostApplicationInBackground();
            }
        }
    };
    private final Handler handler;
    private boolean isInForeground = false;
    private boolean appForeGroundState = isInForeground;
    private Platform.ForegroundStateListener foregroundStateListener = Platform.ForegroundStateListener.NONE;
    private PermissionChecker permissionChecker;
    private boolean hasPermission;

    public BackgroundDetector(Platform.ForegroundStateListener foregroundStateListener){
        this.handler = new Handler();
        this.foregroundStateListener = foregroundStateListener;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (permissionChecker == null) {
            permissionChecker = new PermissionChecker(activity.getApplicationContext());
            hasPermission = permissionChecker.hasScanPermissionCheckAndroid6();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        handler.removeCallbacksAndMessages(null);
        this.isInForeground = true;
        handler.postDelayed(FOREGROUND, 500);
        if (!hasPermission && permissionChecker.hasScanPermissionCheckAndroid6()) {
            hasPermission = true;
            activity.startService(
                    SensorbergServiceIntents.getPingIntent(activity));
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        handler.removeCallbacksAndMessages(null);
        this.isInForeground = false;
        handler.postDelayed(BACKGROUND, 500);
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }
}
