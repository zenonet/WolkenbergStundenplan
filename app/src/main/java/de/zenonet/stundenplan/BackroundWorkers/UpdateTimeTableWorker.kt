package de.zenonet.stundenplan.BackroundWorkers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.zenonet.stundenplan.StundenplanPhoneApplication
import de.zenonet.stundenplan.activities.TimeTableViewActivity
import de.zenonet.stundenplan.common.DataNotAvailableException
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.R
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException
import de.zenonet.stundenplan.glance.updateWidgets
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateTimeTableWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(LogTags.BackgroundWork, "Running background update check...")

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        try {
            // load relevant timetable
            val client = TimeTableManager()
            client.init(applicationContext)
            // remember the local counter value of the relevant week before potentially overriding it
            val localTimeTable = try {
                client.cacheClient.getTimeTableForWeek(Timing.getRelevantWeekOfYear())
            } catch (_: TimeTableLoadException) {
                null
            }
            val timeTable = loadTimeTableAsync(client).await()

            updateWidgets(applicationContext)

            // check if notifications can and should be sent
            if (!PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean("showChangeNotifications", false) ||
                ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()

            // According to the API, the timetable changed
            if (localTimeTable != null && timeTable.CounterValue != localTimeTable.CounterValue) {
                Log.i(LogTags.BackgroundWork, "got new timetable from API")
                // this comparison is a custom implementation that compares per lesson
                if (timeTable != localTimeTable) {
                    Log.i(LogTags.BackgroundWork, "new timetable is different, notifying user...")

                    val intent = Intent(applicationContext, TimeTableViewActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                    // TODO: Make this detect exactly what changed and explain it in the notification content text
                    // Create notification
                    val notification = NotificationCompat.Builder(
                        applicationContext,
                        StundenplanPhoneApplication.TIME_TABLE_UPDATE_CHANNEL_ID
                    )
                        .setContentTitle("Stundenplanänderung")
                        .setContentText("Dein Stundenplan hat sich geändert. Sie ihn Dir an!")
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .build()

                    notificationManager.notify(timeTable.CounterValue.hashCode(), notification)

                } else {
                    Log.i(
                        LogTags.BackgroundWork,
                        "new timetable is identical to old one, ignoring..."
                    )
                }
            }

        } catch (_: DataNotAvailableException) {
            return Result.retry()
        }

        return Result.success()
    }


    private suspend fun loadTimeTableAsync(ttm: TimeTableManager): Deferred<TimeTable> =
        coroutineScope {
            async<TimeTable> {
                withContext(Dispatchers.IO) {
                    ttm.login()
                    ttm.getTimeTableForWeek(Timing.getRelevantWeekOfYear())
                }
            }
        }
}