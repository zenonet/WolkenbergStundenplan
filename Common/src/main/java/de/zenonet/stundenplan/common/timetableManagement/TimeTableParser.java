package de.zenonet.stundenplan.common.timetableManagement;

import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.Utils;

/**
 * TimeTableParser is a timetable provider that uses data sources with raw json data.
 * It gets its data either from the apiClient or the rawCacheClient
 */
public class TimeTableParser {
    public NameLookup lookup;

    public TimeTableApiClient apiClient;
    public RawTimeTableCacheClient rawCacheClient;
    public SharedPreferences sharedPreferences;


    public TimeTableParser(TimeTableApiClient apiClient, NameLookup lookup, SharedPreferences sharedPreferences) {
        this.apiClient = apiClient;
        this.lookup = lookup;
        this.sharedPreferences = sharedPreferences;
        this.rawCacheClient = new RawTimeTableCacheClient();
    }

    public TimeTable getTimetableForWeek(int week) throws TimeTableLoadException {
        if(week < 1 || week > 52) throw new TimeTableLoadException();

        long counter = apiClient.getLatestCounterValue();
        long cacheCounter = sharedPreferences.getLong("rawCacheCounter", -1);

        TimeTableSource source;
        Pair<String, String> rawData;
        boolean isConfirmed = apiClient.isCounterConfirmed;
        if (counter > cacheCounter || !rawCacheClient.doesRawCacheExist()) {
            // Fetch from api
            try {
                rawData = new Pair<>(
                        apiClient.getRawData(),
                        apiClient.getRawSubstitutionData()
                );

                // OPTIMIZABLE: Do saving in a separate thread so that this method can return more quickly
                rawCacheClient.saveRawData(rawData.first, rawData.second);
                sharedPreferences.edit().putLong("rawCacheCounter", counter).apply();
                source = TimeTableSource.Api;
                isConfirmed = true;
            } catch (IOException ignored) {
                // Load older version from raw cache
                rawData = rawCacheClient.loadRawData();
                source = TimeTableSource.RawCache;
            }

        } else {
            // Get data from raw cache
            rawData = rawCacheClient.loadRawData();
            source = TimeTableSource.RawCache;
        }

        Instant t0 = Instant.now();
        TimeTable timeTable = parseWeek(rawData.first, rawData.second, week);
        Instant t1 = Instant.now();
        long ms = ChronoUnit.MILLIS.between(t0, t1);
        Log.i(Utils.LOG_TAG, "Parsing for week " + week + " took " + ms + "ms");

        timeTable.source = source;
        timeTable.CounterValue = counter;
        timeTable.isCacheStateConfirmed = isConfirmed;
        return timeTable;
    }

    private TimeTable parseWeek(String json, String substitutionJson, int week) throws TimeTableLoadException {

        Calendar time = Calendar.getInstance();
        time.setFirstDayOfWeek(Calendar.MONDAY);
        time.set(Calendar.WEEK_OF_YEAR, week);

        try {
            // Interpret the data
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:sssss", Locale.GERMANY);

            TimeTable timeTable = new TimeTable();

            // Basically a 3D array with this structure: weekDay -> period -> week      I'd really like to know why
            JSONArray jsonArray = new JSONArray(json);
            for (int dayI = 0; dayI < 5; dayI++) {
                JSONObject weekDay = jsonArray.getJSONObject(dayI);

                timeTable.Lessons[dayI] = new Lesson[8]; // NOTE: This limits the max hours per day to 8
                int lessonsThisDay = 0;

                // Don't blame me, It was not me who decided to TWO-INDEX THIS!!!!!
                for (int periodI = 2; periodI < timeTable.Lessons[dayI].length + 2; periodI++) {

                    // Skip periods that aren't used in any version of the timetable completely
                    if (!weekDay.has(String.valueOf(periodI)))
                        continue;

                    JSONArray timeTables = weekDay.getJSONArray(String.valueOf(periodI));


                    // OPTIMIZABLE: Instead of iterating here, you could use binary search to improve performance
                    // find the current timetable version for this lesson
                    for (int i = 0; i < timeTables.length(); i++) {
                        JSONObject tt = timeTables.getJSONObject(i);

                        Date startDate = dateFormat.parse(tt.getJSONObject("DATE_FROM").getString("date"));
                        Date endDate = dateFormat.parse(tt.getJSONObject("DATE_TO").getString("date"));

                        // Check if the current time is inside of the validity range of this timetable-version
                        if (!endDate.after(time.getTime()) || !startDate.before(time.getTime()))
                            continue;

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

                        lessonsThisDay = periodI - 1;
                        break;
                    }

                }
//                 After all lessons have been added, resize the lessons array of the day
                timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], lessonsThisDay);
            }

            try {
                applySubstitutions(timeTable, substitutionJson, week);
            }catch (TimeTableLoadException ignored){
            }
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    private void applySubstitutions(TimeTable timeTable, String json, int week) throws TimeTableLoadException {
        try {

            /* BUG: Because the current week is used here but getTimeTableForWeek() uses the data from the timetable in whose time span the current date is in,
                it could happen that the substitution of week t-1 are shown as a modification of the timetable of the week t on weekends.
                 */
            JSONArray substitutions = new JSONObject(json).getJSONObject("substitutions").getJSONArray(String.format("%s-%s", Calendar.getInstance().get(Calendar.YEAR), week));
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
}
