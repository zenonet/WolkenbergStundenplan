package de.zenonet.stundenplan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalTime;
import java.util.Calendar;

import de.zenonet.stundenplan.common.DataNotAvailableException;
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
        Calendar cal = Calendar.getInstance();

        // Ensure it's a weekday
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            return Result.success();

        int nextPeriod = Utils.getCurrentPeriod(LocalTime.now().plusMinutes(45));
        // nextPeriod = 2;
        Context context = getApplicationContext();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Ensure it is schooltime
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

            cal.get(Calendar.DAY_OF_WEEK);
            int dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - 2);
            //int dayOfWeek = 0;

            // Ensure it is schooltime (again)
            if (nextPeriod >= timeTable.Lessons[dayOfWeek].length) {
                notificationManager.cancel(666);
                return Result.success();
            }

            Lesson nextLesson = timeTable.Lessons[dayOfWeek][nextPeriod];
            Lesson lessonAfterThat = timeTable.Lessons[dayOfWeek].length > nextPeriod + 1 ? timeTable.Lessons[dayOfWeek][nextPeriod + 1] : null;


            // Just don't show a notification if the next lesson is not taking place
            if (!nextLesson.isTakingPlace()){
                notificationManager.cancel(666);

                return Result.failure(); // TODO: Make it create a notification here, that says when the next lesson starts
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanApplication.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(String.format("%s %s mit %s bis %s", nextLesson.Room, nextLesson.Subject, nextLesson.Teacher, nextLesson.EndTime))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSilent(true);

            if (lessonAfterThat != null && lessonAfterThat.isTakingPlace()) {
                builder.setContentText(String.format("Danach: %s %s mit %s", lessonAfterThat.Room, lessonAfterThat.Subject, lessonAfterThat.Teacher));
            }

            notificationManager.notify(666, builder.build());
            return Result.success();

        } catch (UserLoadException e) {
            return Result.failure();
        }
    }
}

