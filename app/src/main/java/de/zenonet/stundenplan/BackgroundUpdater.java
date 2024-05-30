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
}

