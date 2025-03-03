package de.zenonet.stundenplan.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import de.zenonet.stundenplan.OnboardingActivity;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.SettingsActivity;
import de.zenonet.stundenplan.TimeTableViewUtils;
import de.zenonet.stundenplan.common.Base64SerializationKt;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.HomeworkManager;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.ResultType;
import de.zenonet.stundenplan.common.StatisticsManager;
import de.zenonet.stundenplan.common.StundenplanApplication;
import de.zenonet.stundenplan.common.TimeTableSource;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.Week;
import de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;
import de.zenonet.stundenplan.homework.HomeworkEditorActivity;
import de.zenonet.stundenplan.nonCrucialUi.NonCrucialUiKt;
import de.zenonet.stundenplan.nonCrucialUi.NonCrucialViewModel;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

public class TimeTableViewActivity extends AppCompatActivity {

    TimeTableManager manager;
    TableLayout table;
    TextView stateView;

    ImageButton previousWeekButton;
    ImageButton nextWeekButton;

    Week selectedWeek = Timing.getRelevantWeekOfYear();
    private TimeTable currentTimeTable;

    private boolean isPreview;
    private boolean isInitialLoad = true;
    private Formatter formatter;

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

        table = findViewById(R.id.tableLayout);
        stateView = findViewById(R.id.stateView);
        formatter = new Formatter(this);

        if (!isPreview)
            loadTimeTableAsync();

        createTableLayout();

        //findViewById(R.id.menuButton).setOnClickListener((sender) -> settingsIntentLauncher.launch(new Intent(this, SettingsActivity.class)));

        previousWeekButton = findViewById(R.id.previousWeekButton);
        nextWeekButton = findViewById(R.id.nextWeekButton);
        ImageButton currentWeekButton = findViewById(R.id.currentWeekButton);
        ImageButton mailButton = findViewById(R.id.mailButton);

        if (isPreview) {
            previousWeekButton.setEnabled(false);
            previousWeekButton.setImageAlpha(0x6F);
            nextWeekButton.setEnabled(false);
            nextWeekButton.setImageAlpha(0x6F);
        }else{
            updateWeekNavButtonEnabledStates();
        }

        nextWeekButton.setOnClickListener((sender) -> {
            selectedWeek = selectedWeek.getSucceedingWeek();
            updateWeekNavButtonEnabledStates();
            loadTimeTableAsync();
        });

        previousWeekButton.setOnClickListener((sender) -> {
            selectedWeek = selectedWeek.getPreceedingWeek();
            updateWeekNavButtonEnabledStates();
            loadTimeTableAsync();
        });

        currentWeekButton.setOnClickListener((sender) -> {
            selectedWeek = Timing.getRelevantWeekOfYear();
            updateWeekNavButtonEnabledStates();
            loadTimeTableAsync();
        });

        mailButton.setOnClickListener((sender) -> {
            openOutlook();
        });

        findViewById(R.id.menuButton).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                popup.setForceShowIcon(true);
            }
            popup.setOnMenuItemClickListener(new TimeTableContextMenuListener());
            popup.getMenuInflater().inflate(R.menu.timetablecontextmenu, popup.getMenu());
            popup.show();
        });

        if (isPreview)
            loadPreviewTimeTable();

        updateWindowFlags();

        ConnectivityManager cm = (ConnectivityManager) this
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> checkForUpdatesAsync());
            }
        });

    }

    private class TimeTableContextMenuListener implements PopupMenu.OnMenuItemClickListener {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final int id = item.getItemId();
            if (id == R.id.shareTimeTable) {
                String shareUrl = Base64SerializationKt.getShareUrlForTimeTable(currentTimeTable);

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareUrl);

                Intent chooserIntent = Intent.createChooser(shareIntent, "Stundenplan teilen");
                startActivity(chooserIntent);
                return true;
            }
            if(id == R.id.shareApp){
                String shareUrl = "https://play.google.com/store/apps/details?id=de.zenonet.stundenplan";

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareUrl);

                Intent chooserIntent = Intent.createChooser(shareIntent, "App teilen");
                startActivity(chooserIntent);
                return true;
            }
            if(id == R.id.openSettings){
                settingsIntentLauncher.launch(new Intent(TimeTableViewActivity.this, SettingsActivity.class));
                return true;
            }
            if(id == R.id.openSearch){
                Intent intent = new Intent(TimeTableViewActivity.this, SearchActivity.class);
                startActivity(intent);
                return true;
            }
/*            if (id == R.id.exportTimeTable) {
                String json = new Gson().toJson(currentTimeTable);

                Intent.createChooser()
                HomeworkManager.INSTANCE.deleteNoteFor(selectedWeek, dayOfWeek, currentTimeTable.Lessons[dayOfWeek][period].SubjectShortName.hashCode());
                runOnUiThread(TimeTableViewActivity.this::onHomeworkDataChanged);
                return true;
            }*/
            return true;
        }
    }


    private void updateWeekNavButtonEnabledStates() {
        /*previousWeekButton.setEnabled(selectedWeek != 0);
        previousWeekButton.setImageAlpha(selectedWeek != 0 ? 0xFF : 0x6F);
        nextWeekButton.setEnabled(selectedWeek != 52);
        nextWeekButton.setImageAlpha(selectedWeek != 52 ? 0xFF : 0x6F);*/
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode){
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if(nextWeekButton.isEnabled()) nextWeekButton.callOnClick();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if(previousWeekButton.isEnabled()) previousWeekButton.callOnClick();
                return true;
            case KeyEvent.KEYCODE_K:
                if(event.isCtrlPressed()){
                    Intent intent = new Intent(this, SearchActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }


    private void initializeTimeTableManagement() {
        manager = new TimeTableManager();
        try {
            manager.init(this);
        } catch (UserLoadException e) {
            // TODO: Show a message saying that the user id couldn't be loaded
        }
    }
    private void updateWindowFlags(){
        boolean showWhenLocked = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showWhenLocked", true);
        getWindow().setFlags(showWhenLocked ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0, WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private void openOutlook(){
        Intent outlookIntent = getPackageManager().getLaunchIntentForPackage("com.microsoft.office.outlook");
        if(outlookIntent != null){
            startActivity(outlookIntent);
            return;
        }
        // open outlook in webview
        Intent intent = new Intent(this, OutlookWebView.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
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
                    updateWindowFlags();
                }
        );

        homeworkEditorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> onHomeworkDataChanged()
        );
    }

    private void updateHomeworkAnnotations() {
        HomeworkManager.INSTANCE.populateTimeTable(selectedWeek, currentTimeTable);
        updateTimeTableView(currentTimeTable);
    }

    private void startLoginProcess() {
        if (intentLauncher == null)
            throw new IllegalStateException("TimeTableViewActivity.startLoginProcess() before TimeTableViewActivity.registerIntentLauncher() was called.");
        intentLauncher.launch(new Intent(this, LoginActivity.class));

    }

    private AtomicReference<TimeTable> loadingTimeTableReference;

    private boolean timeTableLoaded = false;
    int[] timeTableVersionsReceived = new int[1];
    private void loadTimeTableAsync() {
        loadingTimeTableReference = manager.getTimeTableAsyncWithAdjustments(selectedWeek,
                (timeTable) -> {
                    if (timeTable == null) return;
                    timeTableVersionsReceived[0]++;
                    runOnUiThread(() -> {
                                if (!timeTableLoaded)
                                    if (timeTable.source == TimeTableSource.Cache && !timeTable.isCacheStateConfirmed)
                                        Log.i(LogTags.Timing, String.format("Time from app start to cached timetable received: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    else if (timeTable.isCacheStateConfirmed) {
                                        Log.i(LogTags.Timing, String.format("Time from app start to cached timetable confirmed: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    } else {
                                        Log.i(LogTags.Timing, String.format("Time from app start to fetched timetable received: %d ms", StundenplanApplication.getMillisSinceAppStart()));
                                    }

                                if (timeTable.source != TimeTableSource.Cache){
                                    /*BuildersKt.launch(GlobalScope.INSTANCE,
                                            Dispatchers.getMain(),//context to be ran on
                                            CoroutineStart.DEFAULT,
                                            (coroutineScope, continuation) -> TimetableWidgetKt.updateWidgets(TimeTableViewActivity.this, continuation)
                                    );*/
                                }

                                timeTableLoaded = true;
                                currentTimeTable = timeTable;
                                updateTimeTableView(timeTable);
                            }
                    );


                    // add annotations for lessons with homework attached
                    int timeTableIndex = timeTableVersionsReceived[0];

                    HomeworkManager.INSTANCE.populateTimeTable(selectedWeek, timeTable);
                    if (timeTableIndex == timeTableVersionsReceived[0]) {
                        // update view to show homework annotations
                        runOnUiThread(() -> {
                            if(timeTableIndex == 0)
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
                    }else if(error == ResultType.CantLoadTimeTable){
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Stundenplan konnte nicht geladen werdern", Toast.LENGTH_SHORT).show();
                            updateTimeTableView(null);
                        });
                    }
                }
        );
        setTimetableSourceText(null, true);
    }

    private void checkForUpdatesAsync(){
        Log.i(LogTags.UI, "Checking for updates...");
        setTimetableSourceText(currentTimeTable, true);
        new Thread(() -> {
            if(!manager.apiClient.isLoggedIn)
                manager.login();
            long latestCounterValue = manager.apiClient.getLatestCounterValue(true);
            if(!manager.apiClient.isCounterConfirmed){
                Log.i(LogTags.UI, "Update check failed!");
                runOnUiThread(() -> setTimetableSourceText(currentTimeTable, false));
                return;
            }

            if (currentTimeTable != null && latestCounterValue == currentTimeTable.CounterValue) {
                // Counter state is always confirmed here
                currentTimeTable.isCacheStateConfirmed = true;
                currentTimeTable.timeOfConfirmation = manager.apiClient.timeOfConfirmation;
                runOnUiThread(() -> setTimetableSourceText(currentTimeTable, false));
                Log.i(LogTags.UI, "Update check completed! (no changes)");
                return;
            }

            try {
                currentTimeTable = manager.getTimetableForWeekFromRawCacheOrApi(selectedWeek);
                runOnUiThread(() -> updateTimeTableView(currentTimeTable));
            } catch (TimeTableLoadException e) {
                runOnUiThread(() -> setTimetableSourceText(currentTimeTable, false));
                Log.e(LogTags.Api, "Unable to re-fetch timetable");
            }finally {
                runOnUiThread(() -> setTimetableSourceText(currentTimeTable, false));
                Log.i(LogTags.UI, "Update check completed!");
            }
        }).start();
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

    private void updateTimeTableView(@Nullable TimeTable timeTable) {

        if (popup != null) popup.dismiss();

        setTimetableSourceText(timeTable, false);


        TimeTableViewUtils.updateTimeTableView(this, timeTable, formatter);

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


        TimeTableViewUtils.updateDayDisplayForWeek(this, selectedWeek);

        if (!nonCrucialUiLoaded && !isLoadingNonCrucialUi) {
            isLoadingNonCrucialUi = true;
            // Ensure non-crucial-ui is not loaded synchronously here
            new Thread(() -> runOnUiThread(this::loadNonCrucialUi)).start();
        }

        StatisticsManager.reportTimetableTime(StundenplanApplication.getMillisSinceAppStart());

        if (isInitialLoad) {
            Log.i(LogTags.Timing, String.format("Time from application start to cached timetable displayed: %d ms - DISPLAYED", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));
            isInitialLoad = false;
        }
    }

    private void setTimetableSourceText(@Nullable TimeTable timeTable, boolean isRefetching) {
        stateView.setText(Utils.getSourceText(timeTable, isRefetching));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(currentTimeTable != null)
            // Check for timetable updates
            checkForUpdatesAsync();
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
                intent.putExtra("year", selectedWeek.Year);
                intent.putExtra("week", selectedWeek.WeekOfYear);
                intent.putExtra("dayOfWeek", dayOfWeek);
                intent.putExtra("subjectAbbreviationHash", currentTimeTable.Lessons[dayOfWeek][period].SubjectShortName.hashCode());

                homeworkEditorLauncher.launch(intent);
                return true;
            }
            if (id == R.id.clearHomework) {
                HomeworkManager.INSTANCE.deleteNoteFor(selectedWeek, dayOfWeek, currentTimeTable.Lessons[dayOfWeek][period].SubjectShortName.hashCode());
                runOnUiThread(TimeTableViewActivity.this::onHomeworkDataChanged);
                return true;
            }
            return true;
        }
    }


    final int lessonMargin = 3;

    private void createTableLayout() {
        TimeTableViewUtils.createTableLayout(this, table, this::onLessonClicked);

        Log.i(LogTags.Timing, String.format("Time from application start to timetable view generated: %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));

        // If the cached version is available already, update the view directly
        if (loadingTimeTableReference != null && loadingTimeTableReference.get() != null) {
            Log.i(LogTags.UI, "Updating view directly after creating it.");
            updateTimeTableView(loadingTimeTableReference.get());
        }
    }

    private void onHomeworkDataChanged(){
        updateHomeworkAnnotations();
        if(nonCrucialViewModel != null) nonCrucialViewModel.loadHomework(new Continuation<Unit>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {}
        });
    }
    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
        //return getSharedPreferences("de.zenonet.stundenplan", MODE_PRIVATE);
    }

    private boolean nonCrucialUiLoaded = false;

    private boolean isLoadingNonCrucialUi = false;

    NonCrucialViewModel nonCrucialViewModel;
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


        if (isPreview) {
            try {
                nonCrucialViewModel = new NonCrucialViewModel(null, null, Utils.getPreviewTimeTable(this));
            } catch (IOException e) {
                return;
            }
        } else {
            nonCrucialViewModel = new NonCrucialViewModel(StundenplanApplication.getAuxiliaryManager(), null, null);
        }

        NonCrucialUiKt.applyUiToComposeView(cv, nonCrucialViewModel);
        nonCrucialUiLoaded = true;

        Log.i(LogTags.Timing, String.format("Time from application start to non-crucial-ui loaded : %d ms", Duration.between(StundenplanApplication.applicationEntrypointInstant, Instant.now()).toMillis()));


    }

}