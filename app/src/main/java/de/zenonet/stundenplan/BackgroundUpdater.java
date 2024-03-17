package de.zenonet.stundenplan;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.time.LocalTime;
import java.util.Calendar;

public class BackgroundUpdater extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        LocalTime time = LocalTime.now();
        //LocalTime time = LocalTime.of(10, 50);
        int nextPeriod = TimeTableClient.getCurrentPeriod(time.plusMinutes(45));

        // Ensure it is schooltime
        if (nextPeriod == -1) return;

        Calendar cal = Calendar.getInstance();

        // Ensure it's a weekday
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            return;

        // Ensure the app is permitted to send notifications
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;

        // Init the TimeTable client
        TimeTableClient client = new TimeTableClient();
        client.init(context);

        client.loadTimeTableAsync(cal.get(Calendar.WEEK_OF_YEAR), timeTable -> {
            cal.get(Calendar.DAY_OF_WEEK);
            int dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) - 2);
            //int dayOfWeek = 0;

            Lesson nextLesson = timeTable.Lessons[dayOfWeek][nextPeriod];
            Lesson lessonAfterThat = timeTable.Lessons[dayOfWeek].length > nextPeriod + 1 ? timeTable.Lessons[dayOfWeek][nextPeriod + 1] : null;

            // Just don't show a notification if the next lesson is not taking place
            if(!nextLesson.isTakingPlace()) return; // TODO: Make it create a notification here, that says when the next lesson starts

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StundenplanApplication.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(String.format("%s %s mit %s bis %s", nextLesson.Room, nextLesson.Subject, nextLesson.Teacher, nextLesson.EndTime))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSilent(true);

            if (lessonAfterThat != null) {
                builder.setContentText(String.format("Danach: %s %s mit %s", lessonAfterThat.Room, lessonAfterThat.Subject, lessonAfterThat.Teacher));
            }

            notificationManager.notify(666, builder.build());
        }, true);
    }
}

