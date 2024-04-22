package de.zenonet.stundenplan.timetableManagement;

import static de.zenonet.stundenplan.Utils.LOG_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import de.zenonet.stundenplan.DataNotAvailableException;
import de.zenonet.stundenplan.NameLookup;
import de.zenonet.stundenplan.Utils;
import de.zenonet.stundenplan.callbacks.AuthCodeRedeemedCallback;
import de.zenonet.stundenplan.models.User;

public class TimeTableApiClient implements TimeTableClient {
    private String accessToken;
    public User user;

    public NameLookup lookup; // TODO: Initialize
    public SharedPreferences sharedPreferences;
    public boolean isLoggedIn;

    @Override
    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException {

        Calendar time = Calendar.getInstance();
        time.setFirstDayOfWeek(Calendar.MONDAY);
        time.set(Calendar.WEEK_OF_YEAR, week);

        try {

            // Get raw json data
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "timetable/student/" + user.id);
            httpCon.connect();
            int respCode = httpCon.getResponseCode();
            if (respCode != 200)
                throw new TimeTableLoadException();
            String raw = Utils.readAllFromStream(httpCon.getInputStream());


            // Interpret the data
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:sssss");

            TimeTable timeTable = new TimeTable();
            timeTable.isFromCache = false;

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
                        if (endDate.after(time.getTime())) {
                            // Create the lesson
                            Lesson lesson = new Lesson();
                            lesson.Subject = lookup.lookupSubjectName(tt.getInt("SUBJECT_ID"));
                            lesson.SubjectShortName = lookup.lookupSubjectShortName(tt.getInt("SUBJECT_ID"));
                            lesson.Teacher = lookup.lookupTeacher(tt.getInt("TEACHER_ID"));
                            lesson.Room = lookup.lookupRoom(tt.getInt("ROOM_ID"));

                            Pair<LocalTime, LocalTime> startAndEndTime = Utils.getStartAndEndTimeOfPeriod(periodI - 2);
                            lesson.StartTime = startAndEndTime.first;
                            lesson.EndTime = startAndEndTime.second;

                            // Add it to the timetable
                            timeTable.Lessons[dayI][periodI - 2] = lesson;
                            break;
                        }
                    }


                }
            }

            applySubstitutions(timeTable, time);
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    private void applySubstitutions(TimeTable timeTable, Calendar time) throws TimeTableLoadException {
        // Get substitution data from api
        try {
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "substitution/student/" + user.id);
            httpCon.connect();
            if (httpCon.getResponseCode() != 200)
                throw new TimeTableLoadException();

            String rawSubstitutions = Utils.readAllFromStream(httpCon.getInputStream());

            // Interpret substitution data
            int year = time.get(Calendar.YEAR);

            JSONArray substitutions = new JSONObject(rawSubstitutions).getJSONObject("substitutions").getJSONArray(String.format("%s-%s", year, time.get(Calendar.WEEK_OF_YEAR)));
            for (int dayI = 0; dayI < 5; dayI++) {

                if (substitutions.isNull(dayI)) continue;

                JSONObject substitutionsThatDay = substitutions.getJSONObject(dayI);

                if (substitutionsThatDay.has("substitutions")) {
                    JSONArray substitutionsArray = substitutionsThatDay.getJSONArray("substitutions");
                    for (int i = 0; i < substitutionsArray.length(); i++) {
                        JSONObject substitution = substitutionsArray.getJSONObject(i);
                        int period = substitution.getInt("PERIOD") - 2; // Two-indexing again

                        String type = substitution.getString("TYPE");
                        if (type.equals("ELIMINATION") && timeTable.Lessons[dayI][period].Type != LessonType.ExtraLesson) {
                            timeTable.Lessons[dayI][period].Type = LessonType.Cancelled;
                            continue;
                        }

                        if (type.equals("SUBSTITUTION") || type.equals("SWAP") || type.equals("ROOM_SUBSTITUTION")) {
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
                            if (type.equals("ROOM_SUBSTITUTION"))
                                timeTable.Lessons[dayI][period].Type = LessonType.RoomSubstitution;
                            else
                                timeTable.Lessons[dayI][period].Type = LessonType.Substitution;

                            continue;
                        }

                        if (type.equals("EXTRA_LESSON")) {
                            timeTable.Lessons[dayI][period].Type = LessonType.Substitution;

                            // Determine if the period array needs to be resized (Hopefully this will never have to happen)
                            if (period >= timeTable.Lessons[dayI].length) {
                                // Resize the period array
                                timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], period + 1);

                                timeTable.Lessons[dayI][period].Type = LessonType.ExtraLesson;
                            } else {
                                // If the extra lesson is in the time frame of a cancelled lesson, then that's called a substitution
                                timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                            }
                            timeTable.Lessons[dayI][period].Subject = lookup.lookupSubjectName(substitution.getInt("SUBJECT_ID_NEW"));
                            timeTable.Lessons[dayI][period].SubjectShortName = lookup.lookupSubjectShortName(substitution.getInt("SUBJECT_ID_NEW"));
                            timeTable.Lessons[dayI][period].Room = lookup.lookupRoom(substitution.getInt("ROOM_ID_NEW"));
                            timeTable.Lessons[dayI][period].Teacher = lookup.lookupTeacher(substitution.getInt("TEACHER_ID_NEW"));
                            // TODO: Add saving the text property as well (used for things like classtests)
                            continue;
                        }

                        if (type.equals("CLASS_SUBSTITUTION")) {
                            // This is only interesting from a teachers perspective and I doubt a teacher will ever use this app.
                            continue;
                        }

                        if (type.equals("REDUNDANCY")) {
                            // Pretty funny that giving information about redundancies is actually completely redundant.
                            continue;
                        }

                        Log.w("timetableloading", String.format("Unknown substitution type '%s'", type));

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
                } else if (substitutionsThatDay.has("holiday")) {
                    for (int i = 0; i < timeTable.Lessons[dayI].length; i++) {
                        timeTable.Lessons[dayI][i].Type = LessonType.Holiday;
                    }
                }
            }
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    public void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public TimeTable getCurrentTimeTable() throws TimeTableLoadException {
        return getTimeTableForWeek(Calendar.getInstance().get(Calendar.WEEK_OF_YEAR));
    }

    @Override
    public User getUser() throws UserLoadException{
        try {
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "me");
            httpCon.connect();

            int respCode = httpCon.getResponseCode();
            if (respCode != 200) {
                Log.e(LOG_TAG, String.format("Unable to load personal data: Response code %d", respCode));
            }

            user = new Gson().fromJson(new InputStreamReader(httpCon.getInputStream()), User.class);
            return user;
        } catch (Exception e) {
            throw new UserLoadException();
        }
    }

    public boolean checkForChanges() throws DataNotAvailableException {
        try{
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "counter");

            JSONObject response = new JSONObject(Utils.readAllFromStream(httpCon.getInputStream()));

            // Let's just assume this counter works like a big number with a few dashes between digits
            long counter = Long.parseLong(response.getString("COUNTER").replace("-", ""));
            long lastCounter = sharedPreferences.getLong("counter", 0);

            sharedPreferences.edit().putLong("counter", counter).apply();

            return counter != lastCounter;
        } catch (IOException | JSONException e) {
            throw new DataNotAvailableException();
        }
    }

    public void login() throws ApiLoginException {
        String refreshToken = sharedPreferences.getString("refreshToken", "null");

        if(refreshToken.equals("null")) throw new ApiLoginException();
        try {
            HttpURLConnection httpCon = getUrlApiConnection("POST", "token");
            httpCon.setDoOutput(true);

            OutputStream os = httpCon.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write("{\"refresh_token\":\"" + refreshToken + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}");
            osw.flush();
            osw.close();
            os.close();

            int respCode = httpCon.getResponseCode();

            if (respCode != 200) {
                Log.e(LOG_TAG, "Login unsuccessful:" + httpCon.getResponseMessage());
                throw new ApiLoginException();
            }

            String content = Utils.readAllFromStream(httpCon.getInputStream());

            // Deserialization
            JSONObject jObj = new JSONObject(content);

            sharedPreferences.edit().putString("refreshToken", jObj.getString("refresh_token")).apply();
            accessToken = jObj.getString("access_token");
            isLoggedIn = true;
            Log.i(LOG_TAG, "Logged in successfully");
        } catch (IOException | JSONException e) {
            throw new ApiLoginException();
        }
    }

    public void redeemOAuthCodeAsync(String code, AuthCodeRedeemedCallback callback) {
        new Thread(() -> {
            HttpURLConnection httpCon;
            try {
                URL url = new URL("https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/token");
                httpCon = (HttpURLConnection) url.openConnection();
                httpCon.setRequestProperty("Content-Type", "application/json");
                httpCon.setRequestProperty("referer", "https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/");
                httpCon.setDoOutput(true);
                httpCon.setDoInput(true);

                OutputStream os = httpCon.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                osw.write("{\"code\":\"" + code + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}");
                osw.flush();
                osw.close();
                os.close();

                int respCode = httpCon.getResponseCode();
                Log.i(LOG_TAG, "Get response code " + respCode + " while redeeming OAuth code");

                String body = Utils.readAllFromStream(httpCon.getInputStream());
                JSONObject jObj = new JSONObject(body);
                accessToken = jObj.getString("access_token");
                isLoggedIn = true;

                String refreshToken = jObj.getString("refresh_token");
                sharedPreferences.edit().putString("refreshToken", refreshToken).apply();

                if (callback != null) callback.authCodeRedeemed();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private final String baseUrl = "https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/";

    public HttpURLConnection getAuthenticatedUrlConnection(String method, String endpoint) throws IOException {
        HttpURLConnection httpCon = getUrlApiConnection(method, endpoint);
        httpCon.setRequestProperty("Authorization", "Bearer " + accessToken);
        return httpCon;
    }

    public HttpURLConnection getUrlApiConnection(String method, String endpoint) throws IOException {
        HttpURLConnection httpCon = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
        httpCon.setRequestMethod(method);
        httpCon.setRequestProperty("Content-Type", "application/json");
        return httpCon;
    }

}
