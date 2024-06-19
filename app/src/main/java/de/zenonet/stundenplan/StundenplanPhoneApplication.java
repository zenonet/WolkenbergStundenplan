package de.zenonet.stundenplan;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Calendar;

import de.zenonet.stundenplan.common.StundenplanApplication;

public class StundenplanPhoneApplication extends StundenplanApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        scheduleUpdateRepeating();
    }

    public static final String CHANNEL_ID = "STUNDENPLANINFO";

    private void createNotificationChannel() {
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        CharSequence name = "Stundenplan Informationen";
        String description = "Zeigt die nächste Stunde als Benachrichtigung an.";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

    }

    public void scheduleUpdateRepeating() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(!preferences.getBoolean("showNotifications", false) && !preferences.getBoolean("showChangeNotifications", false))
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

        alarmManager.setRepeating(AlarmManager.RTC, Calendar.getInstance().getTimeInMillis()-1,
                AlarmManager.INTERVAL_HALF_HOUR, pendingIntent);
    }
}
