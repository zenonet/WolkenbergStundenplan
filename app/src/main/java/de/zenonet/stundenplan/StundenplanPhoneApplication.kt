package de.zenonet.stundenplan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import de.zenonet.stundenplan.BackroundWorkers.UpdateTimeTableWorker
import de.zenonet.stundenplan.broadcastReceivers.BackgroundUpdater
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.glance.areWidgetsExistent
import java.time.Duration
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StundenplanPhoneApplication : StundenplanApplication() {
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        scheduleUpdateRepeating()
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(
            NotificationChannel(
                STATUS_CHANNEL_ID,
                "Stundenplan Informationen",
                NotificationManager.IMPORTANCE_LOW
            ).also {
                it.description = "Zeigt die nächste Stunde als Benachrichtigung an."
            }
        )

        notificationManager.createNotificationChannel(
            NotificationChannel(
                TIME_TABLE_UPDATE_CHANNEL_ID,
                "Änderungen im Stundenplan",
                NotificationManager.IMPORTANCE_DEFAULT
            ).also {
                it.description = "Zeigt Benachrichtigungen, wenn dein Stundenplan sich ändert."
            }
        )
    }

    fun scheduleUpdateRepeating() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!preferences.getBoolean(
                "showNotifications",
                false
            ) && !preferences.getBoolean("showChangeNotifications", false) && !areWidgetsExistent(
                this
            )
        ) return


        scheduleWorkRequests()

        /*

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(BackgroundUpdater.class, 30, TimeUnit.MINUTES)
                .build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork("timetable_notification_update_worker", ExistingPeriodicWorkPolicy.KEEP, workRequest);
*/
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val intent = Intent(
            this,
            BackgroundUpdater::class.java
        )
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val interval =
            if (preferences.getBoolean("showNotifications", false)) AlarmManager.INTERVAL_HALF_HOUR
            else AlarmManager.INTERVAL_HOUR * 2

        alarmManager.setRepeating(
            AlarmManager.RTC, Calendar.getInstance().timeInMillis - 1,
            interval, pendingIntent
        )
        Log.i(
            LogTags.BackgroundWork,
            String.format(
                "Scheduled alarm for background work every %d hours",
                interval / AlarmManager.INTERVAL_HOUR
            )
        )
    }

    private fun scheduleWorkRequests() {
        val workRequest: PeriodicWorkRequest =
            PeriodicWorkRequest.Builder(UpdateTimeTableWorker::class.java, 4, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
                .build()

        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            "timetable_update_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        const val STATUS_CHANNEL_ID: String = "STUNDENPLANSTATUS"
        const val TIME_TABLE_UPDATE_CHANNEL_ID: String = "STUNDENPLANSHORTTERMCHANGES"
    }
}