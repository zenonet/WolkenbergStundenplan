package de.zenonet.stundenplan.wear.complications


import android.preference.PreferenceManager
import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.RangedValueComplicationData
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlin.math.roundToInt

class DayProgressComplicationService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                min = 1f,
                max = 6f,
                value = 4f,
                contentDescription = PlainComplicationText.Builder(text = "Schul-Fortschritt des Tages")
                    .build()
            )
                .build()

            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = "66%")
                        .build(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Schul-Fortschritt des Tages in %").build()
                ).build()

            else -> null!!
        }
    }

    override fun onComplicationRequest(
        request: androidx.wear.complications.ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        Thread {

            val dayOfWeek = Timing.getCurrentDayOfWeek()
            val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6))
            if (dayOfWeek > 4 || currentPeriod == -1) {
                listener.onComplicationData(null)
                return@Thread
            }

            val isPreview =
                PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application)
                    .getBoolean("showPreview", false)

            val timetable: TimeTable;
            if (isPreview) {
                timetable = Utils.getPreviewTimeTable(StundenplanApplication.application)
            } else {
                val manager = TimeTableManager()
                manager.init(this)
                timetable = manager.getCurrentTimeTable()
            }
            val day: Array<Lesson> = timetable.Lessons[dayOfWeek]

            if (currentPeriod >= day.size) {
                listener.onComplicationData(null)
                return@Thread
            }

            val firstLessonStart = Utils.getStartAndEndTimeOfPeriod(0).first;
            val totalSchooltimeTodaySeconds =
                Utils.getStartAndEndTimeOfPeriod(day.size - 1).second.toSecondOfDay() -
                        firstLessonStart.toSecondOfDay()

            val progressInSeconds =
                Timing.getCurrentTime().toSecondOfDay() - firstLessonStart.toSecondOfDay().toLong()
            val progress = progressInSeconds.toFloat() / totalSchooltimeTodaySeconds

            val data = when (request.complicationType) {

                ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                    min = 1f,
                    max = day.size.toFloat(),
                    value = currentPeriod.toFloat(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Schul-Fortschritt des Tages").build()
                ).setText(
                    PlainComplicationText.Builder(
                        text = ((progress * 100).roundToInt()
                            .toString() + "%")
                    ).build()
                ).build()

                ComplicationType.SHORT_TEXT ->
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(
                            text = ((progress * 100).roundToInt()
                                .toString() + "%")
                        )
                            .build(),
                        contentDescription = PlainComplicationText
                            .Builder(text = "Schul-Fortschritt des Tages in %").build()
                    ).build()

                else -> {
                    if (Log.isLoggable(TAG, Log.WARN)) {
                        Log.w(TAG, "Unexpected complication type ${request.complicationType}")
                    }
                    null
                }
            }
            listener.onComplicationData(data)
        }.start()
    }


    companion object {
        private const val TAG = "MyComplications"
    }
}
