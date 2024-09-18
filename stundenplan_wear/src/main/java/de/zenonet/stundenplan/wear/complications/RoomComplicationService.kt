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

class RoomComplicationService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "D07")
                .build(),
            contentDescription = PlainComplicationText.Builder(text = "Aktueller Raum")
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
            if (dayOfWeek > 4 || currentPeriod == -1) {
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

            Log.i(LogTags.Complications, "Successfully loaded current timetable for room complication")
            if (currentPeriod >= timetable.Lessons[dayOfWeek].size) {
                listener.onComplicationData(null)
                return@Thread
            }

            val text = timetable.Lessons[dayOfWeek][currentPeriod].Room

            Log.i(LogTags.Complications, "Setting complication value '$text'")
            val data = when (request.complicationType) {

                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = text).build(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Aktueller Raum").build()
                )
                    .build()

                else -> {
                    if (Log.isLoggable(LogTags.Complications, Log.WARN)) {
                        Log.w(LogTags.Complications, "Unexpected complication type ${request.complicationType}")
                    }
                    null
                }
            }
            listener.onComplicationData(data)
        }.start()
    }
}
