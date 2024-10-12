package de.zenonet.stundenplan.common.timetableManagement;

import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.Utils;

/**
 * TimeTableParser is a timetable provider that uses data sources with raw json data.
 * It gets its data either from the apiClient or the rawCacheClient
 */
public class TimeTableParser {
    public NameLookup lookup;

    public RawTimeTableCacheClient rawCacheClient;
    public SharedPreferences sharedPreferences;


    public TimeTableParser(NameLookup lookup, SharedPreferences sharedPreferences) {
        this.lookup = lookup;
        this.sharedPreferences = sharedPreferences;
        this.rawCacheClient = new RawTimeTableCacheClient();
    }

    public TimeTable parseWeek(String json, String substitutionJson, int week) throws TimeTableLoadException {

        Calendar time = Calendar.getInstance();
        // Set the week of year
        time.set(Calendar.WEEK_OF_YEAR, week);
        // Ensure the day of week is not a day on the weekend (which may not have a valid timetable)
        time.set(Calendar.DAY_OF_WEEK, 2);

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
                // Update 02.09.2024: Wow, they actually fixed two-indexing (except they forgot to remove it in the official client as well for day), I am so proud of them! Next up they hopefully fix user-id's changing once a year
                // Back to the technical part: it's one indexing but I accept that because there is technically support for a 0th period (maybe I should support that too (TODO))
                for (int periodI = 1; periodI < timeTable.Lessons[dayI].length + 1; periodI++) {

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

                        if (endDate == null || startDate == null) continue;

                        // Check if the current time is inside of the validity range of this timetable-version
                        if (!endDate.after(time.getTime()) || !startDate.before(time.getTime()))
                            continue;

                        // Create the lesson
                        Lesson lesson = new Lesson();
                        lesson.Subject = lookup.lookupSubjectName(tt.getInt("SUBJECT_ID"));
                        lesson.SubjectShortName = lookup.lookupSubjectShortName(tt.getInt("SUBJECT_ID"));
                        lesson.Teacher = lookup.lookupTeacher(tt.getInt("TEACHER_ID"));
                        lesson.Room = lookup.lookupRoom(tt.getInt("ROOM_ID"));

                        Pair<LocalTime, LocalTime> startAndEndTime = Utils.getStartAndEndTimeOfPeriod(periodI - 1);
                        lesson.StartTime = startAndEndTime.first;
                        lesson.EndTime = startAndEndTime.second;

                        // Add it to the timetable
                        timeTable.Lessons[dayI][periodI - 1] = lesson;

                        lessonsThisDay = periodI;
                        break;
                    }

                }
//                 After all lessons have been added, resize the lessons array of the day
                timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], lessonsThisDay);
            }

            try {
                applySubstitutions(timeTable, substitutionJson, week);
            } catch (TimeTableLoadException ignored) {
            }
            return timeTable;
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }

    @NonNull
    private Lesson createLessonFromSubstitution(JSONObject tt, int periodI) throws JSONException {
        Lesson lesson = new Lesson();
        lesson.Subject = lookup.lookupSubjectName(tt.getInt("SUBJECT_ID_NEW"));
        lesson.SubjectShortName = lookup.lookupSubjectShortName(tt.getInt("SUBJECT_ID_NEW"));
        lesson.Teacher = lookup.lookupTeacher(tt.getInt("TEACHER_ID_NEW"));
        lesson.Room = lookup.lookupRoom(tt.getInt("ROOM_ID_NEW"));

        Pair<LocalTime, LocalTime> startAndEndTime = Utils.getStartAndEndTimeOfPeriod(periodI - 1);
        lesson.StartTime = startAndEndTime.first;
        lesson.EndTime = startAndEndTime.second;
        return lesson;
    }

    private void applyDataModifications(JSONObject substitution, Lesson lesson) {
        //TODO: Oh god, pls fix this
        try {
            if (substitution.has("SUBJECT_ID_NEW") && !substitution.isNull("SUBJECT_ID_NEW")) lesson.Subject = lookup.lookupSubjectName(substitution.getInt("SUBJECT_ID_NEW"));
        } catch (Exception ignored) {}
        try {
            if (substitution.has("SUBJECT_ID_NEW") && !substitution.isNull("SUBJECT_ID_NEW")) lesson.SubjectShortName = lookup.lookupSubjectShortName(substitution.getInt("SUBJECT_ID_NEW"));
        } catch (Exception ignored) {}
        try {
            if (substitution.has("ROOM_ID_NEW") && !substitution.isNull("ROOM_ID_NEW")) lesson.Room = lookup.lookupRoom(substitution.getInt("ROOM_ID_NEW"));
        } catch (Exception ignored) {}
        try {
            if (substitution.has("TEACHER_ID_NEW") && !substitution.isNull("TEACHER_ID_NEW")) lesson.Teacher = lookup.lookupTeacher(substitution.getInt("TEACHER_ID_NEW"));
        } catch (Exception ignored) {}
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
                        int period = substitution.getInt("PERIOD") - 1; // Two-indexing again (except not anymore, yay)

                        if (substitution.has("TEXT")) {
                            String text = substitution.getString("TEXT");
                            if (!text.isEmpty())
                                timeTable.Lessons[dayI][period].Text = text;
                        }

                        String type = substitution.getString("TYPE");

                        if (timeTable.Lessons[dayI][period] == null) timeTable.Lessons[dayI][period] = new Lesson();

                        applyDataModifications(substitution, timeTable.Lessons[dayI][period]);

                        if (type.equals("ASSIGNMENT")) {
                            timeTable.Lessons[dayI][period].Text = "Aufgaben";
                            timeTable.Lessons[dayI][period].Type = LessonType.Assignment;
                            continue;
                        }

                        if (type.equals("ELIMINATION") && timeTable.Lessons[dayI][period].Type != LessonType.ExtraLesson) {
                            timeTable.Lessons[dayI][period].Type = LessonType.Cancelled;
                            continue;
                        }

                        if (type.equals("SUPERVISION") && timeTable.Lessons[dayI][period].Type == LessonType.Cancelled) {
                            timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
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

                            // Determine if the period array needs to be resized (Hopefully this will never have to happen)
                            if (period >= timeTable.Lessons[dayI].length) {
                                // Resize the period array
                                timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], period + 1);
                            }

                            if (timeTable.Lessons[dayI][period].Type == LessonType.Cancelled) {
                                // If the extra lesson is in the time frame of a cancelled lesson, then that's called a substitution
                                timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                            } else {
                                timeTable.Lessons[dayI][period].Type = LessonType.ExtraLesson;
                            }
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

                        for (int p = periodFrom; p <= periodTo; p++) {
                            if (timeTable.Lessons[dayI].length <= p || timeTable.Lessons[dayI][p] == null)
                                continue;

                            timeTable.Lessons[dayI][p].Type = LessonType.Absent;
                        }
                    }
                } else if (substitutionsThatDay.has("holiday")) {
                    for (int i = 0; i < timeTable.Lessons[dayI].length; i++) {
                        if (timeTable.Lessons[dayI][i] == null) continue;
                        timeTable.Lessons[dayI][i].Type = LessonType.Holiday;
                    }
                }
            }
        } catch (Exception e) {
            throw new TimeTableLoadException(e);
        }
    }
}
