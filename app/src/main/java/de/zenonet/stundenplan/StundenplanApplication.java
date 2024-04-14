package de.zenonet.stundenplan;

import android.app.*;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class StundenplanApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        scheduleUpdateRepeating();
    }

    public static final String CHANNEL_ID = "STUNDENPLANINFO";
    private AlarmManager alarmManager;

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

    private void scheduleUpdateRepeating() {
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, BackgroundUpdater.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(StundenplanApplication.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {

        }
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, Calendar.getInstance().getTimeInMillis()-1,
                AlarmManager.INTERVAL_HALF_HOUR, pendingIntent);
    }
}
