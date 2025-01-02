package de.zenonet.stundenplan.wear.complications


import android.preference.PreferenceManager
import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager

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
            if(dayOfWeek > 4 || dayOfWeek < 0 || currentPeriod == -1) {
                listener.onComplicationData(null)
                return@Thread
            }

            val isPreview = PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application)
                .getBoolean("showPreview", false)

            val timetable: TimeTable;
            if(isPreview) {
                timetable = Utils.getPreviewTimeTable(StundenplanApplication.application)
            }else{
                val manager = TimeTableManager()
                manager.init(this)
                timetable = manager.getCurrentTimeTable()
            }

            Log.i(LogTags.Complications, "Successfully loaded current timetable for subject complication")

            if (currentPeriod >= timetable.Lessons[dayOfWeek].size) {
                listener.onComplicationData(null)
                return@Thread
            }

            val text = timetable.Lessons[dayOfWeek][currentPeriod].SubjectShortName

            Log.i(LogTags.Complications, "Setting complication value '$text'")
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
