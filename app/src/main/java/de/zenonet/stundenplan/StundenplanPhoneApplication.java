package de.zenonet.stundenplan;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.Calendar;

import de.zenonet.stundenplan.broadcastReceivers.BackgroundUpdater;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.StundenplanApplication;
import de.zenonet.stundenplan.glance.TimetableWidgetKt;

public class StundenplanPhoneApplication extends StundenplanApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        scheduleUpdateRepeating();
    }

    public static final String STATUS_CHANNEL_ID = "STUNDENPLANSTATUS";
    public static final String SHORT_TERM_CHANGES_CHANNEL_ID = "STUNDENPLANSHORTTERMCHANGES";

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(STATUS_CHANNEL_ID, "Stundenplan Informationen", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Zeigt die nächste Stunde als Benachrichtigung an.");
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);


        channel = new NotificationChannel(SHORT_TERM_CHANGES_CHANNEL_ID, "Kurzfristige Stundenplan-Änderungen", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Zeigt Benachrichtigungen, wenn der Stundenplan sich kurzfristig verändert.");
        notificationManager.createNotificationChannel(channel);
    }

    public void scheduleUpdateRepeating() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(!preferences.getBoolean("showNotifications", false) && !preferences.getBoolean("showChangeNotifications", false) && !TimetableWidgetKt.areWidgetsExistent(this))
            return;
/*

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(BackgroundUpdater.class, 30, TimeUnit.MINUTES)
                .build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork("timetable_notification_update_worker", ExistingPeriodicWorkPolicy.KEEP, workRequest);
*/

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, BackgroundUpdater.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        final long interval = preferences.getBoolean("showNotifications", false) ? AlarmManager.INTERVAL_HALF_HOUR : AlarmManager.INTERVAL_HOUR * 2;

        alarmManager.setRepeating(AlarmManager.RTC, Calendar.getInstance().getTimeInMillis()-1,
                interval, pendingIntent);
        Log.i(LogTags.BackgroundWork, String.format("Scheduled alarm for background work every %d hours", interval / AlarmManager.INTERVAL_HOUR));
    }
}
