package de.zenonet.stundenplan;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Week;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.LessonType;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;

public class TimeTableViewUtils {
    static final int lessonMargin = 3;
    public static final int LessonIdOffset = 666;
    public static final int RowCount = 8;
    private static final SimpleDateFormat format = new SimpleDateFormat("dd.MM.", Locale.GERMANY);

    public static void createTableLayout(Context context, ViewGroup table, @Nullable LessonClicked lessonClickedHandler) {
        final int width = table.getMeasuredWidth();
        final int widthPerRow = width / 5;
        final int lessonTextPaddingH = 30;
        final int lessonTextPaddingV = 15;


        final View.OnClickListener onClickListener = lessonClickedHandler == null ? null : (view -> {
            // id = 666 + dayI*9 + periodI
            int id = view.getId() - TimeTableViewUtils.LessonIdOffset;
            int day = id / TimeTableViewUtils.RowCount;
            int period = id - TimeTableViewUtils.RowCount * day;

            // period > 7 is some weird stuff we don't want
            if (period > 7) return;

            lessonClickedHandler.onLessonClicked(day, period, view);
        });


        table.setForegroundGravity(Gravity.FILL);

        // Generate header row
        TableRow headerRow = new TableRow(context);
        for (int i = 0; i < 5; i++) {
            TextView textView = new TextView(context);
            textView.setId(444 + i);
            textView.setTextSize(11);
            textView.setWidth(widthPerRow);
            textView.setGravity(View.TEXT_ALIGNMENT_GRAVITY);
            textView.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH, lessonTextPaddingV);
            headerRow.addView(textView);
        }
        TableRow.LayoutParams params = new TableRow.LayoutParams(widthPerRow, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(lessonMargin, lessonMargin, lessonMargin, lessonMargin);
        headerRow.setLayoutParams(params);

        table.addView(headerRow);

        for (int periodI = 0; periodI < RowCount; periodI++) {
            TableRow row = new TableRow(context);

            for (int dayI = 0; dayI < 5; dayI++) {

                LinearLayout lessonLayout = new LinearLayout(context);
                lessonLayout.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH / 4, lessonTextPaddingV);
                lessonLayout.setOrientation(LinearLayout.VERTICAL);
                lessonLayout.setMinimumWidth(widthPerRow);
                row.addView(lessonLayout);


                // Subject view:
                TextView subjectView = new TextView(context);
                lessonLayout.addView(subjectView);
                subjectView.setTextColor(MaterialColors.getColor(subjectView, R.attr.lessonForeground));
                subjectView.setTextSize(16);
                //alignViewInRelativeLayout(subjectView, RelativeLayout.ALIGN_PARENT_LEFT);

                // Room view:
                TextView roomView = new TextView(context);
                lessonLayout.addView(roomView);
                roomView.setTextColor(MaterialColors.getColor(roomView, R.attr.lessonForeground));
                roomView.setTextSize(11);


                // Teacher view:
                TextView teacherView = new TextView(context);
                lessonLayout.addView(teacherView);
                teacherView.setTextColor(MaterialColors.getColor(teacherView, R.attr.lessonForeground));
                teacherView.setTextSize(11);

                // Teacher view:
                TextView textView = new TextView(context);
                lessonLayout.addView(textView);
                textView.setTextColor(MaterialColors.getColor(textView, R.attr.lessonForeground));
                textView.setTextSize(8);


                TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(widthPerRow,
                        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("useCursedLayout", false)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.setMargins(lessonMargin, lessonMargin, lessonMargin, lessonMargin);

                lessonLayout.setId(LessonIdOffset + dayI * RowCount + periodI);

                // Set click handler for the lesson view
                if(onClickListener != null) lessonLayout.setOnClickListener(onClickListener);

                lessonLayout.setLayoutParams(layoutParams);
            }

            table.addView(row);
        }
    }

    public static void updateTimeTableView(AppCompatActivity activity, @Nullable TimeTable timeTable, Formatter formatter) {

        if(timeTable == null) {
            timeTable = new TimeTable();
            timeTable.Lessons = new Lesson[5][0];
        }
        boolean hasData = false;
        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            hasData |= timeTable.Lessons[dayI].length != 0;

            for (int periodI = 0; periodI < RowCount; periodI++) {
                int viewId = LessonIdOffset + dayI * RowCount + periodI;
                ViewGroup lessonView = activity.findViewById(viewId);

                Lesson lesson = periodI < timeTable.Lessons[dayI].length ? timeTable.Lessons[dayI][periodI] : null;

                lessonView.setVisibility(lesson == null ? View.INVISIBLE : View.VISIBLE);
                if (lesson == null) continue;

                boolean cssl = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("combineSameSubjectLessons", true);
                Lesson lessonAfter = periodI + 1 < timeTable.Lessons[dayI].length ? timeTable.Lessons[dayI][periodI + 1] : null;
                Lesson lessonBefore = periodI - 1 >= 0 ? timeTable.Lessons[dayI][periodI - 1] : null;
                int paddingTop = cssl && lessonBefore != null && lessonBefore.SubjectShortName.equals(lesson.SubjectShortName) ? 0 : lessonMargin;
                int paddingBottom = cssl && lessonAfter != null && lessonAfter.SubjectShortName.equals(lesson.SubjectShortName) ? 0 : lessonMargin;

                TableRow.LayoutParams params = (TableRow.LayoutParams) lessonView.getLayoutParams();
                params.setMargins(lessonMargin, paddingTop, lessonMargin, paddingBottom);
                lessonView.setLayoutParams(params);

                TextView subjectView = (TextView) lessonView.getChildAt(0);
                TextView roomView = (TextView) (lessonView.getChildAt(1));
                TextView teacherView = (TextView) (lessonView.getChildAt(2));
                TextView textView = (TextView) (lessonView.getChildAt(3));

                subjectView.setText(lesson.SubjectShortName);
                roomView.setText(formatter.formatRoomName(lesson.Room));
                teacherView.setText(formatter.formatTeacherName(lesson.Teacher));
                String text = lesson.Text != null ? lesson.Text : "";
                if (lesson.HasHomeworkAttached) {
                    if (text != "") text += "\n";
                    text += "Hausaufgaben";
                }
                textView.setText(text);
                textView.setVisibility(lesson.Text != null || lesson.HasHomeworkAttached ? View.VISIBLE : View.GONE);

                /*if (lesson.Type == LessonType.Assignment)
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.assignment_substituted_lesson));
                else */

                if(lesson.Text != null && (lesson.Text.toLowerCase().contains("klausur") || lesson.Text.toLowerCase().contains("klassenarbeit")))
                    lessonView.setBackgroundColor(activity.getColor(de.zenonet.stundenplan.common.R.color.exam));
                else if (!lesson.isTakingPlace())
                    lessonView.setBackgroundColor(activity.getColor(de.zenonet.stundenplan.common.R.color.cancelled_lesson));
                else if (lesson.Type == LessonType.Substitution)
                    lessonView.setBackgroundColor(activity.getColor(de.zenonet.stundenplan.common.R.color.substituted_lesson));
                else if (lesson.Type == LessonType.RoomSubstitution)
                    lessonView.setBackgroundColor(activity.getColor(de.zenonet.stundenplan.common.R.color.room_substituted_lesson));
                else
                    lessonView.setBackgroundColor(activity.getColor(de.zenonet.stundenplan.common.R.color.regular_lesson));
            }
        }
    }

    public static void updateDayDisplayForWeek(AppCompatActivity activity, Week week) {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);

        Calendar cal = week.getMonday();

        boolean isCurrentWeek = Timing.getRelevantWeekOfYear().equals(week);

        // Update all the textViews:
        for (int i = 444; i < 444 + 5; i++) {
            TextView view = activity.findViewById(i);
            view.setText(format.format(cal.getTime()));
            if (isCurrentWeek  && cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                view.setTextColor(MaterialColors.getColor(view, R.attr.lessonForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.lessonBackground));
            } else {
                view.setTextColor(MaterialColors.getColor(view, R.attr.normalForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.normalBackground));
            }

            cal.add(Calendar.DAY_OF_WEEK, 1);
        }
    }
}
