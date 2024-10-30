package de.zenonet.stundenplan.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
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
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;


import de.zenonet.stundenplan.common.HomeworkManager;
import de.zenonet.stundenplan.glance.TimetableWidgetKt;
import de.zenonet.stundenplan.homework.HomeworkEditorActivity;
import de.zenonet.stundenplan.nonCrucialUi.NonCrucialUiKt;
import de.zenonet.stundenplan.nonCrucialUi.NonCrucialViewModel;
import de.zenonet.stundenplan.OnboardingActivity;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.SettingsActivity;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.ResultType;
import de.zenonet.stundenplan.common.StatisticsManager;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback;
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
    ImageButton previousWeekButton;
    ImageButton nextWeekButton;
    ImageButton currentWeekButton;

    int selectedWeek = Timing.getRelevantWeekOfYear();
    private TimeTable currentTimeTable;

    private boolean isPreview;
    private boolean isInitialLoad = true;
    private Formatter formatter;
    private final int RowCount = 8;
    private final int LessonIdOffset = 666;
    private final View.OnClickListener onClickListener = view -> {
        // id = 666 + dayI*9 + periodI
        int id = view.getId() - LessonIdOffset;
        int day = id / RowCount;
        int period = id - RowCount * day;

        // period > 7 is some weird stuff we don't want
        if (period > 7) return;

        onLessonClicked(day, period, view);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_time_table_view);
        super.onCreate(savedInstanceState);

        initializeTimeTableManagement();

        isPreview = getSharedPreferences().getBoolean("showPreview", false);
        if (!isPreview && !getSharedPreferences().getBoolean("onboardingCompleted", false)) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        registerIntentLauncher();
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

        findViewById(R.id.settingsButton).setOnClickListener((sender) -> settingsIntentLauncher.launch(new Intent(this, SettingsActivity.class)));

        previousWeekButton = findViewById(R.id.previousWeekButton);
        nextWeekButton = findViewById(R.id.nextWeekButton);
        currentWeekButton = findViewById(R.id.currentWeekButton);

        if (isPreview) {
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

        currentWeekButton.setOnClickListener((sender) -> {
            selectedWeek = Timing.getRelevantWeekOfYear();
            previousWeekButton.setEnabled(selectedWeek != 0);
            nextWeekButton.setEnabled(selectedWeek != 52);
            loadTimeTableAsync();
        });

        if (isPreview)
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

    private ActivityResultLauncher<Intent> intentLauncher;
    private ActivityResultLauncher<Intent> settingsIntentLauncher;
    private ActivityResultLauncher<Intent> homeworkEditorLauncher;

    private void registerIntentLauncher() {
        intentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            initializeTimeTableManagement();
                            String code = data.getStringExtra("code");
                            manager.apiClient.redeemOAuthCodeAsync(code, new AuthCodeRedeemedCallback() {
                                @Override
                                public void authCodeRedeemed() {
                                    try {
                                        manager.login();
                                        manager.getUser();
                                    } catch (UserLoadException e) {
                                    }
                                    loadTimeTableAsync();
                                }

                                @Override
                                public void errorOccurred(String message) {
                                    // TODO
                                }
                            });
                        }
                    }
                }
        );

        settingsIntentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (currentTimeTable != null) updateTimeTableView(currentTimeTable);
                }
        );

        homeworkEditorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> updateHomeworkAnnotations()
        );
    }

    private void updateHomeworkAnnotations() {
        HomeworkManager.INSTANCE.populateTimeTable(Calendar.getInstance().get(Calendar.YEAR), selectedWeek, currentTimeTable);
        updateTimeTableView(currentTimeTable);
    }

    private void startLoginProcess() {
        if (intentLauncher == null)
            throw new IllegalStateException("TimeTableViewActivity.startLoginProcess() before TimeTableViewActivity.registerIntentLauncher() was called.");
        intentLauncher.launch(new Intent(this, LoginActivity.class));

    }

    private AtomicReference<TimeTable> loadingTimeTableReference;

    private boolean timeTableLoaded = false;
    private void loadTimeTableAsync() {
        int[] timeTableVersionsReceived = new int[1];
        loadingTimeTableReference = manager.getTimeTableAsyncWithAdjustments(selectedWeek,
                (timeTable) -> {
                    timeTableVersionsReceived[0]++;
                    runOnUiThread(() -> {

                                if (timeTable == null) return;

                                if (!timeTableLoaded)
                                    if (timeTable.source == TimeTableSource.Cache && !timeTable.isCacheStateConfirmed)
                                        Log.i(LogTags.Timing, String.format("Time from app start to cached timetable received: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    else if (timeTable.isCacheStateConfirmed) {
                                        Log.i(LogTags.Timing, String.format("Time from app start to cached timetable confirmed: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    } else {
                                        Log.i(LogTags.Timing, String.format("Time from app start to fetched timetable received: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    }

                                if (timeTable.source != TimeTableSource.Cache)
                                    TimetableWidgetKt.updateWidgets(this);

                                timeTableLoaded = true;
                                currentTimeTable = timeTable;
                                updateTimeTableView(timeTable);
                            }
                    );


                    // add annotations for lessons with homework attached
                    int timeTableIndex = timeTableVersionsReceived[0];
                    HomeworkManager.INSTANCE.populateTimeTable(Calendar.getInstance().get(Calendar.YEAR), selectedWeek, timeTable);
                    if (timeTableIndex == timeTableVersionsReceived[0]) {
                        // update view to show homework annotations
                        runOnUiThread(() -> {
                            Log.i(LogTags.Timing, String.format("Time from app start to homework annotations applied to timetable: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                            updateTimeTableView(timeTable);
                        });
                    }

                },
                error -> {
                    if (error == ResultType.NoLoginSaved || error == ResultType.TokenExpired) {
                        runOnUiThread(() -> {
                            Toast.makeText(TimeTableViewActivity.this, "Dein Login ist abgelaufen. Du musst dich erneut anmelden.", Toast.LENGTH_SHORT).show();
                            startLoginProcess();
                        });
                    }
                }
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

        if (popup != null) popup.dismiss();

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

        boolean hasData = false;
        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            hasData |= timeTable.Lessons[dayI].length != 0;

            for (int periodI = 0; periodI < RowCount; periodI++) {
                int viewId = LessonIdOffset + dayI * RowCount + periodI;
                ViewGroup lessonView = findViewById(viewId);

                Lesson lesson = periodI < timeTable.Lessons[dayI].length ? timeTable.Lessons[dayI][periodI] : null;

                lessonView.setVisibility(lesson == null ? View.INVISIBLE : View.VISIBLE);
                if (lesson == null) continue;

                boolean cssl = getSharedPreferences().getBoolean("combineSameSubjectLessons", false);
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
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.exam));
                else if (!lesson.isTakingPlace())
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.cancelled_lesson));
                else if (lesson.Type == LessonType.Substitution)
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.substituted_lesson));
                else if (lesson.Type == LessonType.RoomSubstitution)
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.room_substituted_lesson));
                else
                    lessonView.setBackgroundColor(getColor(de.zenonet.stundenplan.common.R.color.regular_lesson));
            }
        }

        /*
        // Show notice
        if(!hasData ){
            if(popupWindow == null) {
                ViewGroup popUpView = new LinearLayout(this);
                TextView textView = new TextView(popUpView);
                textView.setText("Frei");
                popUpView.addView(textView);
                popupWindow = new PopupWindow(popUpView, 400, 400, false);
            }
            popupWindow.showAtLocation(findViewById(R.id.mainViewGroup), Gravity.CENTER, 50, 50);
        }*/

        if (isInitialLoad) {
            Log.i(LogTags.Timing, String.format("Time from application start to cached timetable displayed: %d ms - DISPLAYED", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));
            isInitialLoad = false;
        }
        updateDayDisplayForWeek(selectedWeek);

        if (!nonCrucialUiLoaded && !isLoadingNonCrucialUi) {
            isLoadingNonCrucialUi = true;
            // Ensure non-crucial-ui is not loaded synchronously here
            new Thread(() -> runOnUiThread(this::loadNonCrucialUi)).start();
        }

        StatisticsManager.reportTimetableTime(StundenplanApplication.getMillisSinceAppStart());
    }

    @Override
    protected void onResume() {
        super.onResume();
/*
        // Update so that formatting changes from the settings page are reflected
        if(currentTimeTable != null)
            updateTimeTableView(currentTimeTable);*/
    }

    private final SimpleDateFormat format = new SimpleDateFormat("dd.MM.", Locale.GERMANY);

    private void updateDayDisplayForWeek(int week) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = Timing.getCurrentDayOfWeek();
        int year = cal.get(Calendar.YEAR);
        cal.clear(); // Reset the calendar
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.YEAR, year); // Recover the year
        cal.set(Calendar.WEEK_OF_YEAR, week); // Set the desired week of year
        // Now, the calendar points at the first day of the desired week

        // Update all the textViews:
        for (int i = 444; i < 444 + 5; i++) {
            TextView view = findViewById(i);
            view.setText(format.format(cal.getTime()));
            if ((cal.get(Calendar.DAY_OF_WEEK) - 2) % 7 == dayOfWeek) {
                view.setTextColor(MaterialColors.getColor(view, R.attr.lessonForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.lessonBackground));
            } else {
                view.setTextColor(MaterialColors.getColor(view, R.attr.normalForeground));
                view.setBackgroundColor(MaterialColors.getColor(view, R.attr.normalBackground));
            }

            cal.add(Calendar.DAY_OF_WEEK, 1);
        }
    }

    PopupMenu popup;

    private void onLessonClicked(int dayOfWeek, int period, View lessonView) {
        Log.d(LogTags.Debug, String.format("Tapped on day %d at period %d", dayOfWeek, period));

        MenuListener menuListener = new MenuListener();
        menuListener.dayOfWeek = dayOfWeek;
        menuListener.period = period;

        // Just some unnecessary checks because I am afraid of this crashing somehow
        if (currentTimeTable.Lessons.length < dayOfWeek - 1 && currentTimeTable.Lessons[dayOfWeek] == null)
            return;
        if (currentTimeTable.Lessons[dayOfWeek].length < period - 1 && currentTimeTable.Lessons[dayOfWeek][period] == null)
            return;

        Lesson lesson = currentTimeTable.Lessons[dayOfWeek][period];

        if (popup != null) popup.dismiss();

        popup = new PopupMenu(this, lessonView);
        popup.setOnMenuItemClickListener(menuListener);
        popup.getMenuInflater().inflate(R.menu.lessoncontextmenu, popup.getMenu());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
        }
        popup.getMenu().findItem(R.id.menuTimeView).setTitle(String.format(Locale.GERMAN, "%d.  Std:  %s - %s", period+1, lesson.StartTime, lesson.EndTime));
        popup.getMenu().findItem(R.id.clearHomework).setVisible(lesson.HasHomeworkAttached);
        popup.show();
    }

    private class MenuListener implements PopupMenu.OnMenuItemClickListener {

        public int dayOfWeek;
        public int period;

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final int id = item.getItemId();
            if (id == R.id.menuInsertHomework) {
                Intent intent = new Intent(TimeTableViewActivity.this, HomeworkEditorActivity.class);
                intent.putExtra("week", selectedWeek);
                intent.putExtra("dayOfWeek", dayOfWeek);
                intent.putExtra("subjectAbbreviationHash", currentTimeTable.Lessons[dayOfWeek][period].SubjectShortName.hashCode());

                homeworkEditorLauncher.launch(intent);
                return true;
            }
            if (id == R.id.clearHomework) {
                HomeworkManager.INSTANCE.deleteNoteFor(Calendar.getInstance().get(Calendar.YEAR), selectedWeek, dayOfWeek, currentTimeTable.Lessons[dayOfWeek][period].SubjectShortName.hashCode());
                runOnUiThread(TimeTableViewActivity.this::updateHomeworkAnnotations);
                return true;
            }
            return true;
        }
    }


    final int lessonMargin = 3;

    private void createTableLayout() {
        final int width = table.getMeasuredWidth();
        final int widthPerRow = width / 5;
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

        for (int periodI = 0; periodI < RowCount; periodI++) {
            TableRow row = new TableRow(this);

            for (int dayI = 0; dayI < 5; dayI++) {

                LinearLayout lessonLayout = new LinearLayout(this);
                lessonLayout.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH / 4, lessonTextPaddingV);
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

                lessonLayout.setId(LessonIdOffset + dayI * RowCount + periodI);
                lessonLayout.setOnClickListener(onClickListener);
                lessonLayout.setLayoutParams(layoutParams);
            }

            table.addView(row);
        }

        Log.i(LogTags.Timing, String.format("Time from application start to timetable view generated: %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        // If the cached version is available already, update the view directly
        if (loadingTimeTableReference != null && loadingTimeTableReference.get() != null) {
            Log.i(LogTags.UI, "Updating view directly after creating it.");
            updateTimeTableView(loadingTimeTableReference.get());
        }
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
        //return getSharedPreferences("de.zenonet.stundenplan", MODE_PRIVATE);
    }

    private boolean nonCrucialUiLoaded = false;

    private boolean isLoadingNonCrucialUi = false;

    private void loadNonCrucialUi() {

        Log.i(LogTags.Timing, String.format("Time from application start to started loading non-crucial-ui : %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        final int composeViewId = 987;
        ComposeView cv = findViewById(composeViewId);
        if (cv == null) {
            LinearLayout l = findViewById(R.id.mainViewGroup);
            cv = new ComposeView(this);
            cv.setId(composeViewId);
            l.addView(cv);
        }

        NonCrucialViewModel vm;
        if (isPreview) {
            try {
                vm = new NonCrucialViewModel(null, null, Utils.getPreviewTimeTable(this));
            } catch (IOException e) {
                return;
            }
        } else {
            vm = new NonCrucialViewModel(manager, null, null);
        }

        NonCrucialUiKt.applyUiToComposeView(cv, vm);
        nonCrucialUiLoaded = true;

        Log.i(LogTags.Timing, String.format("Time from application start to non-crucial-ui loaded : %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));


    }

}