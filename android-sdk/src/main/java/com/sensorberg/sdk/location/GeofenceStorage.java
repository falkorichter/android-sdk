package com.sensorberg.sdk.location;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;

import com.google.android.gms.location.Geofence;
import com.sensorberg.sdk.Constants;
import com.sensorberg.sdk.Logger;
import com.sensorberg.sdk.settings.DefaultSettings;
import com.sensorberg.sdk.settings.SettingsManager;
import com.sensorberg.sdk.storage.DBHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import ch.hsr.geohash.queries.GeoHashCircleQuery;
import lombok.Getter;

public class GeofenceStorage {

    private static final int MIN_RADIUS = 100;          //100 m
    private static final int MAX_RADIUS = 819200;       //So we'll get there by doubling 100 m
    public static final int HIGH = 100;
    public static final int LOW = HIGH / 2;

    private SharedPreferences preferences;
    private SettingsManager settings;
    private SQLiteDatabase db;

    @Getter private int radius;
    @Getter private int count;

    public GeofenceStorage(Context context, SettingsManager settings, SharedPreferences preferences) {
        this.settings = settings;
        this.preferences = preferences;
        radius = preferences.getInt(
                Constants.SharedPreferencesKeys.Location.INITIAL_GEOFENCES_SEARCH_RADIUS,
                DefaultSettings.DEFAULT_INITIAL_GEOFENCES_SEARCH_RADIUS);
        db = DBHelper.getInstance(context).getReadableDatabase();
        count = getCountQuery();
    }

    public void updateFences(List<String> fences) {
        SQLiteStatement stmt = null;
        try {
            long start = System.currentTimeMillis();
            db.beginTransaction();
            db.execSQL("DELETE FROM " + DBHelper.TABLE_GEOFENCES);
            stmt = db.compileStatement(
                    "INSERT OR IGNORE INTO " + DBHelper.TABLE_GEOFENCES + " (" + DBHelper.TG_FENCE + ") VALUES (?)"
            );
            for (String fence : fences) {
                stmt.clearBindings();
                stmt.bindString(1, fence);
                stmt.executeInsert();
            }
            db.setTransactionSuccessful();
            count = fences.size();
            Logger.log.geofence("Saved "+fences.size()+" in "+(System.currentTimeMillis() - start) + " ms");
        } catch (SQLException ex) {
            Logger.log.geofenceError("Storage error", ex);
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            db.endTransaction();
        }
    }

    /**
     * Get list of N geofences closest to the given location.
     * @param location Location. When null it returns up to HIGH random geofences.
     * @return List of geofences as requested. Always less than HIGH.
     */
    public HashMap<String, Geofence> getGeofences(Location location) throws SQLException {
        if (count == 0) {
            //No geofences, return empty array.
            return new HashMap<>(0);
        } else if (count < HIGH || location == null) {
            //We're below HIGH (or location is unknown), register up to HIGH geofences.
            String sql = "SELECT " + DBHelper.TG_FENCE + " FROM " + DBHelper.TABLE_GEOFENCES + " LIMIT " + HIGH;
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(sql, null);
                return getGeofencesFromCursor(cursor, count);
            } finally {
                close(cursor);
            }
        } else {
            //More than 100 geofences case.
            long start = System.currentTimeMillis();
            WGS84Point center = new WGS84Point(location.getLatitude(), location.getLongitude());
            radius = preferences.getInt(
                    Constants.SharedPreferencesKeys.Location.INITIAL_GEOFENCES_SEARCH_RADIUS,
                    DefaultSettings.DEFAULT_INITIAL_GEOFENCES_SEARCH_RADIUS);
            Cursor cursor = null;
            try {
                cursor = searchGeofencesRange(center, radius);
                if (cursor.getCount() > LOW && cursor.getCount() <= HIGH) {
                    //Good enough on first shot. Use it.
                    return getGeofencesFromCursor(cursor, cursor.getCount());
                } else {
                    //If more than HIGH we'll search and reduce radius, else increase
                    if (cursor.getCount() > HIGH) {
                        cursor = searchAndReduce(center, radius, cursor);
                    } else {
                        cursor = searchAndExtend(center, radius, cursor);
                    }
                    Logger.log.geofence("Filtered " + cursor.getCount() + " out of " + count +
                            " in " + (System.currentTimeMillis() - start) + " ms");
                    return getGeofencesFromCursor(cursor, HIGH);
                }
            } finally {
                close(cursor);
            }
        }
    }

    /**
     * @param center Center point of search.
     * @param radius Initial radius of search. Final value of radius will be stored.
     * @param current Initial cursor. Will be closed if not needed anymore.
     *                It should have > HIGH rows, otherwise it will be returned back.
     * @return Found cursor, possibly having less than HIGH geofences. If this is not possible,
     * then it might have more than HIGH rows. It can also be original cursor.
     */
    private Cursor searchAndReduce(WGS84Point center, int radius, Cursor current) {
        Cursor result = current, previous = null;
        int radiusPrev = radius;
        boolean done = false;
        while (!done) {
            //Decide whether found is acceptable.
            if (current.getCount() <= HIGH) {
                if (current.getCount() > 0) {
                    //Good enough / perfect case.
                    result = current;
                    close(previous);
                } else {
                    //It's zero, use previous.
                    radius = radiusPrev;
                    if (previous != null) {
                        //If the previous value was > 0 then we take it.
                        result = previous;
                        close(current);
                    } else {
                        //Previous is null, invalid invocation. (Called with current = 0)
                        result = current;
                        close(previous);
                    }
                }
                Logger.log.geofence("Found " + result.getCount() + " by reducing radius to " + radius + " m");
                preferences.edit().putInt(
                        Constants.SharedPreferencesKeys.Location.INITIAL_GEOFENCES_SEARCH_RADIUS, radius).apply();
                this.radius = radius;
                break;
            }
            //Loop things
            close(previous);
            radiusPrev = radius;
            radius /= 2;
            if (radius < MIN_RADIUS) {
                //So we don't overshoot or loop forever.
                radius = MIN_RADIUS;
                done = true;
                Logger.log.geofence("Minimal radius reached: " + radius);
            }
            previous = current;
            current = searchGeofencesRange(center, radius);
        }
        return result;
    }

    /**
     * @param center Center point of search.
     * @param radius Initial radius of search. Final value of radius will be stored.
     * @param current Initial cursor. Will be closed if not needed anymore.
     *                It should have <= LOW rows, otherwise it will be returned back.
     * @return Found cursor, possibly having more than LOW and less than HIGH geofences.
     * If this is not possible then it might have more or less, but always at least 1,
     * closest to center point, provided there are geofences at all in MAX_RADIUS.
     */
    private Cursor searchAndExtend(WGS84Point center, int radius, Cursor current) {
        Cursor result = current, previous = null;
        int radiusPrev = radius;
        boolean done = false;
        while (!done) {
            //Decide whether found is acceptable.
            if (current.getCount() > LOW) {
                if (current.getCount() <= HIGH) {
                    //Good enough / perfect case.
                    result = current;
                    close(previous);
                } else {
                    //More than HIGH, check previous cursor.
                    radius = radiusPrev;
                    if (previous != null && previous.getCount() != 0) {
                        //If the previous value was > 0 then we take it.
                        result = previous;
                        close(current);
                    } else {
                        //Else we take current result (which is above HIGH) and then limit it to HIGH.
                        result = current;
                        close(previous);
                    }
                }
                Logger.log.geofence("Found: " + result.getCount() + " by extending radius to " + radius + " m");
                preferences.edit().putInt(
                        Constants.SharedPreferencesKeys.Location.INITIAL_GEOFENCES_SEARCH_RADIUS, radius).apply();
                this.radius = radius;
                break;
            }
            //Loop things
            close(previous);
            radiusPrev = radius;
            radius *= 2;
            if (radius > MAX_RADIUS) {
                //So we don't overshoot or loop forever.
                radius = MAX_RADIUS;
                done = true;
                Logger.log.geofence("Maximum radius reached: " + radius);
            }
            previous = current;
            current = searchGeofencesRange(center, radius);
        }
        return result;
    }

    private List<GeoHash> truncateToBase32(List<GeoHash> geoHashes) {
        List<GeoHash> result = new ArrayList<>(geoHashes.size());
        for (GeoHash search : geoHashes) {
            int bits = search.significantBits();
            int floor = (int) (5*(Math.floor(bits/5)));
            if (floor == 0) {
                //This means the geohash for lookup is below 1 char and OVER 2500 km big.
                //(or even over 9000 perhaps ;)
                floor = 5;
            }
            GeoHash round = GeoHash.fromLongValue(search.longValue(), floor);
            result.add(round);
        }
        return result;
    }

    private HashMap<String, Geofence> getGeofencesFromCursor(Cursor cursor, int limit) {
        HashMap<String, Geofence> result = new HashMap<>(limit);
        Geofence geofence;
        int i = 0;
        while (cursor.moveToNext()) {
            i++;
            if (i <= HIGH) {
                String fence = cursor.getString(cursor.getColumnIndex(DBHelper.TG_FENCE));
                geofence = buildGeofence(fence);
                if (geofence != null) {
                    result.put(fence, geofence);
                }
            } else {
                Logger.log.geofenceError("Over " + HIGH + " found in cursor", null);
                break;
            }
        }
        return result;
    }

    private Cursor searchGeofencesRange(WGS84Point center, int radius) throws SQLException {
        GeoHashCircleQuery query = new GeoHashCircleQuery(center, radius);
        List<GeoHash> geoHashes = truncateToBase32(query.getSearchHashes());
        String sql = "SELECT " + DBHelper.TG_FENCE + " FROM " + DBHelper.TABLE_GEOFENCES + " WHERE " + DBHelper.TG_FENCE + " LIKE ?";
        String[] args = new String[geoHashes.size()];
        for (int i = 0; i < geoHashes.size(); i++) {
            args[i] = geoHashes.get(i).toBase32()+"%";
            if (i > 0) {
                sql += " OR " + DBHelper.TG_FENCE + " LIKE ?";
            }
        }
        sql += " LIMIT " + (HIGH+1);
        return db.rawQuery(sql, args);
    }

    private int getCountQuery() throws SQLException {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT count(1) FROM " + DBHelper.TABLE_GEOFENCES, null);
            if (cursor.moveToNext()) {
                return cursor.getInt(0);
            } else {
                return 0;
            }
        } finally {
            close(cursor);
        }
    }

    private Geofence buildGeofence(String fence) {
        try {
            GeofenceData temp = new GeofenceData(fence);
            return new Geofence.Builder()
                    .setRequestId(temp.getFence())
                    .setCircularRegion(
                            temp.getLatitude(),
                            temp.getLongitude(),
                            temp.getRadius())
                    .setExpirationDuration(Long.MAX_VALUE)
                    .setNotificationResponsiveness(settings.getGeofenceNotificationResponsiveness())
                    //TODO this could be optimized to trigger only on entry / exit according to layout. Not worth it now.
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();
        } catch (IllegalArgumentException ex) {
            Logger.log.geofenceError("Invalid geofence: "+fence, ex);
            return null;
        }
    }

    private void close(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }
}
