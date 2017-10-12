package com.sensorberg.sdk;

import com.google.gson.Gson;
import com.sensorberg.sdk.action.InAppAction;
import com.sensorberg.sdk.di.TestComponent;
import com.sensorberg.sdk.internal.interfaces.BluetoothPlatform;
import com.sensorberg.sdk.internal.transport.interfaces.Transport;
import com.sensorberg.sdk.resolver.BeaconEvent;
import com.sensorberg.sdk.resolver.ResolverConfiguration;
import com.sensorberg.sdk.scanner.BeaconActionHistoryPublisher;
import com.sensorberg.sdk.testUtils.DumbSucessTransport;
import com.sensorberg.sdk.testUtils.TestHandlerManager;
import com.sensorberg.sdk.testUtils.TestServiceScheduler;
import com.sensorberg.utils.ListUtils;

import org.fest.assertions.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(AndroidJUnit4.class)
public class TheInternalApplicationBootstrapperShould {

    private static final java.util.UUID UUID = java.util.UUID.randomUUID();

    private static final long SUPPRESSION_TIME = 10000;

    @Inject
    TestServiceScheduler testServiceScheduler;

    @Inject
    TestHandlerManager testHandlerManager;

    @Inject
    @Named("testBluetoothPlatform")
    BluetoothPlatform bluetoothPlatform;

    @Inject
    SharedPreferences sharedPreferences;

    BeaconActionHistoryPublisher beaconActionHistoryPublisher;

    InternalApplicationBootstrapper tested;

    private BeaconEvent beaconEventSupressionTime;

    private BeaconEvent beaconEventSentOnlyOnce;

    @Inject
    Gson gson;

    @Before
    public void setUp() throws Exception {
        ((TestComponent) SensorbergTestApplication.getComponent()).inject(this);

        beaconActionHistoryPublisher = new BeaconActionHistoryPublisher(mock(Transport.class),testHandlerManager.getCustomClock(), testHandlerManager, sharedPreferences, gson);
        beaconActionHistoryPublisher.deleteAllData();

        tested = new InternalApplicationBootstrapper(new DumbSucessTransport(), testServiceScheduler, testHandlerManager,
                testHandlerManager.getCustomClock(), bluetoothPlatform, new ResolverConfiguration());
        tested.beaconActionHistoryPublisher = beaconActionHistoryPublisher;

        beaconEventSupressionTime = new BeaconEvent.Builder()
                .withAction(new InAppAction(UUID, "irrelevant", "irrelevant", null, null, 0, java.util.UUID.randomUUID().toString()))
                .withSuppressionTime(SUPPRESSION_TIME)
                .withPresentationTime(0)
                .build();

        beaconEventSentOnlyOnce = new BeaconEvent.Builder()
                .withAction(new InAppAction(UUID, "irrelevant", "irrelevant", null, null, 0, java.util.UUID.randomUUID().toString()))
                .withSendOnlyOnce(true)
                .withPresentationTime(0)
                .build();
    }

    @Test
    public void beaconEventFilterShouldHaveOneSuppressionTimeEvent() throws Exception {
        List<BeaconEvent> events = ListUtils.filter(Arrays.asList(beaconEventSupressionTime), tested.beaconEventFilter);
        Assertions.assertThat(events.size()).isEqualTo(1);
    }

    @Test
    public void beaconEventFilterShouldHaveEndOfSuppressionTimeEvent() {
        List<BeaconEvent> events = ListUtils.filter(Arrays.asList(beaconEventSupressionTime), tested.beaconEventFilter);
        Assertions.assertThat(events.size()).isEqualTo(1);

        testHandlerManager.getCustomClock().setNowInMillis(SUPPRESSION_TIME -1);

        List<BeaconEvent> eventsWithSuppressionEvent = ListUtils.filter(Arrays.asList(beaconEventSupressionTime), tested.beaconEventFilter);
        Assertions.assertThat(eventsWithSuppressionEvent.size()).isEqualTo(0);
    }

    @Test
    public void beacon_should_allow_again_after_suppressiontime_is_over() {
        List<BeaconEvent> events = ListUtils.filter(Arrays.asList(beaconEventSupressionTime), tested.beaconEventFilter);
        Assertions.assertThat(events.size()).isEqualTo(1);

        testHandlerManager.getCustomClock().setNowInMillis(SUPPRESSION_TIME + 1);

        List<BeaconEvent> eventsWithSuppressionEvent = ListUtils.filter(Arrays.asList(beaconEventSupressionTime), tested.beaconEventFilter);
        Assertions.assertThat(eventsWithSuppressionEvent.size()).isEqualTo(1);
    }

    @Test
    public void beaconEventFilterShouldHaveSendOnlyOnceEvent() {
        List<BeaconEvent> events = ListUtils.filter(Arrays.asList(beaconEventSentOnlyOnce), tested.beaconEventFilter);
        Assertions.assertThat(events.size()).isEqualTo(1);
    }

    public void shouldReturnSyncEnabled() {
        Assertions.assertThat(tested.isSyncEnabled()).isTrue();
    }

    @Test
    public void shouldPersistAttributesForTargeting() {
        HashMap<String, String> map = new HashMap<>();
        map.put("K1", "V1");
        map.put("K2", "V2");

        InternalApplicationBootstrapper.saveAttributes(map, gson, sharedPreferences);

        tested = null;
        tested = new InternalApplicationBootstrapper(new DumbSucessTransport(), testServiceScheduler, testHandlerManager,
                testHandlerManager.getCustomClock(), bluetoothPlatform, new ResolverConfiguration());

        Assertions.assertThat(tested.attributes.get("K1")).isEqualTo("V1");
        Assertions.assertThat(tested.attributes.get("K2")).isEqualTo("V2");
    }
}
