package de.zenonet.stundenplan.wear.complications


import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.LogTags

class SubjectShortnameComplicationService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "Ch")
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

            val lesson = getCurrentLesson(this)

            Log.i(LogTags.Complications, "Successfully loaded current timetable for subject complication")

            if (lesson == null || !lesson.isTakingPlace) {
                listener.onComplicationData(request.getEmptyData())
                return@Thread
            }

            val text = lesson.SubjectShortName

            Log.i(LogTags.Complications, "Setting complication value '$text'")
            val data = when (request.complicationType) {

                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = text).build(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Aktuelles Fach").build()
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
