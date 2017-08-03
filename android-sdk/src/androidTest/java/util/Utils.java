package util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sensorberg.sdk.model.BeaconId;

import org.json.JSONException;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Random;

import static java.lang.System.arraycopy;

public class Utils {

    static Random random = new Random();

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    public static final long ONE_ADVERTISEMENT_INTERVAL = 100;
    public static final long ONE_SECOND = 1000;
    public static final long TEN_SECONDS = 10 * ONE_SECOND;
    public static final long THIRTY_SECONDS = 30 * ONE_SECOND;
    public static final long FORTY_SECONDS = 40 * ONE_SECOND;
    public static final long ONE_MINUTE = 60 * ONE_SECOND;
    public static final long FIVE_MINUTES = 5 * ONE_MINUTE;
    public static final long TEN_MINUTES = 10 * ONE_MINUTE;
    public static final long THIRTY_MINUTES = 30 * ONE_MINUTE;
    public static final long ONE_HOUR = 60 * ONE_MINUTE;
    public static final long VERY_LONG_TIME = Long.MAX_VALUE / 2L;

    public static final long GRACE_TIME = 5 * ONE_SECOND;
    public static final long EXIT_TIME = 40 * ONE_SECOND;
    public static final long EXIT_TIME_HAS_PASSED = EXIT_TIME + 1;
    public static final long EXIT_TIME_NOT_YET = EXIT_TIME -1;

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static JsonObject getRawResourceAsJSON(int resourceID, Context context) throws IOException, JSONException {
        String theString = getRawResourceAsString(resourceID, context);
        JsonParser parser = new JsonParser();
        return parser.parse(theString).getAsJsonObject();
    }

    public static String getRawResourceAsString(int resourceID, Context context) throws IOException {
        return toString(context.getResources().openRawResource(resourceID));
    }

    public static BeaconId getRandomBeaconId() {
        return new BeaconId(TestConstants.BEACON_PROXIMITY_ID, random.nextInt() % 65535, random.nextInt() % 65535 );
    }

    public static byte[] wrapWithZeroBytes(byte[] bytesForFakeScan, int length) {
        byte[] value = new byte[length];
        arraycopy(bytesForFakeScan, 0, value, 0, bytesForFakeScan.length);
        return value;
    }

    /**
     * Get the contents of an <code>InputStream</code> as a String
     * using the default character encoding of the platform.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input the <code>InputStream</code> to read from
     * @return the requested String
     * @throws NullPointerException if the input is null
     * @throws IOException          if an I/O error occurs
     */
    public static String toString(InputStream input) throws IOException {
        StringWriter output = new StringWriter();
        InputStreamReader in = new InputStreamReader(input);
        copy(in, output);
        return output.toString();
    }

    /**
     * Copy chars from a <code>Reader</code> to a <code>Writer</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedReader</code>.
     * <p>
     * Large streams (over 2GB) will return a chars copied value of
     * <code>-1</code> after the copy has completed since the correct
     * number of chars cannot be returned as an int. For large streams
     * use the <code>copyLarge(Reader, Writer)</code> method.
     *
     * @param input  the <code>Reader</code> to read from
     * @param output the <code>Writer</code> to write to
     * @return the number of characters copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @throws ArithmeticException  if the character count is too large
     * @since Commons IO 1.1
     */
    public static int copy(Reader input, Writer output) throws IOException {
        long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }

    /**
     * Copy chars from a large (over 2GB) <code>Reader</code> to a <code>Writer</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedReader</code>.
     *
     * @param input  the <code>Reader</code> to read from
     * @param output the <code>Writer</code> to write to
     * @return the number of characters copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since Commons IO 1.3
     */
    public static long copyLarge(Reader input, Writer output) throws IOException {
        char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
