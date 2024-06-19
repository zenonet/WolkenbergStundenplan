package de.zenonet.stundenplan;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

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
import java.util.Locale;

import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;

public class BackgroundUpdater extends BroadcastReceiver {

    private Context context;

    // This is not tested
    private void manageShortTermChanges(TimeTable timeTable) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCounter = preferences.getLong("shortTermChanges_lastCounter", -1);

        if (lastCounter == -1 || timeTable.CounterValue <= lastCounter) return;

        File cacheFile = new File(context.getCacheDir(), "/shortTermChangeWatcherCache.json");
        try {

            TimeTable oldTimeTable = new Gson().fromJson(new FileReader(cacheFile), TimeTable.class);
            Lesson[] oldDay = oldTimeTable.Lessons[Timing.getCurrentDayOfWeek()];
            Lesson[] day = timeTable.Lessons[Timing.getCurrentDayOfWeek()];

            for (int i = 0; i < 8; i++) {
                if (oldDay[i].equals(day[i])) continue;
                Lesson l = day[i];
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

                // Show notification
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanPhoneApplication.CHANNEL_ID) // TODO: Add custom channel id
                        .setSmallIcon(de.zenonet.stundenplan.common.R.mipmap.ic_launcher)
                        .setContentTitle("Kurzfristige Stundenplanänderung!")
                        .setContentText(String.format(Locale.GERMANY, "Heute %d. Stunde: %s in %s mit %s", i, l.SubjectShortName, l.Room, l.Teacher))
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                notificationManager.notify(444 + i, builder.build());
            }

            preferences.edit().putLong("shortTermChanges_lastHashCode", timeTable.CounterValue).apply();
            Utils.writeAllText(cacheFile, new Gson().toJson(timeTable));
        } catch (IOException | SecurityException e) {
            Log.e(Utils.LOG_TAG, e.getMessage());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        int dayOfWeek = Timing.getCurrentDayOfWeek();

        // Ensure it's a weekday
        if (dayOfWeek > 4)
            return;

        int nextPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6));
        // nextPeriod = 2;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Ensure it is school-time
        if (nextPeriod == -1) {
            notificationManager.cancel(666);
            return;
        }

        // Ensure the app is permitted to send notifications
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;

        // Init the TimeTable client
        TimeTableManager client = new TimeTableManager();
        try {
            client.init(context);

            TimeTable timeTable;
            try {
                client.login();
                timeTable = client.getCurrentTimeTable();
            } catch (DataNotAvailableException e) {
                return;
            }
            manageShortTermChanges(timeTable);
            //int dayOfWeek = 0;

            if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("showNotifications", false))
                return;

            // Ensure it is school-time but more accurately
            if (nextPeriod >= timeTable.Lessons[dayOfWeek].length || timeTable.Lessons[dayOfWeek][nextPeriod] == null) {
                notificationManager.cancel(666);
                return;
            }

            Lesson nextLesson = timeTable.Lessons[dayOfWeek][nextPeriod];
            Lesson lessonAfterThat = timeTable.Lessons[dayOfWeek].length > nextPeriod + 1 ? timeTable.Lessons[dayOfWeek][nextPeriod + 1] : null;


            // Just don't show a notification if the next lesson is not taking place
            if (!nextLesson.isTakingPlace()) {
                notificationManager.cancel(666);
                return; // TODO: Make it create a notification here, that says when the next lesson starts
            }
            Formatter formatter = new Formatter(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanPhoneApplication.CHANNEL_ID)
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

        } catch (UserLoadException e) {
        }
    }
}

