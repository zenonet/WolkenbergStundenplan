package de.zenonet.stundenplan;

import android.app.*;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import de.zenonet.stundenplan.common.Utils;

public class StundenplanApplication extends Application {
    public static Instant applicationEntrypointInstant;
    @Override
    public void onCreate() {
        super.onCreate();
        applicationEntrypointInstant = Instant.now();
        Utils.CachePath = this.getCacheDir().getPath();

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

    private void scheduleUpdateRepeating() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(BackgroundUpdater.class, 30, TimeUnit.MINUTES)
                .build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork("timetable_notification_update_worker", ExistingPeriodicWorkPolicy.KEEP, workRequest);
    }
}
