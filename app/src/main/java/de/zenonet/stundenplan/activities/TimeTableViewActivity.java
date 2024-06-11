package de.zenonet.stundenplan.activities;

import android.annotation.SuppressLint;
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

import androidx.compose.ui.platform.ComposeView;
import androidx.preference.PreferenceManager;

import android.os.Bundle;

import com.google.android.material.color.MaterialColors;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicReference;


import de.zenonet.stundenplan.NonCrucialComposeUiKt;
import de.zenonet.stundenplan.OnboardingActivity;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.SettingsActivity;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.LessonType;
import de.zenonet.stundenplan.common.StundenplanApplication;
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

    int selectedWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
    private TimeTable currentTimeTable;
    ImageButton previousWeekButton;
    ImageButton nextWeekButton;

    private boolean isPreview;
    private Formatter formatter;
    private final View.OnClickListener onClickListener = view -> {
        // id = 666 + dayI*9 + periodI
        int id = view.getId()-666;
        int day = id / 9;
        int period = id - 9*day;

        // period > 7 is some weird stuff we don't want
        if(period > 7) return;

        onLessonClicked(day, period);
        Log.d(Utils.LOG_TAG, String.format("Tapped on day %d at period %d", day, period));
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        activityCreatedInstant = StundenplanApplication.applicationEntrypointInstant;
        setContentView(R.layout.activity_time_table_view);
        super.onCreate(savedInstanceState);

        nonCrucialUiLoaded = savedInstanceState != null;

        initializeTimeTableManagement();

        isPreview = getSharedPreferences().getBoolean("showPreview", false);
        if (!isPreview && !getSharedPreferences().getBoolean("onboardingCompleted", false)) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Check if the application is set up
        if (!isPreview && !getSharedPreferences().contains("refreshToken")) {
            startLoginProcess();
            return;
        }

        if (!isPreview)
            loadTimeTableAsync();

        table = findViewById(R.id.tableLayout);
        stateView = findViewById(R.id.stateView);
        formatter = new Formatter(this);
        createTableLayout();

        findViewById(R.id.settingsButton).setOnClickListener((sender) -> startActivity(new Intent(this, SettingsActivity.class)));

        previousWeekButton = findViewById(R.id.previousWeekButton);
        nextWeekButton = findViewById(R.id.nextWeekButton);

        if(isPreview){
            previousWeekButton.setEnabled(false);
            nextWeekButton.setEnabled(false);
        }

        nextWeekButton.setOnClickListener((sender) -> {
            selectedWeek++;
            nextWeekButton.setEnabled(selectedWeek != 52);
            loadTimeTableAsync();
        });

        previousWeekButton.setOnClickListener((sender) -> {
            selectedWeek--;
            previousWeekButton.setEnabled(selectedWeek != 0);
            loadTimeTableAsync();
        });

        if(isPreview)
            loadPreviewTimeTable();
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

    private boolean timeTableLoaded = false;

    private void loadTimeTableAsync() {
        loadingTimeTableReference = manager.getTimeTableAsyncWithAdjustments(selectedWeek,
                (timeTable) ->
                        runOnUiThread(() -> {

                                    if (timeTable == null) {
                                        // Show some kind of message here
                                        return;
                                    }
                                    if (!timeTableLoaded)
                                        if (timeTable.source == TimeTableSource.Cache && !timeTable.isCacheStateConfirmed)
                                            Log.i(Utils.LOG_TAG, String.format("Time from activity start to cached timetable received: %d ms", ChronoUnit.MILLIS.between(activityCreatedInstant, Instant.now())));
                                        else if (timeTable.isCacheStateConfirmed) {
                                            Log.i(Utils.LOG_TAG, String.format("Time from activity start to cached timetable confirmed: %d ms", ChronoUnit.MILLIS.between(activityCreatedInstant, Instant.now())));
                                        } else {
                                            Log.i(Utils.LOG_TAG, String.format("Time from activity start to fetched timetable received: %d ms", ChronoUnit.MILLIS.between(activityCreatedInstant, Instant.now())));
                                        }
                                    timeTableLoaded = true;
                                    currentTimeTable = timeTable;
                                    updateTimeTableView(timeTable);
                                }
                        )
        );
    }

    private void loadPreviewTimeTable() {
        try {
            currentTimeTable = Utils.getPreviewTimeTable(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
            // TODO: Show error message here
        }
        updateTimeTableView(currentTimeTable);
    }

    private void updateTimeTableView(TimeTable timeTable) {

        String stateText;
        switch (timeTable.source) {
            case Api:
                stateText = "From API";
                break;
            case Cache:
                stateText = "From cache";
                break;
            case RawCache:
                stateText = "From raw cache";
                break;
            default:
                stateText = "From " + timeTable.source;
        }
        if (timeTable.isCacheStateConfirmed) {
            stateText += " (confirmed)";
        }
        stateView.setText(stateText);

        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            for (int periodI = 0; periodI < timeTable.Lessons[dayI].length; periodI++) {
                int viewId = 666 + dayI * 9 + periodI;
                ViewGroup lessonView = findViewById(viewId);

                Lesson lesson = timeTable.Lessons[dayI][periodI];

                if(lesson == null){
                    lessonView.setVisibility(View.INVISIBLE);
                    continue;
                }

                TextView subjectView = (TextView) lessonView.getChildAt(0);
                TextView roomView = (TextView) (lessonView.getChildAt(1));
                TextView teacherView = (TextView) (lessonView.getChildAt(2));
                TextView textView = (TextView) (lessonView.getChildAt(3));

                subjectView.setText(lesson.SubjectShortName);
                roomView.setText(formatter.formatRoomName(lesson.Room));
                teacherView.setText(formatter.formatTeacherName(lesson.Teacher));
                textView.setText(lesson.Text != null ? lesson.Text : "");
                textView.setVisibility(lesson.Text == null ? View.GONE : View.VISIBLE);

                if (!lesson.isTakingPlace())
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.cancelled_lesson));
                else if (lesson.Type == LessonType.Substitution)
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.substituted_lesson));
                else if (lesson.Type == LessonType.RoomSubstitution)
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.room_substituted_lesson));
                else
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.regular_lesson));
            }
        }
        if (timeTable.source == TimeTableSource.Cache && !timeTable.isCacheStateConfirmed)
            Log.i(Utils.LOG_TAG, String.format("Time from application start to cached timetable displayed: %d ms - DISPLAYED", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        updateDayDisplayForWeek(selectedWeek);

        if (!nonCrucialUiLoaded)
            loadNonCrucialUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update so that formatting changes from the settings page are reflected
        if(currentTimeTable != null)
            updateTimeTableView(currentTimeTable);
    }

    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat format = new SimpleDateFormat("dd.MM.");

    private void updateDayDisplayForWeek(int week) {
        Calendar cal = Calendar.getInstance();
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        int year = cal.get(Calendar.YEAR);
        cal.clear(); // Reset the calendar
        cal.set(Calendar.YEAR, year); // Recover the year
        cal.set(Calendar.WEEK_OF_YEAR, week); // Set the desired week of year
        // Now, the calendar points at the first day of the desired week

        // Update all the textViews:
        for (int i = 444; i < 444 + 5; i++) {
            TextView view = findViewById(i);
            view.setText(format.format(cal.getTime()));
            if (cal.get(Calendar.DAY_OF_YEAR) == dayOfYear) {
                view.setTextColor(MaterialColors.getColor(view, R.attr.lessonForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.lessonBackground));
            } else {
                view.setTextColor(MaterialColors.getColor(view, R.attr.normalForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.normalBackground));
            }

            cal.add(Calendar.DAY_OF_WEEK, 1);
        }
    }

    private void onLessonClicked(int dayOfWeek, int period){

    }


    private void createTableLayout() {
        final int width = table.getMeasuredWidth();
        final int widthPerRow = width / 5;
        final int lessonMargin = 3;
        final int lessonTextPaddingH = 30;
        final int lessonTextPaddingV = 15;

        table.setForegroundGravity(Gravity.FILL);

        // Generate header row
        TableRow headerRow = new TableRow(this);
        for (int i = 0; i < 5; i++) {
            TextView textView = new TextView(this);
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

        for (int periodI = 0; periodI < 10; periodI++) {
            TableRow row = new TableRow(this);

            for (int dayI = 0; dayI < 5; dayI++) {

                LinearLayout lessonLayout = new LinearLayout(this);
                lessonLayout.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH/4, lessonTextPaddingV);
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

                // Teacher view:
                TextView textView = new TextView(this);
                lessonLayout.addView(textView);
                textView.setTextColor(MaterialColors.getColor(textView, R.attr.lessonForeground));
                textView.setTextSize(8);


                TableRow.LayoutParams layoutParams = new TableRow.LayoutParams(widthPerRow,
                        getSharedPreferences().getBoolean("useCursedLayout", false)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : ViewGroup.LayoutParams.MATCH_PARENT);
                layoutParams.setMargins(lessonMargin, lessonMargin, lessonMargin, lessonMargin);

                lessonLayout.setId(666 + dayI * 9 + periodI);
                lessonLayout.setOnClickListener(onClickListener);
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
    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
        //return getSharedPreferences("de.zenonet.stundenplan", MODE_PRIVATE);
    }

    private void loadNonCrucialUi() {
        ComposeView composeView = findViewById(R.id.nonCrucialComposeContainer);
        NonCrucialComposeUiKt.applyUiToComposeView(composeView);
        /*
        getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, NonCrucialUiFragment.class, null).commit();
        nonCrucialUiLoaded = true;

        Log.i(Utils.LOG_TAG, String.format("Time from application start to non-crucial-ui loaded : %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));
    */
    }

}