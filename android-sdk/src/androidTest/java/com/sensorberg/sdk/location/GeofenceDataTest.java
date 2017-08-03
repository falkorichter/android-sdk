package com.sensorberg.sdk.location;

import android.location.Location;
import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GeofenceDataTest {

    private GeofenceData tested;

    @Before
    public void setUp() {

    }

    @Test
    public void shouldThrowInvalidArgument() {
        try {
            String test = null;
            new GeofenceData(test);
        } catch (IllegalArgumentException ex) { }
        try {
            new GeofenceData("");
            fail("Invalid geofence, exception should be thrown");
        } catch (IllegalArgumentException ex) { }
        try {
            new GeofenceData("bbccddeef001122");
            fail("Invalid geofence, exception should be thrown");
        } catch (IllegalArgumentException ex) { }
        try {
            new GeofenceData("ccddeeff0022333");
            fail("Invalid geofence, exception should be thrown");
        } catch (IllegalArgumentException ex) { }
        assertTrue(true);
        try {
            new GeofenceData("ccddeeff000000");
            fail("Invalid geofence, exception should be thrown");
        } catch (IllegalArgumentException ex) { }
        assertTrue(true);
    }

    @Test
    public void shouldTakeValidGeofence() {
        GeofenceData data;
        String fence;
        try {
            fence = "s252w7m5999999";
            data = new GeofenceData(fence);
            assertTrue(data.getFence().equals(fence));
            assertEquals(data.getLatitude(), 0.106, 0.0005);
            assertEquals(data.getLongitude(), 16.102, 0.0005);
            assertEquals(data.getRadius(), 999999);

            fence = "gnzgf2b9000001";
            data = new GeofenceData(fence);
            assertTrue(data.getFence().equals(fence));
            assertEquals(data.getLatitude(), 83.632, 0.0005);
            assertEquals(data.getLongitude(), -34.002, 0.0005);
            assertEquals(data.getRadius(), 1);
        } catch (IllegalArgumentException ex) {
            fail("Valid geofence, exception should not be thrown");
        }
    }

    @Test
    public void shouldConvertProperly() {
        //Can't test that properly because GeofencingEvent constructor is private. TODO.
        /*String fence1 = "s252w7m5999999";
        String fence2 = "gnzgf2b9000001";
        Location location = new Location("provider");
        List<Geofence> geofences = new ArrayList<>();

        Geofence geofence1 = new Geofence.Builder()
                .setRequestId(fence1)
                .setCircularRegion(
                        0.106,
                        16.102,
                        999999)
                .setExpirationDuration(Long.MAX_VALUE)
                .setNotificationResponsiveness(5000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        Geofence geofence2 = new Geofence.Builder()
                .setRequestId(fence2)
                .setCircularRegion(
                        83.632,
                        -34.002,
                        1)
                .setExpirationDuration(Long.MAX_VALUE)
                .setNotificationResponsiveness(5000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();

        geofences.add(geofence1);
        geofences.add(geofence2);

        GeofencingEvent event = new GeofencingEvent(
                -1,
                Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT,
                geofences,
                location);

        GeofenceData.from()*/
    }

}
