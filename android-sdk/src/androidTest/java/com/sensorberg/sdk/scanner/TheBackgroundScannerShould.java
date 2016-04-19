package com.sensorberg.sdk.scanner;

import com.sensorberg.sdk.SensorbergTestApplication;
import com.sensorberg.sdk.di.TestComponent;
import com.sensorberg.sdk.settings.DefaultSettings;
import com.sensorberg.sdk.settings.SettingsManager;
import com.sensorberg.sdk.testUtils.DumbSucessTransport;
import com.sensorberg.sdk.testUtils.TestBluetoothPlatform;
import com.sensorberg.sdk.testUtils.TestFileManager;
import com.sensorberg.sdk.testUtils.TestPlatform;
import com.sensorberg.sdk.testUtils.TestServiceScheduler;

import org.mockito.Mockito;

import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import javax.inject.Inject;

import static com.sensorberg.sdk.testUtils.SensorbergMatcher.isEntryEvent;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;


public class TheBackgroundScannerShould extends AndroidTestCase{

    @Inject
    TestFileManager testFileManager;

    @Inject
    TestServiceScheduler testServiceScheduler;

    @Inject
    TestBluetoothPlatform bluetoothPlatform;

    @Inject
    SharedPreferences sharedPreferences;

    private TestPlatform platform;
    private UIScanner tested;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        ((TestComponent) SensorbergTestApplication.getComponent()).inject(this);
        platform = new TestPlatform();
        setUpScanner();

        tested.hostApplicationInBackground();

        tested.start();
    }

    private void setUpScanner() {
        tested = new UIScanner(new SettingsManager(new DumbSucessTransport(), sharedPreferences), platform.clock, testFileManager, testServiceScheduler, platform, bluetoothPlatform);
    }

    public void test_be_in_background_mode(){
        assertThat(tested.waitTime).isEqualTo(DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME);
        assertThat(tested.scanTime).isEqualTo(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
    }


   public void test_detect_no_beacon_because_it_is_sleeping(){
       platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + 1);

       ScannerListener mockListener = Mockito.mock(ScannerListener.class);
       tested.addScannerListener(mockListener);

       bluetoothPlatform.fakeIBeaconSighting();

       verifyZeroInteractions(mockListener);
   }

    public void test_detect_beacon_because_sleep_has_ended(){
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME - 1);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + 1);

        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME + 1);

        ScannerListener mockListener = Mockito.mock(ScannerListener.class);
        tested.addScannerListener(mockListener);


        bluetoothPlatform.fakeIBeaconSighting();

        verify(mockListener).onScanEventDetected(isEntryEvent());
    }

    public void test_background_times_should_be_switched_to_foreground_times() {
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME - 1);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + 1);

        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME / 2);

        tested.hostApplicationInForeground();
        assertThat(tested.waitTime).isNotEqualTo(DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME);
        assertThat(tested.waitTime).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_WAIT_TIME);
        assertThat(tested.scanTime).isNotEqualTo(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
        assertThat(tested.scanTime).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_SCAN_TIME);
    }

    public void test_detect_beacon_because_sleep_has_ended_due_to_foreground(){
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME - 1);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + 1);

        platform.clock.setNowInMillis(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME + DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME / 2);

        tested.hostApplicationInForeground();

        platform.clock.increaseTimeInMillis(1);

        ScannerListener mockListener = Mockito.mock(ScannerListener.class);
        tested.addScannerListener(mockListener);

        bluetoothPlatform.fakeIBeaconSighting();

        verify(mockListener).onScanEventDetected(isEntryEvent());
    }


}
