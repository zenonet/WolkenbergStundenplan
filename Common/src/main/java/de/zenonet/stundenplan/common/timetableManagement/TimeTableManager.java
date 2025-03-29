package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.ResultType;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Week;
import de.zenonet.stundenplan.common.callbacks.TimeTableLoadFailedCallback;
import de.zenonet.stundenplan.common.callbacks.TimeTableLoadedCallback;
import de.zenonet.stundenplan.common.models.User;

public class TimeTableManager {

    public User user;
    public TimeTableApiClient apiClient = new TimeTableApiClient();
    public TimeTableCacheClient cacheClient = new TimeTableCacheClient();
    public RawTimeTableCacheClient rawCacheClient = new RawTimeTableCacheClient();
    public NameLookup lookup = new NameLookup();
    public  TimeTableParser parser;
    private SharedPreferences sharedPreferences;

    public void init(Context context) throws UserLoadException {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        lookup.lookupDirectory = context.getCacheDir().getAbsolutePath();

        if (lookup.isLookupDataAvailable()) {
            try {
                lookup.loadLookupData();
            } catch (IOException e) {
            }
        }

        apiClient.lookup = lookup;
        apiClient.init(context);

        cacheClient.lookup = lookup;
        cacheClient.init(context);

        parser = new TimeTableParser(lookup);
    }

    public ResultType login() {
        if (apiClient.isLoggedIn) return ResultType.Success;

        ResultType resultType = apiClient.login();
        if(resultType != ResultType.Success) {
            try {
                user = cacheClient.getUser();
            } catch (UserLoadException ignored) {
            }
            return resultType;
        }

        try {
            if (!lookup.isLookupDataAvailable())
                apiClient.fetchMasterData();

            user = getUser();
        }
        catch (UserLoadException e){
            return ResultType.NoLoginSaved;
        }
        catch (DataNotAvailableException e) {
            return ResultType.CantLoadLookupData;
        }

        return ResultType.Success;
    }

    public TimeTable getTimetableForWeekFromRawCacheOrApi(Week week) throws TimeTableLoadException {
        long cacheCounter = sharedPreferences.getLong("rawCacheCounter", -1);

        if(login() != ResultType.Success && cacheCounter == -1)throw new TimeTableLoadException("Failed to log in and cache miss");

        long counter = apiClient.getLatestCounterValue();

        TimeTableSource source;
        Pair<String, String> rawData;
        boolean isConfirmed = apiClient.isCounterConfirmed;
        if (counter > cacheCounter || !rawCacheClient.doesRawCacheExist()) {
            // Fetch from api
            try {
                // Simultaneously fetch masterdata and raw timetable data:

                final byte[] masterDataChanged = {-2};
                Thread masterDataFetchThread = getMasterDataFetchThread(masterDataChanged);

                rawData = apiClient.fetchRawDataFromApi();
                Log.i(LogTags.Api, "Fetched raw timetable data");
                masterDataFetchThread.join();

                // If master data changed, re-fetch user data since it might have changed
                if (masterDataChanged[0] == 1) {
                    Log.i(LogTags.Api, "Detected change in master data");
                    int oldUserId = user.id;
                    // TODO: Just lookup new ID in lookup data instead of refetching userdata
                    fetchUser();

                    // If the user id changed (yeah, that actually happens)
                    if(oldUserId != user.id){
                        Log.i(LogTags.Api, "Detected change in user id, re-fetching...");
                        // We need to re-fetch raw timetable data
                        rawData = apiClient.fetchRawDataFromApi();
                    }
                }


                if (rawData.first != null && rawData.second != null) {
                    // OPTIMIZABLE: Do saving in a separate thread so that this method can return more quickly
                    rawCacheClient.saveRawData(rawData.first, rawData.second);
                    sharedPreferences.edit().putLong("rawCacheCounter", counter).apply();
                    isConfirmed = true;
                    source = TimeTableSource.Api;
                } else {
                    // Load older version from raw cache
                    rawData = rawCacheClient.loadRawData();
                    source = TimeTableSource.RawCache;
                }

            } catch (DataNotAvailableException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        } else {
            // Get data from raw cache
            rawData = rawCacheClient.loadRawData(); // note that this might return null
            source = TimeTableSource.RawCache;
        }

        Instant t0 = Instant.now();
        TimeTable timeTable = parser.parseWeek(rawData.first, rawData.second, week);
        Instant t1 = Instant.now();
        long ms = ChronoUnit.MILLIS.between(t0, t1);
        Log.i(LogTags.Timing, "Parsing for week " + week + " took " + ms + "ms");

        timeTable.source = source;
        timeTable.CounterValue = counter;
        timeTable.isCacheStateConfirmed = isConfirmed;
        timeTable.timeOfConfirmation = apiClient.timeOfConfirmation;
        cacheClient.cacheTimetableForWeek(week, timeTable);
        return timeTable;
    }

    private @NonNull Thread getMasterDataFetchThread(byte[] masterDataChanged) {
        Thread masterDataFetchThread = new Thread(() -> {
            // We also need to check if the lookup data changed (there is no indicator for that, meaning we have to run the ~100ms request every time)
            try {
                masterDataChanged[0] = apiClient.fetchMasterData() ? (byte)1 : 0;
                Log.i(LogTags.Api, "Re-fetched master data");
            } catch (DataNotAvailableException e) {
                masterDataChanged[0] = (byte)-1;
            }

        });
        masterDataFetchThread.start();
        return masterDataFetchThread;
    }

    public TimeTable getTimeTableForWeek(Week week) throws TimeTableLoadException {
        long counter = apiClient.getLatestCounterValue();
        try {
            // Get timetable from cache
            TimeTable timeTable = cacheClient.getTimeTableForWeek(week);

            // Check if it's up to date
            if (timeTable.CounterValue < counter) {
                // If not, get the data from the parser
                return getTimetableForWeekFromRawCacheOrApi(week);
            }

            return timeTable;
        } catch (TimeTableLoadException ignored) {
            // Try parse from raw data cache or from api
            return getTimetableForWeekFromRawCacheOrApi(week);
        }
    }

    public TimeTable getCurrentTimeTable() throws TimeTableLoadException {
        return getTimeTableForWeek(Timing.getRelevantWeekOfYear());
    }
    public AtomicReference<TimeTable> getTimeTableAsyncWithAdjustments(Week week, TimeTableLoadedCallback callback) {
        return getTimeTableAsyncWithAdjustments(week, callback, null);
    }

    public AtomicReference<TimeTable> getTimeTableAsyncWithAdjustments(Week week, TimeTableLoadedCallback callback, TimeTableLoadFailedCallback errorCallback) {

        AtomicInteger stage = new AtomicInteger();

        // This is kind of the wrong name. It really is more like the currently best version of the timetable, we have
        AtomicReference<TimeTable> timeTableFromCache = new AtomicReference<>(null);
        AtomicLong confirmedCounter = new AtomicLong(-1);
        // API fetch thread
        new Thread(() -> {
            try {
                ResultType loginResult = login();
                if(loginResult != ResultType.Success && errorCallback != null){
                    errorCallback.errorOccurred(loginResult);
                    return;
                }

                long counter = apiClient.getLatestCounterValue();
                confirmedCounter.set(apiClient.isCounterConfirmed ? counter : -1);
                // If the data, the cache thread loaded is not up to date
                if (cacheClient.getCounterForCacheEntry(week) < counter || stage.get() == -1 || sharedPreferences.getBoolean("alwaysParse", false)) {
                    // Load new data
                    TimeTable timeTable = getTimetableForWeekFromRawCacheOrApi(week);

                    stage.set(2);
                    timeTableFromCache.set(timeTable);
                    callback.timeTableLoaded(timeTable);
                    cacheClient.cacheTimetableForWeek(week, timeTable);
                } else if (stage.get() == 1 && apiClient.isCounterConfirmed) {
                    // If the cache thread already returned a value, we want to re-return with the addition of it being confirmed
                    timeTableFromCache.get().isCacheStateConfirmed = true;
                    timeTableFromCache.get().timeOfConfirmation = apiClient.timeOfConfirmation;
                    callback.timeTableLoaded(timeTableFromCache.get());
                }

            } catch (DataNotAvailableException ignored) {
                // If the cache and the API failed, indicate that the timetable could not be loaded
                if (stage.get() == -1) callback.timeTableLoaded(null);

                if(errorCallback != null) errorCallback.errorOccurred(ResultType.CantLoadTimeTable);
            }
        }).start();

        // cache fetch thread
        new Thread(() -> {
            try {
                TimeTable timeTable = cacheClient.getTimeTableForWeek(week);

                timeTable.isCacheStateConfirmed = timeTable.CounterValue == confirmedCounter.get();
                timeTable.timeOfConfirmation = apiClient.timeOfConfirmation;
                callback.timeTableLoaded(timeTable);

                // This is just for the astronomically small chance, that the API is faster than the cache, maybe remove later
                if (stage.get() == 2) return;

                stage.set(1);
                timeTableFromCache.set(timeTable);
                callback.timeTableLoaded(timeTable);
            } catch (TimeTableLoadException e) {
                // Indicate that the cache failed
                stage.set(-1);
                if(errorCallback != null) errorCallback.errorOccurred(ResultType.CacheMiss);
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
            fetchUser();
        }
        apiClient.user = user;
        return user;
    }

    private void fetchUser() throws UserLoadException {
        user = apiClient.fetchUserData();
        cacheClient.saveUser(user);
        apiClient.user = user;
    }

    public Post[] getPosts(){
        // Ensure a posts hash is available
        String localHash = sharedPreferences.getString("postsHash", "_");
        apiClient.getLatestCounterValue();
        String apiHash = apiClient.postsHash;

        if(!localHash.equals(apiHash)){
            Post[] posts = apiClient.fetchPosts();
            cacheClient.cachePosts(posts);
            return posts;
        }
        return cacheClient.loadPosts();
    }
}
