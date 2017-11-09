package com.sensorberg.mvp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.sensorberg.SensorbergSdk;
import com.sensorberg.SensorbergSdkEventListener;
import com.sensorberg.sdk.Conversion;
import com.sensorberg.sdk.resolver.BeaconEvent;

public class MainActivity extends AppCompatActivity implements SensorbergSdkEventListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String conversion = getIntent().getStringExtra("conversion");
        if (conversion != null) {
            SensorbergSdk.notifyConversionStatus(this, conversion, Conversion.SUCCESS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String conversion = getIntent().getStringExtra("conversion");
        if (conversion != null) {
            SensorbergSdk.notifyConversionStatus(this, conversion, Conversion.SUCCESS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((App) getApplication()).getSensorbergSdk().registerEventListener(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 42);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 42 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("SensorbergSDK", "Permission Granted");
        }
    }

    @Override
    protected void onStop() {
        ((App) getApplication()).getSensorbergSdk().unregisterEventListener(this);
        super.onStop();
    }

    @Override
    public void presentBeaconEvent(BeaconEvent beaconEvent) {

        SensorbergSdk.notifyConversionStatus(this, beaconEvent.getAction().getInstanceUuid(), Conversion.ACTION_SUPPRESSED);

        Snackbar.make(
                findViewById(R.id.activity_main),
                beaconEvent.toString(),
                Snackbar.LENGTH_LONG).show();
    }
}
