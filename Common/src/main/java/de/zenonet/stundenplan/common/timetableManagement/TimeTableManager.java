package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.util.Log;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import de.zenonet.stundenplan.DataNotAvailableException;
import de.zenonet.stundenplan.NameLookup;

import de.zenonet.stundenplan.common.R;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.models.User;
import de.zenonet.stundenplan.common.callbacks.TimeTableLoadedCallback;

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
            Log.i(Utils.LOG_TAG, "Unable to log into API");
        }

        user = getUser();
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

    public AtomicReference<TimeTable> getTimeTableAsyncWithAdjustments(TimeTableLoadedCallback callback) {

        AtomicInteger stage = new AtomicInteger();

        // This is kind of the wrong name. It really is more like the currently best version of the timetable, we have
        AtomicReference<TimeTable> timeTableFromCache = new AtomicReference<>(null);
        // API fetch thread
        new Thread(() -> {
            try {
                if(!apiClient.isLoggedIn)
                    login();

                // Load from API if there are changes or if the cache failed
                if (apiClient.checkForChanges() || stage.get() == -1) {
                    TimeTable timeTable = apiClient.getCurrentTimeTable();
                    cacheClient.cacheCurrentTimetable(timeTable);

                    stage.set(2);
                    timeTableFromCache.set(timeTable);
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
                // Log.i(Utils.LOG_TAG, String.format("Time from application start to cached timetable loaded: %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

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
        return timeTableFromCache;
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
