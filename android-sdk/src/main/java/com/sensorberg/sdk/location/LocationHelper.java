package com.sensorberg.sdk.location;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import com.sensorberg.sdk.BuildConfig;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.settings.SettingsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LocationHelper {

    private LocationManager manager;
    private SettingsManager settings;

    private GeoHashLocation location;

    public LocationHelper(LocationManager manager, SettingsManager settings) {
        this.manager = manager;
        this.settings = settings;
    }

    /**
     * Get most recent geohash fulfilling accuracy / age boundaries.
     * This methods works as passively as possible.
     *
     * @return Geohash if location is within given accuracy and age, null if not.
     * Also null if the location is not available or permissions are missing.
     */
    public String getGeohash() {
        location = acquireGeohash();
        if (location != null) {
            return location.getGeohash();
        }
        return null;
    }

    /**
     * Is location enabled, as per status bar indicator being on/off
     * @return location enabled/disabled
     */
    public boolean isLocationEnabled() {
        for (String provider : manager.getProviders(true)) {
            if (LocationManager.GPS_PROVIDER.equals(provider)
                    || LocationManager.NETWORK_PROVIDER.equals(provider)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to get current location as passively as possible.
     * (by using getLastKnownLocation only)
     *
     * @return Most recent known location.
     */
    private GeoHashLocation acquireGeohash() {
        List<Location> locations = new ArrayList<>(3);

        // re-evaluated current location
        // it might be still the best, or might be too old
        if (isAccurateAndFreshAndNotNull(location)) {
            locations.add(location);
        }

        // loop through available providers and grab getLastKnownLocation from them
        List<String> providers = manager.getProviders(true);
        for (int i = 0, size = providers.size(); i < size; i++) {
            String provider = providers.get(i);
            try {
                Location location = manager.getLastKnownLocation(provider);
                // filter out bad locations
                if (isAccurateAndFreshAndNotNull(location)) {
                    locations.add(location);
                }
            } catch (SecurityException ex) {
                // this exception should not happen because `getProviders(true);` shouldn't allow
                Logger.log.logError("Missing permission for " + provider + " provider", ex);
            }
        }

        // sort by time and grab the freshest location
        Location freshest = null;
        if (locations.size() > 1) {
            Collections.sort(locations, timeComparator);
        }
        if (locations.size() > 0) {
            freshest = locations.get(0);
        }

        if (freshest instanceof GeoHashLocation) {
            // if the previous location still the best, return it without creating a new object.
            return (GeoHashLocation) freshest;
        } else {
            return freshest == null ? null : new GeoHashLocation(freshest);
        }
    }

    public long getMaxLocationAge() {
        return settings.getGeohashMaxAge();
    }

    public int getGeohashMinAccuracyRadius() {
        return settings.getGeohashMinAccuracyRadius();
    }

    private boolean isAccurateAndFreshAndNotNull(Location location) {
        return location != null &&
                location.getAccuracy() < getGeohashMinAccuracyRadius() &&
                (System.currentTimeMillis() - location.getTime()) < getMaxLocationAge();
    }

    private final Comparator<Location> timeComparator = new Comparator<Location>() {
        @Override
        public int compare(Location t1, Location t2) {
            if (t1.getTime() > t2.getTime()) {
                return -1;
            }
            if (t2.getTime() > t1.getTime()) {
                return 1;
            }
            return 0;
        }
    };
}
