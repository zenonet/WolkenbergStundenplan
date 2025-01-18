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
import androidx.glance.appwidget.updateAll
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.zenonet.stundenplan.StundenplanPhoneApplication
import de.zenonet.stundenplan.activities.TimeTableViewActivity
import de.zenonet.stundenplan.common.DataNotAvailableException
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.R
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.glance.TimetableWidget
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max

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
            Log.i(LogTags.BackgroundWork, "Loaded local timetable")

            val timeTable = loadTimeTableAsync(client).await()
            Log.i(LogTags.BackgroundWork, "Loaded timetable from API")

            TimetableWidget().updateAll(applicationContext)

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

                    val changes = getChanges(Formatter(applicationContext), localTimeTable, timeTable)
                    val changeListString = changes.take(3).map { it.prependIndent("- ") }.joinToString("\n")
                    // Create notification

                    val notification = NotificationCompat.Builder(
                        applicationContext,
                        StundenplanPhoneApplication.TIME_TABLE_UPDATE_CHANNEL_ID
                    )
                        .setContentTitle("Stundenplanänderung")
                        //.setContentText("Dein Stundenplan hat sich geändert. Sie ihn Dir an!")
                        .setContentText(changeListString)
                        .setStyle(NotificationCompat.InboxStyle().also{
                            changes.take(9).forEach { change -> it.addLine(change) }
                        })
/*                        .setStyle(NotificationCompat.BigTextStyle()
                            .bigText(changeListString))*/
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setSmallIcon(R.drawable.ic_notification_icon)
                        .build()

                    notificationManager.notify(timeTable.CounterValue.hashCode(), notification)

                } else {
                    Log.i(
                        LogTags.BackgroundWork,
                        "new timetable is identical to old one, ignoring..."
                    )
                }
            }else{
                Log.i(LogTags.BackgroundWork, "counter value didn't change, done!")
            }

        } catch (_: DataNotAvailableException) {
            return Result.retry()
        }

        return Result.success()
    }

    private fun getChanges(formatter: Formatter, timeTableA: TimeTable, timeTableB: TimeTable): ArrayList<String>{
        val changes = ArrayList<String>()
        for(dayI in 0..4){
            for(period in 0..max(timeTableA.Lessons[dayI].size, timeTableB.Lessons[dayI].size)-1){
                val lessonA:Lesson? = if(period < timeTableA.Lessons[dayI].size) timeTableA.Lessons[dayI][period] else null
                val lessonB:Lesson? = if(period < timeTableB.Lessons[dayI].size) timeTableB.Lessons[dayI][period] else null

                if((lessonA == null && lessonB == null) || lessonA == lessonB) continue

                if(Lesson.doesTakePlace(lessonA) && !Lesson.doesTakePlace(lessonB)){
                    changes.add("${Utils.getWordForDayOfWeek(dayI)} ${period+1}. Std: Ausfall")
                    continue
                }

                if(!Lesson.doesTakePlace(lessonA) && Lesson.doesTakePlace(lessonB)){
                    changes.add("${Utils.getWordForDayOfWeek(dayI)} ${period+1}. Std: Zusatzstunde ${lessonB!!.SubjectShortName} " +
                            "mit ${formatter.formatTeacherName(lessonB.Teacher)} " +
                            "in ${formatter.formatRoomName(lessonB.Room)}"
                    )
                    continue
                }

                if(Lesson.doesTakePlace(lessonA) && Lesson.doesTakePlace(lessonB)){
                    if(lessonA!!.Subject != lessonB!!.Subject){

                    }
                    changes.add(
                        "${Utils.getWordForDayOfWeek(dayI)} ${period+1}. Std: ${lessonB.SubjectShortName} " +
                                "mit ${formatter.formatTeacherName(lessonB.Teacher)} " +
                                "in ${formatter.formatRoomName(lessonB.Room)}"
                    )
                    continue
                }
            }
        }
        return changes
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