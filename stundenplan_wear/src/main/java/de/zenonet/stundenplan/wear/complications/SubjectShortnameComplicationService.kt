package de.zenonet.stundenplan.wear.complications


import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import java.util.Calendar

class SubjectShortnameComplicationService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "Geo")
                .build(),
            contentDescription = PlainComplicationText.Builder(text = "Aktuelles Fach")
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
            val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6))
            if(dayOfWeek > 4 || currentPeriod == -1) {
                listener.onComplicationData(null)
                return@Thread
            }

            val manager = TimeTableManager()
            manager.init(this)

            val timetable = manager.getCurrentTimeTable()
            Log.i(Utils.LOG_TAG, "Successfully loaded current timetable for subject complication")

            if (currentPeriod >= timetable.Lessons[dayOfWeek].size) {
                listener.onComplicationData(null)
                return@Thread
            }

            val text = timetable.Lessons[dayOfWeek][currentPeriod].SubjectShortName

            Log.i(Utils.LOG_TAG, "Setting complication value '$text'")
            val data = when (request.complicationType) {

                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = text).build(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Aktuelles Fach").build()
                )
                    .build()

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
