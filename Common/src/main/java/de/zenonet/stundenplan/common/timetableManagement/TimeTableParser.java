package de.zenonet.stundenplan.common.timetableManagement;

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

import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.Week;

/**
 * TimeTableParser is a timetable provider that uses data sources with raw json data.
 * It gets its data either from the apiClient or the rawCacheClient
 */
public class TimeTableParser {
    public NameLookup lookup;

    public TimeTableParser(NameLookup lookup) {
        this.lookup = lookup;
    }

    public TimeTable parseWeek(String json, String substitutionJson, Week week) throws TimeTableLoadException {

        Calendar time = week.getMonday();
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

            applySubstitutions(timeTable, substitutionJson, week);
            return timeTable;
        }
        catch (TimeTableLoadException e){
            throw e;
        }
        catch (Exception e) {
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

    private void applySubstitutions(TimeTable timeTable, String json, Week week) throws TimeTableLoadException {
        try {
            JSONArray substitutions = new JSONObject(json).getJSONObject("substitutions").getJSONArray(String.format(Locale.GERMANY, "%d-%02d", week.Year, week.WeekOfYear));
            for (int dayI = 0; dayI < 5; dayI++) {

                if (substitutions.isNull(dayI)) continue;

                JSONObject substitutionsThatDay = substitutions.getJSONObject(dayI);

                if (substitutionsThatDay.has("substitutions")) {
                    JSONArray substitutionsArray = substitutionsThatDay.getJSONArray("substitutions");
                    for (int i = 0; i < substitutionsArray.length(); i++) {
                        JSONObject substitution = substitutionsArray.getJSONObject(i);
                        int period = substitution.getInt("PERIOD") - 1; // Two-indexing again (except not anymore, yay)

                        // Yes, apparently there are sometimes 0th lessons which can't even be displayed by the official timetable app
                        if(period < 0){
                            Log.w(LogTags.Parser, String.format("There is a substitution in period %d (zero-indexed). Ignoring...", period));
                            continue;
                        }

                        // Determine if the period array needs to be resized (Hopefully this will never have to happen)
                        if (period >= timeTable.Lessons[dayI].length) {
                            // Resize the period array
                            timeTable.Lessons[dayI] = Arrays.copyOf(timeTable.Lessons[dayI], period + 1);
                        }

                        if(timeTable.Lessons[dayI][period] == null){
                            Lesson l = new Lesson();
                            l.Room = "";
                            l.SubjectShortName = "";
                            l.Subject = "";
                            l.Teacher = "";
                            timeTable.Lessons[dayI][period] = l;
                        }

                        if (substitution.has("TEXT")) {
                            String text = substitution.getString("TEXT");
                            if (!text.isEmpty())
                                timeTable.Lessons[dayI][period].Text = text;
                        }

                        String type = substitution.getString("TYPE");

                        if (timeTable.Lessons[dayI][period] == null) timeTable.Lessons[dayI][period] = new Lesson();

                        // so, we can't apply e.g. new_room_id from an elimination because sometimes a lesson is substituted AND cancelled and the cancellation has the normal room_id
                        // of the lesson as its override so the elimination resets the room override of the substitution (god, I wish the data we're parsing was somewhat logical)
                        if(!type.equals("ASSIGNMENT") && !type.equals("ELIMINATION"))
                            applyDataModifications(substitution, timeTable.Lessons[dayI][period]);

                        switch (type) {
                            case "ASSIGNMENT":
                                timeTable.Lessons[dayI][period].Text = "Aufgaben";
                                timeTable.Lessons[dayI][period].Type = LessonType.Assignment;
                                continue;
                            case "ELIMINATION":
                                if (timeTable.Lessons[dayI][period].Type != LessonType.ExtraLesson && timeTable.Lessons[dayI][period].Type != LessonType.Substitution) {
                                    timeTable.Lessons[dayI][period].Type = LessonType.Cancelled;
                                }
                                continue;
                            case "SUPERVISION":
                                if (timeTable.Lessons[dayI][period].Type == LessonType.Cancelled) {
                                    timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                                }
                                continue;
                            case "SUBSTITUTION":
                            case "SWAP":
                            case "ROOM_SUBSTITUTION":
                                // Update the timetable according to the substitutions
                                if (type.equals("ROOM_SUBSTITUTION"))
                                    timeTable.Lessons[dayI][period].Type = LessonType.RoomSubstitution;
                                else
                                    timeTable.Lessons[dayI][period].Type = LessonType.Substitution;

                                continue;
                            case "EXTRA_LESSON":
                                if (timeTable.Lessons[dayI][period].Type == LessonType.Cancelled) {
                                    // If the extra lesson is in the time frame of a cancelled lesson, then that's called a substitution
                                    timeTable.Lessons[dayI][period].Type = LessonType.Substitution;
                                } else {
                                    timeTable.Lessons[dayI][period].Type = LessonType.ExtraLesson;
                                }
                                continue;
                            case "CLASS_SUBSTITUTION":
                                // This is only interesting from a teachers perspective and I doubt a teacher will ever use this app.
                                continue;
                            case "REDUNDANCY":
                                // Pretty funny that giving information about redundancies is actually completely redundant.
                                continue;
                        }

                        Log.w("timetableloading", String.format("Unknown substitution type '%s'", type));
                    }
                }
                if (substitutionsThatDay.has("absences")) {
                    JSONArray absencesArray = substitutionsThatDay.getJSONArray("absences");
                    for (int i = 0; i < absencesArray.length(); i++) {
                        int periodFrom = Math.max(absencesArray.getJSONObject(i).getInt("PERIOD_FROM") - 1, 0);
                        int periodTo = Math.max(absencesArray.getJSONObject(i).getInt("PERIOD_TO") - 1, 0);

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
