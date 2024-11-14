package de.zenonet.stundenplan.broadcastReceivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import de.zenonet.stundenplan.StundenplanPhoneApplication;
import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.Formatter;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.Timing;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.timetableManagement.Lesson;
import de.zenonet.stundenplan.common.timetableManagement.TimeTable;
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;

public class BackgroundUpdater extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LogTags.BackgroundWork, "Running background updater...");
        int dayOfWeek = Timing.getCurrentDayOfWeek();

        // Ensure it's a weekday
        if (dayOfWeek > 4 || dayOfWeek < 0)
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

        new Thread(() -> {
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

                if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("showNotifications", false))
                    return;

                // Ensure it is school-time but more accurately
                if (nextPeriod >= timeTable.Lessons[dayOfWeek].length || timeTable.Lessons[dayOfWeek][nextPeriod] == null) {
                    notificationManager.cancel(666);
                    return;
                }

                Lesson nextLesson = timeTable.Lessons[dayOfWeek][nextPeriod];


                // Just don't show a notification if the next lesson is not taking place
                if (!nextLesson.isTakingPlace()) {
                    notificationManager.cancel(666);
                    return; // TODO: Make it create a notification here, that says when the next lesson starts
                }
                int count = 1;
                for(int i = nextPeriod+1; i < timeTable.Lessons[dayOfWeek].length; i++){
                    Lesson lesson = timeTable.Lessons[dayOfWeek][i];
                    if(lesson == null || lesson.Type != nextLesson.Type || !lesson.SubjectShortName.equals(nextLesson.SubjectShortName) || !lesson.Room.equals(nextLesson.Room))
                        break;
                    count++;
                }
                Formatter formatter = new Formatter(context);
                String title = String.format("%s in %s mit %s bis %s",
                            nextLesson.Subject,
                            formatter.formatRoomName(nextLesson.Room),
                            formatter.formatTeacherName(nextLesson.Teacher),
                            nextLesson.EndTime);
                if(count > 1){
                    title = String.format(Locale.GERMANY, "%dx %s", count, title);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanPhoneApplication.STATUS_CHANNEL_ID)
                        .setSmallIcon(de.zenonet.stundenplan.common.R.drawable.ic_notification_icon)
                        .setContentTitle(title)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSilent(true);

                Lesson lessonAfterThat = timeTable.Lessons[dayOfWeek].length > nextPeriod + count ? timeTable.Lessons[dayOfWeek][nextPeriod + count] : null;

                if (lessonAfterThat != null && lessonAfterThat.isTakingPlace()) {
                    builder.setContentText(String.format("Danach: %s in %s mit %s",
                            lessonAfterThat.Subject,
                            formatter.formatRoomName(lessonAfterThat.Room),
                            formatter.formatTeacherName(lessonAfterThat.Teacher)
                    ));
                }

                notificationManager.notify(666, builder.build());

            } catch (UserLoadException e) {
            }
        }).start();
    }
}

