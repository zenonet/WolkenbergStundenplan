package de.zenonet.stundenplan.common;

import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

public class Utils {
    public static final String LOG_TAG = "timeTableLoading";
    static final String periodTimeJSON = "{\"0\":{\"PERIOD_TIME_ID\":0,\"START_TIME\":800,\"END_TIME\":845},\"1\":{\"PERIOD_TIME_ID\":1,\"START_TIME\":850,\"END_TIME\":935},\"2\":{\"PERIOD_TIME_ID\":2,\"START_TIME\":955,\"END_TIME\":1040},\"3\":{\"PERIOD_TIME_ID\":3,\"START_TIME\":1045,\"END_TIME\":1130},\"4\":{\"PERIOD_TIME_ID\":4,\"START_TIME\":1155,\"END_TIME\":1240},\"5\":{\"PERIOD_TIME_ID\":5,\"START_TIME\":1245,\"END_TIME\":1330},\"6\":{\"PERIOD_TIME_ID\":6,\"START_TIME\":1340,\"END_TIME\":1425},\"7\":{\"PERIOD_TIME_ID\":7,\"START_TIME\":1430,\"END_TIME\":1515},\"8\":{\"PERIOD_TIME_ID\":8,\"START_TIME\":1520,\"END_TIME\":1605}}";

    public static int getCurrentPeriod(LocalTime time) {
        // Get current time
        int currentMinuteOfDay = time.getHour() * 60 + time.getMinute();

        try {

            // Deserialize JSON
            JSONObject jsonObject = new JSONObject(periodTimeJSON);

            int previousPeriod = -1;
            // Check current period
            for (int i = 0; i < 8; i++) {
                JSONObject periodJSON = jsonObject.getJSONObject(String.valueOf(i));
                int startTime = periodJSON.getInt("START_TIME");
                int endTime = periodJSON.getInt("END_TIME");
                int startHour = startTime / 100;
                int startMinute = startTime % 100;
                int endHour = endTime / 100;
                int endMinute = endTime % 100;
                int startMinuteOfDay = startHour * 60 + startMinute;
                int endMinuteOfDay = endHour * 60 + endMinute;

                if (currentMinuteOfDay >= startMinuteOfDay && currentMinuteOfDay <= endMinuteOfDay) {
                    return i;
                } else if (currentMinuteOfDay < startMinuteOfDay) {
                    return previousPeriod;
                }
                previousPeriod = i;
            }
            return -1;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static Pair<LocalTime, LocalTime> getStartAndEndTimeOfPeriod(int period) {
        try {
            JSONObject periodTimes = new JSONObject(periodTimeJSON);
            if (!periodTimes.has(String.valueOf(period))) return null;

            JSONObject periodJSON = periodTimes.getJSONObject(String.valueOf(period));
            int startTime = periodJSON.getInt("START_TIME");
            int endTime = periodJSON.getInt("END_TIME");

            int startHour = startTime / 100;
            int startMinute = startTime % 100;
            int endHour = endTime / 100;
            int endMinute = endTime % 100;

            return new Pair<>(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));


        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    public static String CachePath;

    public static String readAllFromStream(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            sb.append(output);
        }
        return sb.toString();
    }

    public static void writeAllText(File file, String content) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
    public static String readAllText(File file) throws IOException {
        int length = (int) file.length();
        byte[] bytes = new byte[length];
        try (FileInputStream in = new FileInputStream(file)) {
            in.read(bytes);
        }
        return new String(bytes);
    }
}
