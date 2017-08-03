package com.sensorberg.sdk.scanner;

import com.sensorberg.sdk.SensorbergTestApplication;
import com.sensorberg.sdk.di.TestComponent;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.settings.DefaultSettings;
import com.sensorberg.sdk.settings.SettingsManager;
import com.sensorberg.sdk.testUtils.DumbSucessTransport;
import com.sensorberg.sdk.testUtils.TestFileManager;
import com.sensorberg.sdk.testUtils.TestHandlerManager;
import com.sensorberg.sdk.testUtils.TestServiceScheduler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;

import javax.inject.Inject;
import javax.inject.Named;

import util.Utils;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(AndroidJUnit4.class)
public class TheDefaultScannerSetupShould {

    @Inject
    TestFileManager testFileManager;

    @Inject
    TestServiceScheduler testServiceScheduler;

    @Inject
    TestHandlerManager testHandlerManager;

    @Inject
    @Named("testBluetoothPlatform")
    BluetoothPlatform bluetoothPlatform;

    @Inject
    SharedPreferences sharedPreferences;

    protected Scanner tested;

    protected SettingsManager settings;

    @Before
    public void setUp() throws Exception {
        ((TestComponent) SensorbergTestApplication.getComponent()).inject(this);
        sharedPreferences.edit().clear().commit();

        settings = new SettingsManager(new DumbSucessTransport(), sharedPreferences);
        tested = new Scanner(settings, false, testHandlerManager.getCustomClock(), testFileManager, testServiceScheduler, testHandlerManager,
                bluetoothPlatform);

        tested.scanTime = Long.MAX_VALUE;
        tested.waitTime = 0;
    }

    @After
    public void tearDown() throws Exception {
        settings = null;
        tested = null;
    }

    @Test
    public void test_should_be_initially_setup_to_scan_in_with_the_background_configuration() throws Exception {
        tested.hostApplicationInBackground();

        assertThat(tested.waitTime).isEqualTo(DefaultSettings.DEFAULT_BACKGROUND_WAIT_TIME);
        assertThat(tested.scanTime).isEqualTo(DefaultSettings.DEFAULT_BACKGROUND_SCAN_TIME);
    }

    @Test
    public void test_foreground_scanning_time_should_be_10seconds() {
        tested.hostApplicationInForeground();

        assertThat(settings.getForeGroundScanTime()).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_SCAN_TIME);
        assertThat(tested.scanTime).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_SCAN_TIME);
    }

    @Test
    public void test_foreground_pause_time_should_be_10seconds() {
        tested.hostApplicationInForeground();

        assertThat(settings.getForeGroundWaitTime()).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_WAIT_TIME);
        assertThat(tested.waitTime).isEqualTo(DefaultSettings.DEFAULT_FOREGROUND_WAIT_TIME);
    }

    @Test
    public void test_background_scanning_time_should_be_15seconds() {
        tested.hostApplicationInBackground();

        assertThat(tested.scanTime).isEqualTo(Utils.ONE_SECOND * 15);
    }

    @Test
    public void test_background_pause_time_should_be_2minutes() {
        tested.hostApplicationInBackground();

        assertThat(tested.waitTime).isEqualTo(Utils.ONE_MINUTE * 2);
    }

    @Test
    public void test_exit_time_should_be_correct() {
        assertThat(settings.getExitTimeoutMillis()).isEqualTo(Utils.EXIT_TIME);
    }

    @Test
    public void test_background_scan_time_should_be_bigger_than_exitTimeout() {
        tested.hostApplicationInBackground();

        assertThat(tested.waitTime).isGreaterThan(settings.getExitTimeoutMillis());
    }

    @Test
    public void test_foreground_scan_time_should_be_bigger_than_exitTimeout() {
        assertThat(tested.scanTime).isGreaterThan(settings.getExitTimeoutMillis());
    }
}
