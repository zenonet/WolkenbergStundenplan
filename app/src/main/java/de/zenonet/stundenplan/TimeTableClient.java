package de.zenonet.stundenplan;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;
import com.google.gson.Gson;
import de.zenonet.stundenplan.callbacks.TimeTableFetchedCallback;
import de.zenonet.stundenplan.callbacks.TimeTableLoadedCallback;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;

public class TimeTableClient {

    public SharedPreferences sharedPreferences;

    public String token;
    public boolean isLoggedIn;

    public TimeTable timeTable;

    private NameLookup lookup = new NameLookup();

    public String CachePath;

    public void init(Context context) {
        CachePath = context.getCacheDir().getAbsolutePath();
        lookup.lookupDirectory = context.getCacheDir().getAbsolutePath();
        NameLookup.setFallbackLookup(context.getString(R.string.fallback_lookup));
        sharedPreferences = context.getSharedPreferences("de.zenonet.stundenplan", Context.MODE_PRIVATE);
    }

    public void login() {
        String refreshToken = getRefreshToken();

        HttpURLConnection httpCon;
        try {
            URL url = new URL("https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/token");
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestMethod("POST");
            httpCon.setRequestProperty("Content-Type", "application/json");
            httpCon.setDoOutput(true);
            httpCon.setDoInput(true);

            // I think this actually connects already
            OutputStream os = httpCon.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write("{\"refresh_token\":\"" + refreshToken + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}");
            osw.flush();
            osw.close();
            os.close();

            String content = readAllFromStream(httpCon.getInputStream());

            // Deserialization
            JSONObject jObj = new JSONObject(content);

            setRefreshToken(jObj.getString("refresh_token"));
            token = jObj.getString("access_token");
            isLoggedIn = true;

        } catch (Exception ignored) {
        }
    }

    public void fetchTimeTable(int weekOfYear) {

        if (!isLoggedIn) login();

        // Just retry if it didn't work the first time
        if (!isLoggedIn) login();


        Calendar timeTableTime = Calendar.getInstance();
        timeTableTime.setFirstDayOfWeek(Calendar.MONDAY);
        timeTableTime.set(Calendar.WEEK_OF_YEAR, weekOfYear);

        // Get data from api
        String raw = getTimeTableRaw(536);

        // Interpret the data
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:sssss");

        timeTable = new TimeTable();
        timeTable.isFromCache = false;
        try {
            // Basically a 3D array with this structure: weekDay -> period -> week      I'd really like to know why
            JSONArray jsonArray = new JSONArray(raw);
            for (int dayI = 0; dayI < 5; dayI++) {
                JSONObject weekDay = jsonArray.getJSONObject(dayI);
                final int lessonCount = weekDay.length();
                timeTable.Lessons[dayI] = new Lesson[lessonCount];

                // Don't blame me, It was not me who decided to TWO-INDEX THIS!!!!!
                for (int periodI = 2; periodI < lessonCount + 2; periodI++) {

                    JSONArray timeTables = weekDay.getJSONArray(String.valueOf(periodI));

                    // find the first timetable version whose end time is in the future
                    for (int i = 0; i < timeTables.length(); i++) {
                        JSONObject tt = timeTables.getJSONObject(i);

                        String dateString = tt.getJSONObject("DATE_TO").getString("date");
                        Date endDate = dateFormat.parse(dateString);
                        if (endDate.after(timeTableTime.getTime())) {
                            // Create the lesson
                            Lesson lesson = new Lesson();
                            lesson.Subject = lookup.lookupSubjectName(tt.getInt("SUBJECT_ID"));
                            lesson.SubjectShortName = lookup.lookupSubjectShortName(tt.getInt("SUBJECT_ID"));
                            lesson.Teacher = lookup.lookupTeacher(tt.getInt("TEACHER_ID"));
                            lesson.Room = lookup.lookupRoom(tt.getInt("ROOM_ID"));

                            Pair<LocalTime, LocalTime> startAndEndTime = getStartAndEndTimeOfPeriod(periodI - 2);
                            lesson.StartTime = startAndEndTime.first;
                            lesson.EndTime = startAndEndTime.second;

                            // Add it to the timetable
                            timeTable.Lessons[dayI][periodI - 2] = lesson;
                            break;
                        }
                    }


                }
            }


            // Get substitution data from api
            String rawSubstitutions = getSubstitutionsRaw(536);

            // Interpret substitution data
            int year = timeTableTime.get(Calendar.YEAR);

            JSONArray substitutions = new JSONObject(rawSubstitutions).getJSONObject("substitutions").getJSONArray(String.format("%s-%s", year, weekOfYear));
            for (int dayI = 0; dayI < 5; dayI++) {

                if (substitutions.isNull(dayI)) continue;

                JSONObject substitutionsThatDay = substitutions.getJSONObject(dayI);

                if (substitutionsThatDay.has("substitutions")) {
                    JSONArray substitutionsArray = substitutionsThatDay.getJSONArray("substitutions");
                    for (int i = 0; i < substitutionsArray.length(); i++) {
                        JSONObject substitution = substitutionsArray.getJSONObject(i);
                        int period = substitution.getInt("PERIOD") - 2; // Two-indexing again

                        if (substitution.getString("TYPE").equals("ELIMINATION") && timeTable.Lessons[dayI][period].Type != LessonType.ExtraLesson) {
                            timeTable.Lessons[dayI][period].Type = LessonType.Cancelled;
                            continue;
                        }

                        if (substitution.getString("TYPE").equals("SUBSTITUTION") || substitution.getString("TYPE").equals("SWAP") || substitution.getString("TYPE").equals("ROOM_SUBSTITUTION")) {
                            // Update the timetable according to the substitutions
                            if (substitution.has("SUBJECT_ID_NEW")) {
                                timeTable.Lessons[dayI][period].Subject = lookup.lookupSubjectName(substitution.getInt("SUBJECT_ID_NEW"));
                                timeTable.Lessons[dayI][period].SubjectShortName = lookup.lookupSubjectShortName(substitution.getInt("SUBJECT_ID_NEW"));
                            }
                            if (substitution.has("ROOM_ID_NEW")) {
                                timeTable.Lessons[dayI][period].Room = lookup.lookupRoom(substitution.getInt("ROOM_ID_NEW"));
                            }
                            if (substitution.has("TEACHER_ID_NEW")) {
                                timeTable.Lessons[dayI][period].Teacher = lookup.lookupTeacher(substitution.getInt("TEACHER_ID_NEW"));
                            }
                            timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                            continue;
                        }

                        if (substitution.getString("TYPE").equals("EXTRA_LESSON")) {
                            timeTable.Lessons[dayI][period].Type = LessonType.ExtraLesson;

                            // Determine if the period array needs to be resized
                            if(period >= timeTable.Lessons[dayI].length){
                                // Resize the period array
                                timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], period+1);

                                // If the extra lesson is in the time frame of a cancelled lesson, then that's called a substitution
                                timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                            }else{
                                timeTable.Lessons[dayI][period].Type = LessonType.ExtraLesson;
                            }
                            timeTable.Lessons[dayI][period].Subject = lookup.lookupSubjectName(substitution.getInt("SUBJECT_ID_NEW"));
                            timeTable.Lessons[dayI][period].SubjectShortName = lookup.lookupSubjectShortName(substitution.getInt("SUBJECT_ID_NEW"));
                            timeTable.Lessons[dayI][period].Room = lookup.lookupRoom(substitution.getInt("ROOM_ID_NEW"));
                            timeTable.Lessons[dayI][period].Teacher = lookup.lookupTeacher(substitution.getInt("TEACHER_ID_NEW"));
                            // TODO: Add saving the text property as well
                            continue;
                        }

                        if(substitution.getString("TYPE").equals("CLASS_SUBSTITUTION")){
                            // This is only interesting from a teachers perspective and I doubt a teacher will ever use this app.
                            continue;
                        }

                        if(substitution.getString("TYPE").equals("REDUNDANCY")){
                            // Pretty funny that giving information about redundancies is actually completely redundant.
                            continue;
                        }

                        Log.w("timetableloading", String.format("Unknown substitution type '%s'", substitution.getString("TYPE")));

                    }
                }
                if (substitutionsThatDay.has("absences")) {
                    JSONArray absencesArray = substitutionsThatDay.getJSONArray("absences");
                    for (int i = 0; i < absencesArray.length(); i++) {
                        int periodFrom = Math.max(absencesArray.getJSONObject(i).getInt("PERIOD_FROM") - 2, 0); // Two-indexing again (I am going insane)
                        int periodTo = Math.max(absencesArray.getJSONObject(i).getInt("PERIOD_TO") - 2, 0);

                        for (int p = periodFrom; p < periodTo; p++) {
                            timeTable.Lessons[dayI][p].Type = LessonType.Absent;
                        }
                    }
                }

            }
            cacheTimetable();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void fetchTimeTableAsync(int weekOfYear, TimeTableFetchedCallback callback) {

        int studentId = 536;
        Thread thread = new Thread(() -> {
            fetchTimeTable(weekOfYear);
            callback.timeTableFetched(timeTable);
        });
        thread.start();
    }

    private void insertTimes(TimeTable timeTable) {
        Hashtable<Integer, Pair<LocalTime, LocalTime>> cache = new Hashtable<>();
        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            for (int periodI = 0; periodI < timeTable.Lessons[dayI].length; periodI++) {
                Pair<LocalTime, LocalTime> times;
                if (cache.containsKey(periodI)) {
                    times = cache.get(periodI);
                } else {
                    times = getStartAndEndTimeOfPeriod(periodI);
                    cache.put(periodI, times);
                }
                timeTable.Lessons[dayI][periodI].StartTime = times.first;
                timeTable.Lessons[dayI][periodI].EndTime = times.second;
            }
        }
    }

    private String getTimeTableRaw(int studentId) {
        HttpURLConnection httpCon;
        try {
            URL url = new URL("https://wolkenberg-gymnasium.de/wolkenberg-app/api/timetable/student/" + studentId);
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestProperty("Authorization", "Bearer " + token);
            httpCon.connect();
            int respCode = httpCon.getResponseCode();
            return readAllFromStream(httpCon.getInputStream());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getSubstitutionsRaw(int studentId) {
        HttpURLConnection httpCon;
        try {
            URL url = new URL("https://wolkenberg-gymnasium.de/wolkenberg-app/api/substitution/student/" + studentId);
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestProperty("Authorization", "Bearer " + token);
            httpCon.connect();
            int respCode = httpCon.getResponseCode();
            return readAllFromStream(httpCon.getInputStream());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getRefreshToken() {
        return sharedPreferences.getString("refreshToken", "0.AQUAbReNd3UPKkevzKhVU6sG3anigvv9Ho5KmsaSQTq0tYsbAco.AgABAAEAAADnfolhJpSnRYB1SVj-Hgd8AgDs_wUA9P-YPZxfz5lRcipTW6bUImtZ85-xeSV5-_hW7afh9SG0ioTmwYRHWIm7o4ydIm3PLvNliBZO48nYQ7GI5A8TV2s8amOTMV7jItIq9hcwkmW0xFw9Br1QtjfPHuZ8gpCfP-hKgU1bvmLnttP6HTSEacHBAVCiWxI0Yjs8OhX8GdfRgdmL9FJZPTKjZJxiAJ_hTb0fKbqDJ0P97N4Eie8JVd_Ss7VxTXz4We5yOreooxHZOBpUtnnCbPnZRpvnrlWlOHzPOFyxNNQZU8Y6jjGOt8dhsbL0fhy7XX-FwZHUxxjmKulFs0J44R2wLqAyDQLNYiV8jnvg3R7E77W-qMhXNOJil0Bupt6AhVPyBY_Sw9fC85nP9AliDfraF6WCvlMHaAQFkpC8rMkHBgr1lLYeK4rZ4rWAgiLg7Xlqd9D3KRpltbgBRyzN0-ox1joVvTOeAiPvxKRPEeog7wGf1oQRVZWsRbhQHvIjXpHXj9Ts_gbzjsTwYZp__6eWI629b9smmvyfZblN2nYXyw5qC-XkILB37YZYB3rlH3pPXszzVUMsCgzz7rvtLXTTEFxdmhRluVZ9HTcnuPgu2Kbx4HsXviVRkoIgZ7h3B3P5Ig10dXjasoGgcvLH5jJfHVSbAAOrTaFJ7PDlhszxO7mzdBvM3q6ZL82AUCFDiM3ozxXOxi15tm_ejkvdELOcfug-Ujb7lBCSGhqJ4cwT2slfmdQE-VwYpXg_OdFTyJAvqIn423hJOMoHgtM5BlRs2puuEDVxBEpGjtHAnYM");
    }

    private void setRefreshToken(String newRefreshToken) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("refreshToken", newRefreshToken);
        editor.apply();
    }

    private String readAllFromStream(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            sb.append(output);
        }
        return sb.toString();
    }

    private boolean checkForChanges() {
        Instant t0 = Instant.now();

        if (!isLoggedIn) login();

        HttpURLConnection httpCon;
        try {
            URL url = new URL("https://wolkenberg-gymnasium.de/wolkenberg-app/api/counter");
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestMethod("GET");
            httpCon.setRequestProperty("Content-Type", "application/json");
            httpCon.setRequestProperty("Authorization", "Bearer " + token);
            httpCon.setDoInput(true);

            int respCode = httpCon.getResponseCode();

            JSONObject response = new JSONObject(readAllFromStream(httpCon.getInputStream()));

            // Let's just assume this counter works like a big number with a few random dashes between digits
            long counter = Long.parseLong(response.getString("COUNTER").replace("-", ""));

            long lastCounter = sharedPreferences.getLong("counter", 0);

            // Save new value
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("counter", counter);
            editor.apply();
            Instant t1 = Instant.now();
            System.out.println(Duration.between(t0, t1));

            return counter > lastCounter;
        } catch (Exception e) {
            throw new RuntimeException(e);
            //return true;
        }
    }

    public void cacheTimetable() {
        try {
            String json = new Gson().toJson(timeTable);
            File cacheFile = new File(CachePath, "/timetable.json");

            try (FileOutputStream stream = new FileOutputStream(cacheFile)) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadCachedTimetable() {
        try {
            File cacheFile = new File(CachePath, "/timetable.json");
            int length = (int) cacheFile.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(cacheFile)) {
                in.read(bytes);
            }
            String contents = new String(bytes);

            timeTable = new Gson().fromJson(contents, TimeTable.class);
            timeTable.isFromCache = true;
            // Re-add the uncached start- and end-times of the periods
            insertTimes(timeTable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void loadTimeTableAsync(int weekOfYear, TimeTableLoadedCallback callback) {
        Thread fetchThread = new Thread(() -> {
            Instant t0 = Instant.now();
            if (!checkForChanges()) {
                Log.w("timeTableLoading", "Not loading timetable from api because cache is not outdated");
                return;
            }

            fetchTimeTable(weekOfYear);

            Instant t1 = Instant.now();
            Log.w("timeTableLoading", String.format("TimeTable loaded from api in %d ms", Duration.between(t0, t1).toMillis()));

            callback.timeTableLoaded(timeTable);
        });
        fetchThread.start();

        Thread cacheLoadThread = new Thread(() -> {
            Instant t0 = Instant.now();
            loadCachedTimetable();

            Instant t1 = Instant.now();
            Log.w("timeTableLoading", String.format("TimeTable loaded from cache in %d ms", Duration.between(t0, t1).toMillis()));

            callback.timeTableLoaded(timeTable);
        });
        cacheLoadThread.start();
    }

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

}