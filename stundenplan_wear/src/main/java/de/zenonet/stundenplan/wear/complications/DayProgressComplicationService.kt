package de.zenonet.stundenplan.wear.complications


import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.RangedValueComplicationData
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
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
            if(dayOfWeek < 0 || dayOfWeek > 4){
                listener.onComplicationData(request.getEmptyData())
                return@Thread
            }

            val timeTable = getTimeTable(this)
            if(timeTable == null){
                listener.onComplicationData(request.getEmptyData())
                return@Thread
            }
            val day: Array<Lesson?> = timeTable.Lessons[dayOfWeek]

            val firstLesson = day.first{ it != null && it.isTakingPlace }
            val lastLesson = day.last{ it != null && it.isTakingPlace }

            if(firstLesson == null || lastLesson == null){
                listener.onComplicationData(request.getEmptyData())
                return@Thread
            }

            val totalSecondsToday = lastLesson.EndTime.toSecondOfDay() - firstLesson.StartTime.toSecondOfDay()

            val progressInSeconds = Timing.getCurrentTime().toSecondOfDay() - firstLesson.StartTime.toSecondOfDay().toLong()
            val progress = progressInSeconds.toFloat() / totalSecondsToday
            Log.i(LogTags.Complications, "Setting complication data to day progress: ${(progress*100).roundToInt()}%")

            val data = when (request.complicationType) {

                ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                    min = 1f,
                    max = day.size.toFloat(),
                    value = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6)).toFloat(),
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
