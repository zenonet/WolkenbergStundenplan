package de.zenonet.stundenplan.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import de.zenonet.stundenplan.LessonType;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.TimeTable;
import de.zenonet.stundenplan.TimeTableManager;
import de.zenonet.stundenplan.UserLoadException;

public class TimeTableViewActivity extends AppCompatActivity {

    TimeTableManager manager;
    TableLayout table;
    TextView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setContentView(R.layout.activity_time_table_view);
        super.onCreate(savedInstanceState);

        // Check if the application is set up
        if (!getSharedPreferences().contains("refreshToken") && !getIntent().hasExtra("code")) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }


        manager = new TimeTableManager();
        try {
            manager.init(this);
        } catch (UserLoadException e) {
            // TODO: Show a message saying that the user id couldn't be loaded
        }

        // OAuth code login
        {
            // Login with oauth auth code if this activity was started by the login activity with a code
            Intent intent = getIntent();
            if (intent.hasExtra("code")) {

                // TODO
                manager.apiClient.redeemOAuthCodeAsync(intent.getStringExtra("code"), () -> {
                    try {
                        manager.login();
                    } catch (UserLoadException e) {
                    }
                    loadTimeTableAsync();
                });
            } else {
                new Thread(() -> {
                    try {
                        manager.login();
                        loadTimeTableAsync();
                    } catch (UserLoadException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        }

        table = findViewById(R.id.tableLayout);
        stateView = findViewById(R.id.stateView);
        createTableLayout();
    }

    private void loadTimeTableAsync() {
        manager.getTimeTableAsyncWithAdjustments(
                (timeTable) ->
                        runOnUiThread(() ->
                                updateTimeTableView(timeTable)
                        )
        );
    }

    private void updateTimeTableView(TimeTable timeTable) {

        stateView.setText(timeTable.isFromCache ? (timeTable.isCacheStateConfirmed ? "From cache (confirmed)" : "From cache") : "From API");

        // Return if it's a confirmed timetable because cache is always there before it's being confirmed

        if(timeTable.isCacheStateConfirmed) return;

        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            for (int periodI = 0; periodI < timeTable.Lessons[dayI].length; periodI++) {
                int viewId = 666 + dayI * 9 + periodI;
                ViewGroup lessonView = findViewById(viewId);

                TextView subjectView = (TextView) lessonView.getChildAt(0);
                TextView roomView = (TextView) (lessonView.getChildAt(1));
                TextView teacherView = (TextView) (lessonView.getChildAt(2));

                subjectView.setText(timeTable.Lessons[dayI][periodI].SubjectShortName);
                roomView.setText(timeTable.Lessons[dayI][periodI].Room);
                teacherView.setText(formatTeacherName(timeTable.Lessons[dayI][periodI].Teacher));

                // TODO: Select better colors for this
                if (!timeTable.Lessons[dayI][periodI].isTakingPlace())
                    lessonView.setBackgroundColor(getColor(R.color.cancelled_lesson));
                else if (timeTable.Lessons[dayI][periodI].Type == LessonType.Substitution)
                    lessonView.setBackgroundColor(getColor(R.color.substituted_lesson));
                else if (timeTable.Lessons[dayI][periodI].Type == LessonType.RoomSubstitution)
                    lessonView.setBackgroundColor(getColor(R.color.room_substituted_lesson));
                else
                    lessonView.setBackgroundColor(getColor(R.color.regular_lesson));
            }
        }
    }

    private void createTableLayout() {
        final int width = table.getMeasuredWidth();
        final int widthPerRow = width / 5;
        final int lessonMargin = 3;

        table.setForegroundGravity(Gravity.FILL);

        for (int periodI = 0; periodI < 10; periodI++) {
            TableRow row = new TableRow(this);

            for (int dayI = 0; dayI < 5; dayI++) {

                LinearLayout lessonLayout = new LinearLayout(this);
                final int lessonTextPaddingH = 30;
                final int lessonTextPaddingV = 15;
                lessonLayout.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH, lessonTextPaddingV);
                lessonLayout.setOrientation(LinearLayout.VERTICAL);
                lessonLayout.setMinimumWidth(widthPerRow);
                row.addView(lessonLayout);


                // Subject view:
                TextView subjectView = new TextView(this);
                lessonLayout.addView(subjectView);
                subjectView.setTextColor(Color.BLACK);
                subjectView.setTextSize(16);
                //alignViewInRelativeLayout(subjectView, RelativeLayout.ALIGN_PARENT_LEFT);

                // Room view:
                TextView roomView = new TextView(this);
                lessonLayout.addView(roomView);
                roomView.setTextColor(Color.BLACK);
                roomView.setTextSize(11);


                // Teacher view:
                TextView teacherView = new TextView(this);
                lessonLayout.addView(teacherView);
                teacherView.setTextColor(Color.BLACK);
                teacherView.setTextSize(11);


                TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(widthPerRow,
                        getSharedPreferences().getBoolean("useCursedLayout", false)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.leftMargin = lessonMargin;
                layoutParams.rightMargin = lessonMargin;
                layoutParams.topMargin = lessonMargin;
                layoutParams.bottomMargin = lessonMargin;

                lessonLayout.setId(666 + dayI * 9 + periodI);
                lessonLayout.setLayoutParams(layoutParams);
            }

            table.addView(row);
        }

/*        for (int dayI = 0; dayI < 5; dayI++) {
            //TableRow row = new TableRow(this);
            //row.setLayoutParams(rowParams);
            //row.setBackgroundColor(dayI % 2 == 0 ? Color.RED : Color.BLUE);
            //row.setId(ViewCompat.generateViewId());
            //row.setOrientation(LinearLayout.VERTICAL);
            //row.setMinimumWidth(widthPerRow);

            //LinearLayout innerLayout = new LinearLayout(this);
            //innerLayout.setOrientation(LinearLayout.VERTICAL);
            for (int periodI = 0; periodI < 9; periodI++) {
                TextView lessonView = new TextView(this);
                final int lessonTextPaddingH = 30;
                final int lessonTextPaddingV = 15;
                lessonView.setMinWidth(widthPerRow);
                lessonView.setMinWidth(50);
                lessonView.setGravity(Gravity.FILL_HORIZONTAL);
                lessonView.setForegroundGravity(Gravity.FILL);
                lessonView.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH, lessonTextPaddingV);
                lessonView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                lessonView.setId(666 + dayI * 9 + periodI);
                lessonView.setBackground(getDrawable(R.drawable.border));

                lessonView.setTextSize(22);

                GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams(GridLayout.spec(periodI), GridLayout.spec(dayI));

                layoutParams.width = widthPerRow;
                layoutParams.setGravity(Gravity.FILL);
                lessonView.setLayoutParams(layoutParams);

                //innerLayout.addView(lessonView);
                table.addView(lessonView);
            }

            //row.addView(innerLayout);
            //table.addView(row);
        }*/
    }

    String formatTeacherName(String teacherName) {
        if (!getSharedPreferences().getBoolean("showTeachersInitials", false)) {
            for (int i = 0; i < teacherName.length(); i++) {
                if (teacherName.charAt(i) == '.')
                    return teacherName.substring(i + 2);
            }
        }
        return teacherName;
    }

    private SharedPreferences getSharedPreferences() {
        return getSharedPreferences("de.zenonet.stundenplan", MODE_PRIVATE);
    }
}