package com.sensorberg.sdk.scanner;

import com.sensorberg.sdk.SensorbergTestApplication;
import com.sensorberg.sdk.di.TestComponent;
import com.sensorberg.sdk.settings.DefaultSettings;
import com.sensorberg.sdk.settings.SettingsManager;
import com.sensorberg.sdk.testUtils.DumbSucessTransport;
import com.sensorberg.sdk.testUtils.TestBluetoothPlatform;
import com.sensorberg.sdk.testUtils.TestFileManager;
import com.sensorberg.sdk.testUtils.TestHandlerManager;
import com.sensorberg.sdk.testUtils.TestServiceScheduler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;

import javax.inject.Inject;

import util.Utils;

import static com.sensorberg.sdk.testUtils.SensorbergMatcher.isEntryEvent;
import static com.sensorberg.sdk.testUtils.SensorbergMatcher.isExitEvent;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class TheBluetoothChangesShould {

    @Inject
    TestFileManager testFileManager;

    @Inject
    TestServiceScheduler testServiceScheduler;

    @Inject
    TestHandlerManager testHandlerManager;

    @Inject
    TestBluetoothPlatform bluetoothPlatform;
    @Inject
    SharedPreferences sharedPreferences;

    Scanner tested;
    private long RANDOM_VALUE_THAT_IS_SHORTER_THAN_CLEAN_BEACONMAP_ON_RESTART_TIMEOUT_BUT_LONGER_THAN_EXIT_EVENT_DELAY = Utils.FORTY_SECONDS + 1;
    private SettingsManager settings;

    @Before
    public void setUp() throws Exception {
        ((TestComponent) SensorbergTestApplication.getComponent()).inject(this);

        settings = new SettingsManager(new DumbSucessTransport(), sharedPreferences);
        tested = new Scanner(settings, false, testHandlerManager.getCustomClock(), testFileManager, testServiceScheduler, testHandlerManager, bluetoothPlatform);
        tested.scanTime = Long.MAX_VALUE;
        tested.waitTime = 0L;
        tested.start();
        testHandlerManager.getCustomClock().setNowInMillis(0);
    }

    @Test
    public void assert_random_values_are_within_range() {
        assertThat(RANDOM_VALUE_THAT_IS_SHORTER_THAN_CLEAN_BEACONMAP_ON_RESTART_TIMEOUT_BUT_LONGER_THAN_EXIT_EVENT_DELAY).isLessThan(DefaultSettings.DEFAULT_CLEAN_BEACONMAP_ON_RESTART_TIMEOUT);
        assertThat(RANDOM_VALUE_THAT_IS_SHORTER_THAN_CLEAN_BEACONMAP_ON_RESTART_TIMEOUT_BUT_LONGER_THAN_EXIT_EVENT_DELAY).isGreaterThan(settings.getExitTimeoutMillis());
    }

    @Test
    public void still_sees_exit_events_when_bluetooth_is_restarted_in_a_short_interval() {
        ScannerListener mockScannerListener = mock(ScannerListener.class);
        tested.addScannerListener(mockScannerListener);
        bluetoothPlatform.fakeIBeaconSighting();

        verify(mockScannerListener).onScanEventDetected(isEntryEvent());

        tested.stop();
        reset(mockScannerListener);
        testHandlerManager.getCustomClock().increaseTimeInMillis(
                RANDOM_VALUE_THAT_IS_SHORTER_THAN_CLEAN_BEACONMAP_ON_RESTART_TIMEOUT_BUT_LONGER_THAN_EXIT_EVENT_DELAY);
        tested.start();

        verify(mockScannerListener, never()).onScanEventDetected(isEntryEvent());
        verify(mockScannerListener, never()).onScanEventDetected(isExitEvent());

        long start = testHandlerManager.getCustomClock().now();
        while (testHandlerManager.getCustomClock().now() < start + Utils.GRACE_TIME - Utils.ONE_ADVERTISEMENT_INTERVAL) {
            testHandlerManager.getCustomClock().increaseTimeInMillis(Utils.ONE_ADVERTISEMENT_INTERVAL);
        }
        //Actually, it triggers before the above loop, due to lacking method for testing delay in NonThreadedRunLoopForTesting.scheduleAtFixedRate()
        verify(mockScannerListener).onScanEventDetected(isExitEvent());
    }


    public void beacon_events_are_removed_when_bluetooth_is_restarted_after_a_long_break_interval() {
        ScannerListener mockScannerListener = mock(ScannerListener.class);
        tested.addScannerListener(mockScannerListener);
        bluetoothPlatform.fakeIBeaconSighting();

        verify(mockScannerListener).onScanEventDetected(isEntryEvent());

        tested.stop();
        reset(mockScannerListener);
        testHandlerManager.getCustomClock().increaseTimeInMillis(Utils.VERY_LONG_TIME);
        tested.start();

        verify(mockScannerListener, never()).onScanEventDetected(isEntryEvent());
        verify(mockScannerListener, never()).onScanEventDetected(isExitEvent());

        long start = testHandlerManager.getCustomClock().now();
        while (testHandlerManager.getCustomClock().now() < start + Utils.EXIT_TIME * 2) {
            testHandlerManager.getCustomClock().increaseTimeInMillis(Utils.ONE_ADVERTISEMENT_INTERVAL);
        }
        verify(mockScannerListener, never()).onScanEventDetected(isEntryEvent());
        verify(mockScannerListener, never()).onScanEventDetected(isExitEvent());

        while (testHandlerManager.getCustomClock().now() < start + Utils.ONE_HOUR) {
            testHandlerManager.getCustomClock().increaseTimeInMillis(Utils.ONE_ADVERTISEMENT_INTERVAL);
        }
        verify(mockScannerListener, never()).onScanEventDetected(isEntryEvent());
        verify(mockScannerListener, never()).onScanEventDetected(isExitEvent());

    }
}

