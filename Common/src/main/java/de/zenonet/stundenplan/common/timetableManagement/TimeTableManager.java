package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.models.User;
import de.zenonet.stundenplan.common.callbacks.TimeTableLoadedCallback;

public class TimeTableManager implements TimeTableClient {

    public User user;
    public TimeTableApiClient apiClient = new TimeTableApiClient();
    public TimeTableCacheClient cacheClient = new TimeTableCacheClient();
    public NameLookup lookup = new NameLookup();
    private TimeTableParser parser;

    public void init(Context context) throws UserLoadException {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        lookup.lookupDirectory = context.getCacheDir().getAbsolutePath();

        if(lookup.isLookupDataAvailable()) {
            try {
                lookup.loadLookupData();
            } catch (IOException e) {
            }
        }

        apiClient.lookup = lookup;
        apiClient.init(context);

        cacheClient.lookup = lookup;
        cacheClient.init(context);

        parser = new TimeTableParser(apiClient, lookup, sharedPreferences);
    }

    public void login() throws UserLoadException {
        if (apiClient.isLoggedIn) return;

        try {
            apiClient.login();

            if (!lookup.isLookupDataAvailable())
                apiClient.fetchMasterData();
        } catch (ApiLoginException e) {
            Log.i(Utils.LOG_TAG, "Unable to log into API");
        } catch (DataNotAvailableException e) {
            throw new RuntimeException(e);
        }
        if (!lookup.isLookupDataAvailable()) {
            try {
                lookup.loadLookupData();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        user = getUser();
    }

    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException {
        long counter = apiClient.getLatestCounterValue();
        try {
            // Get timetable from cache
            TimeTable timeTable = cacheClient.getTimeTableForWeek(week);

            // Check if it's up to date
            if (timeTable.CounterValue < counter) {
                // If not, get the data from the parser
                return parser.getTimetableForWeek(week);
            }

            return timeTable;
        } catch (TimeTableLoadException ignored) {
            // Try parse from raw data cache or from api
            return parser.getTimetableForWeek(week);
        }
    }

    public TimeTable getCurrentTimeTable() throws TimeTableLoadException {
        int weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        if (Timing.getCurrentDayOfWeek() > 4) weekOfYear++;
        return getTimeTableForWeek(weekOfYear);
    }

    public AtomicReference<TimeTable> getTimeTableAsyncWithAdjustments(int week, TimeTableLoadedCallback callback) {

        AtomicInteger stage = new AtomicInteger();

        // This is kind of the wrong name. It really is more like the currently best version of the timetable, we have
        AtomicReference<TimeTable> timeTableFromCache = new AtomicReference<>(null);
        AtomicLong confirmedCounter = new AtomicLong(-1);
        // API fetch thread
        new Thread(() -> {
            try {
                if (!apiClient.isLoggedIn)
                    login();

                long counter = apiClient.getLatestCounterValue();
                confirmedCounter.set(apiClient.isCounterConfirmed ? counter : -1);
                // If the data, the cache thread loaded is not up to date
                if(cacheClient.getCounterForCacheEntry(week) < counter || stage.get() == -1){
                    // Load new data
                    TimeTable timeTable = parser.getTimetableForWeek(week);

                    stage.set(2);
                    timeTableFromCache.set(timeTable);
                    callback.timeTableLoaded(timeTable);
                    cacheClient.cacheTimetableForWeek(week, timeTable);
                }else if(stage.get() == 1 && apiClient.isCounterConfirmed) {
                    // If the cache thread already returned a value, we want to re-return with the addition of it being confirmed
                    timeTableFromCache.get().isCacheStateConfirmed = true;
                    callback.timeTableLoaded(timeTableFromCache.get());
                }

            } catch (DataNotAvailableException ignored) {
                // If the cache and the API failed, indicate that the timetable could not be loaded
                if (stage.get() == -1) callback.timeTableLoaded(null);
            }
        }).start();

        // cache fetch thread
        new Thread(() -> {
            try {
                TimeTable timeTable = cacheClient.getTimeTableForWeek(week);

                timeTable.isCacheStateConfirmed = timeTable.CounterValue == confirmedCounter.get();

                callback.timeTableLoaded(timeTable);

                // This is just for the astronomically small chance, that the API is faster than the cache, maybe remove later
                if (stage.get() == 2) return;

                stage.set(1);
                timeTableFromCache.set(timeTable);
                callback.timeTableLoaded(timeTable);
            } catch (TimeTableLoadException e) {
                // Indicate that the cache failed
                stage.set(-1);
            }
        }).start();
        return timeTableFromCache;
    }

    public AtomicReference<TimeTable> getCurrentTimeTableAsyncWithAdjustments(TimeTableLoadedCallback callback) {
        int weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        if (Timing.getCurrentDayOfWeek() > 4) weekOfYear++;
        return getTimeTableAsyncWithAdjustments(weekOfYear, callback);
    }


    public TimeTable getTimeTableForWeekForPerson() {
        return null;
    }

    public User getUser() throws UserLoadException {
        try {
            user = cacheClient.getUser();
        } catch (UserLoadException exception) {
            user = apiClient.getUser();
            cacheClient.saveUser(user);
        }
        apiClient.user = user;
        return user;
    }
}
