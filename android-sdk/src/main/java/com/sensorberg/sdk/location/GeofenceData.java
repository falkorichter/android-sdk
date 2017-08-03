package com.sensorberg.sdk.location;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.sensorberg.sdk.Logger;
import com.sensorberg.utils.Objects;

import java.util.ArrayList;
import java.util.List;

import ch.hsr.geohash.GeoHash;
import lombok.Getter;

/**
 * Parcelable container for sending geofence data across process boundary.
 */
public class GeofenceData implements Parcelable {

    @Getter private final String fence;
    @Getter private final String geohash;
    @Getter private final double latitude;
    @Getter private final double longitude;
    @Getter private final int radius;

    protected GeofenceData(String fence) {
        this.fence = fence;
        String problem = check(fence);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        radius = Integer.valueOf(fence.substring(8, 14));
        if (radius == 0) {
            throw new IllegalArgumentException("Geofence radius can't be 0");
        }
        String start = fence.substring(0, 8);
        try {
            GeoHash hash = GeoHash.fromGeohashString(start);
            geohash = hash.toBase32();
            latitude = hash.getPoint().getLatitude();
            longitude = hash.getPoint().getLongitude();
        } catch (NullPointerException ex) {
            throw new IllegalArgumentException("Invalid geofence geohash: "+start);
        }
    }

    protected GeofenceData(Parcel in) {
        fence = in.readString();
        geohash = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        radius = in.readInt();
    }

    private static String check(String geofenceId) {
        if (geofenceId == null) {
            return "Geofence string can't be null";
        }
        if (geofenceId.length() != 14) {
            return "Geofence string has to be exactly 14 chars";
        }
        if (!geofenceId.substring(8, 14).matches("^[0-9]{6}$")) {
            return "Geofence last 6 chars have to be digits";
        }
        return null;
    }

    protected static List<GeofenceData> from(GeofencingEvent event) {
        String problem = check(event);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        ArrayList<GeofenceData> result = new ArrayList<>();
        for (Geofence geofence : event.getTriggeringGeofences()) {
            try {
                result.add(new GeofenceData(geofence.getRequestId()));
            } catch (IllegalArgumentException ex) {
                Logger.log.logError(ex.getMessage());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("GeofencingEvent has no triggering geofences");
        }
        return result;
    }

    private static String check(GeofencingEvent event) {
        if (event == null) {
            return "GeofencingEvent is null";
        }
        if (event.hasError()) {
            return "GeofencingEvent has error: "+event.getErrorCode();
        }
        if (event.getTriggeringGeofences() == null) {
            return "GeofencingEvent has no triggering geofences";
        }
        if (event.getTriggeringGeofences().isEmpty()) {
            return "GeofencingEvent has empty list of triggering geofences";
        }
        int transition = event.getGeofenceTransition();
        //Only GEOFENCE_TRANSITION_ENTER and GEOFENCE_TRANSITION_EXIT are supported for now.
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER && transition != Geofence.GEOFENCE_TRANSITION_EXIT) {
            return "GeofencingEvent has invalid transition: "+transition;
        }
        return null;
    }

    public static final Creator<GeofenceData> CREATOR = new Creator<GeofenceData>() {
        @Override
        public GeofenceData createFromParcel(Parcel in) {
            return new GeofenceData(in);
        }

        @Override
        public GeofenceData[] newArray(int size) {
            return new GeofenceData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(fence);
        parcel.writeString(geohash);
        parcel.writeDouble(latitude);
        parcel.writeDouble(longitude);
        parcel.writeInt(radius);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (((Object) this).getClass() != other.getClass()) {
            return false;
        }
        GeofenceData otherData = (GeofenceData) other;
        return Objects.equals(otherData.getFence(), fence);
    }

    @Override
    public int hashCode() {
        return fence.hashCode();
    }

    @Override
    public String toString() {
        return fence;
    }
}
