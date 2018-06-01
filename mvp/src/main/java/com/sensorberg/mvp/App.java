package com.sensorberg.mvp;

import android.app.Application;
import android.os.Build;

import com.sensorberg.BackgroundDetector;
import com.sensorberg.SensorbergSdk;
import com.sensorberg.sdk.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.sensorberg.mvp.SensorbergReceiver.initChannels;

/**
 * Created by ronaldo on 2/9/17.
 */
public class App extends Application {

  // TODO: replace this with your API KEY
  private static final String SENSORBERG_KEY = "0";

  static {
    Logger.enableVerboseLogging();
  }
  private SensorbergSdk sensorbergSdk;
  private BackgroundDetector sensorbergDetector;

  public SensorbergSdk getSensorbergSdk() {
    return sensorbergSdk;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    //0 Don't do anything on Sensorberg process
    if (SensorbergSdk.isSensorbergProcess(this)) {
      return;
    }

    //1 Have valid API key
    if (SENSORBERG_KEY == "0") {
      throw new IllegalArgumentException("Please register at portal.sensorberg.com and replace your API key on the `SENSORBERG_KEY` variable");
    }

    //2 Call on Oreo to set up status bar notifications
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      initChannels(this);
    }

    //3 Initialize SDK
    sensorbergSdk = new SensorbergSdk(this, SENSORBERG_KEY);

    //4 Set attributes (optional)
    //Map<String, String> attrs = new HashMap<>();
    //attrs.put("loggedIn", "1");
    //SensorbergSdk.setAttributes(attrs);

    //5 Register background detector and activity callbacks
    sensorbergDetector = new BackgroundDetector(sensorbergSdk);
    registerActivityLifecycleCallbacks(sensorbergDetector);
  }
}
