package com.sensorberg.sdk.demo;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import com.sensorberg.BackgroundDetector;
import com.sensorberg.SensorbergSdk;
import com.sensorberg.SensorbergSdkEventListener;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.action.Action;
import com.sensorberg.sdk.action.ActionType;
import com.sensorberg.sdk.action.InAppAction;
import com.sensorberg.sdk.action.UriMessageAction;
import com.sensorberg.sdk.action.VisitWebsiteAction;
import com.sensorberg.sdk.demo.presenters.MyActionPresenter;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.sdk.testApp.BuildConfig;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.support.multidex.MultiDexApplication;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

@SuppressWarnings("javadoc")
public class DemoApplication extends MultiDexApplication {

    public static final String TAG = "DemoApplication";

    public static final String API_KEY = "8961ee72ea4834053b376ad54007ea277cba4305db12188b74d104351ca8bf8a";

    private SensorbergSdk boot;

    private BackgroundDetector detector;

    //show all internal logging in debug mode
    static {
        if (BuildConfig.DEBUG) {
            Logger.enableVerboseLogging();
        }
    }

    private Context activityContext;

    @Override
    public void onCreate() {
        super.onCreate();
        if (SensorbergSdk.isSensorbergProcess(this)) {
            return;
        }
        Log.d(TAG, "onCreate application");

        boot = new SensorbergSdk(this, API_KEY);
        boot.setLogging(true);
        boot.registerEventListener(new SensorbergSdkEventListener() {
            @Override
            public void presentBeaconEvent(BeaconEvent beaconEvent) {
                Log.d(DemoApplication.TAG, "DemoApplication presentBeaconEvent = " + beaconEvent.toString());
                showAlert(beaconEvent.getAction(), beaconEvent.getTrigger());
            }
        });

        detector = new BackgroundDetector(boot);

        if (Build.VERSION.SDK_INT >= 14) {
            registerActivityLifecycleCallbacks(detector);
        }

        //consider this a bad sample, you may want to use another threading model, AsyncTask or something
        // similar. This part is your responsibility.
        new Thread(new Runnable() {
            @Override
            public void run() {
                long timeBefore = System.currentTimeMillis();
                try {
                    AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(getApplicationContext());
                    if (info == null || info.getId() == null) {
                        Logger.log.logError("AdvertisingIdClient.getAdvertisingIdInfo returned null");
                        return;
                    }
                    boot.setAdvertisingIdentifier(info.getId());
                } catch (IOException e) {
                    Logger.log.logError("could not fetch the advertising identifier beacuse of an IO Exception = ", e.getCause());
                } catch (GooglePlayServicesNotAvailableException e) {
                    Logger.log.logError("play services not available!");
                } catch (GooglePlayServicesRepairableException e) {
                    Logger.log.logError("play services need repairing, error = ", e.getCause());
                } catch (Exception e) {
                    Logger.log.logError("could not fetch the advertising identifier because of an unknown error, error = ", e.getCause());
                }
                Logger.log.verbose("fetching the advertising identifier took " + (System.currentTimeMillis() - timeBefore) + " millis");
            }
        }).start();
    }

    public void showAlert(Action action, Integer trigger) {
        String payload = action.getPayload();
        ActionType type = action.getType();

        switch (type) {
            case MESSAGE_URI:
                UriMessageAction uriMessageAction = (UriMessageAction) action;
                showAlert(uriMessageAction.getUuid().hashCode(), uriMessageAction.getTitle(), uriMessageAction.getContent(),
                        Uri.parse(uriMessageAction.getUri()), payload, trigger, type, action);
                break;
            case MESSAGE_WEBSITE:
                VisitWebsiteAction visitWebsiteAction = (VisitWebsiteAction) action;
                showAlert(visitWebsiteAction.getUuid().hashCode(), visitWebsiteAction.getSubject(), visitWebsiteAction.getBody(),
                        visitWebsiteAction.getUri(), payload, trigger, type, action);
                break;
            case MESSAGE_IN_APP:
                InAppAction inAppAction = (InAppAction) action;
                showAlert(inAppAction.getUuid().hashCode(), inAppAction.getSubject(), inAppAction.getBody(), inAppAction.getUri(), payload, trigger,
                        type, action);
                break;
        }
    }

    private void showAlert(int hashCode, String subject, String body, final Uri uri, final String payload, Integer trigger, ActionType type,
            Action action) {
        if (activityContext == null) {
            MyActionPresenter.showNotification(getApplicationContext(), hashCode, subject, body, uri, action);
            return;
        }
        String triggerString;
        if (trigger == null) {
            triggerString = "unknown";
        } else if (trigger == 1) {
            triggerString = "enter";
        } else if (trigger == 2) {
            triggerString = "exit";
        } else {
            triggerString = "enter/exit";
        }

        StringBuilder bodyText = new StringBuilder(body);
        bodyText.append('\n').append("trigger: ").append(triggerString);
        bodyText.append('\n').append("type:").append(type);
        bodyText.append('\n').append("payload:").append(payload != null ? "attached" : "not attached");
        bodyText.append('\n').append("delay:").append(action.getDelayTime());
        bodyText.append('\n').append("uuid:").append(action.getUuid());
        bodyText.append('\n').append("url:").append(uri);

        AlertDialog.Builder dialog = new AlertDialog.Builder(activityContext)
                .setTitle(subject)
                .setMessage(bodyText.toString())
                .setPositiveButton("open url", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplicationContext(), "open URL " + uri.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
        if (payload != null) {
            dialog.setNeutralButton("Payload", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AlertDialog.Builder payloadDialog = new AlertDialog.Builder(activityContext)
                            .setTitle("Payload:")
                            .setMessage(payload)
                            .setNeutralButton(android.R.string.ok, null);
                    payloadDialog.show();
                }
            });
        }
        dialog.show();
    }

    public void setActivityContext(Context activityContext) {
        this.activityContext = activityContext;
    }

    /**
     *
     * @param type
     */
    public void setLocationPermissionGranted(int type) {
        boot.sendLocationFlagToReceiver(type);
    }
}
