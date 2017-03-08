package com.sensorberg.sdk.internal;

import com.sensorberg.bluetooth.CrashCallBackWrapper;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.scanner.AbstractScanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class AndroidBluetoothPlatform18 implements BluetoothPlatform {

    private final CrashCallBackWrapper crashCallBackWrapper;

    private BluetoothAdapter bluetoothAdapter;

    private final Context context;

    private boolean leScanRunning = false;

    private PermissionChecker permissionChecker;

    public AndroidBluetoothPlatform18(Context ctx) {
        context = ctx;
        permissionChecker = new PermissionChecker(ctx);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT >= 18) {
            crashCallBackWrapper = new CrashCallBackWrapper(ctx);
        } else {
            crashCallBackWrapper = null;
        }
    }

    /**
     * Returns a flag indicating whether Bluetooth is enabled.
     *
     * @return a flag indicating whether Bluetooth is enabled
     */
    @Override
    public boolean isBluetoothLowEnergyDeviceTurnedOn() {
        //noinspection SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement,SimplifiableIfStatement
        return isBluetoothLowEnergySupported() && (bluetoothAdapter.isEnabled());
    }

    /**
     * Returns a flag indicating whether Bluetooth is supported.
     *
     * @return a flag indicating whether Bluetooth is supported
     */
    @Override
    public boolean isBluetoothLowEnergySupported() {
        return bluetoothAdapter != null
                && crashCallBackWrapper != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void startLeScan(AbstractScanner.CommonCallback scanCallback) {
        if (isBluetoothLowEnergySupported() && crashCallBackWrapper != null) {
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON
                    && permissionChecker.hasScanPermissionCheckAndroid6()) {
                try {
                    //noinspection deprecation old API compatability
                    bluetoothAdapter.startLeScan(crashCallBackWrapper);
                    crashCallBackWrapper.setCallback(scanCallback);
                    leScanRunning = true;
                } catch (IllegalStateException e) {
                    // even with the adapter state checking two lines above,
                    // this still crashes https://sensorberg.atlassian.net/browse/AND-248
                    Logger.log.logError("System bug throwing error.", e);
                    leScanRunning = false;
                    crashCallBackWrapper.setCallback(null);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void stopLeScan() {
        if (isBluetoothLowEnergySupported() && crashCallBackWrapper != null) {
            try {
                //noinspection deprecation old API compatability
                bluetoothAdapter.stopLeScan(crashCallBackWrapper);
            } catch (Exception sentBySysteminternally) {
                Logger.log.logError("System bug throwing a NullPointerException internally.", sentBySysteminternally);
            } finally {
                leScanRunning = false;
                crashCallBackWrapper.setCallback(null);
            }
        }
    }

    @Override
    public boolean isLeScanRunning() {
        return leScanRunning;
    }
}
