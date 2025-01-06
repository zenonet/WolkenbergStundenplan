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
import java.time.Duration
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StundenplanPhoneApplication() : StundenplanApplication() {
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
        // Schedule work requests for update checks
        scheduleWorkRequests()

        // Schedule alarm for status notifications
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!preferences.getBoolean("showNotifications", false)) return

        /*

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(BackgroundUpdater.class, 30, TimeUnit.MINUTES)
                .build();

        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork("timetable_notification_update_worker", ExistingPeriodicWorkPolicy.KEEP, workRequest);
*/
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, BackgroundUpdater::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)



        alarmManager.setRepeating(
            AlarmManager.RTC, Calendar.getInstance().timeInMillis - 1,
            AlarmManager.INTERVAL_HOUR, pendingIntent
        )
        Log.i(LogTags.BackgroundWork, "Scheduled alarm for status notifications")
    }

    private fun scheduleWorkRequests() {

        val workRequest: PeriodicWorkRequest =
            PeriodicWorkRequest.Builder(UpdateTimeTableWorker::class.java, 2, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
                .build()

        val workManager = WorkManager.getInstance(this)

        workManager.enqueueUniquePeriodicWork(
            "timetable_update_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.i(LogTags.BackgroundWork, "Created/Updated periodic worker")
    }

    companion object {
        const val STATUS_CHANNEL_ID: String = "STUNDENPLANSTATUS"
        const val TIME_TABLE_UPDATE_CHANNEL_ID: String = "STUNDENPLANSHORTTERMCHANGES"
    }
}