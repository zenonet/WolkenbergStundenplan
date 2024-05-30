package de.zenonet.stundenplan;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;

public class BackgroundUpdater extends Worker {

    public BackgroundUpdater(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int dayOfWeek = Timing.getCurrentDayOfWeek();

        // Ensure it's a weekday
        if (dayOfWeek > 4)
            return Result.success();

        int nextPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6));
        // nextPeriod = 2;
        Context context = getApplicationContext();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Ensure it is school-time
        if (nextPeriod == -1) {
            notificationManager.cancel(666);
            return Result.success();
        }

        // Ensure the app is permitted to send notifications
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return Result.failure();

        // Init the TimeTable client
        TimeTableManager client = new TimeTableManager();
        try {
            client.init(context);

            TimeTable timeTable;
            try {
                client.login();
                timeTable = client.getCurrentTimeTable();
            } catch (DataNotAvailableException e) {
                return Result.failure();
            }
            manageShortTermChanges(timeTable);
            //int dayOfWeek = 0;

            // Ensure it is school-time but more accurately
            if (nextPeriod >= timeTable.Lessons[dayOfWeek].length || timeTable.Lessons[dayOfWeek][nextPeriod] == null) {
                notificationManager.cancel(666);
                return Result.success();
            }

            Lesson nextLesson = timeTable.Lessons[dayOfWeek][nextPeriod];
            Lesson lessonAfterThat = timeTable.Lessons[dayOfWeek].length > nextPeriod + 1 ? timeTable.Lessons[dayOfWeek][nextPeriod + 1] : null;


            // Just don't show a notification if the next lesson is not taking place
            if (!nextLesson.isTakingPlace()) {
                notificationManager.cancel(666);

                return Result.failure(); // TODO: Make it create a notification here, that says when the next lesson starts
            }
            Formatter formatter = new Formatter(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanApplication.CHANNEL_ID)
                    .setSmallIcon(de.zenonet.stundenplan.common.R.mipmap.ic_launcher)
                    .setContentTitle(String.format("%s %s mit %s bis %s",
                            formatter.formatRoomName(nextLesson.Room),
                            nextLesson.Subject,
                            formatter.formatTeacherName(nextLesson.Teacher),
                            nextLesson.EndTime))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSilent(true);

            if (lessonAfterThat != null && lessonAfterThat.isTakingPlace()) {
                builder.setContentText(String.format("Danach: %s %s mit %s",
                        formatter.formatRoomName(lessonAfterThat.Room),
                        lessonAfterThat.Subject,
                        formatter.formatTeacherName(lessonAfterThat.Teacher)
                ));
            }

            notificationManager.notify(666, builder.build());
            return Result.success();

        } catch (UserLoadException e) {
            return Result.failure();
        }
    }

    // This is not tested
    private void manageShortTermChanges(TimeTable timeTable) {
        Context context = getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCounter = preferences.getLong("shortTermChanges_lastCounter", -1);

        if (lastCounter == -1 || timeTable.CounterValue <= lastCounter) return;

        File cacheFile = new File(context.getCacheDir(), "/shortTermChangeWatcherCache.json");
        try {

            TimeTable oldTimeTable = new Gson().fromJson(new FileReader(cacheFile), TimeTable.class);
            Lesson[] oldDay = oldTimeTable.Lessons[Timing.getCurrentDayOfWeek()];
            Lesson[] day = timeTable.Lessons[Timing.getCurrentDayOfWeek()];

            for (int i = 0; i < 8; i++) {
                if(oldDay[i].equals(day[i])) continue;
                Lesson l = day[i];
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                // Show notification
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanApplication.CHANNEL_ID) // TODO: Add custom channel id
                        .setSmallIcon(de.zenonet.stundenplan.common.R.mipmap.ic_launcher)
                        .setContentTitle("Kurzfristige Stundenplanänderung!")
                        .setContentText(String.format("Heute %d. Stunde: %s in %s mit %s", i, l.SubjectShortName, l.Room, l.Teacher))
                        .setPriority(NotificationCompat.PRIORITY_HIGH);
                
                notificationManager.notify(444+i, builder.build());
            }

            preferences.edit().putLong("shortTermChanges_lastHashCode", timeTable.CounterValue).apply();
            Utils.writeAllText(cacheFile, new Gson().toJson(timeTable));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

