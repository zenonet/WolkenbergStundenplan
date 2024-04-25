package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Hashtable;

import de.zenonet.stundenplan.NameLookup;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.models.User;


public class TimeTableCacheClient implements TimeTableClient {

    public String dataPath;
    public NameLookup lookup = new NameLookup();
    private SharedPreferences sharedPreferences;

    public void init(Context context) {
        dataPath = context.getDataDir().getAbsolutePath();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public TimeTable getCurrentTimeTable() throws TimeTableLoadException {

        // TODO: Check if current cache is from this week
        try {
            File cacheFile = new File(Utils.CachePath, "timetable.json");

            int length = (int) cacheFile.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(cacheFile)) {
                in.read(bytes);
            }
            String contents = new String(bytes);

            TimeTable timeTable = new Gson().fromJson(contents, TimeTable.class);
            timeTable.isFromCache = true;
            // Re-add the uncached start- and end-times of the periods
            insertTimes(timeTable);
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    @Override
    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException {
        try {
            File cacheFile = new File(Utils.CachePath, "/" + week + "/.json");

            int length = (int) cacheFile.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(cacheFile)) {
                in.read(bytes);
            }
            String contents = new String(bytes);

            TimeTable timeTable = new Gson().fromJson(contents, TimeTable.class);
            timeTable.isFromCache = true;
            // Re-add the uncached start- and end-times of the periods
            insertTimes(timeTable);
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    private void insertTimes(TimeTable timeTable) {
        Hashtable<Integer, Pair<LocalTime, LocalTime>> cache = new Hashtable<>();
        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            for (int periodI = 0; periodI < timeTable.Lessons[dayI].length; periodI++) {
                Pair<LocalTime, LocalTime> times;
                if (cache.containsKey(periodI)) {
                    times = cache.get(periodI);
                } else {
                    times = Utils.getStartAndEndTimeOfPeriod(periodI);
                    cache.put(periodI, times);
                }
                timeTable.Lessons[dayI][periodI].StartTime = times.first;
                timeTable.Lessons[dayI][periodI].EndTime = times.second;
            }
        }
    }

    public void cacheCurrentTimetable(TimeTable timeTable) {
        try {

            Log.i(Utils.LOG_TAG, "Caching timetable...");
            String json = new Gson().toJson(timeTable);
            File cacheFile = new File(Utils.CachePath, "/timetable.json");

            try (FileOutputStream stream = new FileOutputStream(cacheFile)) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
            }

            // Save when the cache was created
            sharedPreferences.edit().putInt("weekOfCache", Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)).apply();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public User getUser() throws UserLoadException {
        try {
            File userFile = new File(dataPath, "/user.json");

            int length = (int) userFile.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(userFile)) {
                in.read(bytes);
            }
            String contents = new String(bytes);

            return new Gson().fromJson(contents, User.class);
        } catch (Exception e) {
            throw new UserLoadException();
        }
    }

    public void saveUser(User user) {
        try {
            File userFile = new File(dataPath, "/user.json");

            Log.v(Utils.LOG_TAG, "saving user data...");
            try (FileOutputStream out = new FileOutputStream(userFile)) {
                out.write(new Gson().toJson(user).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {

        }
    }
}
