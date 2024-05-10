package de.zenonet.stundenplan.wear.complications


import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.RangedValueComplicationData
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import java.util.Calendar

class DayProgressComplicationService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return RangedValueComplicationData.Builder(
            min = 1f,
            max = 6f,
            value = 4f,
            contentDescription = PlainComplicationText.Builder(text = "Schul-Fortschritt des Tages")
                .build()
        )
            .build()
    }

    override fun onComplicationRequest(
        request: androidx.wear.complications.ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        Thread {

            val dayOfWeek = Timing.getCurrentDayOfWeek()
            val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime())
            if (dayOfWeek > 4 || currentPeriod == -1) {
                listener.onComplicationData(null)
                return@Thread
            }

            val manager = TimeTableManager()
            manager.init(this)
            val day: Array<Lesson> = manager.getCurrentTimeTable().Lessons[dayOfWeek]

            if (day == null || currentPeriod >= day.size) {
                listener.onComplicationData(null)
                return@Thread
            }

            val data = when (request.complicationType) {

                ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                    min = 1f,
                    max = day.size.toFloat(),
                    value = currentPeriod.toFloat(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Schul-Fortschritt des Tages").build()
                ).build()

                ComplicationType.SHORT_TEXT ->
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(text = ((currentPeriod / (day.size - 1) * 100).toString() + "%")).build(),
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
