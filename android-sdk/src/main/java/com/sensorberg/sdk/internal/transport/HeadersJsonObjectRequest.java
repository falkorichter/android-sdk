package com.sensorberg.sdk.internal.transport;

import com.android.sensorbergVolley.AuthFailureError;
import com.android.sensorbergVolley.Cache;
import com.android.sensorbergVolley.NetworkResponse;
import com.android.sensorbergVolley.ParseError;
import com.android.sensorbergVolley.Response;
import com.android.sensorbergVolley.toolbox.HttpHeaderParser;
import com.android.sensorbergVolley.toolbox.JsonRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sensorberg.sdk.model.ISO8601TypeAdapter;
import com.sensorberg.sdk.model.realm.RealmAction;
import com.sensorberg.sdk.model.sugarorm.SugarAction;
import com.sensorberg.sdk.model.sugarorm.SugarScan;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.Map;

public class HeadersJsonObjectRequest<T> extends JsonRequest<T> {

    private final Map<String, String> headers;
    private final Class<T> clazz;
    private boolean shouldAlwaysTryWithNetwork = false;

    //TODO - will need to update - take out adapter
    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, ISO8601TypeAdapter.DATE_ADAPTER)
            .registerTypeAdapter(SugarScan.class, new SugarScan.SugarScanObjectTypeAdapter())
            .registerTypeAdapter(SugarAction.class, new SugarAction.SugarActionTypeAdapter())
            .registerTypeAdapter(JSONObject.class, new JSONObjectTyeAdapter())
            .create();

    public static final Gson gson2 = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();


    public HeadersJsonObjectRequest(int method, String url, Map<String, String> headers, Object body, Response.Listener<T> listener, Response.ErrorListener errorListener, Class<T> clazz) {
        super(method, url, body == null ? null : gson.toJson(body), listener, errorListener);
        this.headers = headers;
        this.clazz = clazz;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        if (response.statusCode == HttpURLConnection.HTTP_NO_CONTENT){
            return Response.success(null, null);
        }
        if (clazz == Cache.Entry.class){
            return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
        }
        else if (clazz == JSONObject.class){
            try {
                String jsonString =
                        new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                //noinspection unchecked -> see if condition
                return Response.success((T) new JSONObject(jsonString), HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        } else {
            try {
                String json = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                return Response.success(gson.fromJson(json, clazz), HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JsonSyntaxException e) {
                return Response.error(new ParseError(e));
            }
        }
    }

    public HeadersJsonObjectRequest<T> setShouldAlwaysTryWithNetwork(boolean shouldAlwaysTryWithNetwork) {
        this.shouldAlwaysTryWithNetwork = shouldAlwaysTryWithNetwork;
        return this;
    }

    @Override
    public boolean shouldAlwaysTryWithNetwork() {
        return shouldAlwaysTryWithNetwork;
    }
}
