package de.zenonet.stundenplan.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.preference.PreferenceManager;

import android.os.Bundle;

import com.google.android.material.color.MaterialColors;

import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicReference;

import de.zenonet.stundenplan.OnboardingActivity;
import de.zenonet.stundenplan.SettingsActivitiy;
import de.zenonet.stundenplan.common.timetableManagement.LessonType;
import de.zenonet.stundenplan.NonCrucialUiFragment;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.StundenplanApplication;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;
import de.zenonet.stundenplan.common.Utils;

public class TimeTableViewActivity extends AppCompatActivity {

    TimeTableManager manager;
    TableLayout table;
    TextView stateView;

    Instant activityCreatedInstant;

    boolean nonCrucialUiLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        activityCreatedInstant = StundenplanApplication.applicationEntrypointInstant;
        setContentView(R.layout.activity_time_table_view);
        super.onCreate(savedInstanceState);

        nonCrucialUiLoaded = savedInstanceState != null;

        initializeTimeTableManagement();

        if(!getSharedPreferences().contains("onboardingCompleted")) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Check if the application is set up
        if (!getSharedPreferences().contains("refreshToken")) {
            startLoginProcess();
            return;
        }

        loadTimeTableAsync();

        table = findViewById(R.id.tableLayout);
        stateView = findViewById(R.id.stateView);
        createTableLayout();

        findViewById(R.id.settingsButton).setOnClickListener((sender) -> startActivity(new Intent(this, SettingsActivitiy.class)));
    }

    private void initializeTimeTableManagement() {
        manager = new TimeTableManager();
        try {
            manager.init(this);
        } catch (UserLoadException e) {
            // TODO: Show a message saying that the user id couldn't be loaded
        }
    }

    private void startLoginProcess() {
        ActivityResultLauncher<Intent> intentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            initializeTimeTableManagement();
                            String code = data.getStringExtra("code");
                            manager.apiClient.redeemOAuthCodeAsync(code, () -> {
                                try {
                                    manager.getUser();
                                } catch (UserLoadException e) {
                                }
                                loadTimeTableAsync();
                            });
                        }
                    }
                }
        );
        intentLauncher.launch(new Intent(this, LoginActivity.class));

    }

    private AtomicReference<TimeTable> loadingTimeTableReference;

    private void loadTimeTableAsync() {
        loadingTimeTableReference = manager.getTimeTableAsyncWithAdjustments(
                (timeTable) ->
                        runOnUiThread(() -> {

                                    if (timeTable == null) {
                                        // Show some kind of message here
                                        return;
                                    }

                                    if (timeTable.isFromCache && !timeTable.isCacheStateConfirmed)
                                        Log.i(Utils.LOG_TAG, String.format("Time from activity start to cached timetable received: %d ms", Duration.between(activityCreatedInstant, Instant.now()).toMillis()));
                                    else if (timeTable.isCacheStateConfirmed) {
                                        Log.i(Utils.LOG_TAG, String.format("Time from activity start to cached timetable confirmed: %d ms", Duration.between(activityCreatedInstant, Instant.now()).toMillis()));
                                    } else {
                                        Log.i(Utils.LOG_TAG, String.format("Time from activity start to fetched timetable received: %d ms", Duration.between(activityCreatedInstant, Instant.now()).toMillis()));
                                    }


                                    updateTimeTableView(timeTable);
                                }
                        )
        );
    }

    private void updateTimeTableView(TimeTable timeTable) {

        stateView.setText(timeTable.isFromCache ? (timeTable.isCacheStateConfirmed ? "From cache (confirmed)" : "From cache") : "From API");

        // Return if it's a confirmed timetable because cache is always there before it's being confirmed

        if (timeTable.isCacheStateConfirmed) return;

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
        if (timeTable.isFromCache && !timeTable.isCacheStateConfirmed)
            Log.i(Utils.LOG_TAG, String.format("Time from application start to cached timetable displayed: %d ms - DISPLAYED", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        if (!nonCrucialUiLoaded)
            loadNonCrucialUi();


        if (timeTable.isFromCache && !timeTable.isCacheStateConfirmed)
            Log.i(Utils.LOG_TAG, String.format("Time from application start to timetable view is actually rendered: %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));
    }

    private void createTableLayout() {
        final int width = table.getMeasuredWidth();
        final int widthPerRow = width / 5;
        final int lessonMargin = 3;

        table.setForegroundGravity(Gravity.FILL);

        // Generate header row
        int dayOfWeek = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)-2)%7;
        TableRow headerRow = new TableRow(this);
        String[] weekdays = new String[]{"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag"};
        for (int i = 0; i < 5; i++) {
            TextView textView = new TextView(this);
            headerRow.addView(textView);
            if(i == dayOfWeek){
                textView.setTextColor(MaterialColors.getColor(textView, R.attr.lessonForeground));
                textView.setBackgroundColor(MaterialColors.getColor(textView, R.attr.lessonBackground));
            }else{
                textView.setTextColor(MaterialColors.getColor(textView, R.attr.normalForeground));
            }
            textView.setTextSize(11);
            textView.setWidth(widthPerRow);
            textView.setGravity(View.TEXT_ALIGNMENT_GRAVITY);
            textView.setText(weekdays[i]);
        }
        table.addView(headerRow);

        for (int periodI = 0; periodI < 10; periodI++) {
            TableRow row = new TableRow(this);

            for (int dayI = 0; dayI < 5; dayI++) {
                final int lessonTextPaddingH = 30;
                final int lessonTextPaddingV = 15;

                LinearLayout lessonLayout = new LinearLayout(this);
                lessonLayout.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH, lessonTextPaddingV);
                lessonLayout.setOrientation(LinearLayout.VERTICAL);
                lessonLayout.setMinimumWidth(widthPerRow);
                row.addView(lessonLayout);


                // Subject view:
                TextView subjectView = new TextView(this);
                lessonLayout.addView(subjectView);
                subjectView.setTextColor(MaterialColors.getColor(subjectView, R.attr.lessonForeground));
                subjectView.setTextSize(16);
                //alignViewInRelativeLayout(subjectView, RelativeLayout.ALIGN_PARENT_LEFT);

                // Room view:
                TextView roomView = new TextView(this);
                lessonLayout.addView(roomView);
                roomView.setTextColor(MaterialColors.getColor(roomView, R.attr.lessonForeground));
                roomView.setTextSize(11);


                // Teacher view:
                TextView teacherView = new TextView(this);
                lessonLayout.addView(teacherView);
                teacherView.setTextColor(MaterialColors.getColor(teacherView, R.attr.lessonForeground));
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

        Log.i(Utils.LOG_TAG, String.format("Time from application start to timetable view generated: %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        // If the cached version is available already, update the view directly
        if (loadingTimeTableReference != null && loadingTimeTableReference.get() != null) {
            Log.i(Utils.LOG_TAG, "Updating view directly after creating it.");
            updateTimeTableView(loadingTimeTableReference.get());
        }
    }

    String formatTeacherName(String teacherName) {
        if (!getSharedPreferences().getBoolean("showTeacherFirstNameInitial", false)) {
            for (int i = 0; i < teacherName.length(); i++) {
                if (teacherName.charAt(i) == '.')
                    return teacherName.substring(i + 2);
            }
        }
        return teacherName;
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
        //return getSharedPreferences("de.zenonet.stundenplan", MODE_PRIVATE);
    }

    private void loadNonCrucialUi() {
        getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, NonCrucialUiFragment.class, null).commit();
        nonCrucialUiLoaded = true;

        Log.i(Utils.LOG_TAG, String.format("Time from application start to non-crucial-ui loaded : %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));
    }
}