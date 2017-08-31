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

    if (SENSORBERG_KEY == "0") {
      throw new IllegalArgumentException("Please register at portal.sensorberg.com and replace your API key on the `SENSORBERG_KEY` variable");
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      initChannels(this);
    }

    sensorbergSdk = new SensorbergSdk(this, SENSORBERG_KEY);
    sensorbergDetector = new BackgroundDetector(sensorbergSdk);
    registerActivityLifecycleCallbacks(sensorbergDetector);

    Map<String, String> attr = new HashMap<>();
    attr.put("value", "54321");
    SensorbergSdk.setAttributes(attr);
  }
}
