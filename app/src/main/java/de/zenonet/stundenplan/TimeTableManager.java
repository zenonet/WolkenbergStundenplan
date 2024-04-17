package de.zenonet.stundenplan;

import static de.zenonet.stundenplan.Utils.LOG_TAG;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import de.zenonet.stundenplan.callbacks.TimeTableLoadedCallback;
import de.zenonet.stundenplan.models.User;

public class TimeTableManager implements TimeTableClient {

    public User user;
    public TimeTableApiClient apiClient = new TimeTableApiClient();
    public TimeTableCacheClient cacheClient = new TimeTableCacheClient();
    public NameLookup lookup = new NameLookup();
    public TimeTable currentTimeTable;

    public void init(Context context) throws UserLoadException {

        lookup.lookupDirectory = context.getCacheDir().getAbsolutePath();
        NameLookup.setFallbackLookup(context.getString(R.string.fallback_lookup));

        apiClient.lookup = lookup;
        apiClient.init(context);

        cacheClient.lookup = lookup;
        cacheClient.init(context);
    }

    public void login() throws UserLoadException {
        if(apiClient.isLoggedIn) return;

        try {
            apiClient.login();
        } catch (ApiLoginException e) {
            Log.i(LOG_TAG, "Unable to log into API");
        }

        user = getUser();
        apiClient.user = user;
    }

    public boolean checkForChanges() throws DataNotAvailableException {
        return apiClient.checkForChanges();
    }

    public TimeTable getCurrentTimeTable() throws TimeTableLoadException {
        currentTimeTable = null;
        try {
            if (apiClient.checkForChanges()) {
                currentTimeTable = apiClient.getCurrentTimeTable();
                cacheClient.cacheCurrentTimetable(currentTimeTable);
            }
        } catch (DataNotAvailableException ignored) {

        }
        if (currentTimeTable == null)
            currentTimeTable = cacheClient.getCurrentTimeTable();

        return currentTimeTable;
    }

    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException {
        TimeTable timeTable;
        try {
            timeTable = apiClient.getTimeTableForWeek(week);
        } catch (TimeTableLoadException ignored) {
            try {
                timeTable = cacheClient.getTimeTableForWeek(week);
            } catch (TimeTableLoadException ignored2) {
                throw new TimeTableLoadException();
            }
        }
        return timeTable;
    }

    public void getTimeTableAsyncWithAdjustments(TimeTableLoadedCallback callback) {

        AtomicInteger stage = new AtomicInteger();
        AtomicReference<TimeTable> timeTableFromCache = new AtomicReference<>();
        // API fetch thread
        new Thread(() -> {
            try {
                if (apiClient.checkForChanges()) {
                    TimeTable timeTable = apiClient.getCurrentTimeTable();
                    cacheClient.cacheCurrentTimetable(timeTable);

                    stage.set(2);
                    callback.timeTableLoaded(timeTable);
                }else{
                    if(stage.get() == 1){
                        timeTableFromCache.get().isCacheStateConfirmed = true;
                        callback.timeTableLoaded(timeTableFromCache.get());
                    }

                    stage.set(1);
                }


            } catch (DataNotAvailableException ignored) {
                // If the cache and the API failed, indicate that the timetable could not be loaded
                if(stage.get() == -1) callback.timeTableLoaded(null);
            }
        }).start();

        // cache fetch thread
        new Thread(() -> {
            try {
                TimeTable timeTable = cacheClient.getCurrentTimeTable();

                // This is just for the astronomically small chance, that the API is faster than the cache, maybe remove later
                if(stage.get() == 2) return;

                stage.set(1);
                timeTableFromCache.set(timeTable);
                callback.timeTableLoaded(timeTable);
            } catch (TimeTableLoadException e) {
                // Indicate that the cache failed
                stage.set(-1);
            }
        }).start();
    }

    public TimeTable getTimeTableForWeekForPerson() {
        return null;
    }

    public User getUser() throws UserLoadException {
        try {
            return cacheClient.getUser();
        } catch (UserLoadException exception) {
            User user = apiClient.getUser();
            cacheClient.saveUser(user);
            return user;
        }
    }
}
