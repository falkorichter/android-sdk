package com.sensorberg.mvp;

import android.app.Application;

import com.sensorberg.BackgroundDetector;
import com.sensorberg.SensorbergSdk;
import com.sensorberg.sdk.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ronaldo on 2/9/17.
 */
public class App extends Application {

  // TODO: replace this with your API KEY
  private static final String SENSORBERG_KEY = "0e620cd907ac0f9b9cc407ece991f889c7401ba056554e1e7b293d887754afc8";

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

    sensorbergSdk = new SensorbergSdk(this, SENSORBERG_KEY);
    sensorbergDetector = new BackgroundDetector(sensorbergSdk);
    registerActivityLifecycleCallbacks(sensorbergDetector);

    Map<String, String> attr = new HashMap<>();
    attr.put("BLZ", "54321");
    SensorbergSdk.setAttributes(attr);
  }
}
