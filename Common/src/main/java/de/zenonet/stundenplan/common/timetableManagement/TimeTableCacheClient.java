package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.Week;
import de.zenonet.stundenplan.common.models.User;


public class TimeTableCacheClient {

    public String dataPath;
    public NameLookup lookup = new NameLookup();
    private SharedPreferences sharedPreferences;

    public void init(Context context) {
        dataPath = context.getDataDir().getAbsolutePath();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public TimeTable getTimeTableForWeek(Week week) throws TimeTableLoadException {
        try {
            File cacheFile = new File(Utils.CachePath, "/" + week.getCacheKey() + ".json");

            int length = (int) cacheFile.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(cacheFile)) {
                in.read(bytes);
            }
            String contents = new String(bytes);

            TimeTable timeTable = new Gson().fromJson(contents, TimeTable.class);
            timeTable.source = TimeTableSource.Cache;
            timeTable.isCacheStateConfirmed = false;
            // Re-add the uncached start- and end-times of the periods
            Utils.insertTimes(timeTable);
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    public void cacheCurrentTimetable(TimeTable timeTable) {
        try {

            Log.i(LogTags.Caching, "Caching timetable...");
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

    public void cacheTimetableForWeek(Week week, TimeTable timetable) {
        try {

            Log.i(LogTags.Caching, "Caching timetable for week " + week + "...");
            String json = new Gson().toJson(timetable);
            File cacheFile = new File(Utils.CachePath, "/" + week.getCacheKey() + ".json");

            try (FileOutputStream stream = new FileOutputStream(cacheFile)) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
            }
            putCounterForCacheEntry(week, timetable.CounterValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public long getCounterForCacheEntry(Week week) {
        File cacheRegistryFile = new File(Utils.CachePath, "/registry.json");

        try {
            JSONObject entries = new JSONObject(Utils.readAllText(cacheRegistryFile));

            String cacheKey = week.getCacheKey();
            if (!entries.has(cacheKey)) return -1;

            return entries.getLong(cacheKey);
        } catch (IOException | JSONException e) {
            return -1;
        }
    }
    public void putCounterForCacheEntry(Week week, long counter){
        File cacheRegistryFile = new File(Utils.CachePath, "/registry.json");

        try {
            JSONObject entries = cacheRegistryFile.exists() ? new JSONObject(Utils.readAllText(cacheRegistryFile)) : new JSONObject();
            entries.put(week.getCacheKey(), counter);
            Utils.writeAllText(cacheRegistryFile, entries.toString());
        } catch (IOException | JSONException e) {
        }
    }

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

            Log.v(LogTags.Caching, "saving user data...");
            try (FileOutputStream out = new FileOutputStream(userFile)) {
                out.write(new Gson().toJson(user).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {

        }
    }

    public void cachePosts(Post[] posts){
        try {
            File rawCacheDir = new File(Utils.CachePath, "raw");
            if(!rawCacheDir.exists()){
                rawCacheDir.mkdir();
            }

            File timetableFile = new File(Utils.CachePath, "/raw/posts.json");

            Log.v(LogTags.Caching, "saving posts...");
            String json = new Gson().toJson(posts);
            try (FileOutputStream out = new FileOutputStream(timetableFile)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {

        }
    }
    public Post[] loadPosts(){
        try {
            File timetableFile = new File(Utils.CachePath, "/raw/posts.json");
            String json = Utils.readAllText(timetableFile);

            return new Gson().fromJson(json, Post[].class);

        } catch (IOException e) {
            return null;
        }
    }
}
